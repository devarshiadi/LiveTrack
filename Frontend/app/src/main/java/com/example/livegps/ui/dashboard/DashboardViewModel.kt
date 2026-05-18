package com.example.livegps.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livegps.LiveGpsApp
import com.example.livegps.service.ServiceController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State + actions for the Dashboard (live map) screen. */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val liveApp = app as LiveGpsApp
    private val repository = liveApp.repository
    private val settings = liveApp.settings

    val latest = repository.latest
    val connection = repository.connection
    val bufferedCount = repository.bufferedCount
    val captureIntervalSec = repository.captureIntervalSec
    val locationAvailable = repository.locationAvailable

    val trackingEnabled = settings.trackingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val highAccuracy = settings.highAccuracy
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Called after device location settings are confirmed satisfied. */
    fun startTracking() {
        viewModelScope.launch {
            settings.setTrackingEnabled(true)
            ServiceController.start(getApplication())
        }
    }

    fun stopTracking() {
        viewModelScope.launch {
            settings.setTrackingEnabled(false)
            ServiceController.stop(getApplication())
        }
    }

    fun refresh() {
        viewModelScope.launch { repository.flush() }
    }

    fun highAccuracyValue(): Boolean = highAccuracy.value
}
