package com.keonhanh.app.ui.lobby

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Message
import com.keonhanh.app.databinding.ItemMessageMeBinding
import com.keonhanh.app.databinding.ItemMessageOtherBinding
import com.keonhanh.app.util.bindAvatar

/**
 * Tin nhắn ở S16: 2 kiểu dòng, của mình (item_message_me) và của người khác (item_message_other).
 * @param onLongClickMine nhấn giữ tin của mình (để xóa); tin người khác không nhấn giữ được
 */
class MessageAdapter(
    private val onLongClickMine: (Message) -> Unit = {},
) : ListAdapter<Message, RecyclerView.ViewHolder>(DiffCallback) {

    class MeHolder(val binding: ItemMessageMeBinding) : RecyclerView.ViewHolder(binding.root)
    class OtherHolder(val binding: ItemMessageOtherBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) =
        if (getItem(position).isMine) TYPE_ME else TYPE_OTHER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ME) {
            MeHolder(ItemMessageMeBinding.inflate(inflater, parent, false))
        } else {
            OtherHolder(ItemMessageOtherBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is MeHolder -> {
                holder.binding.tvMessage.text = message.text
                holder.binding.tvTime.text = message.time
                holder.binding.root.setOnLongClickListener {
                    onLongClickMine(message)
                    true
                }
            }
            is OtherHolder -> {
                val ctx = holder.binding.root.context
                holder.binding.tvAvatar.bindAvatar(message.sender.name, message.sender.id)
                holder.binding.tvSender.text = ctx.getString(R.string.chat_sender_time, message.sender.name, message.time)
                holder.binding.tvMessage.text = message.text
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message) = oldItem == newItem
    }

    private companion object {
        const val TYPE_ME = 1
        const val TYPE_OTHER = 2
    }
}
