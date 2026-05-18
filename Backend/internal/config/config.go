// Package config loads the backend's runtime configuration from command-line
// flags, falling back to environment variables and then built-in defaults.
package config

import (
	"flag"
	"os"
)

// Config holds every tunable the backend needs at startup.
type Config struct {
	Host          string // bind address; 0.0.0.0 lets a LAN phone reach the server
	Port          string // HTTP port
	DBPath        string // SQLite database file path
	AllowedOrigin string // value sent in Access-Control-Allow-Origin
	Dev           bool   // when true, read templates/static from disk instead of the embedded FS
}

// Addr returns the host:port string for http.Server.
func (c Config) Addr() string { return c.Host + ":" + c.Port }

// Load parses flags (with env-var fallbacks) and returns the resulting Config.
// Flags win over environment variables, which win over defaults.
func Load() Config {
	c := Config{
		Host:          env("GPS_HOST", "0.0.0.0"),
		Port:          env("GPS_PORT", "8080"),
		DBPath:        env("GPS_DB_PATH", "gps.db"),
		AllowedOrigin: env("GPS_ALLOWED_ORIGIN", "*"),
		Dev:           env("GPS_DEV", "") != "",
	}

	flag.StringVar(&c.Host, "host", c.Host, "bind address (0.0.0.0 = reachable on the LAN)")
	flag.StringVar(&c.Port, "port", c.Port, "HTTP port")
	flag.StringVar(&c.DBPath, "db", c.DBPath, "SQLite database file path")
	flag.StringVar(&c.AllowedOrigin, "allowed-origin", c.AllowedOrigin, "CORS Access-Control-Allow-Origin value")
	flag.BoolVar(&c.Dev, "dev", c.Dev, "serve templates/static from disk for live editing")
	flag.Parse()

	return c
}

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
