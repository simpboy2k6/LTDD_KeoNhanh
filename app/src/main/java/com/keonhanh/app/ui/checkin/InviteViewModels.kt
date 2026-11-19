package com.keonhanh.app.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keonhanh.app.R
import com.keonhanh.app.data.remote.KeoDoc
import com.keonhanh.app.data.repository.KeoRepository
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.UiText
import com.keonhanh.app.ui.common.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * S06 Mã mời (Phong Hào, tuần 4): đọc kèo thật từ Firestore thay cho SampleData.findKeo.
 * Nghe realtime nên số người trên thẻ tự đổi khi có người vào bằng mã.
 */
class InviteQrViewModel(
    keoId: String,
    keoRepository: KeoRepository,
) : ViewModel() {

    val state: StateFlow<UiState<KeoDoc>> = keoRepository.observeKeo(keoId)
        .map { keo -> if (keo == null) UiState.Error(UiText.Res(R.string.error_keo_not_found)) else UiState.Success(keo) }
        .catch { emit(UiState.Error(it.toUiText())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}

/** Kết quả tìm kèo theo mã ở S07. */
sealed interface ScanEvent {
    data class Open(val keoId: String) : ScanEvent

    /** [fromCamera]: lỗi của mã quét được thì báo bằng Snackbar, lỗi của mã gõ tay thì báo dưới ô nhập. */
    data class Error(val message: UiText, val fromCamera: Boolean) : ScanEvent
}

/**
 * S07 Quét mã (Phong Hào, tuần 4): kiểm định dạng mã, tìm kèo bằng findByInviteCode, rồi báo Fragment mở S15.
 * Trong lúc tìm thì khóa nút để không bấm hai lần.
 */
class ScanQrViewModel(
    private val keoRepository: KeoRepository,
) : ViewModel() {

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _events = Channel<ScanEvent>(Channel.BUFFERED)
    val events: Flow<ScanEvent> = _events.receiveAsFlow()

    fun open(raw: String, fromCamera: Boolean) {
        val code = InviteLink.parse(raw)
        if (code == null) {
            _events.trySend(ScanEvent.Error(UiText.Res(R.string.scan_code_invalid), fromCamera))
            return
        }
        if (_searching.value) return
        viewModelScope.launch {
            _searching.value = true
            try {
                val keo = keoRepository.findByInviteCode(code)
                _events.send(
                    if (keo == null) {
                        ScanEvent.Error(UiText.Res(R.string.scan_code_not_found), fromCamera)
                    } else {
                        ScanEvent.Open(keo.id)
                    },
                )
            } catch (e: Exception) {
                _events.send(ScanEvent.Error(e.toUiText(), fromCamera))
            } finally {
                _searching.value = false
            }
        }
    }
}
