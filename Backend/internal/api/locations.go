package api

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"

	"gpsbackend/internal/model"
	"gpsbackend/internal/store"
)

const maxBodyBytes = 1 << 20

// handlePostLocations accepts a single LocationReport or a JSON array of them.
// The response carries the device's effective capture interval so the app can
// adjust how often it reports (dashboard-controlled).
func (s *Server) handlePostLocations(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(io.LimitReader(r.Body, maxBodyBytes))
	if err != nil {
		writeError(w, http.StatusBadRequest, "could not read request body")
		return
	}

	reports, err := decodeReports(body)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if len(reports) == 0 {
		writeError(w, http.StatusBadRequest, "no locations in request")
		return
	}

	accepted := 0
	for _, rep := range reports {
		if err := validateReport(rep); err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		if err := s.ingestLocation(r.Context(), rep); err != nil {
			slog.Error("ingest location failed", "device", rep.DeviceID, "err", err)
			writeError(w, http.StatusInternalServerError, "could not store location")
			return
		}
		accepted++
	}

	interval := s.effectiveIntervalSec(r.Context(), reports[0].DeviceID)
	writeJSON(w, http.StatusOK, model.LocationAck{
		Status:             "ok",
		Accepted:           accepted,
		ReceivedAt:         time.Now().UTC().Format(time.RFC3339Nano),
		CaptureIntervalSec: interval,
	})
}

func decodeReports(body []byte) ([]model.LocationReport, error) {
	trimmed := bytes.TrimSpace(body)
	if len(trimmed) == 0 {
		return nil, fmt.Errorf("empty request body")
	}
	if trimmed[0] == '[' {
		var arr []model.LocationReport
		if err := json.Unmarshal(trimmed, &arr); err != nil {
			return nil, fmt.Errorf("invalid JSON array: %v", err)
		}
		return arr, nil
	}
	var single model.LocationReport
	if err := json.Unmarshal(trimmed, &single); err != nil {
		return nil, fmt.Errorf("invalid JSON object: %v", err)
	}
	return []model.LocationReport{single}, nil
}

func validateReport(rep model.LocationReport) error {
	if rep.DeviceID == "" {
		return fmt.Errorf("device_id is required")
	}
	if rep.Lat < -90 || rep.Lat > 90 {
		return fmt.Errorf("lat out of range [-90, 90]")
	}
	if rep.Lng < -180 || rep.Lng > 180 {
		return fmt.Errorf("lng out of range [-180, 180]")
	}
	if rep.Bearing != nil && (*rep.Bearing < 0 || *rep.Bearing > 360) {
		return fmt.Errorf("bearing out of range [0, 360]")
	}
	if rep.Battery != nil && (*rep.Battery < 0 || *rep.Battery > 100) {
		return fmt.Errorf("battery out of range [0, 100]")
	}
	return nil
}

// ingestLocation persists a fix, fans it out to dashboards, and raises a
// low-battery alert when appropriate.
func (s *Server) ingestLocation(ctx context.Context, rep model.LocationReport) error {
	receivedAt := time.Now().UTC().UnixMilli()
	tsMillis := receivedAt
	if ms, ok := model.ParseTimestamp(rep.Timestamp); ok {
		tsMillis = ms
	}

	if err := s.store.InsertLocation(ctx, store.Location{
		DeviceID:   rep.DeviceID,
		Lat:        rep.Lat,
		Lng:        rep.Lng,
		Accuracy:   rep.Accuracy,
		Speed:      rep.Speed,
		Bearing:    rep.Bearing,
		Battery:    rep.Battery,
		Timestamp:  tsMillis,
		ReceivedAt: receivedAt,
	}); err != nil {
		return err
	}

	s.maybeLowBatteryAlert(ctx, rep, receivedAt)

	msg := model.BroadcastMessage{
		Type:       "location",
		DeviceID:   rep.DeviceID,
		Lat:        rep.Lat,
		Lng:        rep.Lng,
		Accuracy:   rep.Accuracy,
		Speed:      rep.Speed,
		Bearing:    rep.Bearing,
		Battery:    rep.Battery,
		Timestamp:  model.MillisToRFC3339(tsMillis),
		ReceivedAt: model.MillisToRFC3339(receivedAt),
	}
	if data, err := json.Marshal(msg); err == nil {
		s.hub.Broadcast(data)
	}
	return nil
}

// maybeLowBatteryAlert raises a Low Battery alert when battery < 20%, deduped
// to at most one unacknowledged alert per device per 30 minutes.
func (s *Server) maybeLowBatteryAlert(ctx context.Context, rep model.LocationReport, nowMs int64) {
	if rep.Battery == nil || *rep.Battery >= 20 {
		return
	}
	since := nowMs - 30*60*1000
	if has, _ := s.store.HasRecentAlert(ctx, rep.DeviceID, "low_battery", since); has {
		return
	}
	_, _ = s.store.CreateAlert(ctx, store.Alert{
		DeviceID:  rep.DeviceID,
		Type:      "low_battery",
		Severity:  "high",
		Message:   fmt.Sprintf("Battery level: %d%%", *rep.Battery),
		CreatedAt: nowMs,
	})
}

// effectiveIntervalSec returns the device's capture interval — its per-device
// override if set, otherwise the global default.
func (s *Server) effectiveIntervalSec(ctx context.Context, deviceID string) int {
	def, _ := s.store.DefaultInterval(ctx)
	if dev, err := s.store.GetDevice(ctx, deviceID); err == nil &&
		dev.CaptureIntervalSec != nil && *dev.CaptureIntervalSec > 0 {
		return *dev.CaptureIntervalSec
	}
	if def <= 0 {
		return 120
	}
	return def
}
