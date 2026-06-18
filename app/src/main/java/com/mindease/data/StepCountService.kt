package com.mindease.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps counting steps even when the app is closed.
 * Uses TYPE_STEP_COUNTER sensor which persists across reboots.
 */
class StepCountService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "StepCountService"
        private const val CHANNEL_ID = "step_count_channel"
        private const val NOTIFICATION_ID = 2001
        private const val PREFS = "step_prefs"

        fun start(context: Context) {
            val intent = Intent(context, StepCountService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private lateinit var sensorManager: SensorManager
    private var stepCounter: Sensor? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: SecurityException) {
            Log.e(TAG, "Can't start foreground: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        if (stepCounter != null) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_UI)
            Log.d(TAG, "Step counter sensor registered")
        } else {
            Log.w(TAG, "No step counter sensor available")
        }

        return START_STICKY // Restart if killed
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toLong()
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

            // Get today's date key for daily reset
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val todayKey = sdf.format(java.util.Date())
            val savedDateKey = prefs.getString("step_date", "")

            if (savedDateKey != todayKey) {
                // New day — reset baseline
                prefs.edit()
                    .putFloat("BASE_STEPS", totalStepsSinceBoot.toFloat())
                    .putString("step_date", todayKey)
                    .putInt("DAILY_STEPS", 0)
                    .apply()
                Log.d(TAG, "New day detected, reset baseline to $totalStepsSinceBoot")
            }

            val baseSteps = prefs.getFloat("BASE_STEPS", -1f)
            val dailySteps = if (baseSteps < 0) {
                prefs.edit().putFloat("BASE_STEPS", totalStepsSinceBoot.toFloat()).apply()
                0
            } else {
                (totalStepsSinceBoot - baseSteps.toLong()).toInt().coerceAtLeast(0)
            }

            prefs.edit().putInt("DAILY_STEPS", dailySteps).apply()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Step Counter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks your daily step count"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MindEase")
            .setContentText("Tracking your daily steps")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
