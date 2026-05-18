package api

import (
	"bytes"
	"log/slog"
	"net/http"
)

// handleDashboardPage renders the live-tracking dashboard HTML. The page needs
// no server-injected data — its JavaScript derives the WebSocket URL from
// window.location — so the template is executed with nil data.
func (s *Server) handleDashboardPage(w http.ResponseWriter, _ *http.Request) {
	var buf bytes.Buffer
	if err := s.tmpl.ExecuteTemplate(&buf, "dashboard.html", nil); err != nil {
		slog.Error("render dashboard template", "err", err)
		http.Error(w, "could not render dashboard", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = w.Write(buf.Bytes())
}
