package com.keonhanh.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.keonhanh.app.MainActivity
import com.keonhanh.app.R
import com.keonhanh.app.data.remote.MessageDoc
import com.keonhanh.app.data.remote.formatClock
import com.google.firebase.Timestamp

/*
 * Thông báo trên máy (Phong Hào, tuần 4).
 *
 * Gói Spark không chạy được Cloud Functions nên không có server gửi push (FCM). App tự tạo thông báo:
 *   - App đang mở trên màn hình: InAppNotifier nghe Firestore và báo ngay.
 *   - App không ở trên màn hình: NotifyWorker (WorkManager) kiểm tra 15 phút một lần.
 *   - Nhắc giờ kèo: ReminderWorker hẹn trước giờ hẹn 30 phút.
 * Cả ba đều gọi các hàm trong file này nên thông báo trông giống nhau và bấm vào đều mở đúng phòng chờ.
 *
 * Bấm thông báo: mở MainActivity kèm EXTRA_KEO_ID, MainActivity điều hướng tới S15.
 * Không dùng NavDeepLinkBuilder vì nó tạo lại Activity từ đầu, mà MainActivity mở đầu bằng màn Đăng nhập,
 * nên phòng chờ sẽ bị màn Đăng nhập đè lên.
 */
object NotificationHelper {

    /** Xin vào kèo, được duyệt. */
    const val CHANNEL_KEO = "keo"

    /** Tin nhắn mới trong kèo. */
    const val CHANNEL_CHAT = "chat"

    /** Nhắc trước giờ hẹn. */
    const val CHANNEL_REMINDER = "reminder"

    /** Gọi một lần trong KeoNhanhApp.onCreate. Tạo lại kênh đã có thì Android bỏ qua, không sao. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_KEO, context.getString(R.string.notif_channel_keo), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_CHAT, context.getString(R.string.notif_channel_chat), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.notif_channel_reminder), NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    /** Android 13 trở lên phải được người dùng cho phép; người dùng cũng có thể tắt thông báo trong Cài đặt. */
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // ---------- 4 loại thông báo ----------

    /** Chủ kèo: có người bấm "Xin vào". */
    fun notifyJoinRequest(context: Context, keoId: String, keoTitle: String, requesterName: String) = show(
        context,
        id = idFor("request", keoId, requesterName),
        channel = CHANNEL_KEO,
        title = context.getString(R.string.notif_request_title),
        text = context.getString(R.string.notif_request_text, requesterName, keoTitle),
        keoId = keoId,
    )

    /** Người xin vào: chủ kèo đã duyệt. */
    fun notifyApproved(context: Context, keoId: String, keoTitle: String) = show(
        context,
        id = idFor("approved", keoId),
        channel = CHANNEL_KEO,
        title = context.getString(R.string.notif_approved_title),
        text = context.getString(R.string.notif_approved_text, keoTitle),
        keoId = keoId,
    )

    /** Thành viên: tin nhắn mới. [count] > 1 khi WorkManager gom nhiều tin cùng lúc. */
    fun notifyMessages(context: Context, keoId: String, keoTitle: String, latest: MessageDoc, count: Int = 1) {
        val line = context.getString(R.string.chat_last_line, latest.senderName, latest.text)
        show(
            context,
            // Cùng một id cho mỗi kèo: tin sau thay tin trước, không xếp chồng hàng chục thông báo
            id = idFor("chat", keoId),
            channel = CHANNEL_CHAT,
            title = keoTitle,
            text = if (count > 1) context.getString(R.string.notif_messages_more, count, line) else line,
            keoId = keoId,
        )
    }

    /** Thành viên: còn 30 phút nữa tới giờ hẹn. */
    fun notifyReminder(context: Context, keoId: String, keoTitle: String, startAt: Timestamp, placeName: String) = show(
        context,
        id = idFor("reminder", keoId),
        channel = CHANNEL_REMINDER,
        title = context.getString(R.string.notif_reminder_title, keoTitle),
        text = context.getString(R.string.notif_reminder_text, formatClock(startAt), placeName),
        keoId = keoId,
    )

    // ---------- dùng chung ----------

    private fun idFor(type: String, keoId: String, extra: String = ""): Int = "$type/$keoId/$extra".hashCode()

    @SuppressLint("MissingPermission") // đã kiểm bằng canNotify
    private fun show(context: Context, id: Int, channel: String, title: String, text: String, keoId: String) {
        if (!canNotify(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            // App đang mở thì dùng lại MainActivity hiện có (onNewIntent), không tạo thêm màn mới
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_KEO_ID, keoId)
        }
        // requestCode = id: mỗi thông báo giữ đúng keoId của nó, không bị thông báo sau ghi đè
        val pending = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notifications_24)
            .setColor(ContextCompat.getColor(context, R.color.kn_green))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (channel == CHANNEL_CHAT) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Người dùng vừa thu hồi quyền: bỏ qua, app vẫn chạy bình thường
        }
    }
}
