package com.keonhanh.app.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keonhanh.app.data.model.Sport
import com.keonhanh.app.data.remote.KeoDraft
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.KeoRepository
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CreateKeoViewModel(
    private val keoRepository: KeoRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _createState = MutableStateFlow<UiState<String>?>(null)
    val createState: StateFlow<UiState<String>?> = _createState

    fun createKeo(
        title: String,
        sport: Sport,
        placeName: String,
        lat: Double,
        lng: Double,
        startAtString: String,
        deadlineAtString: String,
        capacity: Int,
        minTrust: Int?
    ) {
        viewModelScope.launch {
            _createState.value = UiState.Loading
            try {
                // Đã sửa thành authRepository.currentUser
                val user = authRepository.currentUser ?: throw Exception("Chưa đăng nhập")
                val hostId = user.uid
                val hostName = user.displayName ?: "Unknown"

                val startAt = com.google.firebase.Timestamp.now()
                val deadlineAt = com.google.firebase.Timestamp.now()

                val draft = KeoDraft(
                    title = title,
                    sport = sport,
                    placeName = placeName,
                    lat = lat,
                    lng = lng,
                    startAt = com.google.firebase.Timestamp.now(),     // Đổi từ tham số String sang Timestamp
                    deadlineAt = com.google.firebase.Timestamp.now(),  // Đổi từ tham số String sang Timestamp
                    capacity = capacity,
                    minTrust = minTrust
                )

                val keoId = keoRepository.createKeo(draft, hostId, hostName)
                _createState.value = UiState.Success(keoId)
            } catch (e: Exception) {
                _createState.value = UiState.Error(e.toUiText())
            }
        }
    }
}