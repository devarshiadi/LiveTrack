package com.example.livegps.core

/** App-wide constants shared across the service, repository and receivers. */
object Constants {
    /** Intent action that tells [com.example.livegps.service.LocationTrackingService] to stop. */
    const val ACTION_STOP = "com.example.livegps.action.STOP"

    /** Maximum number of unsent fixes kept in the Room buffer (oldest are pruned). */
    const val MAX_BUFFER = 10_000

    /** How many buffered fixes are uploaded per HTTP request. */
    const val UPLOAD_BATCH_SIZE = 100
}
