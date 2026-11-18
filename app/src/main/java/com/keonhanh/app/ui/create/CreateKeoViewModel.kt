package com.keonhanh.app.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Sport
import com.keonhanh.app.data.remote.KeoDraft
import com.keonhanh.app.data.repository.AppException
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.KeoRepository
import com.keonhanh.app.data.repository.UserRepository
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class CreateKeoViewModel(
    private val keoRepository: KeoRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _createState = MutableStateFlow<UiState<String>?>(null)
    val createState: StateFlow<UiState<String>?> = _createState

    fun createKeo(
        rawTitle: String,
        sport: Sport,
        rawPlaceName: String,
        lat: Double?,
        lng: Double?,
        startAtString: String,
        deadlineAtString: String,
        capacity: Int,
        minTrust: Int?
    ) {
        viewModelScope.launch {
            _createState.value = UiState.Loading
            try {
                val title = rawTitle.trim()
                val placeName = rawPlaceName.trim()

                // Kiểm tra đầu vào lớp ViewModel theo bảng mục 5
                if (title.length !in 3..60) {
                    throw AppException(R.string.error_keo_title_length)
                }
                if (placeName.isBlank() || lat == null || lng == null) {
                    throw AppException(R.string.error_keo_place_required)
                }
                if (capacity !in 2..30) {
                    throw AppException(R.string.error_keo_capacity)
                }
                if (minTrust != null && minTrust !in 0..100) {
                    throw AppException(R.string.error_keo_trust_range)
                }
                if (deadlineAtString.isBlank()) {
                    throw AppException(R.string.error_keo_deadline_required)
                }
                if (startAtString.isBlank()) {
                    throw AppException(R.string.error_keo_start_required)
                }

                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val deadlineDate = dateFormat.parse(deadlineAtString)
                    ?: throw AppException(R.string.error_keo_deadline_required)
                val startDate = dateFormat.parse(startAtString)
                    ?: throw AppException(R.string.error_keo_start_required)

                // Hạn chốt phải cách hiện tại ít nhất 15 phút
                val minDeadlineMillis = System.currentTimeMillis() + 15 * 60 * 1000L
                if (deadlineDate.time < minDeadlineMillis) {
                    throw AppException(R.string.error_keo_deadline_min)
                }

                // Giờ hẹn phải sau hạn chốt
                if (!startDate.after(deadlineDate)) {
                    throw AppException(R.string.error_keo_start_after_deadline)
                }

                val user = authRepository.currentUser ?: throw AppException(R.string.error_not_signed_in)
                val hostId = user.uid

                // Lấy họ tên thật từ hồ sơ users/{uid}
                // Lấy họ tên thật từ hồ sơ users/{uid}
                val profile = userRepository.getUserProfile(hostId).getOrNull()
                val hostName = profile?.name?.takeIf { it.isNotBlank() }
                    ?: user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@")
                    ?: "Thành viên"

                val draft = KeoDraft(
                    title = title,
                    sport = sport,
                    placeName = placeName,
                    lat = lat,
                    lng = lng,
                    startAt = Timestamp(startDate),
                    deadlineAt = Timestamp(deadlineDate),
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