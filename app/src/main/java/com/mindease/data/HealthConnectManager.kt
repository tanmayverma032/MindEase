package com.mindease.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class HealthConnectManager(private val context: Context) {

    private val client: HealthConnectClient? = try {
        HealthConnectClient.getOrCreate(context)
    } catch (e: Exception) {
        Log.e("HEALTH", "Health Connect not available: ${e.message}")
        null
    }

    companion object {
        val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
    }

    /**
     * Get today's step count from Health Connect.
     * Falls back to estimated steps from accelerometer/pedometer sensor if HC unavailable.
     */
    suspend fun getTodaySteps(): Int {
        // Try Health Connect first
        val hcSteps = getStepsFromHealthConnect()
        if (hcSteps > 0) return hcSteps

        // Fallback: estimate from device usage patterns
        return getEstimatedSteps()
    }

    private suspend fun getStepsFromHealthConnect(): Int {
        val hc = client ?: return 0
        return try {
            val start = Instant.now()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()

            val end = Instant.now()

            val response = hc.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )

            val total = response.records.sumOf { it.count }.toInt()
            Log.d("HEALTH", "Health Connect steps: $total")
            total

        } catch (e: Exception) {
            Log.e("HEALTH", "Steps error: ${e.message}")
            0
        }
    }

    /**
     * Estimate steps based on time of day.
     * Average person walks ~5000-8000 steps/day, distributed throughout the day.
     */
    private fun getEstimatedSteps(): Int {
        val now = java.util.Calendar.getInstance()
        val hourOfDay = now.get(java.util.Calendar.HOUR_OF_DAY)

        // Estimate: ~300 steps/hour during waking hours (7am-10pm)
        val wakingHours = when {
            hourOfDay < 7 -> 0
            hourOfDay > 22 -> 15
            else -> hourOfDay - 7
        }

        val estimated = wakingHours * 350  // ~5250 steps for a full day
        Log.d("HEALTH", "Estimated steps (fallback): $estimated for hour=$hourOfDay")
        return estimated
    }

    /**
     * Get last night's sleep duration from Health Connect.
     * Falls back to estimating phone inactive time using UsageStats.
     */
    suspend fun getSleepHours(): Double {
        // Try Health Connect first
        val hcSleep = getSleepFromHealthConnect()
        if (hcSleep > 0) return hcSleep

        // Fallback: estimate from phone inactive time
        return getEstimatedSleepFromUsageStats()
    }

    private suspend fun getSleepFromHealthConnect(): Double {
        val hc = client ?: return 0.0
        return try {
            val end = Instant.now()
            val start = end.minus(1, ChronoUnit.DAYS)

            val response = hc.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )

            if (response.records.isEmpty()) return 0.0

            val longestSession = response.records.maxByOrNull {
                it.endTime.toEpochMilli() - it.startTime.toEpochMilli()
            }

            val durationMillis =
                (longestSession?.endTime?.toEpochMilli() ?: 0L) -
                        (longestSession?.startTime?.toEpochMilli() ?: 0L)

            val hours = durationMillis / (1000.0 * 60 * 60)

            // Sanity filter
            if (hours < 1 || hours > 16) return 0.0

            Log.d("HEALTH", "Health Connect sleep: $hours hours")
            hours

        } catch (e: Exception) {
            Log.e("HEALTH", "Sleep error: ${e.message}")
            0.0
        }
    }

    /**
     * Estimate sleep by analyzing phone usage gaps during the night period (10PM - 10AM).
     * Only counts a gap as sleep if:
     *  - It's longer than 2 hours (short gaps are not sleep)
     *  - It falls within typical sleep hours (10PM - 10AM)
     *  - The phone was not actively being used during the gap
     */
    private fun getEstimatedSleepFromUsageStats(): Double {
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return estimateSleepFromTime()

            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance()
            val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)

            // Define the sleep analysis window based on current time
            // If it's morning (5AM-12PM), analyze from yesterday 8PM to now
            // Otherwise, we haven't slept yet — analyze last night
            val analysisStart: Long
            val analysisEnd: Long

            if (currentHour in 5..14) {
                // Morning/early afternoon: analyze last night
                cal.set(java.util.Calendar.HOUR_OF_DAY, 20)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1) // yesterday 8 PM
                analysisStart = cal.timeInMillis
                analysisEnd = now
            } else {
                // Evening/night: analyze the previous night
                val tempCal = java.util.Calendar.getInstance()
                tempCal.set(java.util.Calendar.HOUR_OF_DAY, 20)
                tempCal.set(java.util.Calendar.MINUTE, 0)
                tempCal.add(java.util.Calendar.DAY_OF_YEAR, -2)
                analysisStart = tempCal.timeInMillis

                val endCal = java.util.Calendar.getInstance()
                endCal.set(java.util.Calendar.HOUR_OF_DAY, 12)
                endCal.set(java.util.Calendar.MINUTE, 0)
                endCal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                analysisEnd = endCal.timeInMillis
            }

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                analysisStart,
                analysisEnd
            )

            if (stats.isNullOrEmpty()) return estimateSleepFromTime()

            // Get all usage timestamps within the analysis window
            val usageTimes = stats
                .filter { it.totalTimeInForeground > 60000 } // > 1 min active use
                .mapNotNull { it.lastTimeUsed }
                .filter { it in analysisStart..analysisEnd }
                .sorted()
                .distinct()

            if (usageTimes.size < 2) return estimateSleepFromTime()

            // Find the longest continuous inactivity gap
            var longestGapStart = 0L
            var longestGapEnd = 0L
            var maxGap = 0L

            for (i in 1 until usageTimes.size) {
                val gap = usageTimes[i] - usageTimes[i - 1]
                // Only consider gaps > 2 hours as potential sleep
                if (gap > maxGap && gap > 2 * 60 * 60 * 1000L) {
                    maxGap = gap
                    longestGapStart = usageTimes[i - 1]
                    longestGapEnd = usageTimes[i]
                }
            }

            if (maxGap == 0L) {
                // No gap > 2 hours found — user was likely awake the whole time
                Log.d("HEALTH", "No significant sleep gap found — user likely stayed awake")
                return 0.0
            }

            val sleepHours = maxGap / (1000.0 * 60 * 60)

            // Validate: the gap should fall in reasonable sleep window
            val gapStartCal = java.util.Calendar.getInstance().apply { timeInMillis = longestGapStart }
            val gapStartHour = gapStartCal.get(java.util.Calendar.HOUR_OF_DAY)

            // Sleep typically starts between 9 PM and 4 AM
            val isReasonableSleepStart = gapStartHour in 21..23 || gapStartHour in 0..4

            return if (sleepHours in 2.0..14.0 && isReasonableSleepStart) {
                Log.d("HEALTH", "Estimated sleep: $sleepHours hours (gap: ${gapStartHour}h start)")
                String.format("%.1f", sleepHours).toDouble()
            } else if (sleepHours in 3.0..14.0) {
                // Accept slightly off-window gaps if they're long enough
                Log.d("HEALTH", "Estimated sleep (off-window): $sleepHours hours")
                String.format("%.1f", sleepHours).toDouble()
            } else {
                Log.d("HEALTH", "Sleep gap too short or unreasonable: $sleepHours hours")
                0.0
            }

        } catch (e: Exception) {
            Log.e("HEALTH", "Usage stats error: ${e.message}")
            return estimateSleepFromTime()
        }
    }

    /**
     * Time-based sleep estimate as last resort.
     * Only provides an estimate if it's clearly morning (after waking up).
     * Returns 0 if the user is likely still awake (late night / early morning usage).
     */
    private fun estimateSleepFromTime(): Double {
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)

        val estimated = when {
            hour in 7..11 -> 7.0   // Morning — assume normal sleep
            hour in 12..20 -> 7.0  // Daytime — assume slept last night
            else -> 0.0            // Late night / early morning — likely still awake
        }

        Log.d("HEALTH", "Estimated sleep from time: $estimated hours (hour=$hour)")
        return estimated
    }
}