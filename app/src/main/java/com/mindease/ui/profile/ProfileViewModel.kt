package com.mindease.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mindease.data.Repository
import com.mindease.data.UserPreferences
import com.mindease.network.UpdateProfileRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: Repository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _profile = MutableStateFlow(ProfileState())
    val profile: StateFlow<ProfileState> = _profile.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _passwordChangeState = MutableStateFlow<PasswordChangeState>(PasswordChangeState.Idle)
    val passwordChangeState: StateFlow<PasswordChangeState> = _passwordChangeState.asStateFlow()

    init {
        loadProfile()
        loadHealthStats()
    }

    fun loadProfile() {
        viewModelScope.launch {
            val name = userPreferences.userNameFlow.first() ?: "User"
            val email = userPreferences.userEmailFlow.first() ?: ""
            val phone = userPreferences.userPhoneFlow.first() ?: ""
            val shift = userPreferences.shiftTypeFlow.first() ?: "day"
            _profile.value = _profile.value.copy(name = name, email = email, phone = phone, shiftType = shift)
            loadHealthStats()
        }
    }

    private fun loadHealthStats() {
        viewModelScope.launch {
            try {
                val token = userPreferences.authTokenFlow.first()
                val userId = userPreferences.userIdFlow.first()
                if (token.isNullOrBlank() || userId.isNullOrBlank()) return@launch

                val result = repository.getHistory("Bearer $token", userId)
                if (result.isSuccess) {
                    val history = result.getOrNull()?.history ?: emptyList()
                    val totalScans = history.size
                    val scores = history.mapNotNull { it.stress_score }
                    val avgScore = if (scores.isNotEmpty()) scores.average().toInt() else 0

                    _profile.value = _profile.value.copy(
                        totalScans = totalScans,
                        avgScore = avgScore
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun updateProfile(name: String, email: String, phone: String) {
        if (name.isBlank()) {
            _updateState.value = UpdateState.Error("Name cannot be empty")
            return
        }
        _updateState.value = UpdateState.Loading
        viewModelScope.launch {
            try {
                // Save locally immediately (optimistic update)
                userPreferences.saveProfile(name.trim(), email.trim(), phone.trim())
                _profile.value = ProfileState(name = name.trim(), email = email.trim(), phone = phone.trim())

                // Try to update backend if logged in
                val token = userPreferences.authTokenFlow.first()
                if (token != null) {
                    val result = repository.updateProfile(
                        "Bearer $token",
                        UpdateProfileRequest(name.trim(), email.trim(), phone.trim())
                    )
                    if (result.isSuccess) {
                        val resp = result.getOrNull()
                        if (resp?.error != null) {
                            _updateState.value = UpdateState.Error(resp.error)
                        } else {
                            _updateState.value = UpdateState.Success
                        }
                    } else {
                        // Still saved locally — partial success
                        _updateState.value = UpdateState.Success
                    }
                } else {
                    _updateState.value = UpdateState.Success
                }
            } catch (e: Exception) {
                // Local save succeeded, backend failed — still show success for UX
                _updateState.value = UpdateState.Success
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        if (oldPassword.isBlank()) {
            _passwordChangeState.value = PasswordChangeState.Error("Old password cannot be empty")
            return
        }
        if (newPassword.length < 6) {
            _passwordChangeState.value = PasswordChangeState.Error("New password must be at least 6 characters")
            return
        }
        if (newPassword != confirmPassword) {
            _passwordChangeState.value = PasswordChangeState.Error("New passwords don't match")
            return
        }

        _passwordChangeState.value = PasswordChangeState.Loading

        viewModelScope.launch {
            try {
                val token = userPreferences.authTokenFlow.first()
                if (token.isNullOrBlank()) {
                    _passwordChangeState.value = PasswordChangeState.Error("Not logged in")
                    return@launch
                }

                val result = repository.changePassword(
                    "Bearer $token",
                    com.mindease.network.ChangePasswordRequest(oldPassword, newPassword)
                )

                if (result.isSuccess) {
                    val resp = result.getOrNull()
                    if (resp?.detail != null) {
                        _passwordChangeState.value = PasswordChangeState.Error(resp.detail)
                    } else {
                        _passwordChangeState.value = PasswordChangeState.Success
                    }
                } else {
                    _passwordChangeState.value = PasswordChangeState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to change password"
                    )
                }
            } catch (e: Exception) {
                _passwordChangeState.value = PasswordChangeState.Error(e.message ?: "Something went wrong")
            }
        }
    }

    fun resetPasswordChangeState() {
        _passwordChangeState.value = PasswordChangeState.Idle
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    fun saveShiftType(type: String) {
        viewModelScope.launch {
            userPreferences.saveShiftType(type)
            _profile.value = _profile.value.copy(shiftType = type)
        }
    }
}

data class ProfileState(
    val name: String = "User",
    val email: String = "",
    val phone: String = "",
    val totalScans: Int = 0,
    val avgScore: Int = 0,
    val shiftType: String = "day"
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Loading : UpdateState()
    object Success : UpdateState()
    data class Error(val message: String) : UpdateState()
}

sealed class PasswordChangeState {
    object Idle : PasswordChangeState()
    object Loading : PasswordChangeState()
    object Success : PasswordChangeState()
    data class Error(val message: String) : PasswordChangeState()
}

class ProfileViewModelFactory(
    private val repository: Repository,
    private val userPreferences: UserPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(repository, userPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
