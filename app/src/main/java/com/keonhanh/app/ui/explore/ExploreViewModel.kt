package com.keonhanh.app.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.data.model.Sport
import com.keonhanh.app.data.remote.toKeo
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.KeoRepository
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ExploreViewModel(
    private val keoRepository: KeoRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _keoListState = MutableStateFlow<UiState<List<Keo>>>(UiState.Loading)
    val keoListState: StateFlow<UiState<List<Keo>>> = _keoListState

    private var allKeos: List<Keo> = emptyList()

    init {
        observeKeos()
    }

    private fun observeKeos() {
        viewModelScope.launch {
            _keoListState.value = UiState.Loading
            try {
                // Đã sửa thành authRepository.currentUser
                val myUid = authRepository.currentUser?.toString() ?: ""

                keoRepository.observeActiveKeos()
                    .map { keoDocs ->
                        keoDocs.map { doc ->
                            doc.toKeo(members = emptyList(), myUid = myUid, distanceKm = 1.5)
                        }
                    }
                    .catch { e ->
                        _keoListState.value = UiState.Error(e.toUiText())
                    }
                    .collect { keos ->
                        allKeos = keos
                        _keoListState.value = UiState.Success(keos)
                    }
            } catch (e: Exception) {
                _keoListState.value = UiState.Error(e.toUiText())
            }
        }
    }

    fun filterBySport(sport: Sport?) {
        val currentState = _keoListState.value
        if (currentState is UiState.Success || allKeos.isNotEmpty()) {
            val filtered = if (sport == null) {
                allKeos
            } else {
                allKeos.filter { it.sport == sport }
            }
            _keoListState.value = UiState.Success(filtered)
        }
    }
}