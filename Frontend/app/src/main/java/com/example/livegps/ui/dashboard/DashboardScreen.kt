package com.example.livegps.ui.dashboard

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.livegps.data.model.ConnectionState
import com.example.livegps.data.model.LocationSample
import com.example.livegps.location.LocationSettingsHelper
import com.example.livegps.location.LocationSettingsOutcome
import com.example.livegps.ui.components.OsmMapView
import com.example.livegps.ui.components.SectionCard
import com.example.livegps.ui.components.StatusDot
import com.example.livegps.ui.components.formatClock
import com.example.livegps.ui.components.formatInterval
import com.example.livegps.ui.components.rememberMapView
import com.example.livegps.ui.theme.StatusInactive
import com.example.livegps.ui.theme.StatusOffline
import com.example.livegps.ui.theme.StatusOnline
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint

/** The live-map dashboard — matches the mobile mockup. */
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val latest by viewModel.latest.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val buffered by viewModel.bufferedCount.collectAsStateWithLifecycle()
    val intervalSec by viewModel.captureIntervalSec.collectAsStateWithLifecycle()
    val locationAvailable by viewModel.locationAvailable.collectAsStateWithLifecycle()
    val tracking by viewModel.trackingEnabled.collectAsStateWithLifecycle()

    val mapView = rememberMapView()
    var fullscreen by remember { mutableStateOf(false) }
    var topoStyle by remember { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.startTracking()
    }

    val recenter: () -> Unit = {
        latest?.let {
            mapView.controller.animateTo(GeoPoint(it.lat, it.lng))
            mapView.controller.setZoom(17.0)
        }
    }
    val toggleLayers: () -> Unit = {
        topoStyle = !topoStyle
        mapView.setTileSource(if (topoStyle) TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK)
    }
    val toggleTracking: () -> Unit = {
        if (tracking) {
            viewModel.stopTracking()
        } else {
            scope.launch {
                when (val outcome = LocationSettingsHelper.check(context, viewModel.highAccuracyValue())) {
                    is LocationSettingsOutcome.Satisfied -> viewModel.startTracking()
                    is LocationSettingsOutcome.Resolvable -> settingsLauncher.launch(outcome.request)
                    LocationSettingsOutcome.Unavailable -> viewModel.startTracking()
                }
            }
        }
    }
    val shareLocation: () -> Unit = {
        latest?.let { loc ->
            val text = "My location: https://maps.google.com/?q=${loc.lat},${loc.lng}"
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, "Share location"))
        }
    }

    // Keep the osmdroid MapView at ONE stable call site — re-parenting it (e.g.
    // a separate fullscreen branch) crashes with "child already has a parent".
    // Fullscreen just hides the surrounding cards and lets the map Box fill.
    LaunchedEffect(fullscreen) { mapView.invalidate() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!fullscreen) {
            LiveStatusCard(
                tracking = tracking,
                connection = connection,
                locationAvailable = locationAvailable,
                location = latest,
                onRecenter = recenter,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp)),
        ) {
            OsmMapView(mapView, latest, Modifier.fillMaxSize(), follow = tracking)
            MapControlColumn(
                modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
                topoStyle = topoStyle,
                onLayers = toggleLayers,
                onRecenter = recenter,
                fullscreenIcon = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.OpenInFull,
                onFullscreen = { fullscreen = !fullscreen },
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                ZoomControls(
                    onZoomIn = { mapView.controller.zoomIn() },
                    onZoomOut = { mapView.controller.zoomOut() },
                )
                TrackingFab(tracking = tracking, onClick = toggleTracking)
            }
        }

        if (!fullscreen) {
            SessionCard(
                intervalSec = intervalSec,
                buffered = buffered,
                location = latest,
                onRecenter = recenter,
                onShare = shareLocation,
                onRefresh = viewModel::refresh,
            )
        }
    }
}

// ---- pieces ----

@Composable
private fun LiveStatusCard(
    tracking: Boolean,
    connection: ConnectionState,
    locationAvailable: Boolean,
    location: LocationSample?,
    onRecenter: () -> Unit,
) {
    val (label, dot) = statusLabel(tracking, connection, locationAvailable)
    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(dot)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (location != null) {
                        "Lat %.5f,  Lng %.5f".format(location.lat, location.lng)
                    } else {
                        "Waiting for a GPS fix…"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (location != null) {
                    val acc = location.accuracy?.let { "±${it.toInt()} m" } ?: "—"
                    Text(
                        text = "Accuracy $acc  •  updated ${formatClock(location.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MapControlButton(Icons.Filled.MyLocation, "Recenter", onRecenter)
        }
    }
}

@Composable
private fun MapControlColumn(
    modifier: Modifier,
    topoStyle: Boolean,
    onLayers: () -> Unit,
    onRecenter: () -> Unit,
    fullscreenIcon: ImageVector,
    onFullscreen: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MapControlButton(Icons.Filled.Layers, "Map layers", onLayers)
        MapControlButton(Icons.Filled.MyLocation, "Recenter", onRecenter)
        MapControlButton(fullscreenIcon, "Fullscreen", onFullscreen)
    }
}

@Composable
private fun ZoomControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MapControlButton(Icons.Filled.Remove, "Zoom out", onZoomOut)
        MapControlButton(Icons.Filled.Add, "Zoom in", onZoomIn)
    }
}

@Composable
private fun MapControlButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TrackingFab(tracking: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (tracking) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primary,
        label = "fabColor",
    )
    val content = if (tracking) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimary
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = container,
        contentColor = content,
        icon = {
            Icon(
                imageVector = if (tracking) Icons.Filled.Close else Icons.Filled.PlayArrow,
                contentDescription = null,
            )
        },
        text = { Text(if (tracking) "Stop tracking" else "Start tracking") },
    )
}

@Composable
private fun SessionCard(
    intervalSec: Int,
    buffered: Int,
    location: LocationSample?,
    onRecenter: () -> Unit,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
) {
    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize()) {}
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                SessionRow("Interval", "every ${formatInterval(intervalSec)}", "Buffered", buffered.toString())
                Spacer(Modifier.height(8.dp))
                SessionRow(
                    "Accuracy",
                    location?.accuracy?.let { "±${it.toInt()} m" } ?: "—",
                    "Battery",
                    location?.battery?.let { "$it%" } ?: "—",
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SessionChip("Recenter", filled = true, modifier = Modifier.weight(1f), onClick = onRecenter)
            SessionChip("Share", filled = false, modifier = Modifier.weight(1f), onClick = onShare)
            SessionChip("Refresh", filled = false, modifier = Modifier.weight(1f), onClick = onRefresh)
        }
    }
}

@Composable
private fun SessionRow(k1: String, v1: String, k2: String, v2: String) {
    Row {
        SessionField(k1, v1, Modifier.weight(1f))
        SessionField(k2, v2, Modifier.weight(1f))
    }
}

@Composable
private fun SessionField(key: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            key,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SessionChip(
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = modifier.height(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (filled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun statusLabel(
    tracking: Boolean,
    connection: ConnectionState,
    locationAvailable: Boolean,
): Pair<String, Color> = when {
    !tracking -> "Tracking paused" to StatusInactive
    !locationAvailable -> "GPS signal lost" to StatusOffline
    connection == ConnectionState.ONLINE -> "Online — synced with server" to StatusOnline
    connection == ConnectionState.OFFLINE -> "Offline — buffering locally" to StatusOffline
    else -> "Connecting…" to StatusInactive
}
