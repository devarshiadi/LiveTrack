package com.example.livegps.ui.devices

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.livegps.data.model.DeviceInfo
import com.example.livegps.ui.components.AppSearchBar
import com.example.livegps.ui.components.Pill
import com.example.livegps.ui.components.SectionCard
import com.example.livegps.ui.components.StatusDot
import com.example.livegps.ui.components.comingSoon
import com.example.livegps.ui.components.statusColor
import com.example.livegps.ui.components.timeAgo
import com.example.livegps.ui.theme.SeverityMedium
import com.example.livegps.ui.theme.StatusInactive
import com.example.livegps.ui.theme.StatusOffline
import com.example.livegps.ui.theme.StatusOnline

/** Devices tab — searchable device list with a status summary. */
@Composable
fun DevicesScreen(viewModel: DevicesViewModel = viewModel()) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    val filtered = remember(devices, query) {
        if (query.isBlank()) devices
        else devices.filter { it.id.contains(query, true) || it.name.contains(query, true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSearchBar(
                query = query,
                onQueryChange = viewModel::setQuery,
                placeholder = "Search devices…",
                modifier = Modifier.weight(1f),
            )
            SquareButton(Icons.Filled.Tune, "Filter", MaterialTheme.colorScheme.surface) {
                context.comingSoon()
            }
            SquareButton(Icons.Filled.Add, "Add", MaterialTheme.colorScheme.primaryContainer) {
                Toast.makeText(
                    context,
                    "Devices register automatically when the app starts tracking.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

        when {
            loading && devices.isEmpty() ->
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    CircularProgressIndicator()
                }

            filtered.isEmpty() ->
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    Text(
                        "No devices yet — start tracking on a phone.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            else ->
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    items(filtered, key = { it.id }) { device ->
                        DeviceCard(device, Modifier.animateItem())
                    }
                }
        }

        SummaryCard(devices)
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SectionCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(statusColor(device.status), size = 9.dp)
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
                Icon(
                    Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.id,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (device.lat != null && device.lng != null) {
                        "Lat %.5f, Lng %.5f".format(device.lat, device.lng)
                    } else {
                        "Lat — , Lng —"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MotionPill(device)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = device.accuracy?.let { "Accuracy ±${it.toInt()} m" } ?: "No Data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeAgo(device.lastSeenAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { context.comingSoon() }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Device options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionPill(device: DeviceInfo) {
    when {
        device.status == "inactive" ->
            Pill("Inactive", StatusInactive, MaterialTheme.colorScheme.surfaceVariant)
        device.isMoving ->
            Pill("Moving", SeverityMedium, SeverityMedium.copy(alpha = 0.14f))
        else ->
            Pill("Stationary", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
    }
}

@Composable
private fun SummaryCard(devices: List<DeviceInfo>) {
    val online = devices.count { it.status == "online" }
    val offline = devices.count { it.status == "offline" }
    val inactive = devices.count { it.status == "inactive" }

    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "Total Devices",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = devices.size.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                CountRow(StatusOnline, online, "Online")
                CountRow(StatusOffline, offline, "Offline")
                CountRow(StatusInactive, inactive, "Inactive")
            }
            Icon(
                Icons.Filled.SatelliteAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun CountRow(color: Color, count: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color, size = 8.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SquareButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    container: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = if (container == MaterialTheme.colorScheme.surface) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
        modifier = Modifier.size(54.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
