package com.lgram.app.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.lgram.app.databinding.ActivityStoragePermissionBinding
import com.lgram.app.storage.StorageManager
import com.lgram.app.ui.main.MainActivity

/**
 * lgram не працює без повного доступу до /sdcard/Download/lgram (там і сесії,
 * і логи, і конфіги — не тільки медіафайли). Цей екран показується, поки
 * дозвіл MANAGE_EXTERNAL_STORAGE не надано.
 */
class StoragePermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStoragePermissionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoragePermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrant.setOnClickListener { requestPermission() }
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission()) {
            proceed()
        }
    }

    private fun hasPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // на старих версіях вистачає звичайних READ/WRITE, запитаних рантайм-діалогом
        }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                100
            )
        }
    }

    private fun proceed() {
        StorageManager.ensureStructure()
        // MainActivity спочатку перевіряє папку sessions/ та імпортує готові сесії.
        // Якщо там нічого немає і реєстр порожній, вона сама відкриє AuthActivity.
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
