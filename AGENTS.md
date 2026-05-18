# AGENTS.md — technical context for AI agents

This document gives an AI agent (or developer) the technical model of the Live
GPS Tracker, with emphasis on **how location tracking survives the app being
backgrounded, swiped away, killed, or crashed** — and how real-time tracking is
achieved with only standard permissions (no Developer Options).

---

## Repository layout

| Path | Stack |
|---|---|
| `Frontend/` | Android app — Kotlin + Jetpack Compose (Material 3), package `com.example.livegps`. `minSdk 26`, `compileSdk 36`. |
| `Backend/` | Go server — `net/http` + SQLite (`modernc.org/sqlite`, pure-Go, no CGO) + WebSocket (`coder/websocket`). Serves the web dashboard. Deployed as a Docker Hugging Face Space (`app_port 7860`, SQLite at `/data/gps.db`). |

## End-to-end data flow

```
Phone GPS
  → LocationTrackingService  (registers the request)
  → Google Play Services     (holds the request, delivers fixes)
  → LocationUpdateReceiver   (BroadcastReceiver — receives each fix)
  → LocationRepository       (process-wide single source of truth)
  → Room DB  buffered_locations   (durable local buffer, written first)
  → ApiClient  POST /api/locations  (OkHttp)
  → Go backend  → SQLite
  → WebSocket  /ws/dashboard
  → browser dashboard  (Leaflet map; route road-snapped via OSRM map-matching)
```

---

## Background-execution model (the core)

