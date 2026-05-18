// Package model defines the JSON wire formats shared between the Android app,
// the Go backend and the browser dashboard. Timestamps on the wire are RFC3339
// (UTC); internally the store keeps them as unix milliseconds.
package model

import "time"

// LocationReport is what an Android device POSTs to /api/locations.
// A request body may be a single object or a JSON array of these.
type LocationReport struct {
	DeviceID  string   `json:"device_id"`
	Lat       float64  `json:"lat"`
	Lng       float64  `json:"lng"`
	Accuracy  *float64 `json:"accuracy,omitempty"`
	Speed     *float64 `json:"speed,omitempty"`
	Bearing   *float64 `json:"bearing,omitempty"`
	Battery   *int     `json:"battery,omitempty"`   // 0-100
	Timestamp string   `json:"timestamp,omitempty"` // RFC3339; server substitutes receive time if empty
}

// LocationAck is the POST /api/locations response. CaptureIntervalSec tells the
// device how often it should capture location (dashboard-controlled).
type LocationAck struct {
	Status             string `json:"status"`
	Accepted           int    `json:"accepted"`
	ReceivedAt         string `json:"received_at"`
	CaptureIntervalSec int    `json:"capture_interval_sec"`
}

// BroadcastMessage is pushed over the dashboard WebSocket for each new fix.
type BroadcastMessage struct {
	Type       string   `json:"type"` // always "location"
	DeviceID   string   `json:"device_id"`
	DeviceName string   `json:"device_name,omitempty"`
	Lat        float64  `json:"lat"`
	Lng        float64  `json:"lng"`
	Accuracy   *float64 `json:"accuracy,omitempty"`
	Speed      *float64 `json:"speed,omitempty"`
	Bearing    *float64 `json:"bearing,omitempty"`
	Battery    *int     `json:"battery,omitempty"`
	Timestamp  string   `json:"timestamp"`
	ReceivedAt string   `json:"received_at"`
}

// SnapshotMessage is sent once, right after a dashboard connects.
type SnapshotMessage struct {
	Type    string             `json:"type"` // always "snapshot"
	Devices []BroadcastMessage `json:"devices"`
}

// LocationDTO is an embedded last-known position inside DeviceDTO.
type LocationDTO struct {
	Lat       float64  `json:"lat"`
	Lng       float64  `json:"lng"`
	Accuracy  *float64 `json:"accuracy,omitempty"`
	Speed     *float64 `json:"speed,omitempty"`
	Bearing   *float64 `json:"bearing,omitempty"`
	Timestamp string   `json:"timestamp"`
}

// DeviceDTO is returned by GET /api/devices and /api/devices/{id}.
type DeviceDTO struct {
	ID                   string       `json:"id"`
	Name                 string       `json:"name"`
	Status               string       `json:"status"` // online | offline | inactive
	Battery              *int         `json:"battery,omitempty"`
	CaptureIntervalSec   *int         `json:"capture_interval_sec,omitempty"`
	EffectiveIntervalSec int          `json:"effective_interval_sec"`
	FirstSeenAt          string       `json:"first_seen_at"`
	LastSeenAt           string       `json:"last_seen_at"`
	LastLocation         *LocationDTO `json:"last_location"`
}

// DeviceUpdateRequest is the PUT /api/devices/{id} body.
type DeviceUpdateRequest struct {
	Name               string `json:"name"`
	CaptureIntervalSec *int   `json:"capture_interval_sec"` // null clears the override
}

// TrackPoint is one point in a device's history.
type TrackPoint struct {
	Lat       float64  `json:"lat"`
	Lng       float64  `json:"lng"`
	Accuracy  *float64 `json:"accuracy,omitempty"`
	Speed     *float64 `json:"speed,omitempty"`
	Bearing   *float64 `json:"bearing,omitempty"`
	Battery   *int     `json:"battery,omitempty"`
	Timestamp string   `json:"timestamp"`
}

// TrackResponse is returned by GET /api/devices/{id}/track.
type TrackResponse struct {
	DeviceID string       `json:"device_id"`
	Points   []TrackPoint `json:"points"`
}

// SettingsDTO is the GET/PUT /api/settings body.
type SettingsDTO struct {
	DefaultCaptureIntervalSec int `json:"default_capture_interval_sec"`
}

// AlertDTO is one row of GET /api/alerts.
type AlertDTO struct {
	ID           int64  `json:"id"`
	DeviceID     string `json:"device_id"`
	Type         string `json:"type"`
	Severity     string `json:"severity"`
	Message      string `json:"message"`
	CreatedAt    string `json:"created_at"`
	Acknowledged bool   `json:"acknowledged"`
}

// GeofenceDTO is one row of GET /api/geofences.
type GeofenceDTO struct {
	ID        int64   `json:"id"`
	Name      string  `json:"name"`
	Lat       float64 `json:"lat"`
	Lng       float64 `json:"lng"`
	RadiusM   float64 `json:"radius_m"`
	CreatedAt string  `json:"created_at"`
}

// GeofenceRequest is the POST /api/geofences body.
type GeofenceRequest struct {
	Name    string  `json:"name"`
	Lat     float64 `json:"lat"`
	Lng     float64 `json:"lng"`
	RadiusM float64 `json:"radius_m"`
}

// MillisToRFC3339 formats a unix-millisecond timestamp as a UTC RFC3339 string.
func MillisToRFC3339(ms int64) string {
	return time.UnixMilli(ms).UTC().Format(time.RFC3339Nano)
}

// ParseTimestamp parses an RFC3339 string into unix milliseconds.
func ParseTimestamp(s string) (ms int64, ok bool) {
	if s == "" {
		return 0, false
	}
	t, err := time.Parse(time.RFC3339, s)
	if err != nil {
		return 0, false
	}
	return t.UnixMilli(), true
}
