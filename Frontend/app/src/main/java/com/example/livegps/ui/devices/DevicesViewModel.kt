package com.example.livegps.ui.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livegps.LiveGpsApp
import com.example.livegps.data.model.DeviceInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Loads and polls the device list for the Devices tab. */
class DevicesViewModel(app: Application) : AndroidViewModel(app) {

    private val api = (app as LiveGpsApp).apiClient

    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                load()
                delay(15_000)
            }
        }
    }

    fun setQuery(value: String) { _query.value = value }

    fun refresh() = viewModelScope.launch { load() }

    private suspend fun load() {
        runCatching { api.getDevices() }.onSuccess { _devices.value = it }
        _loading.value = false
    }
}
