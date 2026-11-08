package com.keonhanh.app.data.model

/**
 * Luật check-in theo khoảng cách tới điểm hẹn (thay cho mã QR check-in).
 * Đây là giá trị nhóm tạm chốt ở tuần 2; đổi ở đây là cả app đổi theo.
 */
object CheckInRules {
    /** Phải ở trong bán kính này quanh điểm hẹn mới check-in được. */
    const val RADIUS_M = 100

    /** Sai số GPS lớn hơn mức này thì bắt đo lại, tránh check-in nhờ vị trí "nhảy". */
    const val MAX_ACCURACY_M = 50

    /** Check-in mở từ 15 phút trước tới 15 phút sau giờ hẹn (slide 10). */
    const val WINDOW_MINUTES = 15
}

/**
 * 7 trạng thái của thẻ check-in ở S15 (mục 3 tài liệu tuần 2).
 * Tuần 4: lấy vị trí bằng FusedLocationProviderClient, tính khoảng cách bằng Location.distanceBetween,
 * rồi chọn trạng thái theo CheckInRules.
 */
sealed interface CheckInState {
    data class NotYet(val window: String) : CheckInState
    data class TooFar(val distanceM: Int) : CheckInState
    data class PoorAccuracy(val accuracyM: Int) : CheckInState
    data class InRange(val distanceM: Int, val accuracyM: Int) : CheckInState
    data object NoPermission : CheckInState
    data class Done(val time: String, val distanceM: Int) : CheckInState
    data object Missed : CheckInState
}
