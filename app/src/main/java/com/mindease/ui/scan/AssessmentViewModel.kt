package com.mindease.ui.scan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.mindease.data.HealthConnectManager
import com.mindease.data.Repository
import com.mindease.data.SleepDetectionService
import com.mindease.data.SleepPatternRepository
import com.mindease.data.UserPreferences
import com.mindease.network.GeminiHelper
import com.mindease.network.StressInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.net.URL

data class AssessmentState(
    val currentStep: Int = 1,

    val age: String = "",
    val gender: String = "Male",
    val workLifeBalance: String = "Student",
    val chestPain: String = "No",
    val cholesterol: String = "",
    val bodyTemperature: String = "",
    val ecgResult: String = "Normal",

    val restingHeartRate: Int? = null,
    val blinkRate: Int? = null,
    val postExerciseHeartRate: Int? = null,

    val sleepHours: Double? = null,
    val isAutoSleepSelected: Boolean = true,

    val stepsPerDay: String = "",
    val isAutoStepsSelected: Boolean = true,

    val isSubmitting: Boolean = false,
    val isProcessingMedia: Boolean = false,
    val isBlinkProcessing: Boolean = false,
    val error: String? = null,
    val successStressLevel: String? = null,
    val successStressScore: Int? = null,

    // AI-generated personalized tips
    val personalizedTips: List<String> = emptyList(),
    val isTipsLoading: Boolean = false,

    // Sensor data from Health Connect
    val autoSleepHours: Double? = null,
    val autoSteps: Int? = null,
    val isSensorDataLoaded: Boolean = false
)

