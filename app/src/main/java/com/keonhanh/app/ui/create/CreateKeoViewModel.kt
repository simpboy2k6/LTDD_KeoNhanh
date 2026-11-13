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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        deadlineAtString: String,
        capacity: Int,
        minTrust: Int?
    ) {
        viewModelScope.launch {
            _createState.value = UiState.Loading
            try {
                if (deadlineAtString.isBlank()) {
                    throw Exception("Vui lòng chọn hạn chốt")
                }

                val user = authRepository.currentUser ?: throw Exception("Chưa đăng nhập")
                val hostId = user.uid
                val hostName = user.displayName ?: "Unknown"

                // Parse hạn chốt
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val deadlineDate = dateFormat.parse(deadlineAtString) ?: throw Exception("Sai định dạng thời gian")

                // Kiểm tra hạn chốt phải lớn hơn giờ hiện tại
                if (deadlineDate.before(Date())) {
                    throw Exception("Hạn chốt phải nằm trong tương lai")
                }

                // Tự động tính Giờ hẹn (startAt) = Hạn chốt (deadlineAt) + 1 tiếng
                val calendar = Calendar.getInstance()
                calendar.time = deadlineDate
                calendar.add(Calendar.HOUR_OF_DAY, 1)
                val startDate = calendar.time

                val startTimestamp = com.google.firebase.Timestamp(startDate)
                val deadlineTimestamp = com.google.firebase.Timestamp(deadlineDate)

                val draft = KeoDraft(
                    title = title,
                    sport = sport,
                    placeName = placeName,
                    lat = lat,
                    lng = lng,
                    startAt = startTimestamp,
                    deadlineAt = deadlineTimestamp,
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