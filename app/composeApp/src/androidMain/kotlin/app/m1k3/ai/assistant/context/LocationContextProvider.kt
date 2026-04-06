package app.m1k3.ai.assistant.context

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import app.m1k3.ai.domain.context.LocationContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Provides coarse location context using FusedLocationProvider.
 *
 * Uses COARSE accuracy only — city-level, not precise street address.
 * Returns null if permission not granted (no crash, no prompt — just null).
 */
class LocationContextProvider(private val context: Context) {

    companion object {
        private const val TAG = "LocationContextProvider"
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    suspend fun getLocation(): LocationContext? {
        if (!hasPermission()) {
            Log.d(TAG, "No location permission")
            return null
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()

            val location: android.location.Location = suspendCancellableCoroutine<android.location.Location?> { cont ->
                fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc -> cont.resume(loc) }
                    .addOnFailureListener { cont.resume(null) }

                cont.invokeOnCancellation { cts.cancel() }
            } ?: return null

            // Reverse geocode to city name
            val cityName = reverseGeocode(location.latitude, location.longitude)
            LocationContext(
                city = cityName?.first,
                country = cityName?.second,
                lat = location.latitude,
                lon = location.longitude
            )
        } catch (e: Exception) {
            Log.w(TAG, "Location fetch failed: ${e.message}")
            null
        }
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): Pair<String, String>? {
        if (!Geocoder.isPresent()) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<Pair<String, String>?> { cont ->
                    Geocoder(context).getFromLocation(lat, lon, 1) { addresses ->
                        val address = addresses.firstOrNull()
                        cont.resume(
                            if (address != null)
                                Pair(address.locality ?: address.subAdminArea ?: "", address.countryName ?: "")
                            else null
                        )
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = Geocoder(context).getFromLocation(lat, lon, 1)
                val address = addresses?.firstOrNull() ?: return null
                Pair(address.locality ?: address.subAdminArea ?: "", address.countryName ?: "")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoding failed: ${e.message}")
            null
        }
    }
}
