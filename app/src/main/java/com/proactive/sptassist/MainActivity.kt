package com.proactive.sptassist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var statusTextView: TextView // For simple status display

    // Define all permissions your app might need.
    private val REQUIRED_PERMISSIONS = mutableListOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION, // Implies ACCESS_COARSE_LOCATION
        Manifest.permission.SEND_SMS,
        Manifest.permission.CAMERA,
        Manifest.permission.FOREGROUND_SERVICE // Not a runtime permission, but good to list
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) { // Android 10 and below
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView) // Assuming you add this in activity_main.xml

        // Check and request permissions when the activity starts
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            statusTextView.text = "Requesting permissions..."
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        } else {
            Log.d(TAG, "All required permissions already granted.")
            statusTextView.text = "All permissions granted. Starting service..."
            startWebSocketService()
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
                statusTextView.text = "All permissions granted. Starting service..."
                startWebSocketService()
            } else {
                val message = "Permissions not granted: ${deniedPermissions.joinToString()}. Some functionalities may not work."
                Log.w(TAG, message)
                statusTextView.text = message
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                // Consider directing user to app settings if critical permissions are denied
                // Example: if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) { ... }
            }
        }
    }

    private fun startWebSocketService() {
        val serviceIntent = Intent(this, WebSocketService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For Android Oreo (API 26) and above, use startForegroundService()
            // The service must call startForeground() within 5 seconds.
            startForegroundService(serviceIntent)
        } else {
            // For older Android versions, use startService()
            startService(serviceIntent)
        }
        statusTextView.append("\nWebSocket Service started.")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Optional: If you want to stop the service when activity is destroyed (e.g., app closed completely)
        // However, for persistent connection, you usually let it run.
        // val serviceIntent = Intent(this, WebSocketService::class.java)
        // stopService(serviceIntent)
        Log.d(TAG, "MainActivity onDestroy")
    }
}