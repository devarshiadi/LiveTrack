package api

import (
	"encoding/json"
	"net/http"

	"gpsbackend/internal/model"
)

// handleGetSettings returns the global app settings.
func (s *Server) handleGetSettings(w http.ResponseWriter, r *http.Request) {
	def, err := s.store.DefaultInterval(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load settings")
		return
	}
	writeJSON(w, http.StatusOK, model.SettingsDTO{DefaultCaptureIntervalSec: def})
}

// handlePutSettings updates the global default capture interval.
func (s *Server) handlePutSettings(w http.ResponseWriter, r *http.Request) {
	var req model.SettingsDTO
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON")
		return
	}
	if req.DefaultCaptureIntervalSec < 10 {
		writeError(w, http.StatusBadRequest, "default_capture_interval_sec must be >= 10")
		return
	}
	if err := s.store.SetDefaultInterval(r.Context(), req.DefaultCaptureIntervalSec); err != nil {
		writeError(w, http.StatusInternalServerError, "could not save settings")
		return
	}
	writeJSON(w, http.StatusOK, req)
}
