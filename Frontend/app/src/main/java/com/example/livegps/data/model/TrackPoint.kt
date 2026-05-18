package com.example.livegps.data.model

/** One point of a device's location history. */
data class TrackPoint(
    val lat: Double,
    val lng: Double,
    val accuracy: Float?,
    val speed: Float?,
    val bearing: Float?,
    val battery: Int?,
    val timestamp: Long, // epoch ms
)
