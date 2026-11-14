package com.lgram.app.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lgram.app.databinding.ItemChatBinding
import com.lgram.app.model.Dialog

class ChatsAdapter(
    private var dialogs: List<Dialog>,
    private val onClick: (Dialog) -> Unit
) : RecyclerView.Adapter<ChatsAdapter.VH>() {

    inner class VH(val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = dialogs[position]
        holder.binding.tvChatName.text = d.name
        holder.binding.tvLastMessage.text = d.lastMessage
        holder.binding.tvAvatar.text = d.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        if (d.unreadCount > 0) {
            holder.binding.tvUnread.visibility = View.VISIBLE
            holder.binding.tvUnread.text = if (d.unreadCount > 99) "99+" else d.unreadCount.toString()
        } else {
            holder.binding.tvUnread.visibility = View.GONE
        }

        holder.binding.root.setOnClickListener { onClick(d) }
    }

    override fun getItemCount(): Int = dialogs.size

    fun update(newDialogs: List<Dialog>) {
        dialogs = newDialogs
        notifyDataSetChanged()
    }
}
