package com.keonhanh.app.ui.lobby

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.data.model.KeoStatus
import com.keonhanh.app.data.model.Member
import com.keonhanh.app.data.model.Message
import com.keonhanh.app.data.model.MyRole
import com.keonhanh.app.data.remote.KeoDoc
import com.keonhanh.app.data.remote.MemberDoc
import com.keonhanh.app.data.remote.MemberState
import com.keonhanh.app.data.remote.MessageDoc
import com.keonhanh.app.data.remote.toKeo
import com.keonhanh.app.data.remote.toMessage
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.ChatRepository
import com.keonhanh.app.data.repository.KeoRepository
import com.keonhanh.app.data.repository.MemberRepository
import com.keonhanh.app.data.repository.UserRepository
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.UiText
import com.keonhanh.app.ui.common.resolveMyName
import com.keonhanh.app.ui.common.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/** Nút chính ở đáy S15, theo bảng nút mục 3 tài liệu tuần 2. ViewModel chọn nút, Fragment chỉ vẽ. */
enum class PrimaryAction(@StringRes val label: Int, val enabled: Boolean) {
    JOIN(R.string.lobby_join, true),
    REQUEST_JOIN(R.string.lobby_request_join, true),
    CANCEL_REQUEST(R.string.lobby_cancel_request, true),
    LEAVE(R.string.lobby_leave, true),
    CANCEL_KEO(R.string.lobby_cancel, true),
    FULL(R.string.lobby_full, false),
    LOCKED(R.string.lobby_locked, false),
    LOCKED_MEMBER(R.string.lobby_locked_member, false),
    CHECKING_IN(R.string.keo_checking_in, false),
    ENDED(R.string.lobby_ended, false),
}

/** Mọi thứ S15 cần vẽ, đã tính sẵn. */
data class LobbyUi(
    val keo: Keo,
    /** null = mình chưa vào kèo; REQUESTED = đã bấm "Xin vào", đang chờ chủ kèo duyệt. */
    val myState: MemberState?,
    val primaryAction: PrimaryAction,
    /** Điểm uy tín của chủ kèo; null khi chưa đọc được hồ sơ thì ẩn phần "(uy tín ..%)". */
    val hostTrust: Int?,
    /** 0–100: thời gian đã trôi từ lúc tạo kèo tới hạn chốt. */
    val deadlineProgress: Int,
    /** Tin mới nhất để xem trước; null nếu phòng chưa có tin hoặc mình chưa vào kèo. */
    val latestMessage: Message?,
    /** Chỉ thành viên mới mở được chat (luật truy cập). */
    val canOpenChat: Boolean,
    /** Người đang xin vào; chỉ chủ kèo thấy. */
    val pendingRequests: List<Member>,
)

/** Việc chỉ xảy ra một lần (hiện Snackbar, đóng màn), khác với state được vẽ lại liên tục. */
sealed interface LobbyEvent {
    data class ShowMessage(val text: UiText) : LobbyEvent

    /** Kèo vừa bị chủ kèo hủy: quay lại màn trước. */
    data object Closed : LobbyEvent
}

