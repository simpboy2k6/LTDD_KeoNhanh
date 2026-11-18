package com.keonhanh.app.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.keonhanh.app.KeoNhanhApp
import com.keonhanh.app.data.model.KeoStatus
import com.keonhanh.app.data.remote.MemberRole
import com.keonhanh.app.data.remote.MemberState
import java.util.concurrent.TimeUnit

/**
 * Kiểm tra thông báo khi app KHÔNG ở trên màn hình (Phong Hào, tuần 4).
 *
 * WorkManager chạy việc này khoảng 15 phút một lần (mức tối thiểu Android cho phép), chỉ khi có mạng.
 * Mỗi lần chạy: đọc MỘT LẦN những gì mới sau mốc lần trước, báo, rồi ghi mốc mới.
 * Đây là giới hạn của gói Spark (không có server gửi push), nên thông báo nền có thể trễ tới 15 phút.
 *
 * Lưu ý khi thử: bấm Home rồi chờ. Đừng bấm Stop trong Android Studio: Android coi đó là buộc dừng
 * và chặn mọi việc chạy ngầm tới khi mở lại app.
 */
class NotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // App đang mở thì InAppNotifier đã báo rồi
        if (VisibleScreen.appVisible) return Result.success()

        val container = (applicationContext as KeoNhanhApp).container
        val uid = container.authRepository.currentUser?.uid ?: return Result.success()
        val store = NotifyStore(applicationContext)
        val startedAt = System.currentTimeMillis()
        val since = store.lastCheckMillis
        if (since <= 0L) {
            store.lastCheckMillis = startedAt
            return Result.success()
        }

        return try {
            val memberships = container.memberRepository.getMyMemberships(uid)
            val keos = container.keoRepository.getKeos(memberships.map { it.keoId }).associateBy { it.id }
            val active = memberships.filter { m ->
                m.member.state == MemberState.JOINED && keos[m.keoId]?.effectiveStatus(startedAt).let { it != null && it != KeoStatus.ENDED }
            }

            active.forEach { m ->
                val keo = keos.getValue(m.keoId)
                // Được duyệt: kèo cần xin vào và giờ vào (lúc duyệt) sau mốc
                if (m.member.role == MemberRole.MEMBER && keo.minTrust != null &&
                    (m.member.joinedAt?.toDate()?.time ?: 0L) > since
                ) {
                    NotificationHelper.notifyApproved(applicationContext, keo.id, keo.title)
                }
                // Chủ kèo: yêu cầu xin vào mới
                if (m.member.role == MemberRole.HOST) {
                    container.memberRepository.getMembers(keo.id)
                        .filter { it.state == MemberState.REQUESTED && (it.joinedAt?.toDate()?.time ?: 0L) > since }
                        .forEach { NotificationHelper.notifyJoinRequest(applicationContext, keo.id, keo.title, it.displayName) }
                }
                // Tin nhắn mới của người khác: gộp thành một thông báo mỗi kèo
                val messages = container.chatRepository.getMessagesSince(keo.id, since).filter { it.senderId != uid }
                messages.lastOrNull()?.let { latest ->
                    NotificationHelper.notifyMessages(applicationContext, keo.id, keo.title, latest, count = messages.size)
                }
            }

            ReminderScheduler.sync(applicationContext, active.map { keos.getValue(it.keoId) }, startedAt)
            store.lastCheckMillis = startedAt
            Result.success()
        } catch (e: Exception) {
            // Mất mạng giữa chừng: giữ mốc cũ, lần sau đọc lại từ mốc đó
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "notify_check"

        /** Gọi trong KeoNhanhApp.onCreate. KEEP: đã hẹn rồi thì giữ lịch cũ, không tạo thêm. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotifyWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                // Lần đầu chạy sau 15 phút: lúc vừa mở app thì InAppNotifier đang lo
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
