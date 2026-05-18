// Package store wraps the SQLite database. It uses the pure-Go driver
// modernc.org/sqlite, so the backend builds with plain `go build` — no CGO,
// no gcc/MinGW toolchain required (important on Windows and in slim containers).
package store

import (
	"context"
	"database/sql"
	"fmt"
	"strings"

	_ "modernc.org/sqlite" // registers the "sqlite" database/sql driver
)

// Store owns the *sql.DB connection pool and exposes all data operations.
type Store struct {
	db *sql.DB
}

// Open opens (creating if missing) the SQLite database at path, applies the
// connection PRAGMAs, and runs the schema migration.
func Open(path string) (*Store, error) {
	db, err := sql.Open("sqlite", path)
	if err != nil {
		return nil, fmt.Errorf("open sqlite: %w", err)
	}
	db.SetMaxOpenConns(1) // a single connection serialises all access

	if err := db.Ping(); err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("ping sqlite: %w", err)
	}

	s := &Store{db: db}
	if err := s.migrate(); err != nil {
		_ = db.Close()
		return nil, err
	}
	return s, nil
}

func (s *Store) migrate() error {
	for _, p := range []string{
		"PRAGMA journal_mode = WAL;",
		"PRAGMA foreign_keys = ON;",
		"PRAGMA busy_timeout = 5000;",
	} {
		if _, err := s.db.Exec(p); err != nil {
			return fmt.Errorf("pragma %q: %w", p, err)
		}
	}
	if _, err := s.db.Exec(schemaSQL); err != nil {
		return fmt.Errorf("apply schema: %w", err)
	}
	// Upgrade older databases — ignore "duplicate column" errors.
	for _, m := range migrations {
		if _, err := s.db.Exec(m); err != nil && !strings.Contains(err.Error(), "duplicate column") {
			return fmt.Errorf("migration %q: %w", m, err)
		}
	}
	// Ensure the single settings row exists.
	if _, err := s.db.Exec(
		`INSERT OR IGNORE INTO app_settings (id, default_capture_interval_sec) VALUES (1, 120)`,
	); err != nil {
		return fmt.Errorf("seed app_settings: %w", err)
	}
	return nil
}

// Close checkpoints the WAL and closes the database.
func (s *Store) Close() error {
	_, _ = s.db.Exec("PRAGMA wal_checkpoint(TRUNCATE);")
	return s.db.Close()
}

// ---- Locations ----

