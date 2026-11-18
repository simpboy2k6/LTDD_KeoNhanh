package com.keonhanh.app.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.data.remote.toKeo
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.KeoRepository
import com.keonhanh.app.data.repository.LocationRepository
import com.keonhanh.app.data.repository.UserLocation
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.toUiText
import com.keonhanh.app.util.StringUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

data class KeoFilter(
    val query: String = "",
    val sport: String = "",
    val radiusKm: Float = 3.0f,
    val timeRange: String = "" // "Hôm nay", "Ngày mai", "Cuối tuần"
)

class ExploreViewModel(
    private val authRepository: AuthRepository,
    private val keoRepository: KeoRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _keoListState = MutableStateFlow<UiState<List<Keo>>>(UiState.Loading)
    val keoListState = _keoListState.asStateFlow()

    private val _filterFlow = MutableStateFlow(KeoFilter())
    val filterFlow = _filterFlow.asStateFlow()

    // Lưu vị trí GPS dưới dạng StateFlow để có thể làm mới khi người dùng cấp quyền
    private val _myLocationFlow = MutableStateFlow<UserLocation?>(null)

    init {
        refreshLocation()
        observeKeos()
    }

    fun updateFilter(newFilter: KeoFilter) {
        _filterFlow.value = newFilter
    }

    // Gọi hàm này ở onResume hoặc sau khi xin quyền vị trí thành công
    fun refreshLocation() {
        viewModelScope.launch {
            val loc = locationRepository.getLastKnownLocation()
                ?: runCatching { locationRepository.getCurrentLocation() }.getOrNull()
            _myLocationFlow.value = loc
        }
    }

    private fun observeKeos() {
        viewModelScope.launch {
            _keoListState.value = UiState.Loading
            val myUid = authRepository.currentUser?.uid ?: ""

            combine(
                keoRepository.observeActiveKeos(),
                _filterFlow,
                _myLocationFlow
            ) { keoDocs, filter, myLocation ->
                keoDocs
                    .filter { doc -> matchTimeRange(doc.startAt?.toDate(), filter.timeRange) }
                    .mapNotNull { doc ->
                        val distKm: Double? = if (myLocation != null) {
                            myLocation.distanceTo(doc.lat, doc.lng) / 1000.0
                        } else {
                            null // Không có vị trí -> truyền null, không gán cứng 1.5
                        }

                        // Nếu có GPS thì lọc theo bán kính; không có GPS thì bỏ qua lọc bán kính
                        if (distKm != null && distKm > filter.radiusKm) {
                            return@mapNotNull null
                        }

                        doc.toKeo(members = emptyList(), myUid = myUid, distanceKm = distKm)
                    }
                    .filter { keo ->
                        val matchQuery = if (filter.query.isBlank()) {
                            true
                        } else {
                            val q = StringUtil.removeAccent(filter.query)
                            val titleMatch = StringUtil.removeAccent(keo.title).contains(q, ignoreCase = true)
                            val placeMatch = StringUtil.removeAccent(keo.place).contains(q, ignoreCase = true)
                            titleMatch || placeMatch
                        }
                        val matchSport = if (filter.sport.isBlank()) true else keo.sport.name == filter.sport
                        matchQuery && matchSport
                    }
                    .sortedBy { it.distanceKm ?: Double.MAX_VALUE }
            }
                .catch { e ->
                    _keoListState.value = UiState.Error(e.toUiText())
                }
                .collect { filteredKeos ->
                    // Luôn trả về UiState.Success (kể cả khi rỗng) để UI hiện màn trống "Không có kèo phù hợp"
                    _keoListState.value = UiState.Success(filteredKeos)
                }
        }
    }

    private fun matchTimeRange(startDate: Date?, timeRange: String): Boolean {
        if (timeRange.isBlank() || startDate == null) return true

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = startDate }

        return when (timeRange) {
            "Hôm nay" -> {
                now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
            }
            "Ngày mai" -> {
                val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
                tomorrow.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                        tomorrow.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
            }
            "Cuối tuần" -> {
                val dayOfWeek = target.get(Calendar.DAY_OF_WEEK)
                dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
            }
            else -> true
        }
    }
}