/**
 * ViewModel của S15 Chi tiết kèo / phòng chờ (Phong Hào).
 *
 * Luồng dữ liệu:
 *   Firestore --(Repository, Flow realtime)--> ViewModel ghép lại thành LobbyUi --(StateFlow)--> LobbyFragment vẽ
 *   LobbyFragment bấm nút --> ViewModel gọi Repository (transaction) --> Firestore đổi --> Flow tự phát lại --> màn hình tự cập nhật
 * Vì vậy sau khi tham gia/rời kèo, ViewModel KHÔNG tự sửa danh sách thành viên: Firestore báo về là màn hình đổi theo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LobbyViewModel(
    private val keoId: String,
    private val keoRepository: KeoRepository,
    private val memberRepository: MemberRepository,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val myUid: String? get() = authRepository.currentUser?.uid

    // shareIn: nhiều chỗ bên dưới cùng dùng một listener Firestore, không mở 2 listener cho cùng dữ liệu.
    // Lỗi của listener (không có quyền, mất mạng) phải gói vào Result TRƯỚC shareIn:
    // ngoại lệ lọt ra khỏi shareIn rơi thẳng vào viewModelScope và làm crash cả app.
    private val keo: Flow<Result<KeoDoc?>> = keoRepository.observeKeo(keoId)
        .asResult()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    private val members: Flow<Result<List<MemberDoc>>> = memberRepository.observeMembers(keoId)
        .asResult()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** Điểm uy tín chủ kèo: đọc hồ sơ một lần khi biết hostId. */
    private val hostTrust: Flow<Int?> = keo
        .map { it.getOrNull()?.hostId }
        .distinctUntilChanged()
        .transformLatest { hostId ->
            // Phát null trước để màn hình hiện ngay; điểm uy tín hiện sau khi đọc xong hồ sơ chủ kèo
            emit(null)
            if (hostId != null) emit(userRepository.getUserProfile(hostId).getOrNull()?.trustPercent)
        }

    /** Tin mới nhất: chỉ nghe khi mình đã là thành viên, người ngoài không có quyền đọc chat. */
    private val latestMessage: Flow<MessageDoc?> = members
        .map { result -> result.getOrNull().orEmpty().any { it.uid == myUid && it.state == MemberState.JOINED } }
        .distinctUntilChanged()
        .flatMapLatest { joined ->
            if (joined) chatRepository.observeLatestMessage(keoId).catch { emit(null) } else flowOf(null)
        }

    /** Mỗi giây phát một lần để đồng hồ đếm ngược và trạng thái theo giờ (chốt, check-in) tự cập nhật. */
    private val clock: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val state: StateFlow<UiState<LobbyUi>> =
        combine(keo, members, hostTrust, latestMessage, clock) { keoResult, membersResult, trust, latest, now ->
            val error = keoResult.exceptionOrNull() ?: membersResult.exceptionOrNull()
            val keoDoc = keoResult.getOrNull()
            when {
                error != null -> UiState.Error(error.toUiText())
                keoDoc == null -> UiState.Error(UiText.Res(R.string.error_keo_not_found))
                else -> UiState.Success(buildUi(keoDoc, membersResult.getOrNull().orEmpty(), trust, latest, now))
            }
        }
            .catch { emit(UiState.Error(it.toUiText())) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    private val _busy = MutableStateFlow(false)

    /** true trong lúc đang gửi lên Firestore: Fragment khóa nút để không bấm 2 lần. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<LobbyEvent>(Channel.BUFFERED)
    val events: Flow<LobbyEvent> = _events.receiveAsFlow()

    private var myName: String? = null

    /** Fragment gọi khi bấm nút chính (sau khi người dùng đã xác nhận ở hộp thoại, nếu có). */
    fun onPrimaryAction() {
        val ui = (state.value as? UiState.Success)?.data ?: return
        when (ui.primaryAction) {
            PrimaryAction.JOIN -> runAction(R.string.lobby_joined) { uid ->
                memberRepository.join(keoId, uid, myDisplayName())
            }
            PrimaryAction.REQUEST_JOIN -> runAction(R.string.lobby_requested) { uid ->
                memberRepository.requestJoin(keoId, uid, myDisplayName())
            }
            PrimaryAction.LEAVE, PrimaryAction.CANCEL_REQUEST -> runAction(R.string.lobby_left) { uid ->
                memberRepository.leave(keoId, uid)
            }
            PrimaryAction.CANCEL_KEO -> runAction(R.string.lobby_cancelled, closeAfter = true) {
                keoRepository.deleteKeo(keoId)
            }
            else -> Unit
        }
    }

    /** Chủ kèo duyệt người xin vào (tạm thay cho S08). */
    fun approve(uid: String) = runAction(R.string.lobby_approved) { memberRepository.approve(keoId, uid) }

    /** Chủ kèo từ chối người xin vào. */
    fun reject(uid: String) = runAction(R.string.lobby_rejected) { memberRepository.reject(keoId, uid) }

    private fun buildUi(doc: KeoDoc, members: List<MemberDoc>, trust: Int?, latest: MessageDoc?, now: Long): LobbyUi {
        val keo = doc.toKeo(members, myUid, trust, nowMillis = now)
        val myState = members.firstOrNull { it.uid == myUid }?.state
        return LobbyUi(
            keo = keo,
            myState = myState,
            primaryAction = primaryActionFor(keo, myState),
            hostTrust = trust,
            deadlineProgress = deadlineProgress(doc, now),
            latestMessage = latest?.toMessage(myUid),
            canOpenChat = keo.myRole != MyRole.NONE,
            pendingRequests = if (keo.myRole == MyRole.HOST) {
                members.filter { it.state == MemberState.REQUESTED }.map { Member(it.uid, it.displayName) }
            } else {
                emptyList()
            },
        )
    }

    private fun deadlineProgress(doc: KeoDoc, now: Long): Int {
        val start = doc.createdAt?.toDate()?.time ?: return 0
        val end = doc.deadlineAt.toDate().time
        if (end <= start) return 100
        return ((now - start) * 100 / (end - start)).coerceIn(0, 100).toInt()
    }

    private suspend fun myDisplayName(): String =
        myName ?: resolveMyName(authRepository, userRepository).also { myName = it }

    /**
     * Chạy một thao tác ghi: khóa nút, gọi Repository, báo kết quả bằng Snackbar.
     * Lỗi nghiệp vụ (kèo đầy, đã chốt...) đã có câu thông báo sẵn trong AppException.
     */
    private fun runAction(@StringRes success: Int, closeAfter: Boolean = false, block: suspend (uid: String) -> Unit) {
        val uid = myUid
        if (uid == null) {
            _events.trySend(LobbyEvent.ShowMessage(UiText.Res(R.string.error_not_signed_in)))
            return
        }
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block(uid)
                _events.send(LobbyEvent.ShowMessage(UiText.Res(success)))
                if (closeAfter) _events.send(LobbyEvent.Closed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _events.send(LobbyEvent.ShowMessage(e.toUiText()))
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        /** Xoay màn hình thì Fragment ngừng collect vài trăm ms: giữ listener 5 giây để khỏi đọc lại từ đầu. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Bảng nút: trạng thái kèo (hàng) × vai trò của mình (cột).
 * Hàm thuần (không đụng Firestore, không đụng View) nên có unit test riêng: PrimaryActionTest.
 */
internal fun primaryActionFor(keo: Keo, myState: MemberState?): PrimaryAction = when (keo.status) {
    KeoStatus.OPEN, KeoStatus.FULL -> when {
        keo.myRole == MyRole.HOST -> PrimaryAction.CANCEL_KEO
        keo.myRole == MyRole.MEMBER -> PrimaryAction.LEAVE
        myState == MemberState.REQUESTED -> PrimaryAction.CANCEL_REQUEST
        keo.status == KeoStatus.FULL -> PrimaryAction.FULL
        keo.minTrust != null -> PrimaryAction.REQUEST_JOIN
        else -> PrimaryAction.JOIN
    }
    KeoStatus.LOCKED -> when (keo.myRole) {
        MyRole.HOST -> PrimaryAction.CANCEL_KEO
        MyRole.MEMBER -> PrimaryAction.LOCKED_MEMBER
        MyRole.NONE -> PrimaryAction.LOCKED
    }
    // Thành viên thấy thẻ check-in (Minh Khoa) thay cho nút này; người ngoài chỉ thấy nút mờ
    KeoStatus.CHECK_IN -> PrimaryAction.CHECKING_IN
    KeoStatus.ENDED -> PrimaryAction.ENDED
}

/** Gói lỗi của Flow vào Result để đi qua shareIn an toàn (xem ghi chú ở LobbyViewModel.keo). */
private fun <T> Flow<T>.asResult(): Flow<Result<T>> =
    map { Result.success(it) }.catch { emit(Result.failure(it)) }
