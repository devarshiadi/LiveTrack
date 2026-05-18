package com.example.livegps.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.livegps.MainActivity
import com.example.livegps.R
import com.example.livegps.core.Constants
import com.example.livegps.data.model.LocationSample
import com.example.livegps.service.LocationTrackingService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the ongoing tracking notification. Designed to Material 3 notification
 * guidance — most important info first, concise text within the recommended
 * character budgets (title < 29, collapsed < 40, expanded < 80), the brand logo
 * as the large icon, the app accent colour, and a single Stop action.
 */
object TrackingNotification {

    const val CHANNEL_ID = "location_tracking"
    const val NOTIFICATION_ID = 1001
    private const val STOP_REQUEST = 4200
    private const val OPEN_REQUEST = 4201

    /** Creates the low-importance tracking channel. Safe to call repeatedly. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /** Builds the ongoing notification for the current fix + capture interval. */
    fun build(context: Context, sample: LocationSample?, intervalSec: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            context, OPEN_REQUEST,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            context, STOP_REQUEST,
            Intent(context, LocationTrackingService::class.java).setAction(Constants.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val interval = formatInterval(intervalSec)
        val collapsed: String
        val expanded: String
        if (sample != null) {
            val acc = sample.accuracy?.let { "±${it.toInt()} m" } ?: "GPS"
            collapsed = "$acc · updated ${clock(sample.timestamp)}"
            expanded = "Capturing your location every $interval. " +
                "Last fix ${"%.5f, %.5f".format(sample.lat, sample.lng)}."
        } else {
            collapsed = "Waiting for the first GPS fix…"
            expanded = "Capturing your location every $interval. Waiting for the first GPS fix."
        }

        val logo = runCatching {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }.getOrNull()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .also { builder -> logo?.let { builder.setLargeIcon(it) } }
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setColorized(false)
            .setContentTitle("Live GPS · tracking on")
            .setContentText(collapsed)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_notification, "Stop", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /** Refreshes the ongoing notification. */
    fun update(context: Context, sample: LocationSample?, intervalSec: Int) {
        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, build(context, sample, intervalSec))
    }

    private fun formatInterval(sec: Int): String = when {
        sec < 60 -> "$sec sec"
        sec % 60 == 0 -> "${sec / 60} min"
        else -> String.format(Locale.US, "%.1f min", sec / 60.0)
    }

    private fun clock(ms: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))
}
