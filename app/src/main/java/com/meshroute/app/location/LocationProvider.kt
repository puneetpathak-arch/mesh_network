package com.meshroute.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.meshroute.app.mesh.transport.LocationData
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

interface LocationProvider {
    suspend fun getCurrentLocation(timeoutMs: Long = 5000L): LocationData?
    fun getLastKnownLocation(): LocationData?
}

/**
 * Captures real GPS/network location using Android's LocationManager.
 * Gracefully handles missing permissions or disabled location providers.
 */
class AndroidGpsLocationProvider(
    private val context: Context
) : LocationProvider {

    companion object {
        private const val TAG = "GpsLocationProvider"
    }

    private val locationManager: LocationManager? by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    override fun getLastKnownLocation(): LocationData? {
        val lm = locationManager ?: return null
        return try {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            var bestLocation: Location? = null
            for (provider in providers) {
                if (lm.isProviderEnabled(provider)) {
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLocation == null || loc.accuracy < bestLocation.accuracy || loc.time > bestLocation.time) {
                            bestLocation = loc
                        }
                    }
                }
            }

            bestLocation?.let {
                LocationData(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracy = it.accuracy
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing when getting last known location: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error getting last known location: ${e.message}")
            null
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(timeoutMs: Long): LocationData? {
        // First try fast last known location if recent enough
        val lastKnown = getLastKnownLocation()
        if (lastKnown != null) {
            return lastKnown
        }

        val lm = locationManager ?: return null

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try {
                            lm.removeUpdates(this)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error removing location updates: ${e.message}")
                        }
                        if (continuation.isActive) {
                            continuation.resume(
                                LocationData(
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    accuracy = location.accuracy
                                )
                            )
                        }
                    }

                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }

                try {
                    val provider = when {
                        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                        else -> LocationManager.PASSIVE_PROVIDER
                    }

                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())

                    continuation.invokeOnCancellation {
                        try {
                            lm.removeUpdates(listener)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error removing location updates on cancel: ${e.message}")
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException requesting location update: ${e.message}")
                    continuation.resume(null)
                } catch (e: Exception) {
                    Log.w(TAG, "Exception requesting location update: ${e.message}")
                    continuation.resume(null)
                }
            }
        } ?: getLastKnownLocation()
    }
}
