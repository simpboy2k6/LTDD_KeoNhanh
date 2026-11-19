package com.keonhanh.app.notify

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.keonhanh.app.R
import com.keonhanh.app.data.model.KeoStatus
import com.keonhanh.app.data.remote.KeoDoc
import com.keonhanh.app.data.remote.MemberDoc
import com.keonhanh.app.data.remote.MemberRole
import com.keonhanh.app.data.remote.MemberState
import com.keonhanh.app.data.remote.Membership
import com.keonhanh.app.data.repository.ChatRepository
import com.keonhanh.app.data.repository.KeoRepository
import com.keonhanh.app.data.repository.MemberRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Thông báo khi app đang mở trên màn hình (Phong Hào, tuần 4).
 *
 * MainActivity gọi [run] khi app hiện lên và hủy khi app rời màn hình (bấm Home, tắt màn hình).
 * Trong lúc chạy, nghe Firestore:
 *   1. Các dòng members của mình ở mọi kèo -> biết mình được duyệt (REQUESTED -> JOINED).
 *   2. Với mỗi kèo mình làm chủ: danh sách thành viên -> có yêu cầu "Xin vào" mới.
 *   3. Với mỗi kèo mình đã vào: tin nhắn mới nhất -> có tin mới của người khác.
 * Kèo đã kết thúc thì thôi nghe để đỡ tốn lượt đọc của gói Spark.
 *
 * Chỉ báo những gì xảy ra SAU mốc [NotifyStore.lastCheckMillis], nên lúc mở app không báo lại tin cũ.
 */
class InAppNotifier(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val memberRepository: MemberRepository,
    private val keoRepository: KeoRepository,
    private val chatRepository: ChatRepository,
) {
    private val keoCache = mutableMapOf<String, KeoDoc>()

    /** Chạy tới khi bị hủy. Đăng xuất thì dừng nghe, đăng nhập tài khoản khác thì nghe theo tài khoản mới. */
    suspend fun run() {
        auth.uidFlow().collectLatest { uid ->
            if (uid != null) watch(uid)
        }
    }

    private suspend fun watch(uid: String) = coroutineScope {
        val store = NotifyStore(context)
        // Lần đầu cài app chưa có mốc: lấy mốc là bây giờ, không báo dữ liệu cũ
        val since = store.lastCheckMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val requestJobs = mutableMapOf<String, Job>()
        val chatJobs = mutableMapOf<String, Job>()
        var previousStates: Map<String, MemberState>? = null

        memberRepository.observeMyMemberships(uid)
            .catch { Log.w(TAG, "Không nghe được kèo của mình (thiếu index members.uid?)", it) }
            .collect { memberships ->
                val now = System.currentTimeMillis()
                loadMissingKeos(memberships.map { it.keoId })

                notifyApprovals(memberships, previousStates, since)
                previousStates = memberships.associate { it.keoId to it.member.state }

                val active = memberships.filter { m ->
                    m.member.state == MemberState.JOINED && keoCache[m.keoId]?.effectiveStatus(now) != KeoStatus.ENDED
                }
                syncJobs(requestJobs, active.filter { it.member.role == MemberRole.HOST }.map { it.keoId }.toSet()) { keoId ->
                    launch { watchRequests(keoId, since) }
                }
                syncJobs(chatJobs, active.map { it.keoId }.toSet()) { keoId ->
                    launch { watchChat(keoId, uid, since) }
                }
                ReminderScheduler.sync(context, active.mapNotNull { keoCache[it.keoId] }, now)
            }
    }

    /**
     * Được duyệt: dòng của mình đổi từ REQUESTED sang JOINED trong lúc đang nghe,
     * hoặc (lần nhận đầu tiên) đã JOINED ở kèo cần xin vào và giờ vào sau mốc [since].
     */
    private fun notifyApprovals(memberships: List<Membership>, previous: Map<String, MemberState>?, since: Long) {
        memberships
            .filter { it.member.state == MemberState.JOINED && it.member.role == MemberRole.MEMBER }
            .filter { m ->
                if (previous != null) {
                    previous[m.keoId] == MemberState.REQUESTED
                } else {
                    keoCache[m.keoId]?.minTrust != null && m.member.joinedMillis() > since
                }
            }
            .filterNot { VisibleScreen.isShowing(R.id.lobbyFragment, it.keoId) }
            .forEach { NotificationHelper.notifyApproved(context, it.keoId, titleOf(it.keoId)) }
    }

    /** Chủ kèo: báo mỗi yêu cầu "Xin vào" mới; đang mở phòng chờ của kèo đó thì thôi (đã thấy nút duyệt). */
    private suspend fun watchRequests(keoId: String, since: Long) {
        var known: Set<String>? = null
        memberRepository.observeMembers(keoId)
            .catch { Log.w(TAG, "Không nghe được thành viên kèo $keoId", it) }
            .collect { members ->
                val requests = members.filter { it.state == MemberState.REQUESTED }
                val previous = known
                requests
                    .filter { r -> if (previous != null) r.uid !in previous else r.joinedMillis() > since }
                    .filterNot { VisibleScreen.isShowing(R.id.lobbyFragment, keoId) }
                    .forEach { NotificationHelper.notifyJoinRequest(context, keoId, titleOf(keoId), it.displayName) }
                known = requests.map { it.uid }.toSet()
            }
    }

    /** Thành viên: báo tin mới của người khác; đang mở đúng phòng chat đó thì thôi. */
    private suspend fun watchChat(keoId: String, uid: String, since: Long) {
        var newestSeen = since
        chatRepository.observeLatestMessage(keoId)
            .catch { Log.w(TAG, "Không nghe được chat kèo $keoId", it) }
            .collect { message ->
                val sentAt = message?.sentAt?.toDate()?.time ?: return@collect
                if (sentAt <= newestSeen) return@collect
                newestSeen = sentAt
                if (message.senderId != uid && !VisibleScreen.isShowing(R.id.lobbyChatFragment, keoId)) {
                    NotificationHelper.notifyMessages(context, keoId, titleOf(keoId), message)
                }
            }
    }

    private suspend fun loadMissingKeos(keoIds: List<String>) {
        val missing = keoIds.filter { it !in keoCache }
        if (missing.isEmpty()) return
        runCatching { keoRepository.getKeos(missing) }
            .onSuccess { keos -> keos.forEach { keoCache[it.id] = it } }
            .onFailure { Log.w(TAG, "Không đọc được thông tin kèo", it) }
    }

    private fun titleOf(keoId: String) = keoCache[keoId]?.title.orEmpty()

    /** Bật việc nghe cho kèo mới, dừng việc nghe của kèo không còn trong danh sách. */
    private fun CoroutineScope.syncJobs(jobs: MutableMap<String, Job>, wanted: Set<String>, start: CoroutineScope.(String) -> Job) {
        (jobs.keys - wanted).forEach { jobs.remove(it)?.cancel() }
        (wanted - jobs.keys).forEach { jobs[it] = start(it) }
    }

    private companion object {
        const val TAG = "InAppNotifier"
    }
}

private fun MemberDoc.joinedMillis(): Long = joinedAt?.toDate()?.time ?: 0L

/** uid của người đang đăng nhập; phát lại mỗi khi đăng nhập hoặc đăng xuất. */
private fun FirebaseAuth.uidFlow(): Flow<String?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}.distinctUntilChanged()
