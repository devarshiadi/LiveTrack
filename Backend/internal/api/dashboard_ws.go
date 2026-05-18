package api

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/coder/websocket"

	"gpsbackend/internal/hub"
	"gpsbackend/internal/model"
)

// handleDashboardWS upgrades the connection to a WebSocket, immediately sends a
// snapshot of every device's last-known position, then hands the connection to
// the hub to receive live updates.
func (s *Server) handleDashboardWS(w http.ResponseWriter, r *http.Request) {
	conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		// LAN/localhost POC: accept any origin. Lock this down for production.
		InsecureSkipVerify: true,
	})
	if err != nil {
		slog.Warn("websocket accept failed", "err", err)
		return
	}
	defer func() { _ = conn.CloseNow() }()

	client := hub.NewClient(conn)

	// Send the snapshot before registering so a broadcast can't slip in ahead
	// of the initial state.
	if snapshot, err := s.buildSnapshot(r); err == nil {
		if err := client.SendNow(r.Context(), snapshot); err != nil {
			slog.Debug("snapshot send failed", "err", err)
			return
		}
	}

	s.hub.Register(client)
	defer s.hub.Unregister(client)

	client.Run(r.Context()) // blocks until the dashboard disconnects
}

// buildSnapshot serialises every device's latest fix into a SnapshotMessage.
func (s *Server) buildSnapshot(r *http.Request) ([]byte, error) {
	devices, err := s.store.ListDevices(r.Context())
	if err != nil {
		return nil, err
	}

	snap := model.SnapshotMessage{Type: "snapshot", Devices: []model.BroadcastMessage{}}
	for _, d := range devices {
		if d.Last == nil {
			continue
		}
		snap.Devices = append(snap.Devices, model.BroadcastMessage{
			Type:       "location",
			DeviceID:   d.ID,
			DeviceName: d.Name,
			Lat:        d.Last.Lat,
			Lng:        d.Last.Lng,
			Accuracy:   d.Last.Accuracy,
			Speed:      d.Last.Speed,
			Bearing:    d.Last.Bearing,
			Timestamp:  model.MillisToRFC3339(d.Last.Timestamp),
			ReceivedAt: model.MillisToRFC3339(d.Last.ReceivedAt),
		})
	}
	return json.Marshal(snap)
}
