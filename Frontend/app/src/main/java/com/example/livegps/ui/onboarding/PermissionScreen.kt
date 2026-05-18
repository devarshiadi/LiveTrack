package com.example.livegps.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.livegps.permissions.PermissionUtils

/**
 * Two-step location-permission flow:
 *  1. Foreground location + the notification permission.
 *  2. Background location ("Allow all the time") — requested separately, as
 *     Android 11+ requires.
 */
@Composable
fun PermissionScreen(onContinue: () -> Unit) {
    val context = LocalContext.current

    var hasForeground by remember { mutableStateOf(PermissionUtils.hasForegroundLocation(context)) }
    var hasBackground by remember { mutableStateOf(PermissionUtils.hasBackgroundLocation(context)) }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasForeground = PermissionUtils.hasForegroundLocation(context)
        hasBackground = PermissionUtils.hasBackgroundLocation(context)
    }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        hasBackground = PermissionUtils.hasBackgroundLocation(context)
    }

    // Re-check when returning from the system settings screen.
    LifecycleResumeEffect(Unit) {
        hasForeground = PermissionUtils.hasForegroundLocation(context)
        hasBackground = PermissionUtils.hasBackgroundLocation(context)
        onPauseOrDispose { }
    }

    val needsBackgroundStep = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val canContinue = hasForeground && (!needsBackgroundStep || hasBackground)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Location permissions",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Tracking needs location access. Background access keeps it " +
                "working when the app is not on screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionCard(
            title = "Step 1 · Location & notifications",
            description = "Allow location access and the ongoing tracking notification.",
            granted = hasForeground,
            enabled = true,
            actionLabel = "Grant access",
            onAction = { foregroundLauncher.launch(PermissionUtils.foregroundPermissions) },
        )

        if (needsBackgroundStep) {
            PermissionCard(
                title = "Step 2 · Allow all the time",
                description = "On the next screen choose \"Allow all the time\" so tracking " +
                    "continues in the background.",
                granted = hasBackground,
                enabled = hasForeground,
                actionLabel = "Allow all the time",
                onAction = {
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                },
            )
        }

        Button(
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    enabled: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (granted) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.size(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            OutlinedButton(
                onClick = onAction,
                enabled = enabled && !granted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (granted) "Granted" else actionLabel)
            }
        }
    }
}
