package com.mindease.ui.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mindease.data.Repository
import com.mindease.data.UserPreferences
import com.mindease.network.HistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class DayData(
    val label: String,     // "Mon", "Tue", etc.
    val date: String,      // "Mar 01"
    val stress: Float,     // average stress score for that day
    val steps: Float,      // average steps
    val sleep: Float       // average sleep hours
)

data class HistoryState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val history: List<HistoryItem> = emptyList(),
    val last7DaysData: List<DayData> = emptyList(),
    val totalScans: Int = 0,
    val avgScore: Int = 0,
    val lowStressCount: Int = 0
)

class HistoryViewModel(
    private val repository: Repository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        _state.value = _state.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val token = userPreferences.authTokenFlow.first()
                val userId = userPreferences.userIdFlow.first()

                if (token.isNullOrBlank() || userId.isNullOrBlank()) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Please log in to view history"
                    )
                    return@launch
                }

                val result = repository.getHistory("Bearer $token", userId)

                if (result.isSuccess) {
                    val items = result.getOrNull()?.history ?: emptyList()

                    // List is already newest-first from Repository

                    // Derive stress level from score if backend only returns Low/High
                    items.forEach { item ->
                        val score = item.stress_score
                        if (score != null && (item.stress_level == null || item.stress_level == "Unknown")) {
                            item.stress_level = when {
                                score < 40 -> "Low"
                                score < 70 -> "Mid"
                                else -> "High"
                            }
                        }
                    }

                    val totalScans = items.size
                    val avgScore = if (items.isNotEmpty()) {
                        val scores = items.mapNotNull { it.stress_score }
                        if (scores.isNotEmpty()) scores.average().toInt() else 0
                    } else 0

                    val lowCount = items.count {
                        it.stress_level?.contains("Low", ignoreCase = true) == true
                    }

                    val dayData = buildLast7DaysData(items)

                    _state.value = _state.value.copy(
                        isLoading = false,
                        history = items,
                        last7DaysData = dayData,
                        totalScans = totalScans,
                        avgScore = avgScore,
                        lowStressCount = lowCount
                    )

                    Log.d("HISTORY", "Loaded ${items.size} history items")

                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to load history"
                    )
                }

            } catch (e: Exception) {
                Log.e("HISTORY", "Error: ${e.message}")
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Error loading history: ${e.message}"
                )
            }
        }
    }

    /**
     * Delete a specific history item by its position in the displayed list.
     * Delete a specific history item by hiding it locally.
     * Since the backend has no DELETE endpoint, we track deleted scans
     * by their parameter fingerprint in SharedPreferences.
     */
    fun deleteItem(displayIndex: Int) {
        viewModelScope.launch {
            try {
                val currentHistory = _state.value.history
                if (displayIndex >= currentHistory.size) return@launch

                val item = currentHistory[displayIndex]

                // Generate fingerprint and save as hidden
                val fingerprint = com.mindease.data.generateScanFingerprint(item)
                userPreferences.addHiddenScan(fingerprint)

                Log.d("HISTORY", "Hiding scan #${currentHistory.size - displayIndex}: $fingerprint")

                // Remove from local list immediately
                val updatedHistory = currentHistory.toMutableList()
                updatedHistory.removeAt(displayIndex)

                // Recalculate stats
                val scores = updatedHistory.mapNotNull { it.stress_score }
                val avgScore = if (scores.isNotEmpty()) scores.average().toInt() else 0
                val lowCount = updatedHistory.count {
                    it.stress_level?.contains("Low", ignoreCase = true) == true
                }

                _state.value = _state.value.copy(
                    history = updatedHistory,
                    totalScans = updatedHistory.size,
                    avgScore = avgScore,
                    lowStressCount = lowCount
                )

            } catch (e: Exception) {
                Log.e("HISTORY", "Delete failed: ${e.message}")
            }
        }
    }

    /**
     * Build last 7 days of aggregated data for graphs.
     * Groups history items by date, averages stress/steps/sleep per day.
     */
    private fun buildLast7DaysData(history: List<HistoryItem>): List<DayData> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val dayNameFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

        val last7Days = mutableListOf<DayData>()

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateKey = dateFormat.format(cal.time)
            val dayLabel = dayNameFormat.format(cal.time)
            val dateLabel = displayFormat.format(cal.time)

            val dayItems = history.filter { item ->
                val ts = item.timestamp ?: return@filter false
                try {
                    val itemDateStr = parseTimestampToDateString(ts, dateFormat)
                    itemDateStr == dateKey
                } catch (e: Exception) {
                    false
                }
            }

            val stepsList = dayItems.mapNotNull { it.steps }
            val sleepList = dayItems.mapNotNull { it.sleep_hours }

            val avgSteps = if (stepsList.isNotEmpty()) stepsList.average().toFloat() else 0f
            val avgSleep = if (sleepList.isNotEmpty()) sleepList.average().toFloat() else 0f

            // Use the LAST scan's actual stress_level to avoid averaging mismatch
            val lastScan = dayItems.lastOrNull()
            val stressLevel = lastScan?.stress_level ?: ""
            val stressLevelValue = when {
                stressLevel.contains("Low", ignoreCase = true) -> 0f
                stressLevel.contains("Mid", ignoreCase = true) || stressLevel.contains("Medium", ignoreCase = true) -> 1f
                stressLevel.contains("High", ignoreCase = true) -> 2f
                else -> {
                    // Fallback: use score from last scan
                    val score = lastScan?.stress_score ?: 0.0
                    when {
                        score <= 0 -> -1f  // no data
                        score < 40 -> 0f
                        score < 70 -> 1f
                        else -> 2f
                    }
                }
            }

            last7Days.add(DayData(dayLabel, dateLabel, stressLevelValue, avgSteps, avgSleep))
        }

        return last7Days
    }

    /**
     * Parse various timestamp formats to a "yyyy-MM-dd" date string.
     */
    private fun parseTimestampToDateString(ts: String, dateFormat: SimpleDateFormat): String? {
        // Try epoch millis
        val millis = ts.toLongOrNull()
        if (millis != null) {
            return dateFormat.format(Date(millis))
        }

        // Try epoch seconds
        val seconds = ts.toDoubleOrNull()
        if (seconds != null && seconds < 9999999999.0) {
            return dateFormat.format(Date((seconds * 1000).toLong()))
        }

        // Try ISO format
        if (ts.contains("T") && ts.length >= 10) {
            return ts.substring(0, 10)
        }

        // Try plain date
        if (ts.length >= 10) {
            return ts.substring(0, 10)
        }

        return null
    }
}

class HistoryViewModelFactory(
    private val repository: Repository,
    private val userPreferences: UserPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(repository, userPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
