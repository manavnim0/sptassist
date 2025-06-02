package com.proactive.sptassist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

class SmsHelper(private val context: Context) {

    private val TAG = "SmsHelper"

    fun sendSms(number: String, message: String): Map<String, Any> {
        if (!checkPermission()) {
            return mapOf("status" to "error", "message" to "SEND_SMS permission not granted.")
        }
        if (number.isBlank() || message.isBlank()) {
            return mapOf("status" to "error", "message" to "Number and message cannot be empty.")
        }

        try {
            val smsManager = SmsManager.getDefault()
            // You can divide long messages into parts for sendMultipartTextMessage if needed
            smsManager.sendTextMessage(number, null, message, null, null)
            Log.d(TAG, "SMS sent to $number: '$message'")
            return mapOf("status" to "success", "message" to "SMS sent to $number.")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
            return mapOf("status" to "error", "message" to "Failed to send SMS: ${e.message}")
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }
}