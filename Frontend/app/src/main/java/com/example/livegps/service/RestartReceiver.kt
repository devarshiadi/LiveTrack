package com.example.livegps.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.livegps.data.local.SettingsStore
import kotlinx.coroutines.runBlocking

/**
 * Restarts the tracking service after the app is swiped from recents.
 * [LocationTrackingService.onTaskRemoved] schedules a short AlarmManager alarm
 * via [scheduleRestart], which fires this receiver.
 */
class RestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val enabled = runBlocking { SettingsStore(context).trackingEnabledOnce() }
        if (enabled) {
            runCatching { ServiceController.start(context) }
        }
    }

    companion object {
        private const val REQUEST_CODE = 7001

        /** Schedules a one-shot alarm ~2s out to restart the service. */
        fun scheduleRestart(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, RestartReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 2_000L,
                pendingIntent,
            )
        }
    }
}