// InsertLocation upserts the device row, refreshes its battery level, and
// inserts one location fix — all inside a single transaction.
func (s *Store) InsertLocation(ctx context.Context, loc Location) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback() }()

	if _, err = tx.ExecContext(ctx,
		`INSERT INTO devices (id, name, first_seen_at, last_seen_at)
		 VALUES (?, '', ?, ?)
		 ON CONFLICT(id) DO UPDATE SET last_seen_at = excluded.last_seen_at`,
		loc.DeviceID, loc.ReceivedAt, loc.ReceivedAt,
	); err != nil {
		return fmt.Errorf("upsert device: %w", err)
	}

	if loc.Battery != nil {
		if _, err = tx.ExecContext(ctx,
			`UPDATE devices SET battery = ? WHERE id = ?`, *loc.Battery, loc.DeviceID,
		); err != nil {
			return fmt.Errorf("update battery: %w", err)
		}
	}

	if _, err = tx.ExecContext(ctx,
		`INSERT INTO locations
		   (device_id, lat, lng, accuracy, speed, bearing, battery, timestamp, received_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		loc.DeviceID, loc.Lat, loc.Lng, loc.Accuracy, loc.Speed, loc.Bearing,
		loc.Battery, loc.Timestamp, loc.ReceivedAt,
	); err != nil {
		return fmt.Errorf("insert location: %w", err)
	}

	return tx.Commit()
}

const deviceSelect = `
SELECT d.id, d.name, d.battery, d.capture_interval_sec, d.first_seen_at, d.last_seen_at,
       l.lat, l.lng, l.accuracy, l.speed, l.bearing, l.battery, l.timestamp, l.received_at
FROM devices d
LEFT JOIN locations l ON l.id = (
    SELECT id FROM locations
    WHERE device_id = d.id
    ORDER BY timestamp DESC, id DESC
    LIMIT 1
)`

// ListDevices returns every device with its most recent fix, newest-seen first.
func (s *Store) ListDevices(ctx context.Context) ([]DeviceWithLocation, error) {
	rows, err := s.db.QueryContext(ctx, deviceSelect+" ORDER BY d.last_seen_at DESC")
	if err != nil {
		return nil, err
	}
	defer func() { _ = rows.Close() }()

	var out []DeviceWithLocation
	for rows.Next() {
		dwl, err := scanDeviceRow(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, dwl)
	}
	return out, rows.Err()
}

// GetDevice returns one device; returns sql.ErrNoRows for an unknown id.
func (s *Store) GetDevice(ctx context.Context, id string) (DeviceWithLocation, error) {
	row := s.db.QueryRowContext(ctx, deviceSelect+" WHERE d.id = ?", id)
	return scanDeviceRow(row)
}

type scanner interface {
	Scan(dest ...any) error
}

func scanDeviceRow(sc scanner) (DeviceWithLocation, error) {
	var (
		dwl                     DeviceWithLocation
		battery, interval       sql.NullInt64
		lat, lng, acc, spd, brg sql.NullFloat64
		locBattery              sql.NullInt64
		ts, recv                sql.NullInt64
	)
	if err := sc.Scan(
		&dwl.ID, &dwl.Name, &battery, &interval, &dwl.FirstSeenAt, &dwl.LastSeenAt,
		&lat, &lng, &acc, &spd, &brg, &locBattery, &ts, &recv,
	); err != nil {
		return DeviceWithLocation{}, err
	}
	dwl.Battery = nullToIntPtr(battery)
	dwl.CaptureIntervalSec = nullToIntPtr(interval)
	if lat.Valid && lng.Valid {
		dwl.Last = &Location{
			DeviceID:   dwl.ID,
			Lat:        lat.Float64,
			Lng:        lng.Float64,
			Accuracy:   nullToFloatPtr(acc),
			Speed:      nullToFloatPtr(spd),
			Bearing:    nullToFloatPtr(brg),
			Battery:    nullToIntPtr(locBattery),
			Timestamp:  ts.Int64,
			ReceivedAt: recv.Int64,
		}
	}
	return dwl, nil
}

// Track returns a device's location history with timestamp >= since (unix ms),
// ascending, capped at limit rows.
func (s *Store) Track(ctx context.Context, deviceID string, since int64, limit int) ([]Location, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT lat, lng, accuracy, speed, bearing, battery, timestamp
		 FROM locations
		 WHERE device_id = ? AND timestamp >= ?
		 ORDER BY timestamp ASC
		 LIMIT ?`,
		deviceID, since, limit,
	)
	if err != nil {
		return nil, err
	}
	defer func() { _ = rows.Close() }()

	var out []Location
	for rows.Next() {
		var (
			loc           Location
			acc, spd, brg sql.NullFloat64
			battery       sql.NullInt64
		)
		if err := rows.Scan(&loc.Lat, &loc.Lng, &acc, &spd, &brg, &battery, &loc.Timestamp); err != nil {
			return nil, err
		}
		loc.DeviceID = deviceID
		loc.Accuracy = nullToFloatPtr(acc)
		loc.Speed = nullToFloatPtr(spd)
		loc.Bearing = nullToFloatPtr(brg)
		loc.Battery = nullToIntPtr(battery)
		out = append(out, loc)
	}
	return out, rows.Err()
}

// SetDeviceConfig updates a device's name and capture-interval override
// (intervalSec == nil clears the override → device uses the global default).
func (s *Store) SetDeviceConfig(ctx context.Context, id, name string, intervalSec *int) error {
	_, err := s.db.ExecContext(ctx,
		`UPDATE devices SET name = ?, capture_interval_sec = ? WHERE id = ?`,
		name, intervalSec, id,
	)
	return err
}

