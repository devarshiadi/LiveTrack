package com.example.livegps.ui.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.livegps.ui.components.Pill
import com.example.livegps.ui.components.SectionCard
import com.example.livegps.ui.components.comingSoon
import com.example.livegps.ui.components.formatDay
import com.example.livegps.ui.components.formatTime24
import com.example.livegps.ui.theme.SeverityMedium
import com.example.livegps.ui.theme.StatusOnline
import kotlinx.coroutines.launch

/** History tab — a timeline of tracking events derived from the device track. */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val distanceKm by viewModel.totalDistanceKm.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    val rangeLabel = remember(events) {
        if (events.isEmpty()) "All time"
        else "${formatDay(events.last().timestamp)} – ${formatDay(events.first().timestamp)}"
    }
    val grouped = remember(events) {
        events.groupBy { it.timestamp / 86_400_000L * 86_400_000L }
            .toList()
            .sortedByDescending { it.first }
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
            FilterChipBox(Icons.Filled.CalendarMonth, rangeLabel, Modifier.weight(1.4f)) {
                context.comingSoon()
            }
            FilterChipBox(Icons.Filled.FilterList, "All Events", Modifier.weight(1f)) {
                context.comingSoon()
            }
            Surface(
                onClick = {
                    scope.launch {
                        val url = viewModel.exportUrl()
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Download, "Export", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        SummaryCard(totalEvents = events.size, distanceKm = distanceKm)

        when {
            loading && events.isEmpty() ->
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    CircularProgressIndicator()
                }

            events.isEmpty() ->
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    Text(
                        "No tracking history yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            else ->
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    grouped.forEach { (day, dayEvents) ->
                        item(key = "day-$day") {
                            Text(
                                text = formatDay(day),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        // Index-based keys: location timestamps can repeat
                        // (duplicate rows from a re-sent upload batch), and a
                        // LazyColumn requires unique keys or it crashes.
                        itemsIndexed(
                            dayEvents,
                            key = { index, _ -> "ev-$day-$index" },
                        ) { _, event ->
                            TimelineRow(event, Modifier.animateItem())
                        }
                    }
                }
        }
    }
}

@Composable
private fun SummaryCard(totalEvents: Int, distanceKm: Double) {
    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SummaryHalf(
                icon = Icons.Filled.Schedule,
                label = "Total Events",
                value = totalEvents.toString(),
                sub = "In selected period",
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .height(48.dp)
                    .width(1.dp)
                    .clip(RoundedCornerShape(1.dp)),
            ) {
                Surface(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxSize()) {}
            }
            SummaryHalf(
                icon = Icons.Filled.Route,
                label = "Total Distance",
                value = "%.1f km".format(distanceKm),
                sub = "Tracked distance",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryHalf(
    icon: ImageVector,
    label: String,
    value: String,
    sub: String,
    modifier: Modifier,
) {
    Row(modifier = modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimelineRow(event: HistoryEvent, modifier: Modifier = Modifier) {
    Row(modifier = modifier.height(IntrinsicSize.Min)) {
        // Timeline gutter: connecting line + event circle.
        Box(modifier = Modifier.width(44.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.width(2.dp).fillMaxHeight().align(Alignment.Center),
            ) {
                Surface(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxSize()) {}
            }
            Box(
                modifier = Modifier.padding(top = 14.dp).size(34.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val color = eventColor(event.type)
                Surface(color = color.copy(alpha = 0.16f), modifier = Modifier.fillMaxSize()) {}
                Icon(eventIcon(event.type), null, tint = color, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        SectionCard(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
            contentPadding = PaddingValues(14.dp),
        ) {
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatTime24(event.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(event.detail, style = MaterialTheme.typography.bodyMedium)
                }
                val badgeColor = if (event.badge == "Moving") SeverityMedium else MaterialTheme.colorScheme.primary
                Pill(event.badge, badgeColor, badgeColor.copy(alpha = 0.14f))
            }
        }
    }
}

@Composable
private fun FilterChipBox(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(52.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Icon(Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun eventColor(type: HistoryEventType): Color = when (type) {
    HistoryEventType.STARTED -> StatusOnline
    HistoryEventType.MOVEMENT -> SeverityMedium
    HistoryEventType.LOCATION -> Color(0xFF6750A4)
}

private fun eventIcon(type: HistoryEventType): ImageVector = when (type) {
    HistoryEventType.STARTED -> Icons.Filled.PlayArrow
    HistoryEventType.MOVEMENT -> Icons.Filled.DirectionsRun
    HistoryEventType.LOCATION -> Icons.Filled.LocationOn
}
