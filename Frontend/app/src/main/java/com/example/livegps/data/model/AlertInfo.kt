package com.example.livegps.data.model

/** An alert as reported by the backend `GET /api/alerts`. */
data class AlertInfo(
    val id: Long,
    val deviceId: String,
    val type: String,       // low_battery | offline | gps_off | geofence | speed | route
    val severity: String,   // high | medium | low | info
    val message: String,
    val createdAt: Long,    // epoch ms
    val acknowledged: Boolean,
)
