package com.example.livegps.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livegps.LiveGpsApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Shared state for the home shell: accuracy mode + the Alerts-tab badge count. */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val liveApp = app as LiveGpsApp

    val highAccuracy: StateFlow<Boolean> = liveApp.settings.highAccuracy
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _unackedAlerts = MutableStateFlow(0)
    val unackedAlerts: StateFlow<Int> = _unackedAlerts.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                runCatching { liveApp.apiClient.getAlerts() }
                    .onSuccess { alerts -> _unackedAlerts.value = alerts.count { !it.acknowledged } }
                delay(30_000)
            }
        }
    }

    fun setHighAccuracy(value: Boolean) {
        viewModelScope.launch { liveApp.settings.setHighAccuracy(value) }
    }
}
