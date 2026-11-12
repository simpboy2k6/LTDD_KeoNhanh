package com.keonhanh.app.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.data.model.Member
import com.keonhanh.app.databinding.ItemMemberBinding
import com.keonhanh.app.util.bindAvatar

/**
 * Lưới thành viên ở S15.
 * - Bình thường: đủ số chỗ, chỗ trống hiện vòng nét đứt có dấu "+".
 * - checkInMode (kèo đang Check-in): chỉ hiện thành viên; ai đã check-in có dấu tích, ai chưa thì mờ đi.
 */
class MemberAdapter(
    val checkInMode: Boolean = false,
) : ListAdapter<MemberAdapter.Slot, MemberAdapter.ViewHolder>(DiffCallback) {

    /** Một ô trong lưới; member = null là chỗ còn trống. */
    data class Slot(val position: Int, val member: Member?)

    class ViewHolder(val binding: ItemMemberBinding) : RecyclerView.ViewHolder(binding.root)

    fun submitKeo(keo: Keo) {
        val slotCount = if (checkInMode) keo.members.size else keo.maxSlots
        submitList(List(slotCount) { index -> Slot(index, keo.members.getOrNull(index)) })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemMemberBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = getItem(position).member
        val avatar = holder.binding.tvAvatar
        val context = avatar.context

        if (member == null) {
            avatar.setBackgroundResource(R.drawable.bg_slot_empty)
            avatar.backgroundTintList = null
            avatar.setTextColor(ContextCompat.getColor(context, R.color.kn_ink_muted))
            avatar.setText(R.string.slot_empty)
            avatar.contentDescription = null
        } else {
            avatar.bindAvatar(member.name, member.id)
        }
        val checkedIn = member?.checkedIn == true
        holder.binding.ivChecked.isVisible = checkInMode && checkedIn
        avatar.alpha = if (checkInMode && !checkedIn) DIMMED_ALPHA else 1f
    }

    private object DiffCallback : DiffUtil.ItemCallback<Slot>() {
        override fun areItemsTheSame(oldItem: Slot, newItem: Slot) = oldItem.position == newItem.position
        override fun areContentsTheSame(oldItem: Slot, newItem: Slot) = oldItem == newItem
    }

    private companion object {
        const val DIMMED_ALPHA = 0.4f
    }
}
