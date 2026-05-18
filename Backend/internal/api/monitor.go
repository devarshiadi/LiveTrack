package api

import (
	"context"
	"log/slog"
	"time"

	"gpsbackend/internal/store"
)

// RunAlertMonitor periodically raises "offline" alerts for devices that have
// stopped reporting. It blocks until ctx is cancelled.
func (s *Server) RunAlertMonitor(ctx context.Context) {
	ticker := time.NewTicker(60 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			s.checkOfflineDevices(ctx)
		}
	}
}

func (s *Server) checkOfflineDevices(ctx context.Context) {
	devices, err := s.store.ListDevices(ctx)
	if err != nil {
		slog.Warn("alert monitor: list devices failed", "err", err)
		return
	}
	def, _ := s.store.DefaultInterval(ctx)
	now := time.Now().UnixMilli()

	for _, d := range devices {
		effective := def
		if d.CaptureIntervalSec != nil && *d.CaptureIntervalSec > 0 {
			effective = *d.CaptureIntervalSec
		}
		if deviceStatus(d, effective, now) != "offline" {
			continue
		}
		// De-dupe: at most one unacknowledged offline alert per device per hour.
		if has, _ := s.store.HasRecentAlert(ctx, d.ID, "offline", now-60*60*1000); has {
			continue
		}
		_, _ = s.store.CreateAlert(ctx, store.Alert{
			DeviceID:  d.ID,
			Type:      "offline",
			Severity:  "low",
			Message:   "No signal for more than 10 minutes",
			CreatedAt: now,
		})
	}
}
