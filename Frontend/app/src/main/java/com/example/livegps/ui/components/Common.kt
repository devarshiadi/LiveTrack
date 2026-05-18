package com.example.livegps.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.TripOrigin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.livegps.ui.theme.SeverityHigh
import com.example.livegps.ui.theme.SeverityLow
import com.example.livegps.ui.theme.SeverityMedium
import com.example.livegps.ui.theme.StatusInactive
import com.example.livegps.ui.theme.StatusOffline
import com.example.livegps.ui.theme.StatusOnline

/** "Live GPS" title bar with a settings gear — shared by every main tab. */
@Composable
fun AppHeader(onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Live GPS",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Balanced / High accuracy segmented control — shared by every main tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccuracySegmentedButtons(
    highAccuracy: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SegmentedButton(
            selected = !highAccuracy,
            onClick = { onChange(false) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            icon = {
                Icon(Icons.Outlined.TripOrigin, contentDescription = null, modifier = Modifier.size(18.dp))
            },
        ) { Text("Balanced") }

        SegmentedButton(
            selected = highAccuracy,
            onClick = { onChange(true) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            icon = {
                if (highAccuracy) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            },
        ) { Text("High accuracy") }
    }
}

/** White rounded card used across the app. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, color = color, shape = shape, shadowElevation = 1.dp) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** Small rounded label (status / severity / category chip). */
@Composable
fun Pill(
    text: String,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
    }
}

/** Filled circle status indicator. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(color))
}

/** Rounded search field used on Devices/Alerts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier,
    )
}

/** Maps a device status string to its accent colour. */
fun statusColor(status: String): Color = when (status) {
    "online" -> StatusOnline
    "offline" -> StatusOffline
    else -> StatusInactive
}

/** Maps an alert severity string to its accent colour. */
fun severityColor(severity: String): Color = when (severity.lowercase()) {
    "high", "critical" -> SeverityHigh
    "medium", "warning" -> SeverityMedium
    else -> SeverityLow
}
