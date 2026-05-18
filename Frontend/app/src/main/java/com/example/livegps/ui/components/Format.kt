package com.example.livegps.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "2m ago", "5h ago", … */
fun timeAgo(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    val s = ((System.currentTimeMillis() - epochMs) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s ago"
        s < 3600 -> "${s / 60}m ago"
        s < 86_400 -> "${s / 3600}h ago"
        else -> "${s / 86_400}d ago"
    }
}

/** "1:29:17 am" */
fun formatClock(epochMs: Long): String =
    if (epochMs <= 0L) "—"
    else SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(epochMs))

/** "09:16:40 AM" */
fun formatTime24(epochMs: Long): String =
    if (epochMs <= 0L) "—"
    else SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(epochMs))

/** "May 18, 2025" */
fun formatDay(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))

/** Human capture-interval label: "45 sec", "2 min", "2.5 min". */
fun formatInterval(sec: Int): String = when {
    sec < 60 -> "$sec sec"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> String.format(Locale.US, "%.1f min", sec / 60.0)
}
