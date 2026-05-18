package com.example.livegps.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.example.livegps.core.Constants
import com.example.livegps.core.DeviceId
import com.example.livegps.data.local.LocationDao
import com.example.livegps.data.local.SettingsStore
import com.example.livegps.data.local.toEntity
import com.example.livegps.data.local.toSample
import com.example.livegps.data.model.ConnectionState
import com.example.livegps.data.model.LocationSample
import com.example.livegps.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Process-wide single source of truth for location data.
 *
 * The tracking service pushes fixes in via [onNewLocation]; the UI observes the
 * exposed flows. Every fix is persisted to Room before any upload, so nothing
 * is lost when the backend is unreachable — the buffer drains on the next
 * successful upload or when connectivity returns. Each upload response also
 * carries the dashboard-set capture interval.
 */
class LocationRepository(
    context: Context,
    private val dao: LocationDao,
    private val api: ApiClient,
    private val settings: SettingsStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val deviceId: String = DeviceId.get(appContext)

    private val _latest = MutableStateFlow<LocationSample?>(null)
    val latest: StateFlow<LocationSample?> = _latest.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionState.IDLE)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    /** Dashboard-controlled capture interval in seconds. */
    private val _captureIntervalSec = MutableStateFlow(120)
    val captureIntervalSec: StateFlow<Int> = _captureIntervalSec.asStateFlow()

    /** False when the OS reports location is currently unavailable (GPS off / no fix). */
    private val _locationAvailable = MutableStateFlow(true)
    val locationAvailable: StateFlow<Boolean> = _locationAvailable.asStateFlow()

    val bufferedCount: StateFlow<Int> =
        dao.countFlow().stateIn(scope, SharingStarted.Eagerly, 0)

    private val flushMutex = Mutex()

    init {
        scope.launch { _captureIntervalSec.value = settings.captureIntervalSecOnce() }
        registerNetworkCallback()
    }

    /**
     * Records a fix: persist to Room (fast, awaited) and trigger an upload
     * (fire-and-forget). Suspends only for the durable write, so a caller in a
     * BroadcastReceiver can safely finish once this returns.
     */
    suspend fun recordLocation(sample: LocationSample) {
        _latest.value = sample
        dao.insert(sample.toEntity())
        dao.pruneToNewest(Constants.MAX_BUFFER)
        scope.launch { flush() }
    }

    /** Fire-and-forget variant for non-suspend callers. */
    fun onNewLocation(sample: LocationSample) {
        scope.launch { recordLocation(sample) }
    }

    /** Called by the service from LocationCallback.onLocationAvailability. */
    fun setLocationAvailable(available: Boolean) {
        _locationAvailable.value = available
    }

    /** Drains the Room buffer to the backend, oldest first. */
    suspend fun flush() {
        if (!flushMutex.tryLock()) return
        try {
            while (true) {
                val batch = dao.oldestBatch(Constants.UPLOAD_BATCH_SIZE)
                if (batch.isEmpty()) break
                _connection.value = ConnectionState.CONNECTING
                val result = api.uploadBatch(deviceId, batch.map { it.toSample() })
                if (result.success) {
                    dao.deleteByIds(batch.map { it.id })
                    _connection.value = ConnectionState.ONLINE
                    result.captureIntervalSec?.let { applyInterval(it) }
                } else {
                    _connection.value = ConnectionState.OFFLINE
                    break
                }
            }
        } finally {
            flushMutex.unlock()
        }
    }

    private suspend fun applyInterval(sec: Int) {
        if (sec > 0 && sec != _captureIntervalSec.value) {
            _captureIntervalSec.value = sec
            settings.setCaptureIntervalSec(sec)
        }
    }

    private fun registerNetworkCallback() {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { flush() }
                }
            })
        }
    }
}
