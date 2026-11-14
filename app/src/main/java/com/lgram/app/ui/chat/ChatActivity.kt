package com.lgram.app.ui.chat

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lgram.app.bridge.TelethonBridge
import com.lgram.app.databinding.ActivityChatBinding
import com.lgram.app.model.ChatMessage
import com.lgram.app.model.Dialog
import com.lgram.app.storage.StorageManager
import kotlinx.coroutines.launch
import java.io.File

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessagesAdapter

    private lateinit var accountId: String
    private var chatId: Long = 0
    private var chatName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID) ?: run { finish(); return }
        chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0)
        chatName = intent.getStringExtra(EXTRA_CHAT_NAME) ?: "Чат"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = chatName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = MessagesAdapter(
            emptyList(),
            onTap = { msg -> onMessageTapped(msg) },
            onLongPress = { msg -> showMessageMenu(msg) }
        )
        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        binding.rvMessages.layoutManager = lm
        binding.rvMessages.adapter = adapter

        binding.btnSend.setOnClickListener { onSendClicked() }

        loadMessages()
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            val json = TelethonBridge.getMessages(accountId, chatId)
            val messages = ChatMessage.listFromJson(json).sortedBy { it.id }
            adapter.update(messages)
            if (messages.isNotEmpty()) binding.rvMessages.scrollToPosition(messages.size - 1)
        }
    }

    private fun onSendClicked() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return
        binding.etMessage.setText("")
        lifecycleScope.launch {
            val result = TelethonBridge.sendText(accountId, chatId, text)
            if (result == "OK") {
                loadMessages()
            } else {
                Toast.makeText(this@ChatActivity, "Не вдалось надіслати: $result", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Тап по повідомленню з медіа — кешуємо (якщо ще нема) і відкриваємо системним переглядачем. */
    private fun onMessageTapped(msg: ChatMessage) {
        if (msg.mediaType.isEmpty()) return
        Toast.makeText(this, "Завантаження…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val cacheChatDir = File(StorageManager.cacheDir, chatId.toString())
            val result = TelethonBridge.downloadMedia(accountId, chatId, msg.id, cacheChatDir.absolutePath)
            if (result.startsWith("ERROR:")) {
                Toast.makeText(this@ChatActivity, "Помилка: ${result.removePrefix("ERROR:")}", Toast.LENGTH_LONG).show()
                return@launch
            }
            openFile(File(result))
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Файл збережено, але немає застосунку для перегляду: ${file.name}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showMessageMenu(msg: ChatMessage) {
        val options = mutableListOf("Переслати")
        if (msg.out && msg.text.isNotBlank()) options.add("Редагувати")
        options.add("Видалити")
        if (msg.mediaType.isNotEmpty()) options.add("Скачати")

        AlertDialog.Builder(this)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Переслати" -> showForwardPicker(msg)
                    "Редагувати" -> showEditDialog(msg)
                    "Видалити" -> confirmDelete(msg)
                    "Скачати" -> downloadToDownloads(msg)
                }
            }
            .show()
    }

    private fun showForwardPicker(msg: ChatMessage) {
        lifecycleScope.launch {
            val json = TelethonBridge.getDialogs(accountId)
            val dialogs = Dialog.listFromJson(json)
            if (dialogs.isEmpty()) {
                Toast.makeText(this@ChatActivity, "Не вдалось отримати список чатів", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val names = dialogs.map { it.name }.toTypedArray()
            AlertDialog.Builder(this@ChatActivity)
                .setTitle("Переслати в…")
                .setItems(names) { _, which ->
                    val target = dialogs[which]
                    lifecycleScope.launch {
                        val result = TelethonBridge.forwardMessage(accountId, chatId, msg.id, target.id)
                        val text = if (result == "OK") "Переслано в «${target.name}»" else "Помилка: $result"
                        Toast.makeText(this@ChatActivity, text, Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
    }

    private fun showEditDialog(msg: ChatMessage) {
        val input = EditText(this).apply { setText(msg.text) }
        AlertDialog.Builder(this)
            .setTitle("Редагувати повідомлення")
            .setView(input)
            .setPositiveButton("Зберегти") { _, _ ->
                val newText = input.text.toString()
                lifecycleScope.launch {
                    val result = TelethonBridge.editMessage(accountId, chatId, msg.id, newText)
                    if (result == "OK") loadMessages()
                    else Toast.makeText(this@ChatActivity, "Помилка: $result", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun confirmDelete(msg: ChatMessage) {
        AlertDialog.Builder(this)
            .setTitle("Видалити повідомлення?")
            .setMessage("Дію не можна скасувати.")
            .setPositiveButton("Видалити") { _, _ ->
                lifecycleScope.launch {
                    val result = TelethonBridge.deleteMessage(accountId, chatId, msg.id)
                    if (result == "OK") loadMessages()
                    else Toast.makeText(this@ChatActivity, "Помилка: $result", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun downloadToDownloads(msg: ChatMessage) {
        Toast.makeText(this, "Завантаження в lgram/downloads…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = TelethonBridge.downloadMedia(accountId, chatId, msg.id, StorageManager.downloadsDir.absolutePath)
            if (result.startsWith("ERROR:")) {
                Toast.makeText(this@ChatActivity, "Помилка: ${result.removePrefix("ERROR:")}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@ChatActivity, "Збережено: ${File(result).name}", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CHAT_NAME = "chat_name"
    }
}
