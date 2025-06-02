package com.proactive.sptassist

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

class BatteryHelper(private val context: Context) {

    private val TAG = "BatteryHelper"

    fun getBatteryStatus(): Map<String, Any> {
        try {
            // Register a null receiver to get the sticky broadcast of battery status
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                context.registerReceiver(null, ifilter)
            }

            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct: Float = if (scale == 0) 0f else level / scale.toFloat() * 100

            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val usbCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
            val acCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_AC
            val wirelessCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS

            val chargingSource = when {
                usbCharge -> "USB"
                acCharge -> "AC"
                wirelessCharge -> "Wireless"
                else -> "None"
            }

            val batteryHealth = getBatteryHealth(batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN)

            return mapOf(
                "status" to "success",
                "percentage" to batteryPct.toInt(),
                "isCharging" to isCharging,
                "chargingSource" to chargingSource,
                "health" to batteryHealth,
                "temperature_celsius" to (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1) / 10.0 // temperature in tenths of a degree Celsius
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery status", e)
            return mapOf("status" to "error", "message" to "Failed to get battery status: ${e.message}")
        }
    }

    private fun getBatteryHealth(health: Int): String {
        return when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
    }
}