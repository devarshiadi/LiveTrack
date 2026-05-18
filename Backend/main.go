// Command gpsbackend is the live GPS tracking server. It receives location
// reports from Android devices, stores them in SQLite, and broadcasts live
// updates to browser dashboards over WebSocket. It also serves the dashboard
// itself (a Leaflet map) from embedded assets.
package main

import (
	"context"
	"embed"
	"errors"
	"io/fs"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"gpsbackend/internal/api"
	"gpsbackend/internal/config"
	"gpsbackend/internal/hub"
	"gpsbackend/internal/store"
)

// assetsFS embeds the dashboard templates and static assets so the compiled
// binary is fully self-contained — no working-directory-relative file loading.
//
//go:embed templates static
var assetsFS embed.FS

func main() {
	slog.SetDefault(slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})))

	if err := run(); err != nil {
		slog.Error("fatal", "err", err)
		os.Exit(1)
	}
}

func run() error {
	cfg := config.Load()

	// Choose the asset source: disk in -dev mode (live editing), else embedded.
	var assets fs.FS = assetsFS
	if cfg.Dev {
		assets = os.DirFS(".")
		slog.Info("dev mode: serving templates/static from disk")
	}

	st, err := store.Open(cfg.DBPath)
	if err != nil {
		return err
	}
	defer func() {
		if err := st.Close(); err != nil {
			slog.Error("closing store", "err", err)
		}
	}()
	slog.Info("database ready", "path", cfg.DBPath)

	h := hub.New()
	go h.Run()
	defer h.Close()

	srv, err := api.NewServer(cfg, st, h, assets)
	if err != nil {
		return err
	}

	httpSrv := &http.Server{
		Addr:              cfg.Addr(),
		Handler:           srv.Handler(),
		ReadHeaderTimeout: 10 * time.Second,
	}

	// Listen for Ctrl+C / SIGTERM to trigger a graceful shutdown.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	// Background monitor: raises "offline" alerts for silent devices.
	go srv.RunAlertMonitor(ctx)

	serveErr := make(chan error, 1)
	go func() {
		slog.Info("listening", "addr", cfg.Addr(), "dashboard", "http://localhost:"+cfg.Port+"/")
		if err := httpSrv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			serveErr <- err
		}
	}()

	select {
	case err := <-serveErr:
		return err
	case <-ctx.Done():
		slog.Info("shutdown signal received")
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := httpSrv.Shutdown(shutdownCtx); err != nil {
		slog.Error("graceful shutdown failed", "err", err)
	}
	slog.Info("server stopped")
	return nil
}
