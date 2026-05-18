package com.example.livegps.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.livegps.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// One DataStore per process for the "settings" file (the delegate enforces this).
private val Context.dataStore by preferencesDataStore(name = "settings")

/** Persisted user settings: backend URL, tracking flag, onboarding flag, accuracy mode. */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    private object Keys {
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val HIGH_ACCURACY = booleanPreferencesKey("high_accuracy")
        val CAPTURE_INTERVAL_SEC = intPreferencesKey("capture_interval_sec")
    }

    val backendUrl: Flow<String> = dataStore.data.map { prefs ->
        val stored = prefs[Keys.BACKEND_URL]?.takeIf { it.isNotBlank() }
        (stored ?: BuildConfig.BACKEND_BASE_URL).trimEnd('/')
    }
    val trackingEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.TRACKING_ENABLED] ?: false }
    val onboarded: Flow<Boolean> = dataStore.data.map { it[Keys.ONBOARDED] ?: false }
    val highAccuracy: Flow<Boolean> = dataStore.data.map { it[Keys.HIGH_ACCURACY] ?: true }

    /** Capture interval (seconds) — the dashboard-controlled "ETA". Default 2 min. */
    val captureIntervalSec: Flow<Int> = dataStore.data.map { it[Keys.CAPTURE_INTERVAL_SEC] ?: 120 }

    suspend fun setBackendUrl(value: String) {
        dataStore.edit { it[Keys.BACKEND_URL] = value.trim() }
    }

    suspend fun setTrackingEnabled(value: Boolean) {
        dataStore.edit { it[Keys.TRACKING_ENABLED] = value }
    }

    suspend fun setOnboarded(value: Boolean) {
        dataStore.edit { it[Keys.ONBOARDED] = value }
    }

    suspend fun setHighAccuracy(value: Boolean) {
        dataStore.edit { it[Keys.HIGH_ACCURACY] = value }
    }

    suspend fun setCaptureIntervalSec(value: Int) {
        dataStore.edit { it[Keys.CAPTURE_INTERVAL_SEC] = value.coerceAtLeast(10) }
    }

    suspend fun backendUrlOnce(): String = backendUrl.first()
    suspend fun trackingEnabledOnce(): Boolean = trackingEnabled.first()
    suspend fun captureIntervalSecOnce(): Int = captureIntervalSec.first()
}
