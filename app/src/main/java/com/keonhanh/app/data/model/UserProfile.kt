package com.keonhanh.app.data.model

import kotlin.math.roundToInt

data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val attended: Int,
    val committed: Int,
    val lateCancels: Int,
    val hosted: Int,
) {
    val noShows: Int get() = committed - attended

    /**
     * Điểm uy tín theo công thức slide 12:
     * (số lần đến + 4) / (số kèo đã cam kết + 2 × số lần hủy muộn + 5).
     * Người mới (0 kèo) bắt đầu ở 80%.
     */
    val trustPercent: Int
        get() = ((attended + 4) * 100.0 / (committed + 2 * lateCancels + 5)).roundToInt()
}
