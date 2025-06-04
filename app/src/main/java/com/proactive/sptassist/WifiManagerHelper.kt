package com.proactive.sptassist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat // Make sure this is imported

class WifiManagerHelper(private val context: Context) {

    private val TAG = "WifiManagerHelper"
    private val wifiManager: WifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // Helper to check if a specific permission is granted
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getWifiNetworks(): Map<String, Any> {
        // Explicitly check Wi-Fi scan permissions here
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return mapOf("status" to "error", "message" to "Location permissions (ACCESS_FINE_LOCATION or COARSE) are required for Wi-Fi scanning.")
        }
        if (!wifiManager.isWifiEnabled) {
            return mapOf("status" to "error", "message" to "Wi-Fi is not enabled.")
        }

        try {
            val scanResults = wifiManager.scanResults
            val networks = scanResults.map {
                mapOf(
                    "SSID" to it.SSID,
                    "BSSID" to it.BSSID,
                    "Capabilities" to it.capabilities, // e.g., "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"
                    "Level" to it.level // RSSI in dBm
                )
            }
            Log.d(TAG, "Scanned networks: $networks")
            return mapOf("status" to "success", "networks" to networks)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting Wi-Fi networks: ${e.message}", e)
            return mapOf("status" to "error", "message" to "Permission denied for Wi-Fi scanning: ${e.message}. Ensure location permissions are granted.")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Wi-Fi networks: ${e.message}", e)
            return mapOf("status" to "error", "message" to "Failed to scan Wi-Fi networks: ${e.message}")
        }
    }

    fun connectToWifi(ssid: String, password: String): Map<String, Any> {
        // Explicitly check CHANGE_WIFI_STATE permission here
        if (!hasPermission(Manifest.permission.CHANGE_WIFI_STATE)) {
            return mapOf("status" to "error", "message" to "CHANGE_WIFI_STATE permission is required to connect to Wi-Fi.")
        }

        // Ensure Wi-Fi is enabled
        if (!wifiManager.isWifiEnabled) {
            return mapOf("status" to "error", "message" to "Wi-Fi is not enabled.")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // This condition covers API 29+
            try {
                val suggestionBuilder = WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)

                if (password.isEmpty()) {
                    // For open networks, do not set a passphrase
                } else {
                    // For WPA/WPA2/WPA3 networks
                    suggestionBuilder.setWpa2Passphrase(password)
                    // You can add WPA3 support if needed:
                    // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    //    suggestionBuilder.setWpa3Passphrase(password)
                    // }
                }

                // >>>>> COMMENTED OUT TO AVOID 'UNRESOLVED REFERENCE' ERROR <<<<<
                // This feature requires API 30 (Android 11) and seems to be causing
                // persistent issues in your build environment.
                // You can uncomment this block if/when the underlying build issue is resolved.
                // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // R is API 30
                //     suggestionBuilder.setIsAppOwned(true)
                // }

                val suggestion = suggestionBuilder.build()
                val suggestionsList = listOf(suggestion)

                val status = wifiManager.addNetworkSuggestions(suggestionsList)

                return when (status) {
                    WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> {
                        mapOf("status" to "success", "message" to "Network suggestion added. User interaction required to connect.")
                    }
                    WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE -> {
                        mapOf("status" to "info", "message" to "Network suggestion already exists. User might need to connect manually.")
                    }
                    else -> {
                        mapOf("status" to "error", "message" to "Failed to add network suggestion: Status $status")
                    }
                }
            } catch (e: SecurityException) { // Catch SecurityException specifically for permission issues
                Log.e(TAG, "SecurityException connecting to Wi-Fi on API 29+: ${e.message}", e)
                return mapOf("status" to "error", "message" to "Permission denied for network suggestion on API 29+: ${e.message}. Ensure CHANGE_WIFI_STATE is granted.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding network suggestion on API 29+: ${e.message}", e)
                return mapOf("status" to "error", "message" to "Failed to add network suggestion: ${e.message}")
            }
        }
        // --- Older Android versions (API < 29): Use WifiConfiguration (Limited effectiveness) ---
        else {
            @Suppress("DEPRECATION") // Suppress deprecation warning for WifiConfiguration
            try {
                val wifiConfig = WifiConfiguration()
                wifiConfig.SSID = "\"$ssid\"" // SSID must be quoted

                if (password.isEmpty()) {
                    // Open network
                    wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                } else {
                    // For WPA/WPA2/WPA3-Personal
                    wifiConfig.preSharedKey = "\"$password\""
                    wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN) // For WPA2
                    wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA) // For WPA
                    wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                    wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                    wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                    wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                }

                val networkId = wifiManager.addNetwork(wifiConfig)
                if (networkId == -1) {
                    return mapOf("status" to "error", "message" to "Failed to add network configuration.")
                }

                val disconnectStatus = wifiManager.disconnect()
                if (!disconnectStatus) {
                    Log.w(TAG, "Failed to disconnect from current network before connecting to new one.")
                }

                val enableStatus = wifiManager.enableNetwork(networkId, true)
                val reconnectStatus = wifiManager.reconnect()

                if (enableStatus && reconnectStatus) {
                    return mapOf("status" to "success", "message" to "Attempting to connect to $ssid (older API).")
                } else {
                    return mapOf("status" to "error", "message" to "Failed to enable or reconnect to network (older API).")
                }
            } catch (e: SecurityException) { // Catch SecurityException specifically for permission issues
                Log.e(TAG, "SecurityException connecting to Wi-Fi on older API: ${e.message}", e)
                return mapOf("status" to "error", "message" to "Permission denied for Wi-Fi connection on older API: ${e.message}. Ensure CHANGE_WIFI_STATE is granted.")
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to Wi-Fi on older API: ${e.message}", e)
                return mapOf("status" to "error", "message" to "Failed to connect to Wi-Fi (older API): ${e.message}")
            }
        }
    }
}