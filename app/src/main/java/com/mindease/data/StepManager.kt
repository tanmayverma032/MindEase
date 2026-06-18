package com.mindease.data

import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

class StepManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(SENSOR_SERVICE) as SensorManager
    private val prefs: SharedPreferences = context.getSharedPreferences("step_prefs", Context.MODE_PRIVATE)

    private val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var baseSteps: Float
        get() = prefs.getFloat("BASE_STEPS", -1f)
        set(value) = prefs.edit().putFloat("BASE_STEPS", value).apply()

    private var liveDetectorSteps = 0
    private var currentSteps = 0

    // For accelerometer fallback
    private var accelStepCount = 0
        get() = prefs.getInt("ACCEL_STEPS", 0)
        set(value) {
            field = value
            prefs.edit().putInt("ACCEL_STEPS", value).apply()
        }
    
    private var lastMagnitude = 0.0
    private val stepThreshold = 11.5   // tune if needed
    private var lastStepTime = 0L

    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps

    /**
     * Start tracking based on priority logic:
     * IF stepCounter != null → use Step Counter (+ optional Step Detector)
     * ELSE IF stepDetector != null → use Step Detector only
     * ELSE → use Accelerometer fallback
     */
    fun startTracking() {
        if (stepCounter != null) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_UI)
            if (stepDetector != null) {
                sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_UI)
            }
        } else if (stepDetector != null) {
            sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_UI)
        } else if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
        
        // Initialize with background-counted steps or saved accelerometer steps
        val backgroundSteps = getBackgroundSteps()
        if (backgroundSteps > 0) {
            currentSteps = backgroundSteps
            _steps.value = currentSteps
        } else if (stepCounter == null) {
            currentSteps = accelStepCount
            _steps.value = currentSteps
        }
    }

    /**
     * Read the daily step count saved by StepCountService (background).
     */
    fun getBackgroundSteps(): Int {
        return prefs.getInt("DAILY_STEPS", 0)
    }

    fun stopTracking() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Call at midnight to reset counts
     */
    fun resetDailySteps() {
        if (stepCounter != null) {
            baseSteps = -1f
        }
        accelStepCount = 0
        currentSteps = 0
        liveDetectorSteps = 0
        _steps.value = currentSteps
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                if (baseSteps < 0f) {
                    baseSteps = event.values[0]
                }

                val actualSteps = (event.values[0] - baseSteps).toInt()

                currentSteps = actualSteps
                liveDetectorSteps = 0

                _steps.value = currentSteps
            }
            
            Sensor.TYPE_STEP_DETECTOR -> {
                liveDetectorSteps++
                currentSteps += 1

                _steps.value = currentSteps
            }
            
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val magnitude = sqrt((x * x + y * y + z * z).toDouble())
                val currentTime = System.currentTimeMillis()

                if (magnitude > stepThreshold &&
                    lastMagnitude <= stepThreshold &&
                    currentTime - lastStepTime > 300
                ) {
                    accelStepCount++
                    currentSteps = accelStepCount
                    _steps.value = currentSteps
                    lastStepTime = currentTime
                }

                lastMagnitude = magnitude
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}
