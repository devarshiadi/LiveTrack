package com.example.livegps.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.livegps.LiveGpsApp
import com.example.livegps.core.Constants
import com.example.livegps.data.repository.LocationRepository
import com.example.livegps.notification.TrackingNotification
import com.example.livegps.permissions.PermissionUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Foreground service (type `location`) that keeps location tracking alive.
 *
 * It registers a **PendingIntent**-based location request, so Google Play
 * Services keeps delivering fixes to [LocationUpdateReceiver] even if this
 * process crashes or is killed — the OS re-launches the process to deliver
 * them. The service itself just owns the persistent notification and
 * re-requests updates when the dashboard-set interval or accuracy changes.
 */
class LocationTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: LiveGpsApp
    private lateinit var repository: LocationRepository
    private lateinit var fused: FusedLocationProviderClient
    private var started = false
    private var currentIntervalSec = 120

    override fun onCreate() {
        super.onCreate()
        app = application as LiveGpsApp
        repository = app.repository
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Constants.ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        if (!PermissionUtils.hasForegroundLocation(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            goForeground()
            observeSettings()
            observeLatest()
        }
        // START_STICKY: the OS recreates this service after a memory kill.
        return START_STICKY
    }

    private fun goForeground() {
        val notification = TrackingNotification.build(this, repository.latest.value, currentIntervalSec)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                TrackingNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(TrackingNotification.NOTIFICATION_ID, notification)
        }
    }

    /** Re-requests updates whenever the accuracy mode or capture interval changes. */
    private fun observeSettings() {
        combine(
            app.settings.highAccuracy,
            repository.captureIntervalSec,
        ) { high, interval -> high to interval }
            .distinctUntilChanged()
            .onEach { (high, interval) ->
                currentIntervalSec = interval
                requestUpdates(high, interval)
            }
            .launchIn(scope)
    }

    /** Keeps the persistent notification in sync with the latest fix. */
    private fun observeLatest() {
        repository.latest
            .onEach { sample -> TrackingNotification.update(this, sample, currentIntervalSec) }
            .launchIn(scope)
    }

    private fun requestUpdates(highAccuracy: Boolean, intervalSec: Int) {
        val pendingIntent = locationPendingIntent()
        runCatching { fused.removeLocationUpdates(pendingIntent) }

        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val intervalMs = intervalSec.coerceAtLeast(10) * 1000L
        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2).coerceAtLeast(2_000L))
            .setMinUpdateDistanceMeters(0f)
            .build()

        try {
            fused.requestLocationUpdates(request, pendingIntent)
        } catch (e: SecurityException) {
            stopTracking()
        }
    }

    /** The PendingIntent Play Services delivers fixes to — survives process death. */
    private fun locationPendingIntent(): PendingIntent {
        val intent = Intent(this, LocationUpdateReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            LOCATION_PI_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun stopTracking() {
        // Explicit stop — cancel the OS-held location request.
        runCatching { fused.removeLocationUpdates(locationPendingIntent()) }
        started = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** App swiped from recents — schedule a restart so tracking continues. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        RestartReceiver.scheduleRestart(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Deliberately do NOT remove location updates here: if this service was
        // killed (not explicitly stopped), the PendingIntent request must stay
        // alive so Play Services keeps delivering and tracking auto-recovers.
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val LOCATION_PI_REQUEST = 4100
    }
}
