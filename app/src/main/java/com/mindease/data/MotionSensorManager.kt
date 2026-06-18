package com.mindease.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

/**
 * Battery-optimized accelerometer motion detection for sleep tracking.
 *
 * Uses SENSOR_DELAY_NORMAL with sensor batching to minimize wake-ups.
 * Maintains a rolling average of motion magnitude over a 5-minute window.
 * Reports stillness when average motion stays below threshold for 20+ minutes.
 */
class MotionSensorManager(context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "MOTION_SENSOR"

        // Gravity magnitude at rest ≈ 9.81
        // Deviation threshold above gravity to count as "motion"
        private const val STILLNESS_THRESHOLD = 0.5

        // Duration of low motion to declare "still" (ms)
        private const val STILLNESS_DURATION_MS = 20 * 60 * 1000L // 20 minutes

        // Sensor batching: report every 5 seconds
        private const val BATCH_LATENCY_US = 5_000_000 // 5 seconds in microseconds

        // Rolling window size (approx 5 min at ~5s batched intervals = 60 samples)
        private const val ROLLING_WINDOW_SIZE = 60
    }

    data class MotionState(
        val isStill: Boolean = false,
        val avgMagnitude: Double = 0.0,
        val lastMotionTime: Long = System.currentTimeMillis(),
        val stillnessDurationMs: Long = 0L
    )

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _motionState = MutableStateFlow(MotionState())
    val motionState: StateFlow<MotionState> = _motionState

    // Rolling window of motion deviations
    private val motionSamples = mutableListOf<Double>()
    private var stillnessStartTime: Long = 0L
    private var lastSignificantMotionTime: Long = System.currentTimeMillis()

    fun startTracking() {
        if (accelerometer == null) {
            Log.w(TAG, "No accelerometer available")
            return
        }

        // Use SENSOR_DELAY_NORMAL (~200ms) with batching for battery efficiency
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL,
            BATCH_LATENCY_US
        )
        Log.d(TAG, "Motion tracking started with batching")
    }

    fun stopTracking() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Motion tracking stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()

        val magnitude = sqrt(x * x + y * y + z * z)

        // Deviation from gravity (9.81) indicates actual movement
        val deviation = Math.abs(magnitude - 9.81)

        // Add to rolling window
        synchronized(motionSamples) {
            motionSamples.add(deviation)
            if (motionSamples.size > ROLLING_WINDOW_SIZE) {
                motionSamples.removeAt(0)
            }
        }

        val avgDeviation = synchronized(motionSamples) {
            if (motionSamples.isEmpty()) 0.0 else motionSamples.average()
        }

        val now = System.currentTimeMillis()

        if (avgDeviation > STILLNESS_THRESHOLD) {
            // Significant motion detected
            lastSignificantMotionTime = now
            stillnessStartTime = 0L

            _motionState.value = MotionState(
                isStill = false,
                avgMagnitude = avgDeviation,
                lastMotionTime = now,
                stillnessDurationMs = 0L
            )
        } else {
            // Below threshold — track stillness duration
            if (stillnessStartTime == 0L) {
                stillnessStartTime = now
            }

            val stillnessDuration = now - stillnessStartTime
            val isStill = stillnessDuration >= STILLNESS_DURATION_MS

            _motionState.value = MotionState(
                isStill = isStill,
                avgMagnitude = avgDeviation,
                lastMotionTime = lastSignificantMotionTime,
                stillnessDurationMs = stillnessDuration
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    /**
     * Check if there was a motion spike (sudden movement after prolonged stillness).
     * Used for wake detection.
     */
    fun hasMotionSpike(): Boolean {
        val state = _motionState.value
        // Motion spike = was still for 20+ min but now moving
        return state.stillnessDurationMs > STILLNESS_DURATION_MS && state.avgMagnitude > STILLNESS_THRESHOLD * 2
    }
}
