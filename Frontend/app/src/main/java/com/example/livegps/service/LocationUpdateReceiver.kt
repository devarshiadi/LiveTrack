package com.example.livegps.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.livegps.LiveGpsApp
import com.example.livegps.data.model.LocationSample
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives location updates from the FusedLocationProviderClient PendingIntent.
 *
 * Because the location request is held by Google Play Services (not the app
 * process), the OS keeps delivering fixes here — re-launching the app process
 * if it has crashed or been killed. This receiver then persists the fix and
 * re-establishes the foreground service, so tracking auto-recovers.
 */
class LocationUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? LiveGpsApp ?: return

        // Reflect GPS availability for the UI banner.
        LocationAvailability.extractLocationAvailability(intent)?.let {
            app.repository.setLocationAvailable(it.isLocationAvailable)
        }

        val result = LocationResult.extractResult(intent) ?: return
        if (result.locations.isEmpty()) return

        val battery = batteryPercent(context)
        val samples = result.locations.map { loc ->
            LocationSample(
                lat = loc.latitude,
                lng = loc.longitude,
                accuracy = if (loc.hasAccuracy()) loc.accuracy else null,
                speed = if (loc.hasSpeed()) loc.speed else null,
                bearing = if (loc.hasBearing()) loc.bearing else null,
                battery = battery,
                timestamp = if (loc.time > 0L) loc.time else System.currentTimeMillis(),
            )
        }

        // goAsync() keeps the receiver alive while the durable Room write runs —
        // important when the process was revived only to handle this broadcast.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                samples.forEach { app.repository.recordLocation(it) }
                app.repository.setLocationAvailable(true)
            } finally {
                // Re-establish the foreground service if it isn't running
                // (best-effort; START_STICKY also recreates it).
                if (!ServiceController.isRunning(context)) {
                    runCatching { ServiceController.start(context) }
                }
                pending.finish()
            }
        }
    }

    private fun batteryPercent(context: Context): Int? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else null
    }
}
