package com.example.livegps.ui.more

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.livegps.LiveGpsApp
import com.example.livegps.permissions.PermissionUtils
import com.example.livegps.ui.components.SectionCard
import com.example.livegps.ui.components.comingSoon

/** More tab — settings shortcuts, reliability help and app info. */
@Composable
fun MoreScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val deviceId = remember { (context.applicationContext as LiveGpsApp).repository.deviceId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MoreRow(
            icon = Icons.Filled.Settings,
            title = "App settings",
            subtitle = "Backend URL and tracking options",
            onClick = onOpenSettings,
        )
        MoreRow(
            icon = Icons.Filled.BatterySaver,
            title = "Battery optimization",
            subtitle = "Allow background usage for reliable tracking",
            onClick = {
                runCatching { context.startActivity(PermissionUtils.batteryOptimizationIntent(context)) }
            },
        )
        MoreRow(
            icon = Icons.Filled.Lock,
            title = "App permissions",
            subtitle = "Location, notifications and precise location",
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    },
                )
            },
        )
        MoreRow(
            icon = Icons.Filled.PowerSettingsNew,
            title = "OEM autostart help",
            subtitle = "Keep tracking alive on Xiaomi, Oppo, Vivo…",
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com")))
                }
            },
        )
        MoreRow(
            icon = Icons.Filled.Smartphone,
            title = "This device",
            subtitle = deviceId,
            onClick = {
                Toast.makeText(context, "Device ID: $deviceId", Toast.LENGTH_LONG).show()
            },
        )
        MoreRow(
            icon = Icons.Filled.Info,
            title = "About Live GPS",
            subtitle = "Version 1.0 · Live location tracking",
            onClick = { context.comingSoon() },
        )
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    SectionCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
