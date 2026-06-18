package com.mindease.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mindease.data.Repository
import com.mindease.network.AuthRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: Repository) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthState>(AuthState.Idle)
    val uiState: StateFlow<AuthState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        _uiState.value = AuthState.Loading

        viewModelScope.launch {
            val request = AuthRequest(
                email = email.trim(),
                password = password
            )

            val result = repository.login(request)

            _uiState.value = if (result.isSuccess) {
                val resp = result.getOrNull()

                if (resp?.token != null && resp.user_id != null) {
                    // ✅ Session already saved in Repository
                    AuthState.Success
                } else {
                    AuthState.Error("Invalid server response. Token missing.")
                }

            } else {
                val msg = result.exceptionOrNull()?.message
                    ?: "Network error. Please check your connection."
                AuthState.Error(msg)
            }
        }
    }

    fun signup(name: String, email: String, password: String) {
        _uiState.value = AuthState.Loading

        viewModelScope.launch {
            // Save the name locally BEFORE calling the API
            // This ensures we have it even if backend returns empty
            repository.saveSignupName(name.trim())

            val request = AuthRequest(
                name = name.trim(),
                email = email.trim(),
                password = password
            )

            val result = repository.signup(request)

            _uiState.value = if (result.isSuccess) {
                val resp = result.getOrNull()

                when {
                    // Flow 1: OTP verification required (SMTP worked)
                    resp?.message?.contains("Verification code sent") == true -> {
                        AuthState.OtpSent
                    }
                    // Flow 2: Account created directly (SMTP failed, fallback)
                    resp?.token != null && resp.user_id != null -> {
                        AuthState.Success
                    }
                    else -> {
                        AuthState.Error("Signup failed. Please try again.")
                    }
                }

            } else {
                val msg = result.exceptionOrNull()?.message
                    ?: "Network error. Please check your connection."
                AuthState.Error(msg)
            }
        }
    }

    fun verifyEmail(email: String, otp: String) {
        _uiState.value = AuthState.Loading

        viewModelScope.launch {
            val request = com.mindease.network.VerifyEmailRequest(
                email = email.trim(),
                otp_code = otp.trim()
            )

            val result = repository.verifyEmail(request)

            _uiState.value = if (result.isSuccess) {
                val resp = result.getOrNull()

                // Since we also login instantly in our modified backend, we expect success
                if (resp?.token != null || resp?.message?.contains("verified") == true) {
                    AuthState.Success
                } else {
                    AuthState.Error("Verification failed.")
                }
            } else {
                val msg = result.exceptionOrNull()?.message
                    ?: "Invalid verification code."
                AuthState.Error(msg)
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthState.Idle
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object OtpSent : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModelFactory(
    private val repository: Repository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}