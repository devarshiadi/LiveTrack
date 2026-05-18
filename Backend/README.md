---
title: Live GPS Tracker
emoji: 📍
colorFrom: purple
colorTo: indigo
sdk: docker
app_port: 7860
pinned: false
---

# Live GPS Tracking — Backend

Go server for the live GPS tracking system. It receives location reports from
the Android app, stores them in SQLite, broadcasts live updates to browser
dashboards over WebSocket, and serves the dashboard itself (a Leaflet map).

## Deploy on a Hugging Face Space

This folder is a ready-to-paste **Docker Space**:

1. Create a new Space → SDK: **Docker**.
2. Copy every file in this `Backend/` folder into the Space repository
   (the `README.md` above carries the required Space metadata).
3. In the Space **Settings**, add **persistent storage** so `/data` survives
   restarts (the SQLite database is written to `/data/gps.db`).
4. The Space builds the `Dockerfile` and serves on port **7860** —
   open the Space URL to see the dashboard.

The container runs as the non-root user `uid 1000` and writes only to `/data`.

## Run locally

```powershell
cd Backend
go mod tidy
go run .            # http://localhost:8080
```

Or with Docker, mirroring the Space:

```powershell
docker build -t livegps .
docker run -p 7860:7860 -v ${PWD}/data:/data livegps   # http://localhost:7860
```

### Configuration

| Flag | Env | Default | Purpose |
|---|---|---|---|
| `-host` | `GPS_HOST` | `0.0.0.0` | Bind address. |
| `-port` | `GPS_PORT` | `8080` (`7860` in Docker) | HTTP port. |
| `-db` | `GPS_DB_PATH` | `gps.db` (`/data/gps.db` in Docker) | SQLite file path. |
| `-allowed-origin` | `GPS_ALLOWED_ORIGIN` | `*` | CORS origin. |
| `-dev` | `GPS_DEV` | off | Serve `templates/`/`static/` from disk. |

## HTTP API

| Method / path | Purpose |
|---|---|
| `POST /api/locations` | Device submits a fix (single object or JSON array). Response carries `capture_interval_sec`. |
| `GET /ws/dashboard` | Dashboard WebSocket — snapshot then live `location` frames. |
| `GET /api/devices` | Devices with status, battery and last position. |
| `GET /api/devices/{id}` | One device. |
| `PUT /api/devices/{id}` | Set device name + per-device capture interval. |
| `GET /api/devices/{id}/track` | Location history. |
| `GET /api/devices/{id}/export.csv` | Track as a CSV download. |
| `GET` / `PUT /api/settings` | Global default capture interval. |
| `GET /api/alerts` | Alerts (low battery, offline, …). |
| `POST /api/alerts/{id}/ack` | Acknowledge an alert. |
| `GET` / `POST /api/geofences`, `DELETE /api/geofences/{id}` | Geofence zones. |
| `GET /` | The web dashboard. |
| `GET /healthz` | Liveness probe. |

### Location report JSON

```json
{ "device_id": "pixel-7", "lat": 19.07283, "lng": 72.88261,
  "accuracy": 8.5, "speed": 1.4, "bearing": 271.0, "battery": 87,
  "timestamp": "2026-05-19T18:30:00Z" }
```

`accuracy`, `speed`, `bearing`, `battery` and `timestamp` are optional. The
response includes `capture_interval_sec` — the device should report at that
cadence (per-device override, else the global default).

## Notes

- **SQLite** uses the pure-Go driver `modernc.org/sqlite` — no CGO, builds in a
  slim Alpine image.
- The `alerts` monitor goroutine raises an **offline** alert for devices that
  stop reporting, and **low battery** alerts arrive with location reports.
- **Leaflet** loads from a CDN; OpenStreetMap tiles need internet.
