package com.lgram.app

import android.app.Application
import android.content.Intent
import com.lgram.app.bridge.TelethonBridge
import com.lgram.app.storage.StorageManager
import com.lgram.app.ui.CrashActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class LgramApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        StorageManager.ensureStructure()
        TelethonBridge.init(this)
    }

    /**
     * При будь-якому необробленому винятку (включно з крашем ще до появи
     * першого екрану) показуємо стек текстом на екрані — це не залежить від
     * жодних дозволів, на відміну від запису у файл. Додатково пробуємо
     * записати і в logs/crash.log, якщо сховище вже доступне.
     */
    private fun installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val trace = sw.toString()

            try {
                StorageManager.ensureStructure()
                File(StorageManager.logsDir, "crash.log")
                    .appendText("\n==== ${System.currentTimeMillis()} ====\n$trace\n")
            } catch (_: Exception) { /* сховище ще недоступне — не критично, екран все одно покаже */ }

            try {
                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra(CrashActivity.EXTRA_TRACE, trace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
            } catch (_: Exception) { /* навіть якщо не вдалось показати — не падаємо вдруге тут */ }

            exitProcess(1)
        }
    }
}
