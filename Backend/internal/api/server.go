// Package api wires the HTTP routes: the device ingest endpoint, the dashboard
// WebSocket, the read APIs the dashboard polls, and the dashboard page itself.
package api

import (
	"encoding/json"
	"fmt"
	"html/template"
	"io/fs"
	"log/slog"
	"net/http"

	"gpsbackend/internal/config"
	"gpsbackend/internal/hub"
	"gpsbackend/internal/store"
)

// Server holds the dependencies shared by every handler.
type Server struct {
	cfg    config.Config
	store  *store.Store
	hub    *hub.Hub
	tmpl   *template.Template
	static http.Handler
}

// NewServer builds a Server. assets must contain "templates/" and "static/"
// directories at its root (an embed.FS in production, os.DirFS in -dev mode).
func NewServer(cfg config.Config, st *store.Store, h *hub.Hub, assets fs.FS) (*Server, error) {
	tmpl, err := template.ParseFS(assets, "templates/*.html")
	if err != nil {
		return nil, fmt.Errorf("parse templates: %w", err)
	}

	staticFS, err := fs.Sub(assets, "static")
	if err != nil {
		return nil, fmt.Errorf("sub static FS: %w", err)
	}

	return &Server{
		cfg:    cfg,
		store:  st,
		hub:    h,
		tmpl:   tmpl,
		static: http.FileServer(http.FS(staticFS)),
	}, nil
}

// Handler returns the fully-routed http.Handler with middleware applied.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()

	// Device ingest.
	mux.HandleFunc("POST /api/locations", s.handlePostLocations)

	// Devices.
	mux.HandleFunc("GET /api/devices", s.handleListDevices)
	mux.HandleFunc("GET /api/devices/{id}", s.handleGetDevice)
	mux.HandleFunc("PUT /api/devices/{id}", s.handleUpdateDevice)
	mux.HandleFunc("GET /api/devices/{id}/track", s.handleGetTrack)
	mux.HandleFunc("GET /api/devices/{id}/export.csv", s.handleExportCSV)

	// Settings.
	mux.HandleFunc("GET /api/settings", s.handleGetSettings)
	mux.HandleFunc("PUT /api/settings", s.handlePutSettings)

	// Alerts.
	mux.HandleFunc("GET /api/alerts", s.handleListAlerts)
	mux.HandleFunc("POST /api/alerts/{id}/ack", s.handleAckAlert)

	// Geofences.
	mux.HandleFunc("GET /api/geofences", s.handleListGeofences)
	mux.HandleFunc("POST /api/geofences", s.handleCreateGeofence)
	mux.HandleFunc("DELETE /api/geofences/{id}", s.handleDeleteGeofence)

	// Live dashboard WebSocket.
	mux.HandleFunc("GET /ws/dashboard", s.handleDashboardWS)

	// Health probe.
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})

	// Static assets and the dashboard page.
	mux.Handle("GET /static/", http.StripPrefix("/static/", s.static))
	mux.HandleFunc("GET /{$}", s.handleDashboardPage)

	return withMiddleware(mux, s.cfg)
}

// writeJSON marshals v and writes it with the given status code.
func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(v); err != nil {
		slog.Error("encode json response", "err", err)
	}
}

// writeError writes a {"error": "..."} JSON body with the given status code.
func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}
