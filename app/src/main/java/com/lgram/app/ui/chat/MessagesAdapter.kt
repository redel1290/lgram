package com.lgram.app.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lgram.app.databinding.ItemMessageBinding
import com.lgram.app.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessagesAdapter(
    private var messages: List<ChatMessage>,
    private val onTap: (ChatMessage) -> Unit,
    private val onLongPress: (ChatMessage) -> Unit
) : RecyclerView.Adapter<MessagesAdapter.VH>() {

    inner class VH(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = messages[position]
        val b = holder.binding

        val params = b.bubble.layoutParams as android.widget.LinearLayout.LayoutParams
        params.gravity = if (m.out) Gravity.END else Gravity.START
        b.bubble.layoutParams = params

        b.tvSender.visibility = if (!m.out && m.senderName.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
        b.tvSender.text = m.senderName

        b.tvText.text = m.text
        b.tvText.visibility = if (m.text.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE

        if (m.mediaType.isNotEmpty()) {
            b.mediaRow.visibility = android.view.View.VISIBLE
            b.tvMediaIcon.text = iconFor(m.mediaType)
            b.tvMediaLabel.text = labelFor(m.mediaType, m.mediaFilename)
        } else {
            b.mediaRow.visibility = android.view.View.GONE
        }

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeText = if (m.date > 0) sdf.format(Date(m.date * 1000)) else ""
        b.tvMeta.text = if (m.isEdited) "ред. $timeText" else timeText

        b.bubble.setOnClickListener { onTap(m) }
        b.bubble.setOnLongClickListener { onLongPress(m); true }
    }

    override fun getItemCount(): Int = messages.size

    fun update(newMessages: List<ChatMessage>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    private fun iconFor(type: String): String = when (type) {
        "photo" -> "🖼"
        "video" -> "🎬"
        "video_note" -> "⭕"
        "voice" -> "🎤"
        "audio" -> "🎵"
        "sticker" -> "🙂"
        "gif" -> "GIF"
        else -> "📄"
    }

    private fun labelFor(type: String, filename: String): String = when (type) {
        "photo" -> "Фото"
        "video" -> "Відео"
        "video_note" -> "Відеоповідомлення (кружечок)"
        "voice" -> "Голосове повідомлення"
        "audio" -> "Аудіо"
        "sticker" -> "Стікер"
        "gif" -> "GIF-анімація"
        else -> filename.ifBlank { "Файл" }
    }
}
