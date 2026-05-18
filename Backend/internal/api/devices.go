package api

import (
	"database/sql"
	"encoding/csv"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"gpsbackend/internal/model"
	"gpsbackend/internal/store"
)

const (
	defaultTrackLimit = 500
	maxTrackLimit     = 5000
)

// handleListDevices returns every known device with status and last position.
func (s *Server) handleListDevices(w http.ResponseWriter, r *http.Request) {
	devices, err := s.store.ListDevices(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not list devices")
		return
	}
	def, _ := s.store.DefaultInterval(r.Context())
	now := time.Now().UnixMilli()

	out := make([]model.DeviceDTO, 0, len(devices))
	for _, d := range devices {
		out = append(out, toDeviceDTO(d, def, now))
	}
	writeJSON(w, http.StatusOK, out)
}

// handleGetDevice returns a single device.
func (s *Server) handleGetDevice(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	d, err := s.store.GetDevice(r.Context(), id)
	if errors.Is(err, sql.ErrNoRows) {
		writeError(w, http.StatusNotFound, "device not found")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load device")
		return
	}
	def, _ := s.store.DefaultInterval(r.Context())
	writeJSON(w, http.StatusOK, toDeviceDTO(d, def, time.Now().UnixMilli()))
}

// handleUpdateDevice sets a device's name and per-device capture interval.
func (s *Server) handleUpdateDevice(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	var req model.DeviceUpdateRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON")
		return
	}
	if req.CaptureIntervalSec != nil && *req.CaptureIntervalSec < 10 {
		writeError(w, http.StatusBadRequest, "capture_interval_sec must be >= 10")
		return
	}
	if err := s.store.SetDeviceConfig(r.Context(), id, req.Name, req.CaptureIntervalSec); err != nil {
		writeError(w, http.StatusInternalServerError, "could not update device")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

// handleGetTrack returns a device's location history.
func (s *Server) handleGetTrack(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	since, limit, ok := trackParams(w, r)
	if !ok {
		return
	}
	locs, err := s.store.Track(r.Context(), id, since, limit)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load track")
		return
	}
	points := make([]model.TrackPoint, 0, len(locs))
	for _, l := range locs {
		points = append(points, model.TrackPoint{
			Lat: l.Lat, Lng: l.Lng,
			Accuracy: l.Accuracy, Speed: l.Speed, Bearing: l.Bearing, Battery: l.Battery,
			Timestamp: model.MillisToRFC3339(l.Timestamp),
		})
	}
	writeJSON(w, http.StatusOK, model.TrackResponse{DeviceID: id, Points: points})
}

// handleExportCSV streams a device's track as a CSV download (Reports feature).
func (s *Server) handleExportCSV(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	locs, err := s.store.Track(r.Context(), id, 0, maxTrackLimit)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load track")
		return
	}
	w.Header().Set("Content-Type", "text/csv")
	w.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="track-%s.csv"`, id))

	cw := csv.NewWriter(w)
	_ = cw.Write([]string{"timestamp", "lat", "lng", "accuracy_m", "speed_mps", "bearing_deg", "battery_pct"})
	for _, l := range locs {
		_ = cw.Write([]string{
			model.MillisToRFC3339(l.Timestamp),
			strconv.FormatFloat(l.Lat, 'f', 6, 64),
			strconv.FormatFloat(l.Lng, 'f', 6, 64),
			floatOrEmpty(l.Accuracy),
			floatOrEmpty(l.Speed),
			floatOrEmpty(l.Bearing),
			intOrEmpty(l.Battery),
		})
	}
	cw.Flush()
}

func trackParams(w http.ResponseWriter, r *http.Request) (since int64, limit int, ok bool) {
	limit = defaultTrackLimit
	if raw := r.URL.Query().Get("since"); raw != "" {
		ms, valid := model.ParseTimestamp(raw)
		if !valid {
			writeError(w, http.StatusBadRequest, "invalid 'since' timestamp (expected RFC3339)")
			return 0, 0, false
		}
		since = ms
	}
	if raw := r.URL.Query().Get("limit"); raw != "" {
		n, err := strconv.Atoi(raw)
		if err != nil || n <= 0 {
			writeError(w, http.StatusBadRequest, "invalid 'limit'")
			return 0, 0, false
		}
		limit = min(n, maxTrackLimit)
	}
	return since, limit, true
}

// toDeviceDTO converts a store row into its JSON representation, computing
// the device's status and effective capture interval.
func toDeviceDTO(d store.DeviceWithLocation, defaultIntervalSec int, nowMs int64) model.DeviceDTO {
	effective := defaultIntervalSec
	if d.CaptureIntervalSec != nil && *d.CaptureIntervalSec > 0 {
		effective = *d.CaptureIntervalSec
	}
	if effective <= 0 {
		effective = 120
	}

	dto := model.DeviceDTO{
		ID:                   d.ID,
		Name:                 d.Name,
		Status:               deviceStatus(d, effective, nowMs),
		Battery:              d.Battery,
		CaptureIntervalSec:   d.CaptureIntervalSec,
		EffectiveIntervalSec: effective,
		FirstSeenAt:          model.MillisToRFC3339(d.FirstSeenAt),
		LastSeenAt:           model.MillisToRFC3339(d.LastSeenAt),
	}
	if d.Last != nil {
		dto.LastLocation = &model.LocationDTO{
			Lat: d.Last.Lat, Lng: d.Last.Lng,
			Accuracy: d.Last.Accuracy, Speed: d.Last.Speed, Bearing: d.Last.Bearing,
			Timestamp: model.MillisToRFC3339(d.Last.Timestamp),
		}
	}
	return dto
}

// deviceStatus classifies a device as online / offline / inactive.
func deviceStatus(d store.DeviceWithLocation, effectiveSec int, nowMs int64) string {
	if d.Last == nil {
		return "inactive"
	}
	age := nowMs - d.LastSeenAt
	onlineWindow := int64(effectiveSec) * 3 * 1000
	if onlineWindow < 6*60*1000 {
		onlineWindow = 6 * 60 * 1000 // floor: 6 minutes
	}
	switch {
	case age <= onlineWindow:
		return "online"
	case age >= 24*60*60*1000:
		return "inactive"
	default:
		return "offline"
	}
}

func floatOrEmpty(v *float64) string {
	if v == nil {
		return ""
	}
	return strconv.FormatFloat(*v, 'f', 2, 64)
}

func intOrEmpty(v *int) string {
	if v == nil {
		return ""
	}
	return strconv.Itoa(*v)
}
