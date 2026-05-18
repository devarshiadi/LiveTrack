package com.example.livegps.data.model

/** A device as reported by the backend `GET /api/devices`. */
data class DeviceInfo(
    val id: String,
    val name: String,
    val status: String,            // online | offline | inactive
    val battery: Int?,
    val effectiveIntervalSec: Int,
    val lastSeenAt: Long,          // epoch ms
    val lat: Double?,
    val lng: Double?,
    val accuracy: Float?,
    val speed: Float?,
    val locationTimestamp: Long?,
) {
    /** True when the last fix indicates the device is in motion. */
    val isMoving: Boolean get() = (speed ?: 0f) >= 1f
}
