package com.keonhanh.app.ui.checkin

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.keonhanh.app.R
import com.keonhanh.app.data.model.CheckInRules
import com.keonhanh.app.data.model.CheckInState
import com.keonhanh.app.databinding.ViewCheckinCardBinding

/**
 * Vẽ thẻ check-in (view_checkin_card.xml, TV1) và nút chính ở đáy S15 theo một trạng thái.
 * Toàn bộ quy tắc hiển thị của 7 trạng thái nằm ở đây; tuần 4 chỉ cần tính ra CheckInState từ GPS.
 *
 * @param window khung giờ check-in, ví dụ "16:45 – 17:15"
 * @param primary nút chính ở thanh nút dưới của S15
 */
fun ViewCheckinCardBinding.render(state: CheckInState, window: String, primary: MaterialButton) {
    val ctx = root.context
    tvCheckinWindow.text = window
    tvCheckinDistance.isVisible = false
    btnCheckinSecondary.isVisible = false

    when (state) {
        is CheckInState.NotYet -> {
            header(R.drawable.ic_schedule_24, R.color.kn_ink_muted, ctx.getString(R.string.checkin_title_not_yet))
            tvCheckinDetail.text = ctx.getString(R.string.checkin_detail_not_yet, state.window)
            primary.setup(R.string.checkin_action, enabled = false)
        }
        is CheckInState.TooFar -> {
            header(R.drawable.ic_location_on_24, R.color.kn_orange_text, ctx.getString(R.string.checkin_title_far))
            distance(state.distanceM, R.color.kn_orange_text)
            tvCheckinDetail.text = ctx.getString(R.string.checkin_detail_far, CheckInRules.RADIUS_M)
            secondary(R.string.checkin_open_map, R.drawable.ic_map_24)
            primary.setup(R.string.checkin_action, enabled = false)
        }
        is CheckInState.PoorAccuracy -> {
            header(R.drawable.ic_my_location_24, R.color.kn_ink_muted, ctx.getString(R.string.checkin_title_poor_gps))
            tvCheckinDetail.text = ctx.getString(
                R.string.checkin_detail_poor_gps, state.accuracyM, CheckInRules.MAX_ACCURACY_M,
            )
            secondary(R.string.checkin_measure_again, R.drawable.ic_refresh_24)
            primary.setup(R.string.checkin_action, enabled = false)
        }
        is CheckInState.InRange -> {
            header(R.drawable.ic_my_location_24, R.color.kn_green_text, ctx.getString(R.string.checkin_title_in_range))
            distance(state.distanceM, R.color.kn_green_text)
            tvCheckinDetail.text = ctx.getString(
                R.string.checkin_detail_in_range, state.accuracyM, CheckInRules.RADIUS_M,
            )
            primary.setup(R.string.checkin_action, enabled = true)
        }
        CheckInState.NoPermission -> {
            header(R.drawable.ic_location_off_24, R.color.kn_ink_muted, ctx.getString(R.string.checkin_title_no_permission))
            tvCheckinDetail.text = ctx.getString(R.string.checkin_detail_no_permission)
            primary.setup(R.string.checkin_action_grant, enabled = true)
        }
        is CheckInState.Done -> {
            header(R.drawable.ic_check_circle_24, R.color.kn_green_text, ctx.getString(R.string.checkin_title_done, state.time))
            tvCheckinDetail.text = ctx.getString(R.string.checkin_detail_done, state.distanceM)
            primary.setup(R.string.checkin_action_done, enabled = false)
        }
        CheckInState.Missed -> {
            header(R.drawable.ic_error_24, R.color.kn_error, ctx.getString(R.string.checkin_title_missed))
            tvCheckinDetail.text = ctx.getString(R.string.checkin_detail_missed)
            primary.setup(R.string.checkin_action_closed, enabled = false)
        }
    }
}

private fun ViewCheckinCardBinding.header(@DrawableRes icon: Int, @ColorRes color: Int, title: String) {
    ivCheckinIcon.setImageResource(icon)
    ivCheckinIcon.imageTintList = ContextCompat.getColorStateList(root.context, color)
    tvCheckinTitle.text = title
}

private fun ViewCheckinCardBinding.distance(meters: Int, @ColorRes color: Int) {
    tvCheckinDistance.isVisible = true
    tvCheckinDistance.text = root.context.getString(R.string.checkin_distance_value, meters)
    tvCheckinDistance.setTextColor(ContextCompat.getColor(root.context, color))
}

private fun ViewCheckinCardBinding.secondary(@StringRes text: Int, @DrawableRes icon: Int) {
    btnCheckinSecondary.isVisible = true
    btnCheckinSecondary.setText(text)
    btnCheckinSecondary.setIconResource(icon)
}

private fun MaterialButton.setup(@StringRes text: Int, enabled: Boolean) {
    setText(text)
    isEnabled = enabled
}
