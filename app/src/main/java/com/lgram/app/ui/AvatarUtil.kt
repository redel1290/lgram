package com.lgram.app.ui

import android.graphics.BitmapFactory
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import java.io.File

/** Якщо файл аватарки існує на диску — показує його круглим, ховаючи літеру.
 *  Інакше лишає кольоровий кружечок з літерою як є. */
fun loadCircularAvatarIfExists(file: File, imageView: ImageView, letterView: TextView): Boolean {
    if (!file.exists()) return false
    return try {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return false
        val rounded = RoundedBitmapDrawableFactory.create(imageView.resources, bitmap)
        rounded.isCircular = true
        imageView.setImageDrawable(rounded)
        imageView.visibility = android.view.View.VISIBLE
        letterView.visibility = android.view.View.GONE
        true
    } catch (e: Exception) {
        false
    }
}
