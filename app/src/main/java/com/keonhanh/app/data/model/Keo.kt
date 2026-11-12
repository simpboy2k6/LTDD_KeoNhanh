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
 * Một kèo. Tuần 2 dùng chuỗi hiển thị sẵn (time, deadlineCountdown, checkInWindow) cho layout tĩnh.
 * Tuần 3: đổi sang Timestamp và GeoPoint của Firestore.
 */
data class Keo(
    val id: String,
    val title: String,
    val sport: Sport,
    val place: String,
    val distanceKm: Double,
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
)
