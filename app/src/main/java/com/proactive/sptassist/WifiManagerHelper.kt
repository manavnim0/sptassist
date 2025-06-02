package com.proactive.sptassist

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration // For API < 29
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier // For API >= 29 (for direct connection)
import android.net.wifi.WifiNetworkSuggestion // For API >= 29 (for suggestion)
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.net.Network
import android.net.NetworkRequest.Builder
import android.content.Intent

class WifiManagerHelper(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val TAG = "WifiManagerHelper"

    fun getWifiNetworks(): Map<String, Any> {
        if (!wifiManager.isWifiEnabled) {
            return mapOf("status" to "error", "message" to "Wi-Fi is not enabled.")
        }
        try {
            // Ensure ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION is granted for scanResults
            val scanResults = wifiManager.scanResults
            val networks = scanResults.map {
                mapOf(
                    "SSID" to it.SSID,
                    "BSSID" to it.BSSID,
                    "capabilities" to it.capabilities, // e.g., "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"
                    "level" to it.level // RSSI in dBm
                )
            }
            return mapOf("status" to "success", "networks" to networks)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Location permission required for Wi-Fi scanning.", e)
            return mapOf("status" to "error", "message" to "Location permission required for Wi-Fi scanning. Please grant it in app settings.")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Wi-Fi networks", e)
            return mapOf("status" to "error", "message" to "Failed to get Wi-Fi networks: ${e.message}")
        }
    }

    suspend fun connectToWifi(ssid: String, password: String): Map<String, Any> {
        if (!wifiManager.isWifiEnabled) {
            return mapOf("status" to "error", "message" to "Wi-Fi is not enabled.")
        }
        if (ssid.isBlank()) {
            return mapOf("status" to "error", "message" to "SSID cannot be empty.")
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29+
            connectWithNetworkSpecifier(ssid, password)
        } else { // API 28 and below
            connectWithWifiConfiguration(ssid, password)
        }
    }

    // For API 29+ (Android 10 and above)
    // This method attempts a direct connection. User might still get a system dialog.
    private suspend fun connectWithNetworkSpecifier(ssid: String, password: String): Map<String, Any> {
        return suspendCancellableCoroutine { continuation ->
            val networkSpecifierBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)

            if (password.isNotBlank()) {
                // Determine security type
                if (password.length >= 8) { // WPA/WPA2-PSK
                    networkSpecifierBuilder.setWpa2Passphrase(password)
                    Log.d(TAG, "Attempting to connect to WPA2 network: $ssid")
                } else { // WEP
                    networkSpecifierBuilder.setWepKey(password)
                    Log.d(TAG, "Attempting to connect to WEP network: $ssid")
                }
            } else {
                networkSpecifierBuilder.setIsHiddenSubset(false) // For open networks
                Log.d(TAG, "Attempting to connect to open network: $ssid")
            }

            val networkSpecifier = networkSpecifierBuilder.build()

            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(networkSpecifier)
                .build()

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d(TAG, "Network available: $network")
                    connectivityManager.bindProcessToNetwork(network) // Optional: Bind process to this network
                    if (continuation.isActive) {
                        continuation.resume(mapOf("status" to "success", "message" to "Connected to $ssid via NetworkSpecifier."))
                    }
                    connectivityManager.unregisterNetworkCallback(this) // Unregister once connected
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.w(TAG, "Network lost for $ssid")
                    if (continuation.isActive) {
                        continuation.resume(mapOf("status" to "error", "message" to "Lost connection to $ssid during setup."))
                    }
                    connectivityManager.unregisterNetworkCallback(this)
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    Log.w(TAG, "Network unavailable for $ssid (timeout or user rejection)")
                    if (continuation.isActive) {
                        continuation.resume(mapOf("status" to "error", "message" to "Failed to connect to $ssid: Unavailable or rejected by user. Timeout or network not found."))
                    }
                    connectivityManager.unregisterNetworkCallback(this)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        Log.d(TAG, "$ssid network validated (internet access).")
                    }
                }
            }

            try {
                connectivityManager.requestNetwork(networkRequest, networkCallback, 30, TimeUnit.SECONDS) // Request with a timeout
                Log.d(TAG, "Requesting connection to $ssid via NetworkSpecifier...")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: Permission denied for NetworkSpecifier.", e)
                if (continuation.isActive) {
                    continuation.resume(mapOf("status" to "error", "message" to "Permission denied for Wi-Fi connection. Ensure necessary permissions are granted and location is enabled."))
                }
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting network for $ssid", e)
                if (continuation.isActive) {
                    continuation.resume(mapOf("status" to "error", "message" to "Failed to initiate connection to $ssid: ${e.message}"))
                }
                connectivityManager.unregisterNetworkCallback(networkCallback)
            }

            continuation.invokeOnCancellation {
                connectivityManager.unregisterNetworkCallback(networkCallback)
                Log.d(TAG, "Wi-Fi connection request cancelled for $ssid.")
            }
        }
    }


    // For API 28 and below (older method)
    private fun connectWithWifiConfiguration(ssid: String, password: String): Map<String, Any> {
        val wifiConfig = WifiConfiguration()
        wifiConfig.SSID = "\"$ssid\"" // SSID must be quoted

        // Determine security type and set credentials
        if (password.isBlank()) {
            // Open network
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            Log.d(TAG, "Attempting to connect to open network (Legacy): $ssid")
        } else if (password.length >= 8) { // Assuming WPA/WPA2-PSK
            wifiConfig.preSharedKey = "\"$password\"" // WPA/WPA2
            Log.d(TAG, "Attempting to connect to WPA/WPA2 network (Legacy): $ssid")
        } else { // Assuming WEP (less secure, but possible)
            wifiConfig.wepKeys[0] = "\"$password\""
            wifiConfig.wepTxKeyIndex = 0
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
            wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
            Log.d(TAG, "Attempting to connect to WEP network (Legacy): $ssid")
        }

        try {
            // Add or update the network.
            // On older APIs, this often adds a new network even if one exists.
            val networkId = wifiManager.addNetwork(wifiConfig)
            if (networkId == -1) {
                return mapOf("status" to "error", "message" to "Failed to add Wi-Fi network configuration (Legacy). Check SSID/Password format.")
            }

            // Disconnect from current network and enable the new one
            // Note: These calls also require CHANGE_WIFI_STATE and are restricted on newer APIs.
            // But they might work on older ones if permissions are granted.
            val disconnectResult = wifiManager.disconnect()
            if (!disconnectResult) {
                Log.w(TAG, "Failed to disconnect from current Wi-Fi (Legacy). Proceeding.")
            }

            val enableResult = wifiManager.enableNetwork(networkId, true) // Disable others, enable this
            val reconnectResult = wifiManager.reconnect()

            if (enableResult && reconnectResult) {
                return mapOf("status" to "success", "message" to "Attempting to connect to $ssid (Legacy). Please check device Wi-Fi status.")
            } else {
                return mapOf("status" to "error", "message" to "Failed to enable or reconnect to $ssid (Legacy). Check network and password, and ensure 'CHANGE_WIFI_STATE' permission is granted.")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Wi-Fi connection (Legacy) requires CHANGE_WIFI_STATE permission.", e)
            return mapOf("status" to "error", "message" to "Wi-Fi connection requires 'CHANGE_WIFI_STATE' permission. Please grant it.")
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to Wi-Fi (Legacy)", e)
            return mapOf("status" to "error", "message" to "Failed to connect to Wi-Fi (Legacy): ${e.message}")
        }
    }

    // Optional: Function to guide user to Wi-Fi settings if direct connection fails
    fun openWifiSettings() {
        val intent = Intent(Settings.Panel.ACTION_WIFI) // For Android 10+
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Fallback for older Android versions
            val oldIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
            if (oldIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(oldIntent)
            } else {
                Log.e(TAG, "No Wi-Fi settings activity found.")
            }
        }
    }
}