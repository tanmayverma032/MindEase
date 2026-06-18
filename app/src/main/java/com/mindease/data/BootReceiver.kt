package com.mindease.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts background services (Sleep Detection + Step Counting) after device boot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
            
            if (hasPermission) {
                try {
                    SleepDetectionService.start(context)
                } catch (e: Exception) {
                    Log.e("BOOT_RECEIVER", "Failed to start sleep service: ${e.message}")
                }
                try {
                    StepCountService.start(context)
                } catch (e: Exception) {
                    Log.e("BOOT_RECEIVER", "Failed to start step count service: ${e.message}")
                }
            } else {
                Log.d("BOOT_RECEIVER", "Cannot start services: missing ACTIVITY_RECOGNITION permission")
            }
        }
    }
}
