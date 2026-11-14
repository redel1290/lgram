package com.lgram.app.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lgram.app.databinding.ItemAccountSettingsBinding
import com.lgram.app.model.Account

class AccountSettingsAdapter(
    private var accounts: List<Account>,
    private val onExport: (Account) -> Unit,
    private val onDelete: (Account) -> Unit
) : RecyclerView.Adapter<AccountSettingsAdapter.VH>() {

    inner class VH(val binding: ItemAccountSettingsBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAccountSettingsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val acc = accounts[position]
        holder.binding.tvName.text = acc.name
        holder.binding.tvPhone.text = if (acc.active) acc.phone else "${acc.phone} — недоступна сесія"
        holder.binding.btnExport.setOnClickListener { onExport(acc) }
        holder.binding.btnDelete.setOnClickListener { onDelete(acc) }
    }

    override fun getItemCount(): Int = accounts.size

    fun update(newAccounts: List<Account>) {
        accounts = newAccounts
        notifyDataSetChanged()
    }
}
