package com.keonhanh.app.ui.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Message
import com.keonhanh.app.data.remote.toMessage
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.ChatRepository
import com.keonhanh.app.data.repository.KeoRepository
import com.keonhanh.app.data.repository.UserRepository
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.UiText
import com.keonhanh.app.ui.common.resolveMyName
import com.keonhanh.app.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUi(
    val title: String,
    val memberCount: Int,
    val messages: List<Message>,
)

sealed interface ChatEvent {
    data class ShowMessage(val text: UiText) : ChatEvent

    /** Gửi lỗi: trả lại chữ vào ô nhập để người dùng không phải gõ lại. */
    data class RestoreDraft(val text: String) : ChatEvent
}

/**
 * ViewModel của S16 Chat phòng chờ (Phong Hào).
 * Gửi tin xong không tự thêm vào danh sách: listener Firestore báo về tin mới (kể cả tin của mình) và màn hình tự vẽ.
 */
class ChatViewModel(
    private val keoId: String,
    keoRepository: KeoRepository,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val myUid: String? get() = authRepository.currentUser?.uid

    val state: StateFlow<UiState<ChatUi>> =
        combine(keoRepository.observeKeo(keoId), chatRepository.observeMessages(keoId)) { keo, messages ->
            if (keo == null) {
                UiState.Error(UiText.Res(R.string.error_keo_not_found))
            } else {
                UiState.Success(ChatUi(keo.title, keo.memberCount, messages.map { it.toMessage(myUid) }))
            }
        }
            .catch { emit(UiState.Error(it.toUiText())) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _events = Channel<ChatEvent>(Channel.BUFFERED)
    val events: Flow<ChatEvent> = _events.receiveAsFlow()

    private var myName: String? = null

    fun send(text: String) {
        val uid = myUid ?: return report(UiText.Res(R.string.error_not_signed_in))
        viewModelScope.launch {
            try {
                val name = myName ?: resolveMyName(authRepository, userRepository).also { myName = it }
                chatRepository.send(keoId, uid, name, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _events.send(ChatEvent.ShowMessage(e.toUiText()))
                _events.send(ChatEvent.RestoreDraft(text))
            }
        }
    }

    /** Chỉ xóa được tin của mình (luật truy cập cũng chặn nếu cố xóa tin người khác). */
    fun delete(messageId: String) {
        viewModelScope.launch {
            try {
                chatRepository.delete(keoId, messageId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _events.send(ChatEvent.ShowMessage(e.toUiText()))
            }
        }
    }

    private fun report(text: UiText) {
        _events.trySend(ChatEvent.ShowMessage(text))
    }
}
