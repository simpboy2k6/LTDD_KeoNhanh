package com.keonhanh.app.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.keonhanh.app.data.model.ChatRoom
import com.keonhanh.app.databinding.ItemChatRoomBinding

/** Danh sách phòng chat ở S18. */
class ChatRoomAdapter(
    private val onClick: (ChatRoom) -> Unit,
) : ListAdapter<ChatRoom, ChatRoomAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemChatRoomBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemChatRoomBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val room = getItem(position)
        val ctx = holder.binding.root.context
        with(holder.binding) {
            ivSport.setImageResource(room.keo.sport.icon)
            ivSport.backgroundTintList = ContextCompat.getColorStateList(ctx, room.keo.sport.color)
            tvTitle.text = room.keo.title
            tvTime.text = room.time
            tvLastMessage.text = room.lastMessage
            root.setOnClickListener { onClick(room) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ChatRoom>() {
        override fun areItemsTheSame(oldItem: ChatRoom, newItem: ChatRoom) = oldItem.keo.id == newItem.keo.id
        override fun areContentsTheSame(oldItem: ChatRoom, newItem: ChatRoom) = oldItem == newItem
    }
}
