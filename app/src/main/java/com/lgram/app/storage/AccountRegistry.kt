package com.lgram.app.storage

import com.lgram.app.model.Account
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Реєстр акаунтів — простий JSON-файл config/accounts.json.
 * Тут НЕ зберігається session-файл (він окремо, під accounts/<id>/),
 * тільки метадані: ім'я, телефон, api_id/hash, активність, колір.
 */
object AccountRegistry {

    private const val DEFAULT_COLORS = "#5B8DEF,#E67E6B,#4CAF91,#C97BD6,#D6A24C"

    fun loadAll(): MutableList<Account> {
        val file = StorageManager.accountsRegistryFile
        if (!file.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(file.readText())
            MutableList(arr.length()) { i -> Account.fromJson(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            StorageManager.log("AccountRegistry", "Помилка читання accounts.json: ${e.message}")
            mutableListOf()
        }
    }

    fun saveAll(accounts: List<Account>) {
        StorageManager.ensureStructure()
        val arr = JSONArray()
        accounts.forEach { arr.put(it.toJson()) }
        StorageManager.accountsRegistryFile.writeText(arr.toString(2))
    }

    fun add(account: Account) {
        val all = loadAll()
        all.add(account)
        saveAll(all)
    }

    fun update(account: Account) {
        val all = loadAll()
        val idx = all.indexOfFirst { it.id == account.id }
        if (idx >= 0) all[idx] = account else all.add(account)
        saveAll(all)
    }

    fun setActive(accountId: String, active: Boolean) {
        val all = loadAll()
        all.find { it.id == accountId }?.active = active
        saveAll(all)
    }

    /** Видаляє ЛИШЕ запис у реєстрі + файл сесії. Виклик Telegram API НЕ відбувається
     *  (навмисно — щоб не розлогінювати акаунт на боці Telegram, лише прибрати з lgram). */
    fun removeAccountAndSessionFile(accountId: String) {
        val all = loadAll().filterNot { it.id == accountId }
        saveAll(all)
        StorageManager.accountDirFor(accountId).deleteRecursively()
    }

    fun newAccountId(): String = UUID.randomUUID().toString().substring(0, 8)

    fun nextDefaultColor(existingCount: Int): String {
        val colors = DEFAULT_COLORS.split(",")
        return colors[existingCount % colors.size]
    }

    // ---- Редаговані дефолтні api_id / api_hash (config/credentials.json) ----

    data class Credentials(val apiId: Long, val apiHash: String)

    /** При першому запуску створює файл із дефолтними значеннями (щоб було що редагувати). */
    fun loadOrCreateDefaultCredentials(defaultApiId: Long, defaultApiHash: String): Credentials {
        StorageManager.ensureStructure()
        val file = StorageManager.credentialsFile
        if (!file.exists()) {
            val o = JSONObject().apply {
                put("apiId", defaultApiId)
                put("apiHash", defaultApiHash)
            }
            file.writeText(o.toString(2))
            return Credentials(defaultApiId, defaultApiHash)
        }
        return try {
            val o = JSONObject(file.readText())
            Credentials(o.getLong("apiId"), o.getString("apiHash"))
        } catch (e: Exception) {
            Credentials(defaultApiId, defaultApiHash)
        }
    }

    fun saveDefaultCredentials(apiId: Long, apiHash: String) {
        StorageManager.ensureStructure()
        val o = JSONObject().apply {
            put("apiId", apiId)
            put("apiHash", apiHash)
        }
        StorageManager.credentialsFile.writeText(o.toString(2))
    }
}
