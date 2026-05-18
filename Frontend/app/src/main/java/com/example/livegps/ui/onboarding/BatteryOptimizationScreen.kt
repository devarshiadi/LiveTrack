package com.example.livegps.ui.onboarding

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * Guides the user to exempt the app from battery optimization and points to
 * dontkillmyapp.com for OEM-specific autostart settings. Honest about the one
 * thing no app can survive: an explicit "Force Stop".
 */
@Composable
fun BatteryOptimizationScreen(onContinue: () -> Unit) {
    val context = LocalContext.current

    var exempt by remember {
        mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context))
    }
    LifecycleResumeEffect(Unit) {
        exempt = PermissionUtils.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Keep tracking reliable",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Android and phone makers may pause background apps to save " +
                "battery. These steps keep location tracking running.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Battery optimization exemption.
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Allow background battery usage",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (exempt) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Done",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "Removes this app from battery optimization so the OS does " +
                        "not pause tracking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(PermissionUtils.batteryOptimizationIntent(context))
                        }
                    },
                    enabled = !exempt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (exempt) "Already allowed" else "Disable battery optimization")
                }
            }
        }

        // OEM autostart help.
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Phone-specific settings",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "Xiaomi, Oppo, Vivo, Huawei and others need an extra " +
                        "\"Autostart\" toggle. dontkillmyapp.com has the steps for your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com")),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open dontkillmyapp.com")
                }
            }
        }

        // Honest limitation.
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Row(modifier = Modifier.padding(16.dp)) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "Note: if you \"Force stop\" the app from Android Settings, " +
                        "no app can restart itself — that is an OS rule. Reopen the app " +
                        "to resume tracking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("Continue to map")
        }
        TextButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Skip for now")
        }
    }
}
