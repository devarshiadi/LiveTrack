# Live GPS Tracker

A live location-tracking system. An Android app quietly records its GPS
position in the background, and a web dashboard shows it moving on a map in
real time.

---

## What it does

- 📍 **Records location** on a set schedule (for example, every 2 minutes).
- 🔄 **Keeps running in the background** — screen off, app minimised, or even
  swiped away from Recent Apps.
- 🗺️ **Shows every device live** on a web dashboard, drawn as a real
  road-following route (not just dots on straight lines).
- 🔔 **Raises alerts** (low battery, no signal) and keeps a full history.
- 🚀 **No "Developer Options", no hidden setup** — just install and tap *Allow*.

## The two parts

| Part | What it is |
|---|---|
| **Android app** (`Frontend/`) | Installs on the phone. Records and uploads location. |
| **Go server + dashboard** (`Backend/`) | Stores the data and serves the live web dashboard. Runs on a Hugging Face Space. |

---

## How it keeps running in the background

This is the important part — explained in plain terms:

- **Foreground service.** Android lets an app keep a task running as long as it
  shows a notification. The app runs its location tracking as a *foreground
  service*, so you always see a small **"Live GPS — tracking on"** notification
  while it works. That notification is the app's permission slip to keep going.

- **The system delivers the location, not the app.** The app asks Google Play
  Services (a built-in part of Android) to hand it a location every few minutes.
  Play Services holds that request itself — so even if the app is closed or
  dropped from memory, **Android wakes the app back up** to deliver the next
  location.

- **A watchdog job (WorkManager / JobScheduler).** A scheduled background job
  runs every 15 minutes and checks "is tracking still alive?" — if not, it
  starts it again.

- **Auto-restart on crash, swipe, or reboot.** If the app crashes, is swiped
  away from Recent Apps, or the phone restarts, the app brings its tracking back
  by itself. You never have to reopen it.

Put together: **clear the app, lock the phone, leave it for hours — it keeps
recording, and the dashboard keeps updating.**

> **One honest exception:** if you go to **Settings → Apps → Force Stop**,
> Android blocks the app from restarting itself until you open it again. No app
> can get around that — it is an Android safety rule.

## Why it stays smooth (lag-free)

- Tracking runs in its own background service, completely separate from the
  screen — so the app's interface never freezes waiting for GPS or the network.
- All saving and uploading happens off the main thread.
- The interface is built with Jetpack Compose using lazy lists and light
  animations, so scrolling and screen changes stay smooth.

## Real-time location — without Developer Mode

The app never needs Developer Options, ADB, or mock locations. It only uses the
normal permission pop-ups Android shows everyone:

1. **Location → "Allow all the time"** (and Precise location).
2. **Notifications** — so the tracking notification can appear.
3. **"Allow background battery usage"** — so the system doesn't pause it.

That is the whole setup. Tap *Allow* and you're done.

---

## Using it

1. **Backend** — already deployed on a Hugging Face Space; open its URL to see
   the live dashboard.
2. **Android app** — install `app-debug.apk`, open it, complete the short
   permission walkthrough, and tap **Start tracking**.
3. Open the dashboard in any browser — the device appears live, moving along
   the roads, with status, battery and accuracy.

## For developers & AI agents

The deep technical detail — exact Android APIs, the crash-recovery chain, the
full data flow, and how the app survives being killed — is in
**[AGENTS.md](AGENTS.md)**.
