package com.example.livegps.ui.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livegps.LiveGpsApp
import com.example.livegps.data.model.AlertInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Loads and polls alerts (incl. realtime low-battery alerts) for the Alerts tab. */
class AlertsViewModel(app: Application) : AndroidViewModel(app) {

    private val api = (app as LiveGpsApp).apiClient

    private val _alerts = MutableStateFlow<List<AlertInfo>>(emptyList())
    val alerts = _alerts.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                load()
                delay(15_000) // near-realtime refresh
            }
        }
    }

    fun setQuery(value: String) { _query.value = value }

    fun refresh() = viewModelScope.launch { load() }

    fun acknowledge(id: Long) {
        viewModelScope.launch {
            api.ackAlert(id)
            load()
        }
    }

    private suspend fun load() {
        runCatching { api.getAlerts() }.onSuccess { _alerts.value = it }
        _loading.value = false
    }
}
