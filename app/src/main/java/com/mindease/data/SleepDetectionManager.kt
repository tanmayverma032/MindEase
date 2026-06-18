package com.mindease.data

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Core sleep detection engine that combines multiple signals:
 * - Screen off/on events
 * - Motion sensor (accelerometer stillness)
 * - Historical sleep pattern prediction
 * - Phone unlock detection
 *
 * State machine: AWAKE → POSSIBLY_SLEEPING → SLEEPING → WAKING → AWAKE
 *
 * Runs a 60-second evaluation loop for battery efficiency.
 */
class SleepDetectionManager(
    private val motionSensorManager: MotionSensorManager,
    private val screenEventReceiver: ScreenEventReceiver,
    private val sleepPatternRepository: SleepPatternRepository,
    private val userPreferences: UserPreferences
) {

    companion object {
        private const val TAG = "SLEEP_DETECT"

        // Evaluation interval
        private const val EVAL_INTERVAL_MS = 60_000L // 60 seconds

        // Thresholds
        private const val SCREEN_OFF_MIN_MS = 15 * 60 * 1000L       // 15 min screen off → possibly sleeping
        private const val SCREEN_OFF_STRONG_MS = 30 * 60 * 1000L    // 30 min → strong signal
        private const val STILLNESS_MIN_MS = 20 * 60 * 1000L        // 20 min still
        private const val BRIEF_UNLOCK_MS = 2 * 60 * 1000L          // < 2 min unlock = brief

        // Confidence weights (must sum ≈ 100)
        private const val WEIGHT_SCREEN_OFF = 30
        private const val WEIGHT_MOTION_STILL = 30
        private const val WEIGHT_SLEEP_WINDOW = 25
        private const val WEIGHT_NO_UNLOCK = 15
    }

    enum class SleepStatus {
        AWAKE,
        POSSIBLY_SLEEPING,
        SLEEPING,
        WAKING
    }

    data class SleepState(
        val status: SleepStatus = SleepStatus.AWAKE,
        val sleepStartTime: Long = 0L,
        val wakeTime: Long = 0L,
        val durationHours: Double = 0.0,
        val confidence: Int = 0,
        val lastSleepHours: Double = 0.0 // from last completed session
    )

    private val _sleepState = MutableStateFlow(SleepState(
        lastSleepHours = sleepPatternRepository.getLastSleepHours()
    ))
    val sleepState: StateFlow<SleepState> = _sleepState

    private var evaluationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Track brief unlock state
    private var lastUnlockTime = 0L
    private var lastScreenOnTime = 0L
    private var sleepSessionStartTime = 0L

    /**
     * Start the detection evaluation loop.
     */
    fun start() {
        evaluationJob?.cancel()
        evaluationJob = scope.launch {
            Log.d(TAG, "Sleep detection engine started")
            while (isActive) {
                try {
                    evaluate()
                } catch (e: Exception) {
                    Log.e(TAG, "Evaluation error: ${e.message}")
                }
                delay(EVAL_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop the detection engine.
     */
    fun stop() {
        evaluationJob?.cancel()
        Log.d(TAG, "Sleep detection engine stopped")
    }

    /**
     * Core evaluation — runs every 60 seconds.
     */
    private suspend fun evaluate() {
        val screenState = screenEventReceiver.screenState.value
        val motionState = motionSensorManager.motionState.value
        val shiftType = userPreferences.shiftTypeFlow.first()
        val now = System.currentTimeMillis()

        val currentStatus = _sleepState.value.status

        // Calculate individual signal scores
        val screenOffDuration = screenEventReceiver.getScreenOffDuration()
        val isScreenOff = screenState.isScreenOff
        val isStill = motionState.isStill
        val isInWindow = sleepPatternRepository.isInSleepWindow(shiftType)
        val timeSinceLastUnlock = if (screenState.lastUnlockTime > 0) {
            now - screenState.lastUnlockTime
        } else {
            Long.MAX_VALUE
        }

        // Calculate confidence
        val confidence = calculateConfidence(
            screenOffDuration = screenOffDuration,
            isScreenOff = isScreenOff,
            isStill = isStill,
            stillnessDuration = motionState.stillnessDurationMs,
            isInSleepWindow = isInWindow,
            timeSinceLastUnlock = timeSinceLastUnlock,
            motionMagnitude = motionState.avgMagnitude
        )

        Log.d(TAG, "Eval: status=$currentStatus, screenOff=$isScreenOff (${screenOffDuration / 60000}min), " +
                "still=$isStill (${motionState.stillnessDurationMs / 60000}min), " +
                "inWindow=$isInWindow, confidence=$confidence")

        when (currentStatus) {
            SleepStatus.AWAKE -> {
                if (isScreenOff && screenOffDuration >= SCREEN_OFF_MIN_MS && confidence >= 40) {
                    // Transition to POSSIBLY_SLEEPING
                    _sleepState.value = _sleepState.value.copy(
                        status = SleepStatus.POSSIBLY_SLEEPING,
                        sleepStartTime = screenState.lastScreenOffTime,
                        confidence = confidence
                    )
                    sleepSessionStartTime = screenState.lastScreenOffTime
                    Log.d(TAG, "→ POSSIBLY_SLEEPING (confidence=$confidence)")
                }
            }

            SleepStatus.POSSIBLY_SLEEPING -> {
                if (!isScreenOff && !screenState.wasBriefUnlock) {
                    // User woke up before we confirmed sleep
                    _sleepState.value = _sleepState.value.copy(
                        status = SleepStatus.AWAKE,
                        confidence = 0
                    )
                    Log.d(TAG, "→ AWAKE (screen on, not brief unlock)")
                } else if (isScreenOff && screenOffDuration >= SCREEN_OFF_STRONG_MS && isStill && confidence >= 60) {
                    // Strong signals → confirm SLEEPING
                    _sleepState.value = _sleepState.value.copy(
                        status = SleepStatus.SLEEPING,
                        sleepStartTime = sleepSessionStartTime,
                        confidence = confidence
                    )
                    Log.d(TAG, "→ SLEEPING confirmed (confidence=$confidence)")
                } else if (!isScreenOff && screenState.wasBriefUnlock) {
                    // Brief unlock — stay in possibly sleeping
                    Log.d(TAG, "Brief unlock detected, staying POSSIBLY_SLEEPING")
                }
            }

            SleepStatus.SLEEPING -> {
                // Check for wake events
                val unlocked = !isScreenOff && screenState.lastUnlockTime > sleepSessionStartTime
                val motionSpike = !isStill && motionState.avgMagnitude > 1.0

                if (unlocked && !screenState.wasBriefUnlock) {
                    // Real unlock after sleep → WAKING
                    val wakeTime = screenState.lastUnlockTime
                    val durationMs = wakeTime - sleepSessionStartTime
                    val durationHours = durationMs / (1000.0 * 60 * 60)

                    if (durationHours >= 1.0) {
                        completeSleepSession(sleepSessionStartTime, wakeTime, durationHours, confidence, shiftType)
                    }

                    _sleepState.value = _sleepState.value.copy(
                        status = SleepStatus.AWAKE,
                        wakeTime = wakeTime,
                        durationHours = durationHours.coerceIn(0.0, 16.0),
                        lastSleepHours = if (durationHours in 1.0..16.0) durationHours else _sleepState.value.lastSleepHours
                    )
                    Log.d(TAG, "→ AWAKE (unlocked, slept ${String.format("%.1f", durationHours)}h)")

                } else if (motionSpike && screenOffDuration < SCREEN_OFF_MIN_MS) {
                    // Motion spike + screen recently came on → possible wake
                    Log.d(TAG, "Motion spike detected during sleep - monitoring")
                } else if (screenState.wasBriefUnlock) {
                    // Brief unlock during sleep — ignore
                    Log.d(TAG, "Brief unlock during sleep — ignoring")
                }
            }

            SleepStatus.WAKING -> {
                // Transition back to AWAKE
                _sleepState.value = _sleepState.value.copy(status = SleepStatus.AWAKE)
            }
        }
    }

    /**
     * Calculate confidence score (0-100) based on weighted signals.
     */
    private fun calculateConfidence(
        screenOffDuration: Long,
        isScreenOff: Boolean,
        isStill: Boolean,
        stillnessDuration: Long,
        isInSleepWindow: Boolean,
        timeSinceLastUnlock: Long,
        motionMagnitude: Double
    ): Int {
        var score = 0

        // Screen off signal
        if (isScreenOff) {
            score += when {
                screenOffDuration >= SCREEN_OFF_STRONG_MS -> WEIGHT_SCREEN_OFF
                screenOffDuration >= SCREEN_OFF_MIN_MS -> WEIGHT_SCREEN_OFF * 2 / 3
                screenOffDuration >= 10 * 60 * 1000L -> WEIGHT_SCREEN_OFF / 3
                else -> 0
            }
        }

        // Motion stillness signal
        if (isStill) {
            score += when {
                stillnessDuration >= 30 * 60 * 1000L -> WEIGHT_MOTION_STILL
                stillnessDuration >= STILLNESS_MIN_MS -> WEIGHT_MOTION_STILL * 2 / 3
                stillnessDuration >= 10 * 60 * 1000L -> WEIGHT_MOTION_STILL / 3
                else -> 0
            }
        } else if (motionMagnitude > 1.0 && isScreenOff) {
            // Phone on table/vibrating but screen off — reduce confidence
            score -= 10
        }

        // Sleep window signal
        if (isInSleepWindow) {
            score += WEIGHT_SLEEP_WINDOW
        }

        // No unlock signal
        if (timeSinceLastUnlock > SCREEN_OFF_MIN_MS) {
            score += WEIGHT_NO_UNLOCK
        }

        return score.coerceIn(0, 100)
    }

    /**
     * Complete a sleep session — save to repository for historical learning.
     */
    private fun completeSleepSession(
        startTime: Long,
        wakeTime: Long,
        durationHours: Double,
        confidence: Int,
        shiftType: String
    ) {
        if (durationHours < 1.0 || durationHours > 16.0) return

        val session = SleepPatternRepository.SleepSession(
            sleepStartMillis = startTime,
            wakeTimeMillis = wakeTime,
            durationHours = String.format("%.1f", durationHours).toDouble(),
            confidence = confidence,
            shiftType = shiftType
        )
        sleepPatternRepository.saveSleepSession(session)
        Log.d(TAG, "Sleep session saved: ${String.format("%.1f", durationHours)}h, confidence=$confidence%")
    }

    /**
     * Get the last completed sleep session duration in hours.
     * Used by Dashboard and Assessment.
     */
    fun getLastSleepHours(): Double {
        val currentState = _sleepState.value

        // If we have a recently completed session, use that
        if (currentState.lastSleepHours > 0) {
            return currentState.lastSleepHours
        }

        // Otherwise check repository
        return sleepPatternRepository.getLastSleepHours()
    }

    /**
     * Destroy and clean up resources.
     */
    fun destroy() {
        stop()
        scope.cancel()
    }
}
