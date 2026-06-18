package com.mindease.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

/**
 * Stores and retrieves historical sleep data using SharedPreferences (JSON serialized).
 * Keeps the last 14 days of sleep sessions for pattern learning.
 */
class SleepPatternRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sleep_pattern_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val TAG = "SLEEP_PATTERN"
        private const val KEY_SESSIONS = "sleep_sessions"
        private const val MAX_SESSIONS = 14
    }

    // ---------- Data Model ---------- //

    data class SleepSession(
        val sleepStartMillis: Long,
        val wakeTimeMillis: Long,
        val durationHours: Double,
        val confidence: Int, // 0-100
        val shiftType: String // "day" or "night"
    )

    // ---------- Save ---------- //

    fun saveSleepSession(session: SleepSession) {
        val sessions = getRecentSessions().toMutableList()
        sessions.add(session)

        // Keep only last MAX_SESSIONS
        val trimmed = if (sessions.size > MAX_SESSIONS) {
            sessions.takeLast(MAX_SESSIONS)
        } else {
            sessions
        }

        val json = gson.toJson(trimmed)
        prefs.edit().putString(KEY_SESSIONS, json).apply()
        Log.d(TAG, "Saved sleep session. Total stored: ${trimmed.size}")
    }

    // ---------- Read ---------- //

    fun getRecentSessions(): List<SleepSession> {
        val json = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SleepSession>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse sessions: ${e.message}")
            emptyList()
        }
    }

    /**
     * Returns the sleep hours from the most recent completed session
     * (within last 24 hours). Returns 0.0 if no recent session.
     */
    fun getLastSleepHours(): Double {
        val sessions = getRecentSessions()
        if (sessions.isEmpty()) return 0.0

        val latest = sessions.last()
        val ageMillis = System.currentTimeMillis() - latest.wakeTimeMillis

        // Only return if the session ended within the last 24 hours
        return if (ageMillis < 24 * 60 * 60 * 1000L && latest.durationHours in 1.0..16.0) {
            latest.durationHours
        } else {
            0.0
        }
    }

    /**
     * Predicts the user's typical sleep window based on historical data.
     * Returns Pair(expectedSleepStartHour, expectedWakeHour) in 24h format.
     *
     * Falls back to shift-type defaults if not enough data.
     */
    fun getPredictedSleepWindow(shiftType: String): Pair<Int, Int> {
        val sessions = getRecentSessions()

        if (sessions.size < 3) {
            // Not enough data — use shift defaults
            return if (shiftType == "night") {
                Pair(6, 16) // Night shift: sleep 6 AM – 4 PM
            } else {
                Pair(21, 8) // Day shift: sleep 9 PM – 8 AM
            }
        }

        // Calculate average sleep start hour and wake hour
        val startHours = sessions.map { hourOfDay(it.sleepStartMillis) }
        val wakeHours = sessions.map { hourOfDay(it.wakeTimeMillis) }

        val avgStart = circularMean(startHours).toInt().coerceIn(0, 23)
        val avgWake = circularMean(wakeHours).toInt().coerceIn(0, 23)

        Log.d(TAG, "Predicted sleep window: $avgStart:00 – $avgWake:00 (from ${sessions.size} sessions)")
        return Pair(avgStart, avgWake)
    }

    /**
     * Check if the current time falls within the predicted sleep window.
     */
    fun isInSleepWindow(shiftType: String): Boolean {
        val (start, end) = getPredictedSleepWindow(shiftType)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return if (start > end) {
            // Window crosses midnight (e.g., 21–8)
            currentHour >= start || currentHour < end
        } else {
            // Window within same day (e.g., 6–16)
            currentHour in start until end
        }
    }

    // ---------- Helpers ---------- //

    private fun hourOfDay(millis: Long): Double {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
    }

    /**
     * Circular mean for hours (handles wrap-around at midnight).
     */
    private fun circularMean(hours: List<Double>): Double {
        if (hours.isEmpty()) return 0.0

        var sinSum = 0.0
        var cosSum = 0.0

        for (h in hours) {
            val angle = h * 2 * Math.PI / 24.0
            sinSum += Math.sin(angle)
            cosSum += Math.cos(angle)
        }

        var mean = Math.atan2(sinSum / hours.size, cosSum / hours.size)
        if (mean < 0) mean += 2 * Math.PI

        return mean * 24.0 / (2 * Math.PI)
    }

    /**
     * Get last sleep confidence score (0-100).
     */
    fun getLastConfidence(): Int {
        val sessions = getRecentSessions()
        return sessions.lastOrNull()?.confidence ?: 0
    }
}
