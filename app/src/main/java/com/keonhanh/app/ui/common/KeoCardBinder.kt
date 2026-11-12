package com.keonhanh.app.ui.common

import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.data.model.KeoStatus
import com.keonhanh.app.databinding.ItemKeoBinding
import com.keonhanh.app.util.bindAvatar
import com.keonhanh.app.util.formatKm

/**
 * Đổ dữ liệu một kèo vào thẻ item_keo.xml.
 * Dùng chung cho danh sách (KeoAdapter) và thẻ xem trước trên bản đồ (S09).
 */
fun ItemKeoBinding.bind(keo: Keo, showViewButton: Boolean = false) {
    val ctx = root.context

    ivSport.setImageResource(keo.sport.icon)
    ivSport.backgroundTintList = ContextCompat.getColorStateList(ctx, keo.sport.color)

    tvTitle.text = keo.title
    tvSubtitle.text = ctx.getString(R.string.keo_place_distance, keo.place, formatKm(keo.distanceKm))
    tvSlots.text = ctx.getString(R.string.keo_slots, keo.members.size, keo.maxSlots)
    tvDeadline.text = if (keo.status == KeoStatus.CHECK_IN) {
        ctx.getString(R.string.keo_checking_in)
    } else {
        ctx.getString(R.string.keo_deadline, keo.deadlineCountdown)
    }

    // Tối đa 3 avatar, phần còn lại gộp thành "+n"
    val avatarViews = listOf(tvAvatar1, tvAvatar2, tvAvatar3)
    avatarViews.forEachIndexed { index, view ->
        val member = keo.members.getOrNull(index)
        view.isVisible = member != null
        if (member != null) view.bindAvatar(member.name, member.id)
    }
    val extra = keo.members.size - avatarViews.size
    tvAvatarMore.isVisible = extra > 0
    tvAvatarMore.text = ctx.getString(R.string.avatar_more, extra)

    tvTrust.isVisible = keo.minTrust != null
    keo.minTrust?.let { tvTrust.text = ctx.getString(R.string.keo_min_trust, it) }

    btnViewKeo.isVisible = showViewButton
    rowTrust.isVisible = tvTrust.isVisible || showViewButton
}
