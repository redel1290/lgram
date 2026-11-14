package com.lgram.app.model

import org.json.JSONArray
import org.json.JSONObject

data class Dialog(
    val id: Long,
    val name: String,
    val lastMessage: String,
    val lastDate: Long,
    val unreadCount: Int,
    val isGroup: Boolean
) {
    companion object {
        fun listFromJson(json: String): List<Dialog> {
            return try {
                val arr = JSONArray(json)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    Dialog(
                        id = o.getLong("id"),
                        name = o.optString("name", "Без назви"),
                        lastMessage = o.optString("lastMessage", ""),
                        lastDate = o.optLong("lastDate", 0),
                        unreadCount = o.optInt("unreadCount", 0),
                        isGroup = o.optBoolean("isGroup", false)
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

data class ChatMessage(
    val id: Int,
    val out: Boolean,
    val senderName: String,
    val text: String,
    val date: Long,
    val mediaType: String, // "", photo, video, video_note, voice, audio, sticker, gif, document
    val mediaFilename: String,
    val isEdited: Boolean
) {
    companion object {
        fun listFromJson(json: String): List<ChatMessage> {
            return try {
                val arr = JSONArray(json)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    ChatMessage(
                        id = o.getInt("id"),
                        out = o.optBoolean("out", false),
                        senderName = o.optString("senderName", ""),
                        text = o.optString("text", ""),
                        date = o.optLong("date", 0),
                        mediaType = o.optString("mediaType", ""),
                        mediaFilename = o.optString("mediaFilename", ""),
                        isEdited = o.optBoolean("isEdited", false)
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

/** Допоміжне: дістати повідомлення про помилку з JSON виду {"error": "..."} */
fun jsonErrorOrNull(json: String): String? = try {
    val o = JSONObject(json)
    if (o.has("error")) o.getString("error") else null
} catch (e: Exception) {
    null
}
