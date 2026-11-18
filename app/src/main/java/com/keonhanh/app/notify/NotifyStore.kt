package com.keonhanh.app.notify

import android.content.Context

/**
 * Những gì thông báo cần nhớ giữa các lần mở app, lưu trong SharedPreferences (Phong Hào, tuần 4).
 */
class NotifyStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("notify", Context.MODE_PRIVATE)

    /**
     * Mốc "đã báo tới đây". Ghi khi app rời màn hình (InAppNotifier dừng) và sau mỗi lần NotifyWorker chạy.
     * Lần sau chỉ báo những gì xảy ra sau mốc này, nên không báo lại một tin hai lần.
     * 0 = chưa có mốc (lần đầu cài app).
     */
    var lastCheckMillis: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    /** Đã hỏi quyền thông báo một lần chưa (Android 13+). Người dùng từ chối thì không hỏi lại mỗi lần mở app. */
    var askedPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED, value).apply()

    /**
     * Các lịch nhắc đang hẹn, dạng "keoId|giờ hẹn tính bằng ms".
     * Giờ hẹn không đổi thì không hẹn lại, nên nhắc đã hiện rồi không hiện lần nữa.
     */
    var reminders: Set<String>
        get() = prefs.getStringSet(KEY_REMINDERS, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit().putStringSet(KEY_REMINDERS, value).apply()

    private companion object {
        const val KEY_LAST_CHECK = "last_check_millis"
        const val KEY_ASKED = "asked_permission"
        const val KEY_REMINDERS = "reminders"
    }
}

/**
 * Màn hình đang hiện, MainActivity cập nhật mỗi lần chuyển màn.
 * Dùng để không báo khi người dùng đang ở đúng màn đó (đang mở chat của kèo thì không báo tin của kèo đó),
 * và để NotifyWorker biết app đang mở thì nhường cho InAppNotifier.
 */
object VisibleScreen {
    @Volatile var appVisible: Boolean = false
    @Volatile var destinationId: Int = 0
    @Volatile var keoId: String? = null

    fun isShowing(destinationId: Int, keoId: String): Boolean =
        appVisible && this.destinationId == destinationId && this.keoId == keoId
}
