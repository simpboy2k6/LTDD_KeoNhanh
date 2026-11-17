package com.keonhanh.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keonhanh.app.data.model.UserProfile
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.UserRepository
import com.keonhanh.app.ui.auth.AuthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. Định nghĩa các trạng thái UI
sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val profile: UserProfile) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _changeName = MutableStateFlow<String?>(null)
    val changeName: StateFlow<String?> = _changeName.asStateFlow()
    /*init {
        fetchCurrentUserProfile()
    }*/
    public fun updateUsername(name : String){
        val currentUser = authRepository.currentUser

        if (currentUser == null) {
            _uiState.value = ProfileUiState.Error("Người dùng chưa đăng nhập")
            return
        }
        viewModelScope.launch{
            val result = userRepository.ChangeUserName(name,currentUser.uid)

            result.onSuccess {
                _changeName.value = "Đã đổi tên thành công"
            }.onFailure { e->
                _changeName.value = e.message ?: "Có lỗi về mạng "
            }
        }
    }
    public fun fetchCurrentUserProfile() {
        // Bước 1: Lấy thông tin tài khoản đang đăng nhập
        val currentUser = authRepository.currentUser

        if (currentUser == null) {
            _uiState.value = ProfileUiState.Error("Người dùng chưa đăng nhập")
            return
        }

        // Bước 2: Dùng uid để kéo dữ liệu từ Firestore
        viewModelScope.launch {
            val result = userRepository.getUserProfile(currentUser.uid)

            result.onSuccess { userProfile ->
                if (userProfile != null) {
                    _uiState.value = ProfileUiState.Success(userProfile)
                } else {
                    _uiState.value = ProfileUiState.Error("Không tìm thấy hồ sơ người dùng")
                }
            }.onFailure { e ->
                _uiState.value = ProfileUiState.Error(e.message ?: "Lỗi kết nối cơ sở dữ liệu")
            }
        }
    }
    public fun resetChangeName(){
        _changeName.value = null;
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

