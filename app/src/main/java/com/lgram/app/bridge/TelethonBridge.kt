package com.lgram.app.bridge

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.lgram.app.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Усі функції тут suspend і вже самі перемикаються на Dispatchers.IO —
 * з UI-коду їх можна викликати прямо з корутини, не думаючи про потоки.
 *
 * ВАЖЛИВО: функції Python-модуля самі по собі блокуючі (вони чекають asyncio
 * результат усередині). withContext(Dispatchers.IO) тут не косметика, а
 * обов'язкова умова — інакше Chaquopy-виклик підвісить головний потік.
 */
object TelethonBridge {

    private lateinit var module: PyObject

    fun init(context: Context) {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        module = Python.getInstance().getModule("telethon_bridge")
    }

    suspend fun sendCode(accountId: String, apiId: Long, apiHash: String, phone: String): String =
        withContext(Dispatchers.IO) {
            val sessionPath = StorageManager.sessionPathFor(accountId)
            module.callAttr("send_code", accountId, sessionPath, apiId, apiHash, phone).toString()
        }

    suspend fun signInCode(accountId: String, phone: String, code: String): String =
        withContext(Dispatchers.IO) {
            module.callAttr("sign_in_code", accountId, phone, code).toString()
        }

    suspend fun signInPassword(accountId: String, password: String): String =
        withContext(Dispatchers.IO) {
            module.callAttr("sign_in_password", accountId, password).toString()
        }

    suspend fun checkAuthorized(accountId: String, apiId: Long, apiHash: String): Boolean =
        withContext(Dispatchers.IO) {
            val sessionPath = StorageManager.sessionPathFor(accountId)
            module.callAttr("check_authorized", accountId, sessionPath, apiId, apiHash).toBoolean()
        }

    suspend fun getMeName(accountId: String): String =
        withContext(Dispatchers.IO) {
            module.callAttr("get_me_name", accountId).toString()
        }

    suspend fun disconnect(accountId: String) = withContext(Dispatchers.IO) {
        module.callAttr("disconnect", accountId)
    }

    suspend fun getDialogs(accountId: String, limit: Int = 50): String =
        withContext(Dispatchers.IO) {
            module.callAttr("get_dialogs", accountId, limit).toString()
        }

    suspend fun getMessages(accountId: String, chatId: Long, limit: Int = 40, offsetId: Int = 0): String =
        withContext(Dispatchers.IO) {
            module.callAttr("get_messages", accountId, chatId, limit, offsetId).toString()
        }

    suspend fun sendText(accountId: String, chatId: Long, text: String): String =
        withContext(Dispatchers.IO) {
            module.callAttr("send_text", accountId, chatId, text).toString()
        }

    suspend fun downloadMedia(accountId: String, chatId: Long, messageId: Int, destDir: String): String =
        withContext(Dispatchers.IO) {
            module.callAttr("download_media", accountId, chatId, messageId, destDir).toString()
        }

    suspend fun forwardMessage(accountId: String, fromChatId: Long, messageId: Int, toChatId: Long): String =
        withContext(Dispatchers.IO) {
            module.callAttr("forward_message", accountId, fromChatId, messageId, toChatId).toString()
        }

    suspend fun editMessage(accountId: String, chatId: Long, messageId: Int, newText: String): String =
        withContext(Dispatchers.IO) {
            module.callAttr("edit_message", accountId, chatId, messageId, newText).toString()
        }

    suspend fun deleteMessage(accountId: String, chatId: Long, messageId: Int): String =
        withContext(Dispatchers.IO) {
            module.callAttr("delete_message", accountId, chatId, messageId).toString()
        }

    suspend fun getProfile(accountId: String): String =
        withContext(Dispatchers.IO) {
            module.callAttr("get_profile", accountId).toString()
        }

    suspend fun updateProfile(accountId: String, firstName: String, lastName: String, bio: String): String =
        withContext(Dispatchers.IO) {
            module.callAttr("update_profile", accountId, firstName, lastName, bio).toString()
        }
}
