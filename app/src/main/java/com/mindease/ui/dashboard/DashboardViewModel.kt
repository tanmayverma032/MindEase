package com.mindease.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mindease.data.HealthConnectManager
import com.mindease.data.Repository
import com.mindease.data.SleepDetectionService
import com.mindease.data.SleepPatternRepository
import com.mindease.data.UserPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.mindease.network.HistoryItem

class DashboardViewModel(
    private val repository: Repository,
    private val userPreferences: UserPreferences,
    private val healthManager: HealthConnectManager,
    private val stepManager: com.mindease.data.StepManager,
    private val sleepPatternRepository: SleepPatternRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    init {
        stepManager.startTracking()
        checkSleepOnboarding()
        refreshDashboard()
    }

    override fun onCleared() {
        super.onCleared()
        stepManager.stopTracking()
    }

    fun refreshDashboard() {
        viewModelScope.launch {

            try {
                val userId = userPreferences.userIdFlow.first()
                val token = userPreferences.authTokenFlow.first()
                val userName = userPreferences.userNameFlow.first()

                // ✅ ALWAYS FETCH LIVE DATA
                val steps = try {
                    stepManager.steps.value
                } catch (e: Exception) {
                    Log.e("HEALTH", "Steps error: ${e.message}")
                    0
                }

                val sleep = try {
                    // Priority: SleepDetectionManager → HealthConnect → estimate
                    val sleepManager = SleepDetectionService.activeManager
                    val detected = sleepManager?.getLastSleepHours() ?: 0.0
                    if (detected > 0) {
                        detected
                    } else {
                        // Fallback: check pattern repository
                        val repoSleep = sleepPatternRepository.getLastSleepHours()
                        if (repoSleep > 0) repoSleep else healthManager.getSleepHours()
                    }
                } catch (e: Exception) {
                    Log.e("HEALTH", "Sleep error: ${e.message}")
                    0.0
                }

                // ✅ If user not logged in → still show live data
                if (token.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        userName = userName,
                        steps = steps,
                        sleepHours = sleep,
                        isLoading = false
                    )
                    return@launch
                }

                // ✅ Fetch backend history
                val result = repository.getHistory("Bearer $token", userId)

                if (result.isSuccess) {

                    val history = result.getOrNull()?.history ?: emptyList()

                    // List is already newest-first from Repository
                    val latest = history.firstOrNull()

                    // Derive stress level from score if needed
                    val stressLevel = if (latest != null) {
                        val score = latest.stress_score
                        if (latest.stress_level != null && latest.stress_level != "Unknown") {
                            latest.stress_level!!
                        } else if (score != null) {
                            when {
                                score < 40 -> "Low"
                                score < 70 -> "Mid"
                                else -> "High"
                            }
                        } else "Unknown"
                    } else "No scan yet"

                    val streak = calculateStreak(history)

                    // Build 7-day history for popup graphs
                    val (stepsHist, sleepHist) = buildLast7DaysHistory(history)

                    _uiState.value = DashboardState(
                        userName = userName,
                        stressLevel = stressLevel,
                        lastScore = latest?.stress_score?.toInt() ?: 0,
                        heartRate = latest?.heart_rate ?: 0,
                        totalScans = history.size,
                        dayStreak = streak,
                        steps = steps,              // ✅ LIVE
                        sleepHours = sleep,         // ✅ LIVE
                        stepsHistory = stepsHist,
                        sleepHistory = sleepHist,
                        isLoading = false
                    )

                } else {
                    Log.e("DASHBOARD", "History fetch failed")

                    _uiState.value = _uiState.value.copy(
                        userName = userName,
                        steps = steps,
                        sleepHours = sleep,
                        isLoading = false
                    )
                }

            } catch (e: Exception) {
                Log.e("DASHBOARD", "Crash: ${e.message}")

                _uiState.value = _uiState.value.copy(
                    isLoading = false
                )
            }
        }
    }

    /**
     * Build last 7 days steps and sleep history from scan data.
     */
    private fun buildLast7DaysHistory(history: List<HistoryItem>): Pair<List<Pair<String, Int>>, List<Pair<String, Double>>> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val dayNameFormat = SimpleDateFormat("EEE", Locale.getDefault())

        val stepsHistory = mutableListOf<Pair<String, Int>>()
        val sleepHistory = mutableListOf<Pair<String, Double>>()

        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateKey = dateFormat.format(cal.time)
            val dayLabel = dayNameFormat.format(cal.time)

            val dayItems = history.filter { item ->
                val ts = item.timestamp ?: return@filter false
                try {
                    val itemDateStr = parseTimestampToDate(ts, dateFormat)
                    itemDateStr == dateKey
                } catch (e: Exception) {
                    false
                }
            }

            val avgSteps = dayItems.mapNotNull { it.steps }.let { if (it.isNotEmpty()) it.average().toInt() else 0 }
            val avgSleep = dayItems.mapNotNull { it.sleep_hours }.let { if (it.isNotEmpty()) it.average() else 0.0 }

            stepsHistory.add(Pair(dayLabel, avgSteps))
            sleepHistory.add(Pair(dayLabel, avgSleep))
        }

        return Pair(stepsHistory, sleepHistory)
    }

    private fun parseTimestampToDate(ts: String, dateFormat: SimpleDateFormat): String? {
        val millis = ts.toLongOrNull()
        if (millis != null) return dateFormat.format(Date(millis))
        val seconds = ts.toDoubleOrNull()
        if (seconds != null && seconds < 9999999999.0) return dateFormat.format(Date((seconds * 1000).toLong()))
        if (ts.length >= 10) return ts.substring(0, 10)
        return null
    }

    // ✅ STREAK LOGIC (CORRECT)
    private fun calculateStreak(history: List<HistoryItem>): Int {
        if (history.isEmpty()) return 0

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")

        val dates = history.mapNotNull {
            val ts = it.timestamp ?: return@mapNotNull null
            try {
                // Try epoch millis first
                val millis = ts.toLongOrNull()
                if (millis != null) {
                    return@mapNotNull sdf.format(Date(millis))
                }
                // Try epoch seconds
                val seconds = ts.toDoubleOrNull()
                if (seconds != null && seconds < 9999999999.0) {
                    return@mapNotNull sdf.format(Date((seconds * 1000).toLong()))
                }
                // Try ISO string
                if (ts.length >= 10) {
                    return@mapNotNull ts.substring(0, 10)
                }
                null
            } catch (_: Exception) {
                null
            }
        }.distinct().sortedDescending()

        var streak = 0
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))

        for (date in dates) {
            val expectedDate = sdf.format(calendar.time)

            if (date == expectedDate) {
                streak++
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        return streak
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    private fun checkSleepOnboarding() {
        viewModelScope.launch {
            val done = userPreferences.sleepOnboardingDoneFlow.first()
            val shiftType = userPreferences.shiftTypeFlow.first()
            _uiState.value = _uiState.value.copy(
                showSleepOnboarding = !done,
                shiftType = shiftType
            )
        }
    }

    fun saveShiftType(type: String) {
        viewModelScope.launch {
            userPreferences.saveShiftType(type)
            _uiState.value = _uiState.value.copy(
                showSleepOnboarding = false,
                shiftType = type
            )
        }
    }
}

data class DashboardState(
    val userName: String = "",
    val stressLevel: String = "Unknown",
    val lastScore: Int = 0,
    val sleepHours: Double = 0.0,
    val steps: Int = 0,
    val heartRate: Int = 0,
    val totalScans: Int = 0,
    val dayStreak: Int = 0,
    val stepsHistory: List<Pair<String, Int>> = emptyList(),
    val sleepHistory: List<Pair<String, Double>> = emptyList(),
    val isLoading: Boolean = true,
    val showSleepOnboarding: Boolean = false,
    val shiftType: String = "day"
)

class DashboardViewModelFactory(
    private val repository: Repository,
    private val userPreferences: UserPreferences,
    private val healthManager: HealthConnectManager,
    private val stepManager: com.mindease.data.StepManager,
    private val sleepPatternRepository: SleepPatternRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(
                repository,
                userPreferences,
                healthManager,
                stepManager,
                sleepPatternRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}