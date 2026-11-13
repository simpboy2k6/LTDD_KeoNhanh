package com.keonhanh.app.data.model

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.keonhanh.app.R

/** Môn chơi: mỗi môn có tên, icon và màu riêng (bảng màu mục 4 tài liệu tuần 2). */
enum class Sport(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @ColorRes val color: Int,
    @ColorRes val tint: Int,
    @ColorRes val textOnTint: Int,
) {
    SOCCER(R.string.sport_soccer, R.drawable.ic_sports_soccer_24, R.color.kn_green, R.color.kn_green_tint, R.color.kn_green_text),
    BADMINTON(R.string.sport_badminton, R.drawable.ic_badminton_24, R.color.kn_orange, R.color.kn_orange_tint, R.color.kn_orange_text),
    BOARD_GAME(R.string.sport_board_game, R.drawable.ic_chess_pawn_24, R.color.kn_purple, R.color.kn_purple_tint, R.color.kn_purple_text),
}

/** 5 trạng thái của một kèo (slide 10). Tuần 3 suy ra từ giờ hẹn, hạn chốt và số người. */
enum class KeoStatus { OPEN, FULL, LOCKED, CHECK_IN, ENDED }

/** Vai trò của người đang dùng app đối với một kèo. */
enum class MyRole { NONE, MEMBER, HOST }

data class Member(
    val id: String,
    val name: String,
    val checkedIn: Boolean = false,
)

/**
 * Một kèo, ở dạng sẵn sàng để hiển thị (chuỗi giờ, đếm ngược, khung check-in đã định dạng).
 * Tuần 3: dữ liệu thật đọc từ Firestore vào KeoDoc (data/remote), rồi đổi sang Keo bằng KeoDoc.toKeo(...)
 * trong data/remote/Mappers.kt; layout và Adapter tuần 2 dùng tiếp Keo nên không phải sửa.
 */
data class Keo(
    val id: String,
    val title: String,
    val sport: Sport,
    val place: String,
    /** Khoảng cách tới điểm hẹn; null khi chưa đo (S15, S17 không cần vị trí) thì thẻ kèo chỉ hiện tên điểm hẹn. */
    val distanceKm: Double?,
    val time: String,
    val deadlineCountdown: String,
    val hostName: String,
    val hostTrust: Int,
    val members: List<Member>,
    val maxSlots: Int,
    val minTrust: Int?,
    val inviteCode: String,
    val status: KeoStatus = KeoStatus.OPEN,
    val checkInWindow: String = "",
    val myRole: MyRole = MyRole.NONE,
    /**
     * Số thành viên. Danh sách ở Khám phá và Kèo của tôi không tải từng thành viên (đỡ tốn lượt đọc)
     * nên [members] có thể rỗng; số người lấy từ trường memberCount lưu sẵn trên Firestore.
     */
    val memberCount: Int = members.size,
)
