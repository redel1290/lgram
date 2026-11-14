package com.lgram.app.model

import org.json.JSONObject

/**
 * @param id         внутрішній унікальний ідентифікатор (не телефон, не залежить від Telegram)
 * @param name       назва акаунта, яку задає сам юзер ("Мій", "Друг Олег" тощо) — перша буква
 *                   цієї назви показується в кружечку бічного меню
 * @param phone      номер телефону (лише для показу/зручності)
 * @param apiId      може відрізнятись від дефолтного — на випадок бану конкретної пари id/hash
 * @param apiHash
 * @param colorHex   колір кружечка в бічному меню, якщо акаунт неактивний
 * @param active     false = сесія позначена неактивною (НЕ означає, що файл видалено)
 */
data class Account(
    val id: String,
    var name: String,
    var phone: String,
    var apiId: Long,
    var apiHash: String,
    var colorHex: String,
    var active: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("phone", phone)
        put("apiId", apiId)
        put("apiHash", apiHash)
        put("colorHex", colorHex)
        put("active", active)
    }

    companion object {
        fun fromJson(o: JSONObject): Account = Account(
            id = o.getString("id"),
            name = o.getString("name"),
            phone = o.optString("phone", ""),
            apiId = o.getLong("apiId"),
            apiHash = o.getString("apiHash"),
            colorHex = o.optString("colorHex", "#5B8DEF"),
            active = o.optBoolean("active", true)
        )
    }
}