class AssessmentViewModel(
    private val repository: Repository,
    private val userPreferences: UserPreferences,
    private val healthManager: HealthConnectManager,
    private val stepManager: com.mindease.data.StepManager,
    private val sleepPatternRepository: SleepPatternRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssessmentState())
    val uiState: StateFlow<AssessmentState> = _uiState.asStateFlow()

    init {
        loadSensorData()
        preWarmBlinkApi()
    }

    /**
     * Pre-warm the Blink API server on Render (cold starts take 30-60s).
     * Hitting the root endpoint wakes it up before the user needs it.
     */
    private fun preWarmBlinkApi() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = URL(com.mindease.BuildConfig.BLINK_URL)
                    val connection = url.openConnection()
                    connection.connectTimeout = 60000
                    connection.readTimeout = 60000
                    connection.getInputStream().close()
                }
                Log.d("BLINK", "Blink API pre-warmed successfully")
            } catch (e: Exception) {
                Log.d("BLINK", "Pre-warm attempt: ${e.message}")
            }
        }
    }

    fun updateBasicInfo(
        age: String, gender: String, workLifeBalance: String,
        chestPain: String, cholesterol: String, bodyTemperature: String,
        ecgResult: String
    ) {
        _uiState.value = _uiState.value.copy(
            age = age.trim(),
            gender = gender,
            workLifeBalance = workLifeBalance,
            chestPain = chestPain,
            cholesterol = cholesterol.trim(),
            bodyTemperature = bodyTemperature.trim(),
            ecgResult = ecgResult,
            error = null
        )
    }

    fun updateRestingHR(hr: Int?) {
        _uiState.value = _uiState.value.copy(restingHeartRate = hr ?: 72)
    }

    fun updateBlinkRate(rate: Int?) {
        _uiState.value = _uiState.value.copy(blinkRate = rate ?: 15)
    }

    fun updatePostExerciseHR(hr: Int?) {
        _uiState.value = _uiState.value.copy(postExerciseHeartRate = hr ?: 90)
    }

    fun updateSleep(hours: Double?, isAuto: Boolean) {
        _uiState.value = _uiState.value.copy(
            sleepHours = hours ?: 7.0,
            isAutoSleepSelected = isAuto
        )
    }

    fun updateSteps(steps: String?, isAuto: Boolean) {
        _uiState.value = _uiState.value.copy(
            stepsPerDay = steps?.trim() ?: "5000",
            isAutoStepsSelected = isAuto
        )
    }

    fun nextStep() {
        val current = _uiState.value.currentStep
        if (current < 6) {
            _uiState.value = _uiState.value.copy(currentStep = current + 1)
        }
    }

    fun previousStep() {
        val current = _uiState.value.currentStep
        if (current > 1) {
            _uiState.value = _uiState.value.copy(currentStep = current - 1)
        }
    }

    fun processBlinkVideo(file: File) {
        _uiState.value = _uiState.value.copy(isBlinkProcessing = true)
        nextStep()

        viewModelScope.launch {
            try {
                val requestFile = file.asRequestBody("video/mp4".toMediaTypeOrNull())
                val videoPart = MultipartBody.Part.createFormData("video", file.name, requestFile)

                val result = repository.countBlinks(videoPart)

                if (result.isSuccess) {
                    val rate = result.getOrNull()?.blink_rate ?: 15
                    _uiState.value = _uiState.value.copy(
                        blinkRate = rate,
                        isBlinkProcessing = false
                    )
                    Log.d("BLINK", "Blink rate detected: $rate")
                } else {
                    Log.e("BLINK", "API error: ${result.exceptionOrNull()?.message}")
                    _uiState.value = _uiState.value.copy(
                        blinkRate = 15, // use default so user isn't stuck
                        isBlinkProcessing = false
                    )
                }
            } catch (e: Exception) {
                Log.e("BLINK", "Processing error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    blinkRate = 15,
                    isBlinkProcessing = false
                )
            } finally {
                try { file.delete() } catch (_: Exception) {}
            }
        }
    }

    fun loadSensorData() {
        viewModelScope.launch {
            try {
                val steps = try {
                    stepManager.steps.value
                } catch (e: Exception) {
                    Log.e("ASSESSMENT", "Steps sensor error: ${e.message}")
                    0
                }

                val sleep = try {
                    val sleepManager = SleepDetectionService.activeManager
                    val detected = sleepManager?.getLastSleepHours() ?: 0.0
                    if (detected > 0) {
                        detected
                    } else {
                        val repoSleep = sleepPatternRepository.getLastSleepHours()
                        if (repoSleep > 0) repoSleep else healthManager.getSleepHours()
                    }
                } catch (e: Exception) {
                    Log.e("ASSESSMENT", "Sleep sensor error: ${e.message}")
                    0.0
                }

                _uiState.value = _uiState.value.copy(
                    autoSteps = steps,
                    autoSleepHours = sleep,
                    isSensorDataLoaded = true
                )

                Log.d("ASSESSMENT", "Sensor data loaded: steps=$steps, sleep=$sleep")

            } catch (e: Exception) {
                Log.e("ASSESSMENT", "Failed to load sensor data: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    autoSteps = 0,
                    autoSleepHours = 0.0,
                    isSensorDataLoaded = true
                )
            }
        }
    }

    fun submitAssessment() {
        val state = _uiState.value

        val age = state.age.toIntOrNull()
        val cholesterol = state.cholesterol.toDoubleOrNull()
        val temp = state.bodyTemperature.toDoubleOrNull()
        val steps = state.stepsPerDay.toIntOrNull()

        if (age == null || cholesterol == null || temp == null || steps == null) {
            _uiState.value = state.copy(error = "Please enter valid numeric values")
            return
        }

        _uiState.value = state.copy(isSubmitting = true, error = null)

        viewModelScope.launch {
            try {
                val token = userPreferences.authTokenFlow.first()
                val userId = userPreferences.userIdFlow.first()

                if (token.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        error = "User not authenticated",
                        isSubmitting = false
                    )
                    return@launch
                }

                val input = StressInput(
                    eyeBlinkRate = state.blinkRate ?: 15,
                    restingHeartRate = state.restingHeartRate ?: 72,
                    heartRateAfterExercise = state.postExerciseHeartRate ?: 90,
                    age = age,
                    gender = state.gender,
                    worklife = state.workLifeBalance,
                    sleepDuration = state.sleepHours ?: 7.0,
                    chestPain = when (state.chestPain) {
                        "Yes" -> 2
                        "Mild" -> 1
                        else -> 0
                    },
                    cholesterol = cholesterol,
                    ecgResult = when (state.ecgResult) {
                        "Sinus_Tachycardia" -> 2
                        "Mild_Tachycardia" -> 1
                        else -> 0
                    },
                    bodyTemp = temp,
                    steps = steps,
                    userId = userId ?: ""
                )

                Log.d("API_JSON", Gson().toJson(input))

                val result = repository.predictStress("Bearer $token", input)

                if (result.isSuccess) {
                    val resp = result.getOrNull()

                    Log.d("PREDICTION", "Response: stress_level=${resp?.stress_level}, stress_score=${resp?.stress_score}")

                    _uiState.value = _uiState.value.copy(
                        successStressLevel = resp?.stress_level,
                        successStressScore = resp?.stress_score,
                        isSubmitting = false,
                        isTipsLoading = true
                    )

                    // Generate personalized tips using Gemini
                    generatePersonalizedTips(
                        stressLevel = resp?.stress_level ?: "Unknown",
                        sleepHours = state.sleepHours ?: 7.0,
                        steps = steps,
                        restingHR = state.restingHeartRate ?: 72,
                        postExerciseHR = state.postExerciseHeartRate ?: 90,
                        blinkRate = state.blinkRate ?: 15,
                        age = age,
                        cholesterol = cholesterol,
                        bodyTemp = temp,
                        chestPain = when (state.chestPain) { "Yes" -> 2; "Mild" -> 1; else -> 0 },
                        ecgResult = when (state.ecgResult) { "Sinus_Tachycardia" -> 2; "Mild_Tachycardia" -> 1; else -> 0 }
                    )

                } else {
                    _uiState.value = _uiState.value.copy(
                        error = result.exceptionOrNull()?.message ?: "Prediction failed",
                        isSubmitting = false
                    )
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unexpected error",
                    isSubmitting = false
                )
            }
        }
    }

    /**
     * Call Gemini API to generate personalized tips based on user's data.
     */
    private fun generatePersonalizedTips(
        stressLevel: String, sleepHours: Double, steps: Int,
        restingHR: Int, postExerciseHR: Int, blinkRate: Int,
        age: Int, cholesterol: Double, bodyTemp: Double,
        chestPain: Int, ecgResult: Int
    ) {
        viewModelScope.launch {
            try {
                val tips = GeminiHelper.generateTips(
                    stressLevel, sleepHours, steps,
                    restingHR, postExerciseHR, blinkRate,
                    age, cholesterol, bodyTemp,
                    chestPain, ecgResult
                )

                _uiState.value = _uiState.value.copy(
                    personalizedTips = tips,
                    isTipsLoading = false
                )
            } catch (e: Exception) {
                Log.e("TIPS", "Failed to generate tips: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    personalizedTips = listOf(
                        "Take regular breaks and practice deep breathing exercises.",
                        "Aim for 7-8 hours of quality sleep each night.",
                        "Stay active with at least 30 minutes of walking daily."
                    ),
                    isTipsLoading = false
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = AssessmentState()
        loadSensorData()
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

class AssessmentViewModelFactory(
    private val repository: Repository,
    private val userPreferences: UserPreferences,
    private val healthManager: HealthConnectManager,
    private val stepManager: com.mindease.data.StepManager,
    private val sleepPatternRepository: SleepPatternRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AssessmentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AssessmentViewModel(repository, userPreferences, healthManager, stepManager, sleepPatternRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}