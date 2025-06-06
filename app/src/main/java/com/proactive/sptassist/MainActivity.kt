package com.proactive.sptassist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView // Import TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var statusTextView: TextView // Declare TextView
    private lateinit var toggleConnectionButton: Button // Declare Button

    // Define all permissions your app needs that require runtime requests.
    private val REQUIRED_PERMISSIONS = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE
    ).apply {
        // Add POST_NOTIFICATIONS permission for Android 13 (API 33) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()


    // ActivityResultLauncher for handling multiple permission requests
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            if (allPermissionsGranted) {
                Log.d(TAG, "All required permissions granted.")
                updateStatus("Permissions Granted. Starting service...")
                startWebSocketService()
            } else {
                val deniedPermissions = permissions.entries.filter { !it.value }.map { it.key }
                Log.w(TAG, "Permissions not granted: ${deniedPermissions.joinToString()}")
                updateStatus("Permissions Denied. Service cannot start.")
                Toast.makeText(this, "Some permissions were denied. App features might be limited.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements using their IDs from activity_main.xml
        statusTextView = findViewById(R.id.statusTextView)
        toggleConnectionButton = findViewById(R.id.toggleConnectionButton)

        // Set initial status and button text
        updateStatus("App Status: Ready to connect.")
        toggleConnectionButton.text = "CONNECT"

        // Set OnClickListener for the button
        toggleConnectionButton.setOnClickListener {
            // For simplicity, this example just tries to connect.
            // In a real app, you'd likely toggle between connect/disconnect state.
            checkAndRequestPermissions()
        }
    }

    /**
     * Checks if all required permissions are granted. If not, requests them.
     */
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            Log.d(TAG, "All required permissions already granted.")
            updateStatus("Permissions already granted. Starting service...")
            startWebSocketService()
        }
    }

    /**
     * Starts the WebSocketService if all necessary permissions are granted.
     * This ensures the service doesn't crash immediately due to missing permissions.
     */
    private fun startWebSocketService() {
        // Verify that ALL of the required permissions are indeed granted before starting the service
        val areAllPermissionsGranted = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (areAllPermissionsGranted) {
            val serviceIntent = Intent(this, WebSocketService::class.java)
            // Using ContextCompat.startForegroundService is good practice for API 26+
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.d(TAG, "WebSocketService started successfully.")
            updateStatus("Service Started. Connecting to VM...")
            toggleConnectionButton.text = "DISCONNECT" // Update button text
            Toast.makeText(this, "Service Started!", Toast.LENGTH_SHORT).show()
        } else {
            Log.w(TAG, "Cannot start WebSocketService: Not all required runtime permissions were granted.")
            updateStatus("Service Not Started: Permissions missing.")
            Toast.makeText(this, "Cannot start service: Please grant all permissions.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Updates the text of the statusTextView.
     */
    private fun updateStatus(status: String) {
        statusTextView.text = "App Status: $status"
    }

    // You might also want a way to stop the service, e.g., on a second button click
    // or when the app is destroyed if the service isn't meant to run indefinitely.
    // For now, I'm keeping it simple, but you could add a stop button logic:
    // private fun stopWebSocketService() {
    //     val serviceIntent = Intent(this, WebSocketService::class.java)
    //     stopService(serviceIntent)
    //     updateStatus("Service Stopped.")
    //     toggleConnectionButton.text = "CONNECT"
    //     Toast.makeText(this, "Service Stopped!", Toast.LENGTH_SHORT).show()
    // }
}