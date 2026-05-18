package com.example.livegps.location

import android.content.Context
import android.location.LocationManager
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

/** Result of checking whether device location settings allow tracking. */
sealed interface LocationSettingsOutcome {
    /** Location services are on and configured — tracking can start. */
    data object Satisfied : LocationSettingsOutcome

    /** Location is off but can be enabled via a system dialog. */
    data class Resolvable(val request: IntentSenderRequest) : LocationSettingsOutcome

    /** Location settings cannot be satisfied automatically. */
    data object Unavailable : LocationSettingsOutcome
}

/** Wraps the Play Services SettingsClient — detects GPS-off and offers to fix it. */
object LocationSettingsHelper {

    /**
     * Checks whether location settings satisfy a tracking request. When they
     * don't, returns a [LocationSettingsOutcome.Resolvable] carrying the
     * system "turn on location" dialog for the caller to launch.
     */
    suspend fun check(context: Context, highAccuracy: Boolean): LocationSettingsOutcome {
        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = LocationRequest.Builder(priority, 10_000L).build()
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(request)
            .build()

        return try {
            LocationServices.getSettingsClient(context)
                .checkLocationSettings(settingsRequest)
                .await()
            LocationSettingsOutcome.Satisfied
        } catch (e: ResolvableApiException) {
            LocationSettingsOutcome.Resolvable(
                IntentSenderRequest.Builder(e.resolution).build(),
            )
        } catch (e: Exception) {
            LocationSettingsOutcome.Unavailable
        }
    }

    /** True if the OS location master switch is on. */
    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return lm.isLocationEnabled
    }
}
