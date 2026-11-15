package com.lgram.app.ui.main

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lgram.app.databinding.ItemAccountBinding
import com.lgram.app.model.Account
import com.lgram.app.storage.StorageManager
import com.lgram.app.ui.loadCircularAvatarIfExists

class AccountsAdapter(
    private var accounts: List<Account>,
    private val onClick: (Account) -> Unit,
    private val onAvatarMissing: (Account) -> Unit = {}
) : RecyclerView.Adapter<AccountsAdapter.VH>() {

    private val requestedAvatars = HashSet<String>()

    inner class VH(val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val acc = accounts[position]
        holder.binding.tvName.text = acc.name
        holder.binding.tvStatus.text = if (acc.active) acc.phone else "Недоступна сесія"
        holder.binding.tvStatus.setTextColor(if (acc.active) Color.parseColor("#6D7883") else Color.parseColor("#E8503A"))

        val letter = acc.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.binding.tvAvatar.text = letter

        val bg = holder.binding.tvAvatar.background.mutate() as GradientDrawable
        val color = try {
            Color.parseColor(acc.colorHex)
        } catch (e: Exception) {
            Color.parseColor("#5B8DEF")
        }
        // неактивна сесія — сіріший/тьмяніший кружечок, щоб візуально відрізнялась
        bg.setColor(if (acc.active) color else Color.parseColor("#4A5560"))

        holder.binding.ivAvatar.visibility = android.view.View.GONE
        holder.binding.tvAvatar.visibility = android.view.View.VISIBLE
        val avatarFile = StorageManager.avatarFileFor(acc.id, "me")
        val loaded = loadCircularAvatarIfExists(avatarFile, holder.binding.ivAvatar, holder.binding.tvAvatar)
        if (!loaded && acc.active && requestedAvatars.add(acc.id)) {
            onAvatarMissing(acc)
        }

        holder.binding.root.setOnClickListener { onClick(acc) }
    }

    override fun getItemCount(): Int = accounts.size

    fun update(newAccounts: List<Account>) {
        accounts = newAccounts
        notifyDataSetChanged()
    }
}
