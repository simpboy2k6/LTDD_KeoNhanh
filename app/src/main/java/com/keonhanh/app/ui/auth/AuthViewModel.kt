package com.keonhanh.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keonhanh.app.data.model.UserProfile
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.Int

// 1. Khai báo các trạng thái của màn hình (UI State)
sealed class AuthUiState {
    object Idle : AuthUiState()                  // Trạng thái chờ (chưa làm gì)
    object Loading : AuthUiState()               // Đang xoay vòng tải
    object Success : AuthUiState()               // Đăng nhập/Đăng ký thành công
    data class Error(val message: String) : AuthUiState() // Có lỗi xảy ra
}

// 2. ViewModel chính
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Hàm xử lý Đăng nhập (Cho màn hình S02)
    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Vui lòng nhập đầy đủ thông tin")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = authRepository.signIn(email, pass)

            result.onSuccess {
                _uiState.value = AuthUiState.Success
            }.onFailure { e ->
                _uiState.value = AuthUiState.Error(e.message ?: "Đăng nhập thất bại")
            }
        }
    }

    // Hàm xử lý Đăng ký (Cho màn hình S03)
    fun register(email: String, pass: String, fullName: String) {
        if (email.isBlank() || pass.isBlank() || fullName.isBlank()) {
            _uiState.value = AuthUiState.Error("Vui lòng điền đủ các trường")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            // Bước 1: Tạo tài khoản trên Firebase Auth
            val authResult = authRepository.signUp(email, pass)

            authResult.onSuccess { firebaseUser ->
                // Bước 2: Tạo UserProfile với uid vừa nhận được
                val newProfile = UserProfile(
                    uid = firebaseUser.uid,
                    email = email,
                    name = fullName,
                    attended = 0,
                    committed = 0,
                    lateCancels = 0,
                    hosted = 0
                )

                // Bước 3: Lưu lên Firestore
                val dbResult = userRepository.createUserProfile(newProfile)

                dbResult.onSuccess {
                    _uiState.value = AuthUiState.Success
                }.onFailure { e ->
                    // Lỗi khi lưu vào database (mạng yếu, v.v.)
                    _uiState.value = AuthUiState.Error(e.message ?: "Lỗi tạo hồ sơ dữ liệu")
                }

            }.onFailure { e ->
                // Lỗi khi tạo tài khoản (email đã tồn tại, pass quá ngắn...)
                _uiState.value = AuthUiState.Error(e.message ?: "Đăng ký thất bại")
            }
        }
    }

    // Đưa trạng thái về Idle sau khi hiển thị lỗi xong
    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}

// 3. Factory để khởi tạo ViewModel (Vì ViewModel này cần truyền tham số Repository)
class AuthViewModelFactory(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(authRepository, userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}