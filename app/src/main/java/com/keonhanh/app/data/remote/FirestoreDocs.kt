package com.keonhanh.app.data.remote

import com.google.firebase.Timestamp
import com.keonhanh.app.data.model.CheckInRules
import com.keonhanh.app.data.model.KeoStatus
import com.keonhanh.app.data.model.Sport

/*
 * Các lớp "...Doc" là bản sao đúng như document lưu trên Firestore (tầng data).
 * UI không dùng trực tiếp: Mappers.kt đổi chúng sang model hiển thị (Keo, Member, Message)
 * mà các layout tuần 2 đang dùng, nên Fragment và Adapter cũ gần như không phải sửa.
 */

/** Document keos/{keoId}. */
data class KeoDoc(
    val id: String,
    val hostId: String,
    /** Tên enum [Sport]: SOCCER, BADMINTON, BOARD_GAME. */
    val sport: String,
    val title: String,
    val placeName: String,
    val lat: Double,
    val lng: Double,
    val startAt: Timestamp,
    val deadlineAt: Timestamp,
    val capacity: Int,
    val memberCount: Int,
    /** null = kèo không đặt ngưỡng, ai cũng bấm "Tham gia kèo" được; có ngưỡng thì phải "Xin vào". */
    val minTrust: Int?,
    val status: String,
    val inviteCode: String,
    val createdAt: Timestamp?,
    val updatedAt: Timestamp?,
) {
    /**
     * Trạng thái thật của kèo lúc [nowMillis], suy ra từ thời gian và số người (slide 10).
     * Không cần job chạy trên server để đổi trạng thái: ai mở app cũng tính ra cùng một kết quả.
     * Trường `status` trên Firestore chỉ dùng để đánh dấu kèo đã kết thúc sớm (ENDED).
     */
    fun effectiveStatus(nowMillis: Long = System.currentTimeMillis()): KeoStatus {
        if (status == KeoStatus.ENDED.name) return KeoStatus.ENDED
        val window = CheckInRules.WINDOW_MINUTES * 60_000L
        val start = startAt.toDate().time
        return when {
            nowMillis >= start + window -> KeoStatus.ENDED
            nowMillis >= start - window -> KeoStatus.CHECK_IN
            nowMillis >= deadlineAt.toDate().time -> KeoStatus.LOCKED
            memberCount >= capacity -> KeoStatus.FULL
            else -> KeoStatus.OPEN
        }
    }

    val sportEnum: Sport get() = Sport.entries.firstOrNull { it.name == sport } ?: Sport.SOCCER
}

/**
 * Dữ liệu người dùng nhập ở S12 Tạo kèo / Sửa kèo (Huy Hoàng dùng).
 * Không có id, hostId, memberCount... vì những trường đó Repository tự điền.
 */
data class KeoDraft(
    val sport: Sport,
    val title: String,
    val placeName: String,
    val lat: Double,
    val lng: Double,
    val startAt: Timestamp,
    val deadlineAt: Timestamp,
    val capacity: Int,
    val minTrust: Int?,
)

enum class MemberRole { HOST, MEMBER }

/** JOINED = đã ở trong kèo; REQUESTED = đã bấm "Xin vào", chờ chủ kèo duyệt ở S08. */
enum class MemberState { JOINED, REQUESTED }

enum class Attendance { PRESENT, ABSENT }

/** Document keos/{keoId}/members/{uid}. */
data class MemberDoc(
    val uid: String,
    val displayName: String,
    val role: MemberRole,
    val state: MemberState,
    /** null trong lúc chờ server ghi giờ (FieldValue.serverTimestamp). */
    val joinedAt: Timestamp?,
    // 3 trường check-in để sẵn cho tuần 4 (Minh Khoa ghi khi bấm Check-in ở S15)
    val checkInAt: Timestamp? = null,
    val checkInDistanceM: Int? = null,
    val attendance: Attendance? = null,
)

/** Một dòng thành viên kèm id của kèo, dùng cho S17 Kèo của tôi. */
data class Membership(val keoId: String, val member: MemberDoc)

/** Document keos/{keoId}/messages/{msgId}. */
data class MessageDoc(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    /** Giờ server; ngay sau khi gửi là giờ ước tính trên máy, vài trăm ms sau mới là giờ thật. */
    val sentAt: Timestamp?,
)
