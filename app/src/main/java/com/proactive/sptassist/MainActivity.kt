package com.proactive.sptassist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button // Import Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var statusTextView: TextView
    private lateinit var toggleConnectionButton: Button // Declare the button

    private var isServiceRunning = false // Track the state of the WebSocketService

    private val REQUIRED_PERMISSIONS = mutableListOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.CAMERA,
        Manifest.permission.FOREGROUND_SERVICE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        toggleConnectionButton = findViewById(R.id.toggleConnectionButton) // Initialize the button

        // Set initial state of the button and check permissions
        updateUiForServiceState()
        checkAndRequestPermissions()

        toggleConnectionButton.setOnClickListener {
            if (isServiceRunning) {
                stopWebSocketService()
            } else {
                startWebSocketService()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            statusTextView.text = "Requesting permissions..."
            toggleConnectionButton.isEnabled = false // Disable button while requesting
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        } else {
            Log.d(TAG, "All required permissions already granted.")
            statusTextView.text = "All permissions granted. Ready to connect."
            toggleConnectionButton.isEnabled = true // Enable button if permissions are already granted
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            var allGranted = true
            val deniedPermissions = mutableListOf<String>()

            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    deniedPermissions.add(permissions[i])
                }
            }

            if (allGranted) {
                Log.d(TAG, "All requested permissions granted by user.")
                statusTextView.text = "All permissions granted. Ready to connect."
                toggleConnectionButton.isEnabled = true // Enable button
            } else {
                val message = "Permissions not granted: ${deniedPermissions.joinToString()}. Cannot establish WebSocket connection."
                Log.w(TAG, message)
                statusTextView.text = message
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                toggleConnectionButton.isEnabled = false // Keep button disabled if permissions are denied
            }
        }
    }

    private fun startWebSocketService() {
        val serviceIntent = Intent(this, WebSocketService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        isServiceRunning = true
        updateUiForServiceState()
        statusTextView.append("\nWebSocket Service starting...")
    }

    private fun stopWebSocketService() {
        val serviceIntent = Intent(this, WebSocketService::class.java)
        stopService(serviceIntent)
        isServiceRunning = false
        updateUiForServiceState()
        statusTextView.append("\nWebSocket Service stopping...")
    }

    private fun updateUiForServiceState() {
        if (isServiceRunning) {
            toggleConnectionButton.text = "DISCONNECT"
        } else {
            toggleConnectionButton.text = "CONNECT"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity onDestroy")
        // Consider if you want to stop the service when the activity is destroyed.
        // For a persistent WebSocket, you might not want to stop it here.
        // If you do want to stop it:
        // if (isServiceRunning) {
        //     stopWebSocketService()
        // }
    }
}