### 1. Foreground service
`service/LocationTrackingService.kt` is a started **foreground service**.
Manifest: `android:foregroundServiceType="location"`, permissions
`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION`. It calls
`ServiceCompat.startForeground(id, notification, FOREGROUND_SERVICE_TYPE_LOCATION)`
with an ongoing notification. A running foreground service is exempt from
background-execution limits and from Doze throttling — this is what allows a
fixed ~2-minute cadence at all (plain background location is throttled to "a
few times per hour" on Android 8+).

### 2. PendingIntent-based location updates — survives process death
The service does **not** use an in-process `LocationCallback`. It registers:

```kotlin
fusedLocationClient.requestLocationUpdates(locationRequest, pendingIntent)
```

…where `pendingIntent` targets the `LocationUpdateReceiver` `BroadcastReceiver`
(`PendingIntent.FLAG_MUTABLE`, required so Play Services can attach the result).

The location request is then **owned by Google Play Services (system-side)**,
not by the app process. Therefore:
- If the app process is killed or crashes, Play Services still holds the request.
- At the next interval Play Services broadcasts the fix; the OS **re-launches
  the app process** to run `LocationUpdateReceiver.onReceive`.
- The receiver (`goAsync()`) persists the fix to Room, then calls
  `ServiceController.start()` to re-establish the foreground service.

Result: tracking resumes within one capture interval (~2 min) after any kill,
with zero user action — this is the primary "auto turn on after crash" mechanism.

### 3. `START_STICKY`
`onStartCommand` returns `START_STICKY`. After a low-memory kill the OS recreates
the service and re-invokes `onStartCommand`, which re-registers the PendingIntent.
`onDestroy` deliberately does **not** call `removeLocationUpdates` — so a kill
leaves the Play-Services request alive. Only an explicit user Stop
(`ACTION_STOP`) removes it.

### 4. `onTaskRemoved` → AlarmManager → `RestartReceiver`
When the app is swiped from Recent Apps, `onTaskRemoved` calls
`RestartReceiver.scheduleRestart()` — an `AlarmManager.setAndAllowWhileIdle`
alarm (~2 s) whose broadcast restarts the service.

### 5. `BootReceiver`
`RECEIVE_BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` → `BootReceiver` restarts the
service after a reboot, gated on the persisted `trackingEnabled` flag.

### 6. WorkManager watchdog (JobScheduler)
`service/TrackingWatchdogWorker.kt` is a `PeriodicWorkRequest` (15 min — the
WorkManager minimum) enqueued in `LiveGpsApp.onCreate`. WorkManager schedules it
through **JobScheduler**, which the OS runs even if the app process is dead. The
worker checks `ServiceController.isRunning()` and restarts the service if it is
not. This is the slow-path safety net.

### 7. Uncaught-exception handler
`LiveGpsApp.installCrashHandler()` installs a
`Thread.setDefaultUncaughtExceptionHandler` that calls
`RestartReceiver.scheduleRestart()` and then delegates to the previous handler —
so an app crash also schedules a tracking restart before the process dies.

### Recovery summary

| Event | Recovery mechanism |
|---|---|
| App backgrounded / screen off | Foreground service keeps running |
| Swiped from Recent Apps | `onTaskRemoved` → AlarmManager → `RestartReceiver` |
| Low-memory process kill | `START_STICKY` + the Play-Services PendingIntent re-delivery |
| App crash (uncaught exception) | Crash handler schedules restart + PendingIntent re-delivery |
| Device reboot | `BootReceiver` (`BOOT_COMPLETED`) |
| Service silently dead | `TrackingWatchdogWorker` (WorkManager/JobScheduler, 15 min) |

## "Clearing the app" — what actually happens

Clearing the app = swiping it from Recent Apps. The task is removed, but:
the foreground service keeps running (`onTaskRemoved` also schedules a restart),
`START_STICKY` covers a memory reap, and the Play-Services-held location
PendingIntent keeps delivering — re-launching the process if needed. Net effect:
tracking continues.

## Honest limits — cannot be auto-recovered

- **Force Stop** (Settings → Apps → Force Stop) and **Clear data**: Android puts
  the app into a *stopped state* — `START_STICKY`, alarms, WorkManager jobs and
  boot receivers are all blocked until the user manually launches the app again.
  This is OS policy; no app can bypass it.
- **OEM battery killers** (Xiaomi/MIUI, Oppo/ColorOS, Vivo, Huawei, Samsung):
  aggressive vendor battery management can still kill the service. The only
  mitigation is the user enabling "Autostart" + the battery-optimization
  exemption; the app's onboarding links to dontkillmyapp.com.

## Permissions — no Developer Options required

Real-time, always-on tracking is achieved with **only standard runtime
permission prompts** — no ADB, no mock locations, no Developer Options:

- `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` (precise), then
  `ACCESS_BACKGROUND_LOCATION` ("Allow all the time") — requested two-step, as
  Android 11+ requires.
- `POST_NOTIFICATIONS` (Android 13+) — for the ongoing notification.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — a normal user-facing "allow
  background usage" dialog (not a developer setting).
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `RECEIVE_BOOT_COMPLETED`,
  `WAKE_LOCK`, `INTERNET`.

## Capture interval ("ETA") — dashboard-controlled

The Go backend returns `capture_interval_sec` in the `POST /api/locations`
response. `LocationRepository` stores it (and persists it via DataStore); the
service observes it and rebuilds its `LocationRequest` interval. Per-device
override + global default are settable from the dashboard. Default 120 s.

## Offline resilience

Every fix is written to the Room table `buffered_locations` **before** any
upload. `LocationRepository.flush()` drains the buffer oldest-first; on failure
the rows remain and retry on the next fix or when connectivity returns
(`ConnectivityManager` default-network callback). Buffer is capped (10,000 rows,
oldest pruned). No fix is lost across crashes or network outages.

## Performance / lag-free design

- Tracking is entirely off the UI thread: a foreground service + IO-dispatcher
  coroutines. The UI process and the tracker are decoupled through the
  process-wide `LocationRepository` and the Room DB.
- Compose UI: `collectAsStateWithLifecycle`, `LazyColumn` with stable unique
  keys + `Modifier.animateItem()`, fade navigation transitions, and a single
  hoisted osmdroid `MapView` (never re-created — re-parenting an Android `View`
  across composition crashes, so the map has exactly one stable call site).

## Key files

**Frontend**
- `service/LocationTrackingService.kt` — foreground service; registers the location PendingIntent
- `service/LocationUpdateReceiver.kt` — receives fixes; survives process death; re-establishes the service
- `service/RestartReceiver.kt`, `BootReceiver.kt`, `TrackingWatchdogWorker.kt`, `ServiceController.kt` — recovery layers
- `data/repository/LocationRepository.kt` — single source of truth, Room buffer, upload orchestration
- `data/remote/ApiClient.kt` — backend REST client (upload + reads)
- `data/local/` — Room DB (`AppDatabase`, `LocationDao`, `LocationEntity`) + `SettingsStore` (DataStore)
- `notification/TrackingNotification.kt` — the ongoing Material 3 notification (+ Stop action)
- `LiveGpsApp.kt` — process singletons (`repository`, `apiClient`, `settings`) + crash handler
- `ui/` — Compose screens: `dashboard/`, `devices/`, `history/`, `alerts/`, `more/`, `home/HomeScaffold.kt`

**Backend**
- `main.go` — entry point, graceful shutdown, alert monitor goroutine
- `internal/api/` — HTTP handlers (`locations`, `devices`, `alerts`, `geofences`, `settings`, `dashboard_ws`)
- `internal/store/` — SQLite schema + queries
- `internal/hub/` — WebSocket fan-out hub
- `templates/`, `static/` — the embedded web dashboard (Leaflet + OSRM road-snapping)
- `Dockerfile`, `README.md` — Hugging Face Space (Docker SDK) deployment

## Build

- **Frontend:** `cd Frontend && ./gradlew assembleDebug` — requires Android SDK + JDK 17.
  Output: `Frontend/app/build/outputs/apk/debug/app-debug.apk`.
- **Backend:** `cd Backend && go run .` (local, port 8080) or `docker build` (port 7860).

## Notes for future changes

- A new `LazyColumn` must use **unique item keys** — location timestamps can
  repeat (duplicate rows from a re-sent upload batch); key by index or DB id.
- Do not place the osmdroid `MapView` at more than one composition call site.
- Keep `onDestroy` from removing location updates — that would break kill-recovery.
- The OSRM endpoint (`router.project-osrm.org`) in `static/js/dashboard.js` is a
  public demo server; self-host it for production load.
