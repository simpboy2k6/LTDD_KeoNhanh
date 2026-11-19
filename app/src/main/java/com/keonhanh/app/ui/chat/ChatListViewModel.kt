package com.keonhanh.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keonhanh.app.R
import com.keonhanh.app.data.model.ChatRoom
import com.keonhanh.app.data.remote.KeoDoc
import com.keonhanh.app.data.remote.MemberState
import com.keonhanh.app.data.remote.MessageDoc
import com.keonhanh.app.data.remote.toKeo
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.ChatRepository
import com.keonhanh.app.data.repository.KeoRepository
import com.keonhanh.app.data.repository.MemberRepository
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.UiText
import com.keonhanh.app.ui.common.toUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ViewModel của S18 Danh sách chat (Phong Hào, tuần 4). Thay cho SampleData.chatRooms.
 *
 * 1. Nghe các dòng members của mình ở mọi kèo (giống Kèo của tôi), chỉ lấy kèo đã vào (JOINED):
 *    người đang chờ duyệt chưa đọc được chat (luật truy cập chặn).
 * 2. Đọc thông tin các kèo đó một lần (getKeos).
 * 3. Với mỗi kèo, nghe tin nhắn mới nhất; kèo có tin mới nhất đứng đầu, kèo chưa có tin xếp cuối.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModel(
    memberRepository: MemberRepository,
    private val keoRepository: KeoRepository,
    private val chatRepository: ChatRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val state: StateFlow<UiState<List<ChatRoom>>> = run {
        val uid = authRepository.currentUser?.uid
        val source: Flow<UiState<List<ChatRoom>>> = if (uid == null) {
            flowOf(UiState.Error(UiText.Res(R.string.error_not_signed_in)))
        } else {
            memberRepository.observeMyMemberships(uid)
                .map { memberships ->
                    memberships.filter { it.member.state == MemberState.JOINED }.map { it.keoId }.toSet()
                }
                // Chỉ đọc lại kèo khi danh sách kèo đổi, không phải mỗi lần một dòng thành viên đổi
                .distinctUntilChanged()
                .flatMapLatest { keoIds -> roomsOf(keoIds, uid) }
                .map { UiState.Success(it) }
        }
        source
            .catch { emit(UiState.Error(it.toUiText())) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
    }

    private fun roomsOf(keoIds: Set<String>, uid: String): Flow<List<ChatRoom>> = flow {
        val keos = if (keoIds.isEmpty()) emptyList() else keoRepository.getKeos(keoIds.toList())
        if (keos.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        // combine: mỗi khi một phòng có tin mới, cả danh sách được sắp lại
        val latest = keos.map { keo -> chatRepository.observeLatestMessage(keo.id).map { keo to it } }
        emitAll(
            combine(latest) { rooms ->
                val now = System.currentTimeMillis()
                rooms.sortedByDescending { (keo, message) -> sortTime(keo, message) }
                    .map { (keo, message) -> toChatRoom(keo, message, uid, now) }
            },
        )
    }

    private fun sortTime(keo: KeoDoc, message: MessageDoc?): Long =
        message?.sentAt?.toDate()?.time
            // Phòng chưa có tin: xếp sau mọi phòng có tin, kèo mới tạo đứng trước
            ?: ((keo.createdAt?.toDate()?.time ?: 0L) - NO_MESSAGE_OFFSET)

    private fun toChatRoom(keo: KeoDoc, message: MessageDoc?, uid: String, now: Long) = ChatRoom(
        keo = keo.toKeo(members = emptyList(), myUid = uid, nowMillis = now),
        lastMessage = message?.text.orEmpty(),
        time = message?.sentAt?.let { formatChatTime(it.toDate().time, now) }.orEmpty(),
        senderName = message?.senderName.orEmpty(),
        isMine = message?.senderId == uid,
    )

    private companion object {
        /** Đẩy phòng chưa có tin xuống dưới phòng có tin (khoảng 30 năm tính bằng ms). */
        const val NO_MESSAGE_OFFSET = 1_000_000_000_000L
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM")

/** Tin hôm nay hiện giờ "19:30", tin ngày khác hiện ngày "12/11". */
internal fun formatChatTime(sentAtMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val sent = Instant.ofEpochMilli(sentAtMillis).atZone(zone)
    // LocalDate.ofInstant cần Android 12 trở lên; app chạy từ Android 8 nên đi qua atZone
    val today: LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return if (sent.toLocalDate() == today) timeFormat.format(sent) else dateFormat.format(sent)
}
