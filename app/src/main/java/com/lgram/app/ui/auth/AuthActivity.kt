package com.lgram.app.ui.auth

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.lgram.app.bridge.TelethonBridge
import com.lgram.app.databinding.ActivityAuthBinding
import com.lgram.app.model.Account
import com.lgram.app.storage.AccountRegistry
import com.lgram.app.ui.main.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
    private var qrJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSendCode.setOnClickListener { onSendCode() }
        binding.btnConfirmCode.setOnClickListener { onConfirmCode() }
        binding.btnConfirmPassword.setOnClickListener { onConfirmPassword() }
        binding.btnQrLogin.setOnClickListener { onStartQrLogin() }
        binding.btnQrBack.setOnClickListener { cancelQrAndGoBack() }
    }

    override fun onDestroy() {
        super.onDestroy()
        qrJob?.cancel()
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

    // ---- Вхід через QR-код (альтернатива коду в SMS/Telegram, логіка номер+код не чіпається) ----

    private fun onStartQrLogin() {
        val name = binding.etAccountName.text.toString().trim()
        if (name.isEmpty()) {
            showError("Вкажи назву акаунта перед входом через QR")
            return
        }
        val creds = AccountRegistry.loadOrCreateDefaultCredentials(39965259L, "3c5a8b106e8180896c741810b844fdc4")
        val accountId = AccountRegistry.newAccountId()
        pendingAccountId = accountId

        binding.tvStatus.visibility = View.GONE
        setLoading(true)

        lifecycleScope.launch {
            val urlOrError = TelethonBridge.qrLoginStart(accountId, creds.apiId, creds.apiHash)
            setLoading(false)
            if (urlOrError.startsWith("ERROR:")) {
                showError("Не вдалось почати QR-вхід: ${urlOrError.removePrefix("ERROR:")}")
                return@launch
            }
            binding.stepPhone.visibility = View.GONE
            binding.stepQr.visibility = View.VISIBLE
            showQrImage(urlOrError)
            startQrPolling(accountId)
        }
    }

    private fun showQrImage(url: String) {
        try {
            val size = 600
            val writer = QRCodeWriter()
            val matrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            binding.ivQrCode.setImageBitmap(bmp)
        } catch (e: Exception) {
            showError("Не вдалось намалювати QR: ${e.message}")
        }
    }

    private fun startQrPolling(accountId: String) {
        qrJob?.cancel()
        qrJob = lifecycleScope.launch {
            while (isActive) {
                binding.tvQrStatus.text = "Очікування сканування…"
                val result = TelethonBridge.qrLoginWait(accountId, 20)
                when (result) {
                    "OK" -> {
                        finishLogin(accountId)
                        return@launch
                    }
                    "NEED_PASSWORD" -> {
                        binding.stepQr.visibility = View.GONE
                        binding.stepPassword.visibility = View.VISIBLE
                        return@launch
                    }
                    "EXPIRED" -> {
                        val newUrl = TelethonBridge.qrLoginRecreate(accountId)
                        if (newUrl.startsWith("ERROR:")) {
                            showError("QR протух і не оновився: ${newUrl.removePrefix("ERROR:")}")
                            return@launch
                        }
                        showQrImage(newUrl)
                        // і далі по колу — чекаємо сканування нового коду
                    }
                    else -> {
                        showError("QR-вхід: ${result.removePrefix("ERROR:")}")
                        return@launch
                    }
                }
            }
        }
    }

    private fun cancelQrAndGoBack() {
        qrJob?.cancel()
        binding.stepQr.visibility = View.GONE
        binding.stepPhone.visibility = View.VISIBLE
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
