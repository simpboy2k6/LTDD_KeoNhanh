package com.keonhanh.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.Timestamp
import com.keonhanh.app.KeoNhanhApp
import com.keonhanh.app.data.remote.KeoDoc
import com.keonhanh.app.data.remote.MemberState
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Nhắc giờ kèo (Phong Hào, tuần 4).
 *
 * Không gọi hàm hẹn nhắc ở từng nút "Tham gia", "Duyệt", "Tạo kèo", "Rời kèo"...
 * Thay vào đó, mỗi khi danh sách kèo của mình đổi (InAppNotifier, NotifyWorker), gọi [sync] với các kèo đang ở:
 *   - kèo mới vào (tự vào, được duyệt, vừa tạo) -> hẹn nhắc trước giờ hẹn 30 phút
 *   - kèo đổi giờ hẹn -> hẹn lại
 *   - rời kèo, bị hủy kèo -> không còn trong danh sách -> hủy lịch nhắc
 */
object ReminderScheduler {

    const val REMIND_BEFORE_MINUTES = 30L

    /**
     * Còn bao lâu nữa thì nhắc. null nếu kèo đã bắt đầu (không nhắc nữa).
     * Vào kèo khi chỉ còn dưới 30 phút thì nhắc ngay (0).
     */
    fun delayMillis(startAtMillis: Long, nowMillis: Long): Long? {
        if (startAtMillis <= nowMillis) return null
        return (startAtMillis - TimeUnit.MINUTES.toMillis(REMIND_BEFORE_MINUTES) - nowMillis).coerceAtLeast(0L)
    }

    fun sync(context: Context, myActiveKeos: List<KeoDoc>, nowMillis: Long) {
        val workManager = WorkManager.getInstance(context)
        val store = NotifyStore(context)
        val scheduled = store.reminders
        val wanted = mutableSetOf<String>()

        myActiveKeos.forEach { keo ->
            val startMillis = keo.startAt.toDate().time
            val delay = delayMillis(startMillis, nowMillis) ?: return@forEach
            val entry = "${keo.id}|$startMillis"
            wanted += entry
            if (entry in scheduled) return@forEach // đã hẹn đúng giờ này rồi

            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        ReminderWorker.KEY_KEO_ID to keo.id,
                        ReminderWorker.KEY_TITLE to keo.title,
                        ReminderWorker.KEY_PLACE to keo.placeName,
                        ReminderWorker.KEY_START_AT to startMillis,
                    ),
                )
                .build()
            // REPLACE: kèo đổi giờ hẹn thì lịch cũ bị thay bằng lịch mới
            workManager.enqueueUniqueWork(workName(keo.id), ExistingWorkPolicy.REPLACE, request)
        }

        val wantedKeoIds = wanted.map { it.substringBefore('|') }.toSet()
        scheduled.map { it.substringBefore('|') }
            .filter { it !in wantedKeoIds }
            .forEach { workManager.cancelUniqueWork(workName(it)) }
        store.reminders = wanted
    }

    private fun workName(keoId: String) = "reminder_$keoId"
}

/** Chạy đúng lúc trước giờ hẹn 30 phút, kể cả khi app đã tắt (WorkManager tự mở lại process). */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val keoId = inputData.getString(KEY_KEO_ID) ?: return Result.success()
        val container = (applicationContext as KeoNhanhApp).container
        val uid = container.authRepository.currentUser?.uid ?: return Result.success()

        // Kiểm lại trước khi báo: kèo có thể đã bị hủy hoặc mình đã rời kèo sau lúc hẹn nhắc
        val checked = runCatching {
            val keo = container.keoRepository.getKeos(listOf(keoId)).firstOrNull()
            val stillMember = container.memberRepository.getMembers(keoId)
                .any { it.uid == uid && it.state == MemberState.JOINED }
            keo?.takeIf { stillMember }
        }
        if (checked.isSuccess) {
            val keo = checked.getOrNull() ?: return Result.success()
            NotificationHelper.notifyReminder(applicationContext, keo.id, keo.title, keo.startAt, keo.placeName)
        } else {
            // Mất mạng đúng lúc nhắc: vẫn nhắc bằng thông tin đã lưu lúc hẹn, thà nhắc thừa còn hơn lỡ kèo
            NotificationHelper.notifyReminder(
                applicationContext,
                keoId,
                inputData.getString(KEY_TITLE).orEmpty(),
                Timestamp(Date(inputData.getLong(KEY_START_AT, 0L))),
                inputData.getString(KEY_PLACE).orEmpty(),
            )
        }
        return Result.success()
    }

    companion object {
        const val KEY_KEO_ID = "keoId"
        const val KEY_TITLE = "title"
        const val KEY_PLACE = "place"
        const val KEY_START_AT = "startAt"
    }
}
