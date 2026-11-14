package com.lgram.app.ui.settings

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lgram.app.bridge.TelethonBridge
import com.lgram.app.databinding.ActivitySettingsBinding
import com.lgram.app.model.Account
import com.lgram.app.storage.AccountRegistry
import com.lgram.app.storage.StorageManager
import kotlinx.coroutines.launch
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var accountsAdapter: AccountSettingsAdapter

    // акаунт, чию сесію зараз експортуємо — потрібен, бо SAF-колбек приходить пізніше
    private var pendingExportAccount: Account? = null

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) performExport(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        accountsAdapter = AccountSettingsAdapter(
            emptyList(),
            onExport = { acc -> startExport(acc) },
            onDelete = { acc -> confirmDeleteSession(acc) }
        )
        binding.rvAccountsSettings.layoutManager = LinearLayoutManager(this)
        binding.rvAccountsSettings.adapter = accountsAdapter

        binding.btnSaveCredentials.setOnClickListener { saveCredentials() }
        binding.btnSaveProfile.setOnClickListener { saveProfile() }

        loadCredentials()
        loadAccounts()
        loadProfile()
    }

    override fun onResume() {
        super.onResume()
        loadAccounts()
    }

    private fun loadAccounts() {
        accountsAdapter.update(AccountRegistry.loadAll())
    }

    private fun loadCredentials() {
        val creds = AccountRegistry.loadOrCreateDefaultCredentials(39965259L, "3c5a8b106e8180896c741810b844fdc4")
        binding.etApiId.setText(creds.apiId.toString())
        binding.etApiHash.setText(creds.apiHash)
    }

    private fun saveCredentials() {
        val apiId = binding.etApiId.text.toString().toLongOrNull()
        val apiHash = binding.etApiHash.text.toString().trim()
        if (apiId == null || apiHash.isEmpty()) {
            Toast.makeText(this, "Перевір API ID / hash", Toast.LENGTH_SHORT).show()
            return
        }
        AccountRegistry.saveDefaultCredentials(apiId, apiHash)
        Toast.makeText(this, "Збережено. Застосується для нових акаунтів.", Toast.LENGTH_SHORT).show()
    }

    private fun loadProfile() {
        val accountId = AccountRegistry.loadAll().firstOrNull { it.active }?.id ?: return
        lifecycleScope.launch {
            val json = TelethonBridge.getProfile(accountId)
            try {
                val o = JSONObject(json)
                if (!o.has("error")) {
                    binding.etFirstName.setText(o.optString("firstName"))
                    binding.etLastName.setText(o.optString("lastName"))
                    binding.etBio.setText(o.optString("bio"))
                }
            } catch (_: Exception) { /* профіль не критичний для роботи налаштувань */ }
        }
    }

    private fun saveProfile() {
        val accountId = AccountRegistry.loadAll().firstOrNull { it.active }?.id
        if (accountId == null) {
            Toast.makeText(this, "Немає активного акаунта", Toast.LENGTH_SHORT).show()
            return
        }
        val first = binding.etFirstName.text.toString()
        val last = binding.etLastName.text.toString()
        val bio = binding.etBio.text.toString()
        lifecycleScope.launch {
            val result = TelethonBridge.updateProfile(accountId, first, last, bio)
            val msg = if (result == "OK") "Профіль оновлено" else "Помилка: $result"
            Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Експорт сесії: копія файлу в місце, яке вибере сам юзер; оригінал НЕ видаляється ----

    private fun startExport(account: Account) {
        pendingExportAccount = account
        createDocumentLauncher.launch("lgram_${account.name}_session.session")
    }

    private fun performExport(uri: Uri) {
        val account = pendingExportAccount ?: return
        pendingExportAccount = null
        try {
            val sessionFile = StorageManager.sessionFileFor(account.id)
            if (!sessionFile.exists()) {
                Toast.makeText(this, "Файл сесії не знайдено", Toast.LENGTH_SHORT).show()
                return
            }
            contentResolver.openOutputStream(uri)?.use { out ->
                sessionFile.inputStream().use { it.copyTo(out) }
            }
            Toast.makeText(this, "Сесію «${account.name}» експортовано (оригінал лишився на місці)", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Помилка експорту: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ---- Видалення файлу сесії: лише з lgram, без логауту в Telegram ----

    private fun confirmDeleteSession(account: Account) {
        AlertDialog.Builder(this)
            .setTitle("Видалити файл сесії?")
            .setMessage(
                "Акаунт «${account.name}» буде прибрано з lgram, а файл сесії видалено з " +
                    "пристрою. Сам акаунт у Telegram НЕ вийде з системи — лише lgram більше " +
                    "не зможе ним користуватись, поки не додаси його заново через звичайний вхід."
            )
            .setPositiveButton("Видалити") { _, _ ->
                lifecycleScope.launch { TelethonBridge.disconnect(account.id) }
                AccountRegistry.removeAccountAndSessionFile(account.id)
                loadAccounts()
                Toast.makeText(this, "Файл сесії видалено", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }
}
