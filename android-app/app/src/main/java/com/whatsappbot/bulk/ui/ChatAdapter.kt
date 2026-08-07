package com.whatsappbot.bulk.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.whatsappbot.bulk.R
import com.whatsappbot.bulk.data.ChatItem
import com.whatsappbot.bulk.databinding.ItemChatBinding

class ChatAdapter(
    private val onClick: (ChatItem) -> Unit,
) : RecyclerView.Adapter<ChatAdapter.VH>() {

    private val items = mutableListOf<ChatItem>()

    fun submit(list: List<ChatItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatItem) {
            binding.textName.text = item.name
            val kind = if (item.isGroup) {
                binding.root.context.getString(R.string.group)
            } else {
                binding.root.context.getString(R.string.direct)
            }
            binding.textSub.text = if (item.unreadCount > 0) {
                "$kind · ${item.unreadCount}"
            } else {
                kind
            }
            binding.avatar.text = item.name.take(1)
            binding.avatar.setBackgroundResource(
                if (item.isGroup) R.drawable.bg_avatar_group else R.drawable.bg_avatar_dm
            )
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
