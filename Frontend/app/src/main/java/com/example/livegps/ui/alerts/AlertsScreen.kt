package com.example.livegps.ui.alerts

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.livegps.data.model.AlertInfo
import com.example.livegps.ui.components.AppSearchBar
import com.example.livegps.ui.components.Pill
import com.example.livegps.ui.components.SectionCard
import com.example.livegps.ui.components.comingSoon
import com.example.livegps.ui.components.severityColor
import com.example.livegps.ui.components.timeAgo
import com.example.livegps.ui.theme.SeverityHigh
import com.example.livegps.ui.theme.SeverityMedium
import com.example.livegps.ui.theme.StatusOnline

/** Alerts tab — overview, the active alert list (incl. realtime battery alerts), and history counts. */
@Composable
fun AlertsScreen(viewModel: AlertsViewModel = viewModel()) {
    val context = LocalContext.current
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    val visible = remember(alerts, query) {
        if (query.isBlank()) alerts
        else alerts.filter {
            it.message.contains(query, true) || it.deviceId.contains(query, true) ||
                it.type.contains(query, true)
        }
    }
    val active = remember(visible) { visible.filter { !it.acknowledged } }
    val counts = remember(alerts) { AlertCounts.from(alerts) }

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
            AppSearchBar(query, viewModel::setQuery, "Search alerts…", Modifier.weight(1f))
            SquareIcon(Icons.Filled.Tune, MaterialTheme.colorScheme.surface) { context.comingSoon() }
            SquareIcon(Icons.Filled.Add, MaterialTheme.colorScheme.primaryContainer) {
                Toast.makeText(context, "Alerts are generated automatically by the server.", Toast.LENGTH_LONG).show()
            }
        }

        if (loading && alerts.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            item(key = "overview") { AlertsOverviewCard(counts) }

            item(key = "active-header") {
                SectionHeader("Active Alerts (${active.size})")
            }

            if (active.isEmpty()) {
                item(key = "active-empty") {
                    Text(
                        "No active alerts — all clear.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(active, key = { it.id }) { alert ->
                    AlertCard(alert, Modifier.animateItem()) { viewModel.acknowledge(alert.id) }
                }
            }

            item(key = "history-header") { SectionHeader("Alert History") }
            item(key = "history") { AlertHistoryRow(counts) }
        }
    }
}

private data class AlertCounts(
    val active: Int,
    val today: Int,
    val week: Int,
    val month: Int,
    val acknowledged: Int,
    val total: Int,
) {
    companion object {
        fun from(alerts: List<AlertInfo>): AlertCounts {
            val now = System.currentTimeMillis()
            val startToday = now / 86_400_000L * 86_400_000L
            return AlertCounts(
                active = alerts.count { !it.acknowledged },
                today = alerts.count { it.createdAt >= startToday },
                week = alerts.count { it.createdAt >= now - 7 * 86_400_000L },
                month = alerts.count { it.createdAt >= now - 30 * 86_400_000L },
                acknowledged = alerts.count { it.acknowledged },
                total = alerts.size,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun AlertsOverviewCard(counts: AlertCounts) {
    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            "Alerts Overview",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OverviewStat(Icons.Filled.NotificationsActive, SeverityHigh, counts.active, "Active")
            OverviewStat(Icons.Filled.WarningAmber, SeverityMedium, counts.today, "Today")
            OverviewStat(Icons.Filled.Info, MaterialTheme.colorScheme.primary, counts.week, "This Week")
            OverviewStat(Icons.Filled.CheckCircle, StatusOnline, counts.month, "This Month")
        }
    }
}

@Composable
private fun OverviewStat(icon: ImageVector, color: Color, value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Surface(color = color.copy(alpha = 0.15f), modifier = Modifier.fillMaxSize()) {}
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AlertCard(alert: AlertInfo, modifier: Modifier = Modifier, onAcknowledge: () -> Unit) {
    val color = severityColor(alert.severity)
    var menuOpen by remember { mutableStateOf(false) }

    SectionCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Surface(color = color.copy(alpha = 0.15f), modifier = Modifier.fillMaxSize()) {}
                Icon(alertIcon(alert.type), null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        humanizeAlertType(alert.type),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill(severityLabel(alert.severity), color, color.copy(alpha = 0.14f))
                    Spacer(Modifier.weight(1f))
                    Text(
                        timeAgo(alert.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Device: ${alert.deviceId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(alert.message, style = MaterialTheme.typography.bodyMedium)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Acknowledge") },
                        onClick = {
                            menuOpen = false
                            onAcknowledge()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertHistoryRow(counts: AlertCounts) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryTile(Icons.Filled.CheckCircle, StatusOnline, counts.acknowledged, "Acknowledged", Modifier.weight(1f))
        HistoryTile(Icons.Filled.NotificationsActive, SeverityHigh, counts.active, "Active", Modifier.weight(1f))
        HistoryTile(Icons.Filled.WarningAmber, SeverityMedium, counts.today, "Today", Modifier.weight(1f))
        HistoryTile(Icons.Filled.Info, MaterialTheme.colorScheme.primary, counts.total, "Total", Modifier.weight(1f))
    }
}

@Composable
private fun HistoryTile(icon: ImageVector, color: Color, value: Int, label: String, modifier: Modifier) {
    SectionCard(modifier = modifier, contentPadding = PaddingValues(10.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SquareIcon(icon: ImageVector, container: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = if (container == MaterialTheme.colorScheme.surface) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
        modifier = Modifier.size(54.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun alertIcon(type: String): ImageVector = when (type) {
    "low_battery" -> Icons.Filled.BatteryAlert
    "offline" -> Icons.Filled.WifiOff
    "gps_off" -> Icons.Filled.LocationOff
    "speed" -> Icons.Filled.Speed
    "route" -> Icons.Filled.AltRoute
    else -> Icons.Filled.WarningAmber
}

private fun humanizeAlertType(type: String): String = when (type) {
    "low_battery" -> "Low Battery"
    "offline" -> "No Signal"
    "gps_off" -> "GPS Disabled"
    "speed" -> "Speed Alert"
    "route" -> "Route Deviation"
    else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun severityLabel(severity: String): String =
    severity.replaceFirstChar { it.uppercase() }
