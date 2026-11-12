package com.keonhanh.app.ui.explore

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Sport
import com.keonhanh.app.databinding.ViewExploreHeaderBinding

/** Bán kính đang chọn (km). Tuần 3 chuyển vào ViewModel dùng chung cho S09, S10, S11. */
const val DEFAULT_RADIUS_KM = 3

/**
 * Cài đặt phần đầu dùng chung của S09 / S10 (view_explore_header.xml).
 * @param toggleText chữ trên nút đổi chế độ xem ("Danh sách" hoặc "Bản đồ")
 */
fun ViewExploreHeaderBinding.setup(
    @StringRes toggleText: Int,
    @DrawableRes toggleIcon: Int,
    onToggle: () -> Unit,
    onOpenFilter: () -> Unit,
    onScanInvite: () -> Unit,
    onSportChanged: (Sport?) -> Unit = {},
) {
    val ctx = root.context
    chipRadius.text = ctx.getString(R.string.explore_radius_chip, DEFAULT_RADIUS_KM)
    chipRadius.setOnClickListener { onOpenFilter() }
    btnScanInvite.setOnClickListener { onScanInvite() }

    btnToggleView.setText(toggleText)
    btnToggleView.setIconResource(toggleIcon)
    btnToggleView.setOnClickListener { onToggle() }

    chipGroupSport.setOnCheckedStateChangeListener { _, checkedIds ->
        val sport = when (checkedIds.firstOrNull()) {
            R.id.chip_soccer -> Sport.SOCCER
            R.id.chip_badminton -> Sport.BADMINTON
            R.id.chip_board_game -> Sport.BOARD_GAME
            else -> null // "Tất cả"
        }
        onSportChanged(sport)
    }
}
