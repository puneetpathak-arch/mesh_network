package com.meshroute.app.mesh.ble

import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PowerMode {
    FULL_PERFORMANCE, // Battery > 50%: SCAN_MODE_LOW_LATENCY, high duty cycle
    BALANCED,         // Battery 20%-50%: SCAN_MODE_BALANCED
    POWER_SAVER,      // Battery < 20%: SCAN_MODE_LOW_POWER
    EMERGENCY_OVERRIDE // Triggered during active SOS broadcasting: force maximum scan mode temporarily
}

data class PowerProfile(
    val scanMode: Int,
    val advertiseMode: Int,
    val advertiseTxPower: Int
)

/**
 * Adaptive Battery-Aware Duty Cycling Manager:
 * Monitors device battery level and adjusts BLE scan/advertise parameters to preserve
 * battery life while ensuring off-grid SOS messages override restrictions.
 */
class BlePowerManager(private val context: Context) {

    companion object {
        private const val TAG = "BlePowerManager"
    }

    private val _powerMode = MutableStateFlow(PowerMode.FULL_PERFORMANCE)
    val powerMode: StateFlow<PowerMode> = _powerMode.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private var isReceiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val pct = (level * 100) / scale
                    _batteryLevel.value = pct
                    evaluatePowerMode(pct)
                }
            }
        }
    }

    fun start() {
        if (isReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = context.registerReceiver(batteryReceiver, filter)
        if (initialIntent != null) {
            batteryReceiver.onReceive(context, initialIntent)
        }
        isReceiverRegistered = true
        Log.i(TAG, "BlePowerManager started")
    }

    fun stop() {
        if (!isReceiverRegistered) return
        runCatching { context.unregisterReceiver(batteryReceiver) }
        isReceiverRegistered = false
        Log.i(TAG, "BlePowerManager stopped")
    }

    fun setEmergencyOverride(active: Boolean) {
        if (active) {
            Log.w(TAG, "POWER MANAGER: Emergency SOS active! Overriding power saver mode to FULL_PERFORMANCE")
            _powerMode.value = PowerMode.EMERGENCY_OVERRIDE
        } else {
            evaluatePowerMode(_batteryLevel.value)
        }
    }

    private fun evaluatePowerMode(batteryPct: Int) {
        if (_powerMode.value == PowerMode.EMERGENCY_OVERRIDE) return

        val newMode = when {
            batteryPct > 50 -> PowerMode.FULL_PERFORMANCE
            batteryPct in 20..50 -> PowerMode.BALANCED
            else -> PowerMode.POWER_SAVER
        }

        if (_powerMode.value != newMode) {
            Log.i(TAG, "Power mode changed from ${_powerMode.value} to $newMode (Battery: $batteryPct%)")
            _powerMode.value = newMode
        }
    }

    fun getCurrentPowerProfile(): PowerProfile {
        return when (_powerMode.value) {
            PowerMode.FULL_PERFORMANCE, PowerMode.EMERGENCY_OVERRIDE -> PowerProfile(
                scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY,
                advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY,
                advertiseTxPower = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
            )
            PowerMode.BALANCED -> PowerProfile(
                scanMode = ScanSettings.SCAN_MODE_BALANCED,
                advertiseMode = AdvertiseSettings.ADVERTISE_MODE_BALANCED,
                advertiseTxPower = AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
            )
            PowerMode.POWER_SAVER -> PowerProfile(
                scanMode = ScanSettings.SCAN_MODE_LOW_POWER,
                advertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_POWER,
                advertiseTxPower = AdvertiseSettings.ADVERTISE_TX_POWER_LOW
            )
        }
    }
}
