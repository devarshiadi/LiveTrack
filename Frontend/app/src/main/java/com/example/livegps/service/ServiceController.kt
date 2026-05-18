package com.example.livegps.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.livegps.core.Constants

/** Start/stop helpers and a liveness check for [LocationTrackingService]. */
object ServiceController {

    fun start(context: Context) {
        val intent = Intent(context, LocationTrackingService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = Constants.ACTION_STOP
        }
        context.startService(intent)
    }

    /** True if the tracking service is currently running in this process. */
    @Suppress("DEPRECATION") // getRunningServices is still valid for an app's own services
    fun isRunning(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == LocationTrackingService::class.java.name
        }
    }
}
