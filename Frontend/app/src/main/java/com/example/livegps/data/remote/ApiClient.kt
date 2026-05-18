package com.example.livegps.data.remote

import com.example.livegps.data.local.SettingsStore
import com.example.livegps.data.model.AlertInfo
import com.example.livegps.data.model.DeviceInfo
import com.example.livegps.data.model.LocationSample
import com.example.livegps.data.model.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Result of a location upload — whether it succeeded and the server-set interval. */
data class UploadResult(val success: Boolean, val captureIntervalSec: Int?)

/** HTTP client for the Go backend: location upload plus the dashboard read APIs. */
class ApiClient(private val settings: SettingsStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Uploads a batch of fixes; returns success and the server's capture interval. */
    suspend fun uploadBatch(deviceId: String, samples: List<LocationSample>): UploadResult =
        withContext(Dispatchers.IO) {
            if (samples.isEmpty()) return@withContext UploadResult(true, null)
            val base = settings.backendUrlOnce()
            val array = JSONArray()
            for (s in samples) {
                array.put(JSONObject().apply {
                    put("device_id", deviceId)
                    put("lat", s.lat)
                    put("lng", s.lng)
                    s.accuracy?.let { put("accuracy", it.toDouble()) }
                    s.speed?.let { put("speed", it.toDouble()) }
                    s.bearing?.let { put("bearing", it.toDouble()) }
                    s.battery?.let { put("battery", it) }
                    put("timestamp", Instant.ofEpochMilli(s.timestamp).toString())
                })
            }
            val req = Request.Builder()
                .url("$base/api/locations")
                .post(array.toString().toRequestBody(jsonMedia))
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext UploadResult(false, null)
                    val interval = resp.body?.string()
                        ?.let { runCatching { JSONObject(it).optInt("capture_interval_sec", -1) }.getOrDefault(-1) }
                        ?.takeIf { it > 0 }
                    UploadResult(true, interval)
                }
            } catch (e: Exception) {
                UploadResult(false, null)
            }
        }

    suspend fun getDevices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        val base = settings.backendUrlOnce()
        httpGet("$base/api/devices")?.let { body ->
            val arr = JSONArray(body)
            (0 until arr.length()).map { parseDevice(arr.getJSONObject(it)) }
        } ?: emptyList()
    }

    suspend fun getAlerts(): List<AlertInfo> = withContext(Dispatchers.IO) {
        val base = settings.backendUrlOnce()
        httpGet("$base/api/alerts")?.let { body ->
            val arr = JSONArray(body)
            (0 until arr.length()).map { parseAlert(arr.getJSONObject(it)) }
        } ?: emptyList()
    }

    suspend fun ackAlert(id: Long): Boolean = withContext(Dispatchers.IO) {
        val base = settings.backendUrlOnce()
        val req = Request.Builder()
            .url("$base/api/alerts/$id/ack")
            .post("".toRequestBody(jsonMedia))
            .build()
        try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getTrack(deviceId: String): List<TrackPoint> = withContext(Dispatchers.IO) {
        val base = settings.backendUrlOnce()
        httpGet("$base/api/devices/$deviceId/track?limit=5000")?.let { body ->
            val points = JSONObject(body).optJSONArray("points") ?: return@withContext emptyList()
            (0 until points.length()).map { parseTrackPoint(points.getJSONObject(it)) }
        } ?: emptyList()
    }

    private fun httpGet(url: String): String? = try {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    } catch (e: Exception) {
        null
    }

    // ---- parsing ----

    private fun parseDevice(o: JSONObject): DeviceInfo {
        val last = o.optJSONObject("last_location")
        return DeviceInfo(
            id = o.getString("id"),
            name = o.optString("name", ""),
            status = o.optString("status", "inactive"),
            battery = o.optIntOrNull("battery"),
            effectiveIntervalSec = o.optInt("effective_interval_sec", 120),
            lastSeenAt = parseMillis(o.optString("last_seen_at")),
            lat = last?.optDouble("lat"),
            lng = last?.optDouble("lng"),
            accuracy = last?.optFloatOrNull("accuracy"),
            speed = last?.optFloatOrNull("speed"),
            locationTimestamp = last?.let { parseMillis(it.optString("timestamp")) },
        )
    }

    private fun parseAlert(o: JSONObject): AlertInfo = AlertInfo(
        id = o.optLong("id"),
        deviceId = o.optString("device_id", ""),
        type = o.optString("type", ""),
        severity = o.optString("severity", "info"),
        message = o.optString("message", ""),
        createdAt = parseMillis(o.optString("created_at")),
        acknowledged = o.optBoolean("acknowledged", false),
    )

    private fun parseTrackPoint(o: JSONObject): TrackPoint = TrackPoint(
        lat = o.optDouble("lat"),
        lng = o.optDouble("lng"),
        accuracy = o.optFloatOrNull("accuracy"),
        speed = o.optFloatOrNull("speed"),
        bearing = o.optFloatOrNull("bearing"),
        battery = o.optIntOrNull("battery"),
        timestamp = parseMillis(o.optString("timestamp")),
    )

    private fun parseMillis(rfc3339: String): Long =
        runCatching { Instant.parse(rfc3339).toEpochMilli() }.getOrDefault(0L)
}

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optFloatOrNull(key: String): Float? =
    if (has(key) && !isNull(key)) optDouble(key).toFloat() else null
