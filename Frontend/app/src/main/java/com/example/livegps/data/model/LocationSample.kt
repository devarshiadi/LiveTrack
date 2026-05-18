package com.example.livegps.data.model

/** One GPS fix. Accuracy/speed/bearing/battery are nullable — a fix may omit them. */
data class LocationSample(
    val lat: Double,
    val lng: Double,
    val accuracy: Float?,
    val speed: Float?,
    val bearing: Float?,
    val battery: Int?,
    val timestamp: Long, // epoch milliseconds, capture time
)
