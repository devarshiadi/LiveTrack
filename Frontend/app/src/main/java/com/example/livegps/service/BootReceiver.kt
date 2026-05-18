package com.example.livegps.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.livegps.data.local.SettingsStore
import kotlinx.coroutines.runBlocking

/** Restarts location tracking after the device reboots — if it was enabled. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> Unit
            else -> return
        }
        val enabled = runBlocking { SettingsStore(context).trackingEnabledOnce() }
        if (enabled) {
            runCatching { ServiceController.start(context) }
        }
    }
}
