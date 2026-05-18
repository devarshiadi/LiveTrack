package com.example.livegps.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livegps.LiveGpsApp
import com.example.livegps.core.haversineMeters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class HistoryEventType { STARTED, LOCATION, MOVEMENT }

/** One row of the History timeline, derived from the device's track. */
data class HistoryEvent(
    val type: HistoryEventType,
    val timestamp: Long,
    val detail: String,
    val badge: String,
) {
    val title: String
        get() = when (type) {
            HistoryEventType.STARTED -> "Tracking Started"
            HistoryEventType.LOCATION -> "Location Update"
            HistoryEventType.MOVEMENT -> "Movement Detected"
        }
}

/** Builds the History timeline + totals from this device's track. */
class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val liveApp = app as LiveGpsApp
    private val api = liveApp.apiClient
    private val deviceId = liveApp.repository.deviceId

    private val _events = MutableStateFlow<List<HistoryEvent>>(emptyList())
    val events = _events.asStateFlow()

    private val _totalDistanceKm = MutableStateFlow(0.0)
    val totalDistanceKm = _totalDistanceKm.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                load()
                delay(20_000)
            }
        }
    }

    fun refresh() = viewModelScope.launch { load() }

    suspend fun exportUrl(): String =
        "${liveApp.settings.backendUrlOnce()}/api/devices/$deviceId/export.csv"

    private suspend fun load() {
        val track = runCatching { api.getTrack(deviceId) }.getOrDefault(emptyList())
        var distance = 0.0
        val events = ArrayList<HistoryEvent>(track.size)
        for (i in track.indices) {
            val p = track[i]
            if (i > 0) {
                val prev = track[i - 1]
                distance += haversineMeters(prev.lat, prev.lng, p.lat, p.lng)
            }
            val speedKmh = (p.speed ?: 0f) * 3.6f
            val type = when {
                i == 0 -> HistoryEventType.STARTED
                speedKmh >= 8f -> HistoryEventType.MOVEMENT
                else -> HistoryEventType.LOCATION
            }
            // Every captured point shows its lat/lng; movement also shows speed.
            val coords = "Lat %.5f, Lng %.5f".format(p.lat, p.lng)
            val detail = when (type) {
                HistoryEventType.MOVEMENT -> "$coords · ${speedKmh.toInt()} km/h"
                else -> coords
            }
            val badge = when (type) {
                HistoryEventType.STARTED -> "Manual"
                HistoryEventType.MOVEMENT -> "Moving"
                HistoryEventType.LOCATION -> "High accuracy"
            }
            events += HistoryEvent(type, p.timestamp, detail, badge)
        }
        _events.value = events.asReversed() // newest first
        _totalDistanceKm.value = distance / 1000.0
        _loading.value = false
    }
}
