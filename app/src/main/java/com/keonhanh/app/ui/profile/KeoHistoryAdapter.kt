package com.keonhanh.app.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.keonhanh.app.R
import com.keonhanh.app.databinding.ItemKeoBinding
import com.keonhanh.app.ui.common.bind

/** Adapter hiển thị từng thẻ kèo trong màn Lịch sử kèo. */
class KeoHistoryAdapter(
    private val onItemClick: (HistoryKeoItem) -> Unit,
) : RecyclerView.Adapter<KeoHistoryAdapter.ViewHolder>() {

    private var items: List<HistoryKeoItem> = emptyList()

    fun submitList(newItems: List<HistoryKeoItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemKeoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemKeoBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.binding.root.context

        // Đổ thông tin chung của kèo (tên, địa điểm, avatar, số người...)
        holder.binding.bind(item.keo)

        // Đổi nhãn trạng thái thành ĐÃ CHECKIN (xanh) hoặc VẮNG MẶT (đỏ/cam)
        val badge = holder.binding.tvDeadline
        badge.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0) // Bỏ icon đồng hồ

        if (item.checkedIn) {
            badge.setText(R.string.history_status_checked_in)
            badge.backgroundTintList = ContextCompat.getColorStateList(context, R.color.kn_green_tint)
            badge.setTextColor(ContextCompat.getColor(context, R.color.kn_green_text))
        } else {
            badge.setText(R.string.history_status_absent)
            badge.backgroundTintList = ContextCompat.getColorStateList(context, R.color.kn_orange_tint)
            badge.setTextColor(ContextCompat.getColor(context, R.color.kn_error))
        }

        holder.binding.root.setOnClickListener {
            onItemClick(item)
        }
    }
}

