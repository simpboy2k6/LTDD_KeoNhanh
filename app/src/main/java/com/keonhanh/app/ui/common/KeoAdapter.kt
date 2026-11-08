package com.keonhanh.app.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.databinding.ItemKeoBinding

/** Danh sách thẻ kèo (S10 Khám phá – danh sách, S17 Kèo của tôi). */
class KeoAdapter(
    private val onClick: (Keo) -> Unit,
) : ListAdapter<Keo, KeoAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemKeoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemKeoBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val keo = getItem(position)
        holder.binding.bind(keo)
        holder.binding.root.setOnClickListener { onClick(keo) }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Keo>() {
        override fun areItemsTheSame(oldItem: Keo, newItem: Keo) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Keo, newItem: Keo) = oldItem == newItem
    }
}
