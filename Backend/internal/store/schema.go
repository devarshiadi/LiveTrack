package store

// schemaSQL is the full database schema. Every statement is idempotent
// (IF NOT EXISTS), so migrate() can safely run it on every startup.
const schemaSQL = `
CREATE TABLE IF NOT EXISTS devices (
    id                   TEXT PRIMARY KEY,
    name                 TEXT    NOT NULL DEFAULT '',
    battery              INTEGER,            -- last reported battery %, nullable
    capture_interval_sec INTEGER,            -- per-device override, nullable
    first_seen_at        INTEGER NOT NULL,
    last_seen_at         INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS locations (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id   TEXT    NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    lat         REAL    NOT NULL,
    lng         REAL    NOT NULL,
    accuracy    REAL,
    speed       REAL,
    bearing     REAL,
    battery     INTEGER,
    timestamp   INTEGER NOT NULL,   -- device clock, unix milliseconds
    received_at INTEGER NOT NULL    -- server clock, unix milliseconds
);

-- Single-row table holding global app settings.
CREATE TABLE IF NOT EXISTS app_settings (
    id                           INTEGER PRIMARY KEY CHECK (id = 1),
    default_capture_interval_sec INTEGER NOT NULL DEFAULT 120
);

CREATE TABLE IF NOT EXISTS geofences (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    lat        REAL    NOT NULL,
    lng        REAL    NOT NULL,
    radius_m   REAL    NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS alerts (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id    TEXT    NOT NULL,
    type         TEXT    NOT NULL,   -- low_battery | offline | gps_off
    severity     TEXT    NOT NULL,   -- info | warning | critical
    message      TEXT    NOT NULL,
    created_at   INTEGER NOT NULL,
    acknowledged INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_locations_device_time ON locations(device_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_locations_received    ON locations(received_at);
CREATE INDEX IF NOT EXISTS idx_alerts_created        ON alerts(created_at DESC);
`

// migrations are ALTER statements that upgrade a pre-existing database created
// before these columns existed. Each runs individually and "duplicate column"
// errors are ignored, so this is safe on both fresh and old databases.
var migrations = []string{
	`ALTER TABLE devices ADD COLUMN battery INTEGER`,
	`ALTER TABLE devices ADD COLUMN capture_interval_sec INTEGER`,
	`ALTER TABLE locations ADD COLUMN battery INTEGER`,
}
