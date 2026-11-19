package com.keonhanh.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keonhanh.app.R
import com.keonhanh.app.data.model.UserProfile
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.UserRepository
import com.keonhanh.app.ui.common.UiText
import com.keonhanh.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. Định nghĩa các trạng thái UI
sealed class ProfileUiState {
    data object Loading : ProfileUiState()
    data class Success(val profile: UserProfile) : ProfileUiState()
    data class Error(val message: UiText) : ProfileUiState()
}

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _changeName = MutableStateFlow<UiText?>(null)
    val changeName: StateFlow<UiText?> = _changeName.asStateFlow()

    fun signOut() {
        authRepository.signOut()
    }

    fun updateUsername(name: String) {
        val currentUser = authRepository.currentUser
        if (currentUser == null) {
            _changeName.value = UiText.Res(R.string.error_not_signed_in)
            return
        }

        val trimmed = name.trim()
        if (trimmed.length !in 2..40) {
            _changeName.value = UiText.Res(R.string.error_name_length)
            return
        }

        viewModelScope.launch {
            val result = userRepository.ChangeUserName(trimmed, currentUser.uid)
            result.onSuccess {
                _changeName.value = UiText.Res(R.string.profile_name_changed)
                fetchCurrentUserProfile()
            }.onFailure { e ->
                _changeName.value = e.toUiText()
            }
        }
    }

    fun fetchCurrentUserProfile() {
        val currentUser = authRepository.currentUser
        if (currentUser == null) {
            _uiState.value = ProfileUiState.Error(UiText.Res(R.string.error_not_signed_in))
            return
        }

        viewModelScope.launch {
            val result = userRepository.getUserProfile(currentUser.uid)
            result.onSuccess { userProfile ->
                if (userProfile != null) {
                    _uiState.value = ProfileUiState.Success(userProfile)
                } else {
                    _uiState.value = ProfileUiState.Error(UiText.Res(R.string.err_user_not_found))
                }
            }.onFailure { e ->
                _uiState.value = ProfileUiState.Error(e.toUiText())
            }
        }
    }

    fun resetChangeName() {
        _changeName.value = null
    }

    class ProfileViewModelFactory(
        private val authRepository: AuthRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ProfileViewModel(authRepository, userRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

