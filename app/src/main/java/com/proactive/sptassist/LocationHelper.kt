package com.proactive.sptassist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
//
//class LocationHelper(private val context: Context) {
//
//    private val fusedLocationClient: FusedLocationProviderClient =
//        LocationServices.getFusedLocationProviderClient(context)
//    private val TAG = "LocationHelper"
//
//    suspend fun getCurrentLocation(): Map<String, Any> {
//        if (!checkPermissions()) {
//            return mapOf("status" to "error", "message" to "Location permissions (ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION) not granted.")
//        }
//
//        try {
//            // Try to get the last known location first
//            val lastLocation = fusedLocationClient.lastLocation.await() // This will now work with play-services-tasks-ktx
//            if (lastLocation != null) {
//                Log.d(TAG, "Got last known location: Lat=${lastLocation.latitude}, Lon=${lastLocation.longitude}")
//                return mapOf(
//                    "status" to "success",
//                    "latitude" to lastLocation.latitude,
//                    "longitude" to lastLocation.longitude,
//                    "accuracy" to lastLocation.accuracy,
//                    "source" to "last_known"
//                )
//            } else {
//                // If last location is null, request a fresh one
//                Log.d(TAG, "Last known location is null, requesting fresh location...")
//                val freshLocation = getFreshLocation()
//                if (freshLocation != null) {
//                    Log.d(TAG, "Got fresh location: Lat=${freshLocation.latitude}, Lon=${freshLocation.longitude}")
//                    return mapOf(
//                        "status" to "success",
//                        "latitude" to freshLocation.latitude,
//                        "longitude" to freshLocation.longitude,
//                        "accuracy" to freshLocation.accuracy,
//                        "source" to "fresh_update"
//                    )
//                } else {
//                    Log.e(TAG, "Could not retrieve current location after fresh request.")
//                    return mapOf("status" to "error", "message" to "Could not retrieve current location.")
//                }
//            }
//        } catch (e: SecurityException) {
//            Log.e(TAG, "SecurityException: Location permission denied or missing.", e)
//            return mapOf("status" to "error", "message" to "Location permission denied or missing. Please enable GPS and grant permissions.")
//        } catch (e: Exception) {
//            Log.e(TAG, "Error getting location", e)
//            return mapOf("status" to "error", "message" to "Failed to get location: ${e.message}")
//        }
//    }
//
//    private suspend fun getFreshLocation(): android.location.Location? = suspendCancellableCoroutine { continuation ->
//        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // Request high accuracy, 5 sec interval
//            .setMinUpdateIntervalMillis(1000) // Minimum 1 sec interval
//            .setWaitForAccurateLocation(false)
//            .setMaxUpdates(1) // Get only one update and stop
//            .build()
//
//        val locationCallback = object : LocationCallback() {
//            override fun onLocationResult(locationResult: LocationResult) {
//                super.onLocationResult(locationResult)
//                locationResult.lastLocation?.let { location ->
//                    if (continuation.isActive) {
//                        continuation.resume(location)
//                        fusedLocationClient.removeLocationUpdates(this) // Stop updates after receiving one
//                    }
//                }
//            }
//
//            override fun onLocationAvailability(p0: LocationAvailability) {
//                super.onLocationAvailability(p0)
//                if (!p0.isLocationAvailable && continuation.isActive) {
//                    Log.w(TAG, "Location not available.")
//                    // If you want to fail immediately if location is not available
//                    // continuation.resume(null) // Or continuation.resumeWithException(new LocationUnavailableException())
//                }
//            }
//        }
//
//        try { // ADDED TRY-CATCH BLOCK HERE
//            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
//                .addOnFailureListener { e ->
//                    Log.e(TAG, "Error requesting location updates", e)
//                    if (continuation.isActive) {
//                        continuation.resumeWithException(e)
//                    }
//                }
//        } catch (e: SecurityException) {
//            Log.e(TAG, "SecurityException when requesting location updates: ${e.message}", e)
//            if (continuation.isActive) {
//                continuation.resumeWithException(e) // Resume with the exception to propagate it
//            }
//        }
//
//
//        continuation.invokeOnCancellation {
//            fusedLocationClient.removeLocationUpdates(locationCallback)
//            Log.d(TAG, "Location request cancelled.")
//        }
//    }
//
//    private fun checkPermissions(): Boolean {
//        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
//                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
//    }
//}