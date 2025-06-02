package com.proactive.sptassist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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

class WebSocketService : Service() {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Disable read timeout for WebSockets
        .build()
    private val gson = Gson()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- Constants and Device ID ---
    companion object {
        private const val TAG = "WebSocketService"
        private const val NOTIFICATION_CHANNEL_ID = "VMControlChannel"
        private const val NOTIFICATION_ID = 101

        // IMPORTANT: Generate a truly unique ID for each device.
        // This could be:
        // 1. A UUID generated once and stored in SharedPreferences.
        // 2. An ID provided by a user during an enrollment process.
        // DO NOT use Android ID (deprecated due to privacy concerns).
        // For this example, let's use a simple placeholder.
        // In a real app, generate and persist this securely.
        private var DEVICE_ID: String = ""
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize DEVICE_ID securely
        if (DEVICE_ID.isEmpty()) {
            val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            DEVICE_ID = sharedPrefs.getString("device_unique_id", null) ?: run {
                val newId = UUID.randomUUID().toString()
                sharedPrefs.edit().putString("device_unique_id", newId).apply()
                newId
            }
        }
        Log.d(TAG, "Service created. Device ID: $DEVICE_ID")
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        connectWebSocket()
        return START_STICKY // Service will be restarted by the system if killed (e.g., due to low memory)
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectWebSocket()
        serviceScope.cancel() // Cancel all coroutines started by this service
        Log.d(TAG, "WebSocketService onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // This is not a bound service; it runs independently
    }

    // --- Notification Channel for Foreground Service ---
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
            .setSmallIcon(R.mipmap.ic_launcher) // Use your app icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // --- WebSocket Connection Logic ---
    private fun connectWebSocket() {
        // Ensure only one connection attempt is active
        webSocket?.close(1000, "Reconnecting")
        webSocket = null

        // **IMPORTANT: Use wss:// for production with your VM's public IP/Domain and Port**
        // For testing, if you're using HTTP (not recommended for production): ws://your-vm-ip:port
        val wsUrl = "wss://34.41.54.255:4444/ws?deviceId=$DEVICE_ID"
        // Example: wss://192.168.1.100:8443/ws?deviceId=abc-123
        // Example: wss://myvmserver.com:8443/ws?deviceId=abc-123

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        Log.d(TAG, "Attempting to connect to WebSocket: $wsUrl")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened successfully!")
                // Send initial handshake or authentication token
                val handshakeMessage = mapOf(
                    "type" to "auth",
                    "deviceId" to DEVICE_ID,
                    "token" to "your_auth_token_here_for_vm" // Replace with a real token/secret
                )
                sendData(gson.toJson(handshakeMessage))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Receiving: $text")
                // Parse the command and execute
                serviceScope.launch {
                    handleIncomingCommand(text)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "Receiving binary data: ${bytes.hex()}")
                // Handle binary messages if needed (e.g., received images)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $code / $reason")
                webSocket.close(1000, null) // Close gracefully
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Error: ${t.message}", t)
                // Implement robust reconnection logic with a delay and exponential backoff
                serviceScope.launch {
                    Log.d(TAG, "Attempting to reconnect WebSocket in 5 seconds...")
                    delay(5000) // Wait 5 seconds before attempting to reconnect
                    connectWebSocket()
                }
            }
        })
    }

    private fun disconnectWebSocket() {
        webSocket?.close(1000, "App closing")
        webSocket = null
    }

    // --- Sending Data to VM ---
    fun sendData(data: String) {
        webSocket?.send(data)
        Log.d(TAG, "Sent: $data")
    }

    // Send a structured result back to the VM
    fun sendResult(commandId: String, result: Any) {
        val message = mapOf(
            "type" to "result",
            "commandId" to commandId,
            "deviceId" to DEVICE_ID,
            "data" to result
        )
        sendData(gson.toJson(message))
    }

    // --- Command Handling ---
    private suspend fun handleIncomingCommand(jsonCommand: String) {
        try {
            val command = gson.fromJson(jsonCommand, Map::class.java) as Map<String, String>
            val commandType = command["command"]
            val commandId = command["commandId"] ?: "unknown" // Unique ID for each command

            Log.d(TAG, "Executing command: $commandType with ID: $commandId")

            val result: Any = when (commandType) {
                "show_wifi" -> WifiManagerHelper(this).getWifiNetworks()
                "connect_wifi" -> {
                    val ssid = command["ssid"] ?: ""
                    val password = command["password"] ?: ""
                    WifiManagerHelper(this).connectToWifi(ssid, password)
                }
                "get_location" -> LocationHelper(this).getCurrentLocation()
                "send_sms" -> {
                    val number = command["number"] ?: ""
                    val message = command["message"] ?: ""
                    SmsHelper(this).sendSms(number, message)
                }
                "take_photo" -> {
                    // This is more complex. You might need to start an Activity for result,
                    // or use a CameraX implementation that can save to a file and then
                    // send the file's URI/Base64 representation.
                    // For now, it's a placeholder.
                    CameraHelper(this).takePhoto() // This will return a dummy result
                }
                "get_battery_status" -> BatteryHelper(this).getBatteryStatus()
                else -> mapOf("status" to "error", "message" to "Unknown command type: $commandType")
            }
            sendResult(commandId, result)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling command: ${e.message}", e)
            sendResult("unknown", mapOf("status" to "error", "message" to "Failed to process command: ${e.message}"))
        }
    }
}