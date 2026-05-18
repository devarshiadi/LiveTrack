package api

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"gpsbackend/internal/model"
	"gpsbackend/internal/store"
)

// handleListGeofences returns all geofences.
func (s *Server) handleListGeofences(w http.ResponseWriter, r *http.Request) {
	fences, err := s.store.ListGeofences(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not list geofences")
		return
	}
	out := make([]model.GeofenceDTO, 0, len(fences))
	for _, g := range fences {
		out = append(out, model.GeofenceDTO{
			ID: g.ID, Name: g.Name, Lat: g.Lat, Lng: g.Lng, RadiusM: g.RadiusM,
			CreatedAt: model.MillisToRFC3339(g.CreatedAt),
		})
	}
	writeJSON(w, http.StatusOK, out)
}

// handleCreateGeofence creates a circular geofence.
func (s *Server) handleCreateGeofence(w http.ResponseWriter, r *http.Request) {
	var req model.GeofenceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON")
		return
	}
	if req.Name == "" || req.RadiusM <= 0 {
		writeError(w, http.StatusBadRequest, "name and a positive radius_m are required")
		return
	}
	id, err := s.store.CreateGeofence(r.Context(), store.Geofence{
		Name: req.Name, Lat: req.Lat, Lng: req.Lng, RadiusM: req.RadiusM,
		CreatedAt: time.Now().UnixMilli(),
	})
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not create geofence")
		return
	}
	writeJSON(w, http.StatusCreated, map[string]int64{"id": id})
}

// handleDeleteGeofence removes a geofence.
func (s *Server) handleDeleteGeofence(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(r.PathValue("id"), 10, 64)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid geofence id")
		return
	}
	if err := s.store.DeleteGeofence(r.Context(), id); err != nil {
		writeError(w, http.StatusInternalServerError, "could not delete geofence")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}
