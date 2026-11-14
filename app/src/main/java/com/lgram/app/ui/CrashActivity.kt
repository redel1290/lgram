package com.lgram.app.ui

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

/**
 * Мінімальний екран для показу стеку необробленого винятку. Жодних дозволів
 * чи залежностей не потребує — просто текстове поле, щоб можна було прочитати
 * і скопіювати причину краша, навіть якщо дозвіл на сховище ще не надано.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = intent.getStringExtra(EXTRA_TRACE) ?: "Невідома помилка"

        val tv = TextView(this).apply {
            text = "lgram впав із помилкою.\nСкопіюй текст нижче (затримай палець на тексті) і надішли:\n\n$trace"
            setTextIsSelectable(true)
            setPadding(32, 64, 32, 64)
            setTextColor(0xFFFFFFFF.toInt())
        }
        val scroll = ScrollView(this).apply {
            addView(tv)
            setBackgroundColor(0xFF17212B.toInt())
        }
        setContentView(scroll)
    }

    companion object {
        const val EXTRA_TRACE = "trace"
    }
}