// ---- Settings ----

// DefaultInterval returns the global default capture interval (seconds).
func (s *Store) DefaultInterval(ctx context.Context) (int, error) {
	var v int
	err := s.db.QueryRowContext(ctx,
		`SELECT default_capture_interval_sec FROM app_settings WHERE id = 1`,
	).Scan(&v)
	if err != nil {
		return 120, err
	}
	return v, nil
}

// SetDefaultInterval updates the global default capture interval.
func (s *Store) SetDefaultInterval(ctx context.Context, sec int) error {
	_, err := s.db.ExecContext(ctx,
		`UPDATE app_settings SET default_capture_interval_sec = ? WHERE id = 1`, sec,
	)
	return err
}

// ---- Geofences ----

func (s *Store) ListGeofences(ctx context.Context) ([]Geofence, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT id, name, lat, lng, radius_m, created_at FROM geofences ORDER BY created_at DESC`)
	if err != nil {
		return nil, err
	}
	defer func() { _ = rows.Close() }()

	var out []Geofence
	for rows.Next() {
		var g Geofence
		if err := rows.Scan(&g.ID, &g.Name, &g.Lat, &g.Lng, &g.RadiusM, &g.CreatedAt); err != nil {
			return nil, err
		}
		out = append(out, g)
	}
	return out, rows.Err()
}

func (s *Store) CreateGeofence(ctx context.Context, g Geofence) (int64, error) {
	res, err := s.db.ExecContext(ctx,
		`INSERT INTO geofences (name, lat, lng, radius_m, created_at) VALUES (?, ?, ?, ?, ?)`,
		g.Name, g.Lat, g.Lng, g.RadiusM, g.CreatedAt,
	)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (s *Store) DeleteGeofence(ctx context.Context, id int64) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM geofences WHERE id = ?`, id)
	return err
}

// ---- Alerts ----

func (s *Store) ListAlerts(ctx context.Context, limit int) ([]Alert, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT id, device_id, type, severity, message, created_at, acknowledged
		 FROM alerts ORDER BY created_at DESC LIMIT ?`, limit)
	if err != nil {
		return nil, err
	}
	defer func() { _ = rows.Close() }()

	var out []Alert
	for rows.Next() {
		var (
			a   Alert
			ack int
		)
		if err := rows.Scan(&a.ID, &a.DeviceID, &a.Type, &a.Severity, &a.Message, &a.CreatedAt, &ack); err != nil {
			return nil, err
		}
		a.Acknowledged = ack != 0
		out = append(out, a)
	}
	return out, rows.Err()
}

func (s *Store) CreateAlert(ctx context.Context, a Alert) (int64, error) {
	res, err := s.db.ExecContext(ctx,
		`INSERT INTO alerts (device_id, type, severity, message, created_at, acknowledged)
		 VALUES (?, ?, ?, ?, ?, 0)`,
		a.DeviceID, a.Type, a.Severity, a.Message, a.CreatedAt,
	)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (s *Store) AckAlert(ctx context.Context, id int64) error {
	_, err := s.db.ExecContext(ctx, `UPDATE alerts SET acknowledged = 1 WHERE id = ?`, id)
	return err
}

// HasRecentAlert reports whether an unacknowledged alert of the given type for
// the device was created at or after sinceMs — used to de-duplicate alerts.
func (s *Store) HasRecentAlert(ctx context.Context, deviceID, alertType string, sinceMs int64) (bool, error) {
	var n int
	err := s.db.QueryRowContext(ctx,
		`SELECT COUNT(*) FROM alerts
		 WHERE device_id = ? AND type = ? AND created_at >= ? AND acknowledged = 0`,
		deviceID, alertType, sinceMs,
	).Scan(&n)
	return n > 0, err
}

// ---- helpers ----

func nullToFloatPtr(n sql.NullFloat64) *float64 {
	if !n.Valid {
		return nil
	}
	v := n.Float64
	return &v
}

func nullToIntPtr(n sql.NullInt64) *int {
	if !n.Valid {
		return nil
	}
	v := int(n.Int64)
	return &v
}
