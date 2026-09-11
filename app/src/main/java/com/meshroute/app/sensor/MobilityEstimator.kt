package com.meshroute.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

enum class MobilityState(val code: Byte, val scoreMultiplier: Float) {
    STATIONARY(0, 0.2f),
    WALKING(1, 0.7f),
    HIGH_SPEED(2, 1.0f); // In transit, vehicle, or high mobility (highest encounter rate)

    companion object {
        fun fromCode(code: Byte): MobilityState {
            return entries.firstOrNull { it.code == code } ?: STATIONARY
        }
    }
}

/**
 * Lightweight on-device mobility sensor listener.
 * Evaluates device movement without continuous GPS battery drain.
 */
class MobilityEstimator(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _currentMobility = MutableStateFlow(MobilityState.STATIONARY)
    val currentMobility: StateFlow<MobilityState> = _currentMobility.asStateFlow()

    private var lastSampleTime = System.currentTimeMillis()
    private var varianceAccumulator = 0f
    private var sampleCount = 0
    private var runningMagnitude = 9.8f

    fun start() {
        if (accelerometer != null && sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        val delta = kotlin.math.abs(magnitude - runningMagnitude)
        runningMagnitude = 0.9f * runningMagnitude + 0.1f * magnitude
        varianceAccumulator += delta
        sampleCount++

        val now = System.currentTimeMillis()
        if (now - lastSampleTime >= 5000L) { // Evaluate every 5 seconds
            val avgVariance = if (sampleCount > 0) varianceAccumulator / sampleCount else 0f
            _currentMobility.value = when {
                avgVariance > 3.5f -> MobilityState.HIGH_SPEED
                avgVariance > 0.8f -> MobilityState.WALKING
                else -> MobilityState.STATIONARY
            }
            lastSampleTime = now
            varianceAccumulator = 0f
            sampleCount = 0
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
