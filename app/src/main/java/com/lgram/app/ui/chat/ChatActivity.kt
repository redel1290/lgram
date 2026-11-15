package com.lgram.app.ui.chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.lgram.app.ui.ActionSheet
import com.lgram.app.ui.ActionItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessagesAdapter

    private lateinit var accountId: String
    private var chatId: Long = 0
    private var chatName: String = ""
    private var pollJob: Job? = null
    private var lastMessageIds: List<Int> = emptyList()

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onFilePicked(uri)
    }

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
        binding.btnAttach.setOnClickListener { pickFileLauncher.launch("*/*") }

        loadMessages()
        lifecycleScope.launch { TelethonBridge.markRead(accountId, chatId) }
    }

    override fun onResume() {
        super.onResume()
        startPolling()
    }

    override fun onPause() {
        super.onPause()
        pollJob?.cancel()
    }

    /** Поки чат відкритий — тихо перевіряємо нові повідомлення кожні кілька секунд.
     *  Це не "справжній" push (events), але просто й надійно вирішує ситуацію, коли
     *  відповідь від бота/співрозмовника з'являється в офіційному Telegram, а в lgram
     *  немає, поки не відкриєш чат заново. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                delay(4000)
                loadMessages(silent = true)
            }
        }
    }

    private fun loadMessages(silent: Boolean = false) {
        lifecycleScope.launch {
            val json = TelethonBridge.getMessages(accountId, chatId)
            val messages = ChatMessage.listFromJson(json).sortedBy { it.id }
            val ids = messages.map { it.id }
            if (silent && ids == lastMessageIds) return@launch // нічого не змінилось — не смикаємо список
            lastMessageIds = ids
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

    // ---- Надсилання файлів ----

    private fun onFilePicked(uri: Uri) {
        Toast.makeText(this, "Підготовка файлу…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val localFile = copyUriToCache(uri)
            if (localFile == null) {
                Toast.makeText(this@ChatActivity, "Не вдалось прочитати файл", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val result = TelethonBridge.sendFile(accountId, chatId, localFile.absolutePath)
            if (result == "OK") {
                loadMessages()
            } else {
                Toast.makeText(this@ChatActivity, "Не вдалось надіслати файл: $result", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            var name = "file_${System.currentTimeMillis()}"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
            }
            val outFile = File(StorageManager.cacheDir, "outgoing_$name")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            outFile
        } catch (e: Exception) {
            null
        }
    }

    // ---- Перегляд/відтворення медіа: НЕ качаємо автоматично все підряд ----

    private fun cachedFileFor(messageId: Int): File? {
        val dir = File(StorageManager.cacheDir, chatId.toString())
        if (!dir.exists()) return null
        return dir.listFiles()?.firstOrNull { it.name.startsWith("${messageId}_") }
    }

    /** Тап по медіа: якщо вже є в кеші — відкриваємо одразу (нуль зайвого трафіку).
     *  Якщо нема — питаємо підтвердження, і тільки тоді якаємо (саме цього просили:
     *  звичайний тап не повинен сам по собі починати качати все підряд). */
    private fun onMessageTapped(msg: ChatMessage) {
        if (msg.mediaType.isEmpty()) return

        val cached = cachedFileFor(msg.id)
        if (cached != null) {
            openFile(cached)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Завантажити?")
            .setMessage("Це медіа ще не завантажене. Завантажити й відкрити зараз?")
            .setPositiveButton("Завантажити") { _, _ -> downloadAndOpen(msg) }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun downloadAndOpen(msg: ChatMessage) {
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

    // ---- Контекстне меню (bottom sheet з іконками) ----

    private fun showMessageMenu(msg: ChatMessage) {
        val actions = mutableListOf<ActionItem>()
        actions.add(ActionItem("📤", "Переслати") { showForwardPicker(msg) })
        if (msg.out && msg.text.isNotBlank()) {
            actions.add(ActionItem("✏️", "Редагувати") { showEditDialog(msg) })
        }
        if (msg.mediaType.isNotEmpty()) {
            actions.add(ActionItem("⬇️", "Скачати в Download") { downloadToDownloads(msg) })
        }
        actions.add(ActionItem("🗑", "Видалити", destructive = true) { confirmDelete(msg) })

        ActionSheet.show(this, actions)
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
