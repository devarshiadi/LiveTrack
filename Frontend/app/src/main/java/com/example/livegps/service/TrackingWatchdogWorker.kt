package com.example.livegps.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.livegps.data.local.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * Periodic safety net. If tracking is enabled but the service is not running
 * (e.g. an OS memory kill that START_STICKY did not recover), restart it.
 * Runs every 15 minutes — the WorkManager minimum for periodic work.
 */
class TrackingWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val enabled = SettingsStore(applicationContext).trackingEnabledOnce()
        if (enabled && !ServiceController.isRunning(applicationContext)) {
            runCatching { ServiceController.start(applicationContext) }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "tracking_watchdog"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<TrackingWatchdogWorker>(
                15, TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
