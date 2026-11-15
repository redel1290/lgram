package com.lgram.app.ui.main

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lgram.app.bridge.TelethonBridge
import com.lgram.app.databinding.ActivityMainBinding
import com.lgram.app.model.Account
import com.lgram.app.model.Dialog
import com.lgram.app.storage.AccountRegistry
import com.lgram.app.storage.StorageManager
import com.lgram.app.ui.auth.AuthActivity
import com.lgram.app.ui.chat.ChatActivity
import com.lgram.app.ui.settings.SettingsActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var accountsAdapter: AccountsAdapter
    private lateinit var chatsAdapter: ChatsAdapter
    private var activeAccountId: String? = null
    private var showArchived = false
    private var allDialogs: List<Dialog> = emptyList()
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_sort_by_size)
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(Gravity.START)
        }

        accountsAdapter = AccountsAdapter(
            emptyList(),
            onClick = { account -> onAccountClicked(account) },
            onAvatarMissing = { account -> fetchAccountAvatar(account) }
        )
        binding.rvAccounts.layoutManager = LinearLayoutManager(this)
        binding.rvAccounts.adapter = accountsAdapter

        binding.btnAddAccount.setOnClickListener {
            startActivity(Intent(this, AuthActivity::class.java))
        }

        binding.menuSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.tabAllChats.setOnClickListener { setArchiveTab(false) }
        binding.tabArchive.setOnClickListener { setArchiveTab(true) }

        chatsAdapter = ChatsAdapter(
            emptyList(),
            accountId = "", // оновиться разом з activeAccountId перед першим showDialogs()
            onClick = { dialog -> onChatClicked(dialog) },
            onAvatarMissing = { dialog -> fetchChatAvatar(dialog) }
        )
        binding.rvChats.layoutManager = LinearLayoutManager(this)
        binding.rvChats.adapter = chatsAdapter

        refreshAccounts()
    }

    override fun onResume() {
        super.onResume()
        refreshAccounts()
        startPolling()
    }

    override fun onPause() {
        super.onPause()
        pollJob?.cancel()
    }

    /** Поки екран відкритий — тихо оновлюємо список чатів кожні кілька секунд,
     *  щоб бачити нові повідомлення й без-інтернет-дружній кеш не застоювався. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                delay(5000)
                if (activeAccountId != null) loadChats(silent = true)
            }
        }
    }

    private fun setArchiveTab(archived: Boolean) {
        showArchived = archived
        binding.tabAllChats.setTextColor(if (!archived) 0xFFFFFFFF.toInt() else 0xFF6D7883.toInt())
        binding.tabArchive.setTextColor(if (archived) 0xFFFFFFFF.toInt() else 0xFF6D7883.toInt())
        showDialogs()
    }

    private fun refreshAccounts() {
        val accounts = AccountRegistry.loadAll()
        accountsAdapter.update(accounts)
        if (accounts.isEmpty()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        if (activeAccountId == null) {
            activeAccountId = accounts.firstOrNull { it.active }?.id ?: accounts.first().id
            supportActionBar?.title = accounts.find { it.id == activeAccountId }?.name ?: "lgram"
            loadChats()
        }
        lifecycleScope.launch {
            accounts.forEach { acc ->
                val alive = TelethonBridge.checkAuthorized(acc.id, acc.apiId, acc.apiHash)
                if (alive != acc.active) {
                    AccountRegistry.setActive(acc.id, alive)
                }
            }
            accountsAdapter.update(AccountRegistry.loadAll())
        }
    }

    private fun onAccountClicked(account: Account) {
        if (!account.active) {
            Toast.makeText(this, "Недоступна сесія — перевір файл у налаштуваннях", Toast.LENGTH_LONG).show()
            return
        }
        activeAccountId = account.id
        supportActionBar?.title = account.name
        binding.drawerLayout.closeDrawers()
        loadChats()
    }

    private fun loadChats(silent: Boolean = false) {
        val accountId = activeAccountId ?: return
        if (!silent) {
            binding.tvEmptyState.text = "Завантаження чатів…"
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvChats.visibility = View.GONE
        }

        lifecycleScope.launch {
            val json = TelethonBridge.getDialogs(accountId)
            val dialogs = Dialog.listFromJson(json)
            allDialogs = dialogs
            showDialogs()
        }
    }

    private fun showDialogs() {
        val accountId = activeAccountId ?: return
        val filtered = allDialogs.filter { it.isArchived == showArchived }
        if (filtered.isEmpty()) {
            binding.tvEmptyState.text = if (showArchived) "Архів порожній" else "Чатів не знайдено"
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvChats.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvChats.visibility = View.VISIBLE
            chatsAdapter.updateAccountAndList(accountId, filtered)
        }
    }

    private fun fetchChatAvatar(dialog: Dialog) {
        val accountId = activeAccountId ?: return
        lifecycleScope.launch {
            val dest = StorageManager.avatarFileFor(accountId, dialog.id.toString())
            val result = TelethonBridge.downloadAvatar(accountId, dialog.id.toString(), dest.absolutePath)
            if (result != "NONE" && !result.startsWith("ERROR:")) {
                chatsAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun fetchAccountAvatar(account: Account) {
        lifecycleScope.launch {
            val dest = StorageManager.avatarFileFor(account.id, "me")
            val result = TelethonBridge.downloadAvatar(account.id, "me", dest.absolutePath)
            if (result != "NONE" && !result.startsWith("ERROR:")) {
                accountsAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun onChatClicked(dialog: Dialog) {
        val accountId = activeAccountId ?: return
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_ACCOUNT_ID, accountId)
            putExtra(ChatActivity.EXTRA_CHAT_ID, dialog.id)
            putExtra(ChatActivity.EXTRA_CHAT_NAME, dialog.name)
        }
        startActivity(intent)
    }
}
