package api

import (
	"log/slog"
	"net/http"
	"time"

	"gpsbackend/internal/config"
)

// withMiddleware wraps the mux with panic recovery, request logging and CORS.
// Order matters: recovery is outermost so it catches panics from everything.
func withMiddleware(next http.Handler, cfg config.Config) http.Handler {
	return recoverMiddleware(logMiddleware(corsMiddleware(next, cfg)))
}

// recoverMiddleware turns a handler panic into a 500 instead of crashing the
// process or dropping the connection.
func recoverMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if rec := recover(); rec != nil {
				slog.Error("panic recovered", "err", rec, "path", r.URL.Path)
				http.Error(w, "internal server error", http.StatusInternalServerError)
			}
		}()
		next.ServeHTTP(w, r)
	})
}

// statusRecorder captures the response status code for logging.
type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (r *statusRecorder) WriteHeader(code int) {
	r.status = code
	r.ResponseWriter.WriteHeader(code)
}

// Unwrap exposes the underlying ResponseWriter so the WebSocket library can
// hijack the connection through http.ResponseController.
func (r *statusRecorder) Unwrap() http.ResponseWriter { return r.ResponseWriter }

func logMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(rec, r)
		slog.Info("request",
			"method", r.Method,
			"path", r.URL.Path,
			"status", rec.status,
			"dur", time.Since(start).Round(time.Millisecond).String(),
		)
	})
}

// corsMiddleware adds permissive CORS headers so the dashboard (or any browser
// tool) can call the API. Native Android HTTP clients ignore CORS, so this is
// harmless for the device ingest path.
func corsMiddleware(next http.Handler, cfg config.Config) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", cfg.AllowedOrigin)
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}
