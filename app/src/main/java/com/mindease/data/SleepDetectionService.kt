package com.mindease.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mindease.MainActivity

/**
 * Foreground service that runs the sleep detection engine in the background.
 *
 * Battery optimizations:
 * - Sensor batching (5-second report latency)
 * - 60-second evaluation interval
 * - Low-frequency accelerometer (SENSOR_DELAY_NORMAL)
 * - No continuous high-frequency processing
 */
class SleepDetectionService : Service() {

    companion object {
        private const val TAG = "SLEEP_SERVICE"
        private const val CHANNEL_ID = "sleep_tracking_channel"
        private const val NOTIFICATION_ID = 1001

        // Static reference for ViewModel access (simple approach, no DI framework)
        @Volatile
        var activeManager: SleepDetectionManager? = null
            internal set

        fun start(context: Context) {
            val intent = Intent(context, SleepDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SleepDetectionService::class.java))
        }
    }

    private var motionSensorManager: MotionSensorManager? = null
    private var screenEventReceiver: ScreenEventReceiver? = null
    private var sleepDetectionManager: SleepDetectionManager? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SleepDetectionService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "SleepDetectionService starting")

        // Start as foreground service
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission for foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // Initialize components
        val userPreferences = UserPreferences(applicationContext)
        val sleepPatternRepository = SleepPatternRepository(applicationContext)
        motionSensorManager = MotionSensorManager(applicationContext)
        screenEventReceiver = ScreenEventReceiver()

        // Register screen event receiver
        screenEventReceiver?.register(applicationContext)

        // Start motion tracking
        motionSensorManager?.startTracking()

        // Create and start the detection engine
        sleepDetectionManager = SleepDetectionManager(
            motionSensorManager!!,
            screenEventReceiver!!,
            sleepPatternRepository,
            userPreferences
        )
        sleepDetectionManager?.start()

        // Store reference for access from ViewModel
        activeManager = sleepDetectionManager

        Log.d(TAG, "Sleep detection engine running")

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SleepDetectionService destroying")

        sleepDetectionManager?.destroy()
        motionSensorManager?.stopTracking()
        screenEventReceiver?.unregister(applicationContext)

        activeManager = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sleep Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors sleep patterns for health tracking"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MindEase")
            .setContentText("Sleep tracking active")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
