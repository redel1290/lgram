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
import com.lgram.app.storage.AccountRegistry
import com.lgram.app.ui.auth.AuthActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var accountsAdapter: AccountsAdapter
    private var activeAccountId: String? = null

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
        val accounts = AccountRegistry.loadAll()
        accountsAdapter.update(accounts)
        if (accounts.isEmpty()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        if (activeAccountId == null) {
            activeAccountId = accounts.first { it.active }.takeIf { true }?.id ?: accounts.first().id
            supportActionBar?.title = accounts.find { it.id == activeAccountId }?.name ?: "lgram"
        }
        // Фонова перевірка живучості сесій — якщо якась відвалилась, позначаємо неактивною,
        // АЛЕ ніколи не видаляємо файл сам по собі (це робить лише сам юзер у налаштуваннях).
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
        // Тут у наступній версії буде завантаження списку чатів саме цього акаунта
    }
}
