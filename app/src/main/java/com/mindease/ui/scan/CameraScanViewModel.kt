package com.mindease.ui.scan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mindease.data.Repository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class CameraScanViewModel(private val repository: Repository) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _currentHeartRate = MutableStateFlow<Int>(0)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    private val _signalQuality = MutableStateFlow(SignalQuality.NONE)
    val signalQuality: StateFlow<SignalQuality> = _signalQuality.asStateFlow()

    private val heartRateBuffer = mutableListOf<Int>()
    private var scanJob: Job? = null

    fun updateSignalQuality(quality: SignalQuality) {
        _signalQuality.value = quality
    }

    fun updateCurrentHeartRate(hr: Int) {
        if (hr in 50..170) {
            heartRateBuffer.add(hr)
            if (heartRateBuffer.size > 20) heartRateBuffer.removeAt(0)

            // IQR outlier removal for stability
            if (heartRateBuffer.size >= 5) {
                val sorted = heartRateBuffer.sorted()
                val q1 = sorted[sorted.size / 4]
                val q3 = sorted[sorted.size * 3 / 4]
                val iqr = q3 - q1
                val lower = q1 - 1.5 * iqr
                val upper = q3 + 1.5 * iqr
                val filtered = heartRateBuffer.filter { it >= lower && it <= upper }
                if (filtered.isNotEmpty()) {
                    _currentHeartRate.value = filtered.average().roundToInt()
                }
            } else {
                _currentHeartRate.value = heartRateBuffer.average().roundToInt()
            }
        }
    }

    fun startHRMeasurement() {
        scanJob?.cancel()
        heartRateBuffer.clear()
        _currentHeartRate.value = 0
        _signalQuality.value = SignalQuality.NONE
        _scanState.value = ScanState.MeasuringHR

        scanJob = viewModelScope.launch {
            delay(20000)

            val finalHR = if (heartRateBuffer.size >= 5) {
                val sorted = heartRateBuffer.sorted()
                val q1 = sorted[sorted.size / 4]
                val q3 = sorted[sorted.size * 3 / 4]
                val iqr = q3 - q1
                val filtered = sorted.filter { it >= q1 - 1.5 * iqr && it <= q3 + 1.5 * iqr }
                if (filtered.isNotEmpty()) filtered.average().roundToInt() else 0
            } else {
                0
            }

            if (finalHR in 50..170) {
                Log.d("HR_SCAN", "Final HR: $finalHR BPM (from ${heartRateBuffer.size} readings)")
                _scanState.value = ScanState.HRSuccess(finalHR)
            } else {
                _scanState.value = ScanState.Error(
                    "Could not detect heart rate. Press your finger firmly over the camera lens and ensure the flashlight illuminates your fingertip."
                )
            }
        }
    }

    fun setError(message: String) {
        _scanState.value = ScanState.Error(message)
    }

    fun setBlinkSuccess(blinkRate: Int) {
        scanJob?.cancel()
        val finalHr = if (currentHeartRate.value in 50..170) currentHeartRate.value else 75
        _scanState.value = ScanState.Success(blinkRate, finalHr)
        Log.d("BLINK_FLOW", "Local blink detection finished: $blinkRate BPM")
    }

    fun resetState() {
        scanJob?.cancel()
        heartRateBuffer.clear()
        _currentHeartRate.value = 0
        _signalQuality.value = SignalQuality.NONE
        _scanState.value = ScanState.Idle
    }
}

enum class SignalQuality { NONE, POOR, FAIR, GOOD }

sealed class ScanState {
    object Idle : ScanState()
    object Recording : ScanState()
    object Processing : ScanState()
    object MeasuringHR : ScanState()
    data class Success(val blinkRate: Int, val heartRate: Int) : ScanState()
    data class HRSuccess(val heartRate: Int) : ScanState()
    data class Error(val message: String) : ScanState()
}

class CameraScanViewModelFactory(private val repository: Repository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraScanViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CameraScanViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}