package com.lgram.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lgram.app.bridge.TelethonBridge
import com.lgram.app.databinding.ActivityMainBinding
import com.lgram.app.model.Account
import com.lgram.app.storage.StorageManager
import com.lgram.app.storage.AccountRegistry
import com.lgram.app.ui.auth.AuthActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var accountsAdapter: AccountsAdapter
    private var activeAccountId: String? = null
    private var refreshingAccounts = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_sort_by_size) // тимчасова гамбургер-іконка
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(Gravity.START)
        }

        accountsAdapter = AccountsAdapter(emptyList()) { account -> onAccountClicked(account) }
        binding.rvAccounts.layoutManager = LinearLayoutManager(this)
        binding.rvAccounts.adapter = accountsAdapter

        binding.btnAddAccount.setOnClickListener {
            startActivity(Intent(this, AuthActivity::class.java))
        }

        binding.menuSettings.setOnClickListener {
            Toast.makeText(this, "Налаштування з'являться у наступній версії", Toast.LENGTH_SHORT).show()
        }

        binding.rvChats.visibility = View.GONE // поки немає чатів — див. v1.1.0
        refreshAccounts()
    }

    override fun onResume() {
        super.onResume()
        refreshAccounts()
    }

    private fun refreshAccounts() {
        if (refreshingAccounts) return
        refreshingAccounts = true

        lifecycleScope.launch {
            try {
                importDroppedSessions()

                val accounts = AccountRegistry.loadAll()
                accountsAdapter.update(accounts)
                if (accounts.isEmpty()) {
                    startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                    finish()
                    return@launch
                }

                if (activeAccountId == null) {
                    activeAccountId = accounts.firstOrNull { it.active }?.id ?: accounts.first().id
                    supportActionBar?.title = accounts.find { it.id == activeAccountId }?.name ?: "lgram"
                }

                // Перевіряємо сесії у фоні та оновлюємо їхній статус у реєстрі.
                accounts.forEach { acc ->
                    val alive = TelethonBridge.checkAuthorized(acc.id, acc.apiId, acc.apiHash)
                    if (alive != acc.active) AccountRegistry.setActive(acc.id, alive)
                }
                accountsAdapter.update(AccountRegistry.loadAll())
            } finally {
                refreshingAccounts = false
            }
        }
    }

    /** Імпорт готових Telethon *.session із /Download/lgram/sessions. */
    private suspend fun importDroppedSessions() {
        StorageManager.ensureStructure()
        val sessionFiles = StorageManager.sessionsImportDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("session", ignoreCase = true) }
            .orEmpty()
        if (sessionFiles.isEmpty()) return

        val creds = AccountRegistry.loadOrCreateDefaultCredentials(
            defaultApiId = 39965259L,
            defaultApiHash = "3c5a8b106e8180896c741810b844fdc4"
        )

        sessionFiles.forEach { source ->
            val accountId = AccountRegistry.newAccountId()
            val target = StorageManager.sessionFileFor(accountId)
            try {
                source.copyTo(target, overwrite = true)
                val authorized = TelethonBridge.checkAuthorized(accountId, creds.apiId, creds.apiHash)
                if (authorized) {
                    val accountName = TelethonBridge.getMeName(accountId)
                        .ifBlank { source.nameWithoutExtension }
                    val existingCount = AccountRegistry.loadAll().size
                    AccountRegistry.add(
                        Account(
                            id = accountId,
                            name = accountName,
                            phone = source.nameWithoutExtension,
                            apiId = creds.apiId,
                            apiHash = creds.apiHash,
                            colorHex = AccountRegistry.nextDefaultColor(existingCount),
                            active = true
                        )
                    )
                    // Оригінал прибираємо лише після успішного додавання до реєстру.
                    source.delete()
                    StorageManager.log("SessionImport", "Імпортовано ${source.name}")
                } else {
                    TelethonBridge.disconnect(accountId)
                    target.delete()
                    StorageManager.log("SessionImport", "Сесію ${source.name} не вдалося авторизувати")
                }
            } catch (e: Exception) {
                try {
                    TelethonBridge.disconnect(accountId)
                    target.delete()
                } catch (_: Exception) { }
                StorageManager.log("SessionImport", "Помилка імпорту ${source.name}: ${e.message}")
            }
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
        // Тут у наступній версії буде завантаження списку чатів саме цього акаунта
    }
}
