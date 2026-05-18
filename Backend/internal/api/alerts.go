package api

import (
	"net/http"
	"strconv"

	"gpsbackend/internal/model"
)

const alertsListLimit = 200

// handleListAlerts returns recent alerts, newest first.
func (s *Server) handleListAlerts(w http.ResponseWriter, r *http.Request) {
	alerts, err := s.store.ListAlerts(r.Context(), alertsListLimit)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not list alerts")
		return
	}
	out := make([]model.AlertDTO, 0, len(alerts))
	for _, a := range alerts {
		out = append(out, model.AlertDTO{
			ID:           a.ID,
			DeviceID:     a.DeviceID,
			Type:         a.Type,
			Severity:     a.Severity,
			Message:      a.Message,
			CreatedAt:    model.MillisToRFC3339(a.CreatedAt),
			Acknowledged: a.Acknowledged,
		})
	}
	writeJSON(w, http.StatusOK, out)
}

// handleAckAlert marks an alert acknowledged.
func (s *Server) handleAckAlert(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(r.PathValue("id"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid alert id")
		return
	}
	if err := s.store.AckAlert(r.Context(), id); err != nil {
		writeError(w, http.StatusInternalServerError, "could not acknowledge alert")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}
