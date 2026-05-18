package com.example.livegps.core

import android.content.Context
import java.util.UUID

/** Provides a stable, per-install identifier used to tag every location report. */
object DeviceId {
    private const val PREFS = "device_identity"
    private const val KEY = "device_id"

    /** Returns the device id, generating and persisting one on first call. */
    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, fresh).apply()
        return fresh
    }
}
