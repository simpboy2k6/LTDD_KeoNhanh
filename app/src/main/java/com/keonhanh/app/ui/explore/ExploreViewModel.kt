package com.keonhanh.app.ui.explore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.data.remote.toKeo
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.KeoRepository
import com.keonhanh.app.data.repository.LocationRepository
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.UiText
import com.keonhanh.app.ui.common.toUiText
// Nhớ import các class liên quan như AuthRepository, KeoRepository, UiState, v.v.
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import com.keonhanh.app.util.StringUtil // Thêm import này sau khi bạn tạo StringUtil

// 1. Tạo class KeoFilter để lưu trữ trạng thái của bộ lọc
data class KeoFilter(
    val query: String = "",
    val sport: String = "",
    val radiusKm: Float = 5.0f, // Đặt giá trị mặc định là 5km
    val timeRange: String = ""  // "Hôm nay", "Ngày mai", "Cuối tuần"
)

class ExploreViewModel(
    private val authRepository: AuthRepository,
    private val keoRepository: KeoRepository,
    private val locationRepository: LocationRepository // Inject LocationRepository vào đây
) : ViewModel() {

    // 2. Khai báo StateFlow cho UiState và Bộ lọc
    private val _keoListState = MutableStateFlow<UiState<List<Keo>>>(UiState.Loading)
    val keoListState = _keoListState.asStateFlow()

    private val _filterFlow = MutableStateFlow(KeoFilter())
    val filterFlow = _filterFlow.asStateFlow()

    init {
        observeKeos()
    }

    // Hàm này sẽ được gọi từ UI (BottomSheet, Thanh tìm kiếm) để cập nhật bộ lọc
    fun updateFilter(newFilter: KeoFilter) {
        _filterFlow.value = newFilter
    }

    // 3. Cập nhật hàm observeKeos() sử dụng combine và tính khoảng cách
    private fun observeKeos() {
        viewModelScope.launch {
            _keoListState.value = UiState.Loading

            try {
                // Sửa lại cách lấy ID người dùng
                val myUid = authRepository.currentUser?.uid ?: ""

                // Lấy vị trí gần nhất từ LocationRepository do Minh Khoa viết
                val myLocation = locationRepository.getLastKnownLocation()

                // Kết hợp luồng dữ liệu từ Firestore và luồng bộ lọc
                keoRepository.observeActiveKeos()
                    .combine(_filterFlow) { keoDocs, filter ->

                        // 1. Khai báo rõ kiểu trả về là List<Keo> để trình biên dịch hiểu
                        // ... (bên trong khối combine)
                        val mappedKeos: List<Keo> = keoDocs.map { doc ->
                            var distKm = 1.5 // Đổi từ 1.5f (Float) thành 1.5 (Double)

                            if (myLocation != null) {
                                val docLat = doc.lat
                                val docLng = doc.lng

                                val distanceMeters = myLocation.distanceTo(docLat, docLng)
                                // Bỏ đuôi .toFloat(), để nguyên phép chia tạo ra kiểu Double
                                distKm = distanceMeters / 1000.0
                            }

                            // Gọi hàm toKeo, truyền khoảng cách kiểu Double
                            doc.toKeo(members = emptyList(), myUid = myUid, distanceKm = distKm)
                        }

                        // 2. Lọc dữ liệu trên thiết bị
                        mappedKeos.filter { keo ->
                            val matchQuery = if (filter.query.isBlank()) {
                                true
                            } else {
                                // Đảm bảo bạn đã tạo object StringUtil như mình hướng dẫn ở bước trước
                                val normalizedTitle = StringUtil.removeAccent(keo.title)
                                val normalizedQuery = StringUtil.removeAccent(filter.query)
                                normalizedTitle.contains(normalizedQuery, ignoreCase = true)
                            }

                            val matchSport = if (filter.sport.isBlank()) true else keo.sport.name == filter.sport
                            val matchRadius = keo.distanceKm <= filter.radiusKm

                            matchQuery && matchSport && matchRadius
                        }
                    }
                    .catch { e ->
                        _keoListState.value = UiState.Error(e.toUiText())
                    }
                    .collect { filteredKeos ->
                        if (filteredKeos.isEmpty()) {
                            // Sử dụng UiText.Res và truyền vào ID string đã khai báo
                            _keoListState.value = UiState.Error(UiText.Res(R.string.error_empty_keo))
                        } else {
                            _keoListState.value = UiState.Success(filteredKeos)
                        }
                    }

            } catch (e: Exception) {
                _keoListState.value = UiState.Error(e.toUiText())
            }
        }
    }
}