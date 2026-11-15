package com.lgram.app.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lgram.app.databinding.ItemChatBinding
import com.lgram.app.model.Dialog
import com.lgram.app.storage.StorageManager
import com.lgram.app.ui.loadCircularAvatarIfExists

class ChatsAdapter(
    private var dialogs: List<Dialog>,
    private var accountId: String,
    private val onClick: (Dialog) -> Unit,
    /** Викликається один раз на чат, якщо аватарки ще нема в кеші — хай
     *  виклик сам скачає файл і за потреби оновить адаптер. */
    private val onAvatarMissing: (Dialog) -> Unit
) : RecyclerView.Adapter<ChatsAdapter.VH>() {

    private val requestedAvatars = HashSet<Long>()

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
        holder.binding.ivAvatar.visibility = android.view.View.GONE
        holder.binding.tvAvatar.visibility = android.view.View.VISIBLE

        val avatarFile = StorageManager.avatarFileFor(accountId, d.id.toString())
        val loaded = loadCircularAvatarIfExists(avatarFile, holder.binding.ivAvatar, holder.binding.tvAvatar)
        if (!loaded && requestedAvatars.add(d.id)) {
            onAvatarMissing(d)
        }

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

    /** Коли перемикаємось на інший акаунт — і список, і контекст аватарок (які
     *  належать саме цьому акаунту) мають оновитись разом, інакше покажемо
     *  чужі кешовані фото під неправильними підписами. */
    fun updateAccountAndList(newAccountId: String, newDialogs: List<Dialog>) {
        if (accountId != newAccountId) requestedAvatars.clear()
        accountId = newAccountId
        dialogs = newDialogs
        notifyDataSetChanged()
    }
}
