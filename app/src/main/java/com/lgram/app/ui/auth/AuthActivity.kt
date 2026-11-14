package com.lgram.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lgram.app.bridge.TelethonBridge
import com.lgram.app.databinding.ActivityAuthBinding
import com.lgram.app.model.Account
import com.lgram.app.storage.AccountRegistry
import com.lgram.app.ui.main.MainActivity
import kotlinx.coroutines.launch

/**
 * Штатний вхід Telethon: номер телефону -> код з Telegram -> (якщо є 2FA) пароль.
 * Дозволяє додати скільки завгодно акаунтів (кожен виклик цього екрану — ще один
 * акаунт у реєстрі lgram, зі своїм session-файлом).
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private var pendingAccountId: String? = null
    private var pendingPhone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSendCode.setOnClickListener { onSendCode() }
        binding.btnConfirmCode.setOnClickListener { onConfirmCode() }
        binding.btnConfirmPassword.setOnClickListener { onConfirmPassword() }
    }

    private fun showError(text: String) {
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = text
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun onSendCode() {
        val name = binding.etAccountName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        if (name.isEmpty() || phone.isEmpty()) {
            showError("Вкажи назву акаунта та номер телефону")
            return
        }

        val creds = AccountRegistry.loadOrCreateDefaultCredentials(
            defaultApiId = 39965259L,
            defaultApiHash = "3c5a8b106e8180896c741810b844fdc4"
        )

        val accountId = AccountRegistry.newAccountId()
        pendingAccountId = accountId
        pendingPhone = phone
        binding.tvStatus.visibility = View.GONE
        setLoading(true)

        lifecycleScope.launch {
            val result = TelethonBridge.sendCode(accountId, creds.apiId, creds.apiHash, phone)
            setLoading(false)
            if (result == "OK") {
                binding.stepPhone.visibility = View.GONE
                binding.stepCode.visibility = View.VISIBLE
            } else {
                showError("Не вдалось надіслати код: ${result.removePrefix("ERROR:")}")
            }
        }
    }

    private fun onConfirmCode() {
        val accountId = pendingAccountId ?: return
        val code = binding.etCode.text.toString().trim()
        if (code.isEmpty()) {
            showError("Введи код")
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            val result = TelethonBridge.signInCode(accountId, pendingPhone, code)
            setLoading(false)
            when (result) {
                "OK" -> finishLogin(accountId)
                "NEED_PASSWORD" -> {
                    binding.stepCode.visibility = View.GONE
                    binding.stepPassword.visibility = View.VISIBLE
                }
                else -> showError("Невірний код: ${result.removePrefix("ERROR:")}")
            }
        }
    }

    private fun onConfirmPassword() {
        val accountId = pendingAccountId ?: return
        val password = binding.etPassword.text.toString()
        if (password.isEmpty()) {
            showError("Введи пароль")
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            val result = TelethonBridge.signInPassword(accountId, password)
            setLoading(false)
            if (result == "OK") {
                finishLogin(accountId)
            } else {
                showError("Невірний пароль: ${result.removePrefix("ERROR:")}")
            }
        }
    }

    private fun finishLogin(accountId: String) {
        val name = binding.etAccountName.text.toString().trim()
        val existingCount = AccountRegistry.loadAll().size
        val creds = AccountRegistry.loadOrCreateDefaultCredentials(39965259L, "3c5a8b106e8180896c741810b844fdc4")

        AccountRegistry.add(
            Account(
                id = accountId,
                name = name,
                phone = pendingPhone,
                apiId = creds.apiId,
                apiHash = creds.apiHash,
                colorHex = AccountRegistry.nextDefaultColor(existingCount),
                active = true
            )
        )

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
