package com.example.livegps

import android.app.Application
import com.example.livegps.data.local.AppDatabase
import com.example.livegps.data.local.SettingsStore
import com.example.livegps.data.remote.ApiClient
import com.example.livegps.data.repository.LocationRepository
import com.example.livegps.notification.TrackingNotification
import com.example.livegps.service.RestartReceiver
import com.example.livegps.service.TrackingWatchdogWorker
import org.osmdroid.config.Configuration

/** Application entry point — builds the process-wide singletons. */
class LiveGpsApp : Application() {

    /** Shared HTTP client for backend reads + uploads. */
    lateinit var apiClient: ApiClient
        private set

    /** Shared by the tracking service (producer) and the UI (consumer). */
    lateinit var repository: LocationRepository
        private set

    /** Shared settings store. */
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()

        // osmdroid needs a user agent set before it loads any map tiles.
        Configuration.getInstance().userAgentValue = packageName

        settings = SettingsStore(this)
        apiClient = ApiClient(settings)
        repository = LocationRepository(
            context = this,
            dao = AppDatabase.get(this).locationDao(),
            api = apiClient,
            settings = settings,
        )

        TrackingNotification.createChannel(this)
        TrackingWatchdogWorker.enqueue(this)
        installCrashHandler()
    }

    /**
     * On any uncaught exception, schedule a restart of the tracking service
     * before the process dies — "auto turn on if it crashes". RestartReceiver
     * re-checks whether tracking is enabled when the alarm fires. The previous
     * handler still runs so the crash is reported normally.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { RestartReceiver.scheduleRestart(this) }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
