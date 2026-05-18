# Live GPS Tracking — Android App

Kotlin + Jetpack Compose app that tracks this device's GPS location, keeps
tracking while backgrounded or swiped from recents, shows the live position on
an in-app map, and uploads fixes to the Go backend.

## Stack

- **Kotlin 2.0.21 + Jetpack Compose**, Material 3 (purple baseline theme).
- **osmdroid** — OpenStreetMap map, no API key.
- **FusedLocationProviderClient** (Google Play Services) for GPS.
- **Room** offline buffer, **DataStore** settings, **WorkManager** watchdog,
  **OkHttp** REST client.
- `minSdk 26`, `compileSdk`/`targetSdk 35`, AGP 8.7, Gradle 8.9.

## Build

This project needs the **Android SDK + JDK 17**. The simplest path is
**Android Studio** (it bundles JDK 17 and the SDK):

1. Open Android Studio → **Open** → select this `Frontend/` folder.
2. Android Studio writes `local.properties` (the SDK path) and runs the first
   Gradle sync, downloading Gradle 8.9, AGP and all dependencies.
3. **Run** ▶ on an emulator or a device (Android 8.0+).

Command line (once a JDK + the SDK are installed and `local.properties` has
`sdk.dir`):

```powershell
cd Frontend
.\gradlew.bat assembleDebug
# output: app\build\outputs\apk\debug\app-debug.apk
```

## Configure the backend URL

`BuildConfig.BACKEND_BASE_URL` defaults to `http://10.0.2.2:8080` — the Android
**emulator's** alias for the host PC running the Go backend.

- **Emulator:** start the backend (`go run .` in `../Backend`); the default works.
- **Physical device:** open the app's **Settings** screen and set the backend
  URL to the PC's LAN IP, e.g. `http://192.168.1.20:8080` (phone and PC on the
  same Wi-Fi; allow the port through the firewall). No rebuild needed.

## How background tracking survives

| Situation | What keeps it alive |
|---|---|
| App backgrounded / screen off | Foreground service (type `location`) + ongoing notification |
| App swiped from recents | `onTaskRemoved()` → AlarmManager → `RestartReceiver` |
| OS memory kill | `onStartCommand` returns `START_STICKY` |
| Device reboot | `BootReceiver` on `BOOT_COMPLETED` |
| Service silently dead | `TrackingWatchdogWorker` (WorkManager, every 15 min) |
| Battery optimizer / OEM killer | Onboarding guides the user (battery-opt prompt + dontkillmyapp.com) |
| **Explicit "Force Stop" in Settings** | **Nothing — OS policy.** The app says so honestly; reopen to resume. |

When the backend is unreachable every fix is still saved to the Room buffer and
uploaded later (oldest first) once the network or server returns.

## Project layout

```
app/src/main/java/com/example/livegps/
├── LiveGpsApp.kt              Application — builds the shared repository
├── MainActivity.kt            Compose host
├── core/                      Constants, stable device id
├── data/
│   ├── model/                 LocationSample, ConnectionState
│   ├── local/                 Room (entity/dao/db), DataStore settings
│   ├── remote/                OkHttp REST client
│   └── repository/            LocationRepository — single source of truth
├── service/                   Foreground service + boot/restart receivers + watchdog
├── notification/              Ongoing notification
├── permissions/               Permission + battery-optimization helpers
└── ui/                        Compose Material 3 screens (onboarding, map, settings)
```

## Verify

1. Run the Go backend (`../Backend`).
2. Launch the app, complete onboarding (location "Allow all the time",
   notifications, battery optimization).
3. Tap **Start tracking** — the ongoing notification appears, the marker
   follows you, the device shows up live on the web dashboard.
4. Press Home / swipe from recents — tracking continues (check the notification
   and `adb shell dumpsys activity services com.example.livegps`).
5. Stop the backend — the status card shows a rising "queued" count; restart it
   and the buffer drains, count returns to 0.
