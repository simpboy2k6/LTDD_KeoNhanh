package com.keonhanh.app.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keonhanh.app.data.remote.GeocodingRepository
import com.keonhanh.app.data.remote.PlaceResult
import com.keonhanh.app.data.repository.LocationRepository
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PickLocationViewModel(
    private val geocodingRepository: GeocodingRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    // Trạng thái danh sách tìm kiếm địa chỉ
    private val _searchState = MutableStateFlow<UiState<List<PlaceResult>>>(UiState.Success(emptyList()))
    val searchState = _searchState.asStateFlow()

    // Trạng thái nút "Vị trí của tôi"
    private val _myLocationState = MutableStateFlow<UiState<PlaceResult?>>(UiState.Success(null))
    val myLocationState = _myLocationState.asStateFlow()

    fun searchAddress(query: String) {
        viewModelScope.launch {
            _searchState.value = UiState.Loading
            try {
                // Lấy vị trí gần nhất để ưu tiên tìm kiếm quanh khu vực người dùng (Đà Nẵng)
                val currentLoc = locationRepository.getLastKnownLocation()
                val results = geocodingRepository.searchAddress(
                    rawQuery = query,
                    userLocation = currentLoc
                )
                _searchState.value = UiState.Success(results)
            } catch (e: Exception) {
                _searchState.value = UiState.Error(e.toUiText())
            }
        }
    }

    fun fetchMyLocation() {
        viewModelScope.launch {
            _myLocationState.value = UiState.Loading
            try {
                val loc = locationRepository.getCurrentLocation()
                val place = PlaceResult(
                    name = "Vị trí hiện tại của tôi",
                    lat = loc.lat,
                    lng = loc.lng
                )
                _myLocationState.value = UiState.Success(place)
            } catch (e: Exception) {
                _myLocationState.value = UiState.Error(e.toUiText())
            }
        }
    }
}