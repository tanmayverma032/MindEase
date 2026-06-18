package com.mindease.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * BroadcastReceiver for screen on/off and user unlock events.
 *
 * Tracks screen state changes for sleep detection:
 * - SCREEN_OFF → possible sleep start
 * - USER_PRESENT (unlock) → possible wake event
 * - Handles brief unlock edge case (< 2 min unlock → not a real wake)
 */
class ScreenEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SCREEN_EVENT"

        // If user unlocks phone for less than this duration, it's a brief check, not waking up
        private const val BRIEF_UNLOCK_THRESHOLD_MS = 2 * 60 * 1000L // 2 minutes
    }

    data class ScreenState(
        val isScreenOff: Boolean = false,
        val lastScreenOffTime: Long = 0L,
        val lastUnlockTime: Long = 0L,
        val lastScreenOnTime: Long = 0L,
        val screenOffDurationMs: Long = 0L,
        val wasBriefUnlock: Boolean = false
    )

    private val _screenState = MutableStateFlow(ScreenState())
    val screenState: StateFlow<ScreenState> = _screenState

    // Track screen on time to detect brief unlocks
    private var screenOnTimestamp: Long = 0L

    override fun onReceive(context: Context?, intent: Intent?) {
        val now = System.currentTimeMillis()

        when (intent?.action) {
            Intent.ACTION_SCREEN_OFF -> {
                val currentState = _screenState.value

                // Check if the previous screen-on was a brief unlock
                val wasBrief = if (screenOnTimestamp > 0 && currentState.lastScreenOffTime > 0) {
                    (now - screenOnTimestamp) < BRIEF_UNLOCK_THRESHOLD_MS
                } else {
                    false
                }

                _screenState.value = currentState.copy(
                    isScreenOff = true,
                    lastScreenOffTime = if (wasBrief && currentState.lastScreenOffTime > 0) {
                        // Keep the original screen off time if this was just a brief unlock
                        currentState.lastScreenOffTime
                    } else {
                        now
                    },
                    wasBriefUnlock = wasBrief
                )

                Log.d(TAG, "Screen OFF (brief unlock: $wasBrief)")
            }

            Intent.ACTION_SCREEN_ON -> {
                screenOnTimestamp = now
                val currentState = _screenState.value

                val screenOffDuration = if (currentState.lastScreenOffTime > 0) {
                    now - currentState.lastScreenOffTime
                } else {
                    0L
                }

                _screenState.value = currentState.copy(
                    isScreenOff = false,
                    lastScreenOnTime = now,
                    screenOffDurationMs = screenOffDuration
                )

                Log.d(TAG, "Screen ON (was off for ${screenOffDuration / 60000} min)")
            }

            Intent.ACTION_USER_PRESENT -> {
                val currentState = _screenState.value

                _screenState.value = currentState.copy(
                    lastUnlockTime = now
                )

                Log.d(TAG, "User UNLOCKED phone")
            }
        }
    }

    /**
     * Register this receiver for screen events. Must be called from a Context.
     * Note: Screen on/off intents can only be registered programmatically.
     */
    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(this, filter)
        Log.d(TAG, "ScreenEventReceiver registered")
    }

    /**
     * Unregister this receiver.
     */
    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
            Log.d(TAG, "ScreenEventReceiver unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Receiver was not registered: ${e.message}")
        }
    }

    /**
     * Get how long the screen has been off (in ms).
     * Returns 0 if screen is currently on.
     */
    fun getScreenOffDuration(): Long {
        val state = _screenState.value
        return if (state.isScreenOff && state.lastScreenOffTime > 0) {
            System.currentTimeMillis() - state.lastScreenOffTime
        } else {
            0L
        }
    }
}
