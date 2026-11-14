package com.lgram.app.storage

import android.os.Environment
import java.io.File

/**
 * Усе lgram зберігає під /sdcard/Download/lgram — жодних файлів у приватній
 * data/data-директорії застосунку. Так, за бажання, можна зайти в будь-який
 * файловий менеджер і подивитись/скопіювати/видалити будь-що руками.
 *
 * Структура:
 *   lgram/
 *     accounts/<accountId>/session.session   — Telethon-сесія акаунта
 *     config/accounts.json                   — реєстр акаунтів (НЕ секрети, просто метадані)
 *     config/credentials.json                — api_id / api_hash (редагується в налаштуваннях)
 *     downloads/                              — сюди кладуться файли, які юзер свідомо "Скачав"
 *     cache/                                  — тимчасові/кешовані медіа (прев'ю, стікери тощо)
 *     logs/                                   — логи застосунку
 */
object StorageManager {

    val root: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "lgram")

    val accountsDir: File get() = File(root, "accounts")
    /** Сюди можна вручну копіювати готові Telethon-файли *.session для імпорту. */
    val sessionsImportDir: File get() = File(root, "sessions")
    val configDir: File get() = File(root, "config")
    val downloadsDir: File get() = File(root, "downloads")
    val cacheDir: File get() = File(root, "cache")
    val logsDir: File get() = File(root, "logs")

    val accountsRegistryFile: File get() = File(configDir, "accounts.json")
    val credentialsFile: File get() = File(configDir, "credentials.json")

    /** Викликати один раз при старті застосунку — створює всю структуру, якщо її ще нема. */
    fun ensureStructure() {
        listOf(root, accountsDir, sessionsImportDir, configDir, downloadsDir, cacheDir, logsDir).forEach {
            if (!it.exists()) it.mkdirs()
        }
    }

    fun sessionFileFor(accountId: String): File {
        val dir = File(accountsDir, accountId)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "session.session")
    }

    /** Повертає шлях БЕЗ розширення .session — саме такого формату очікує Telethon. */
    fun sessionPathFor(accountId: String): String {
        val f = sessionFileFor(accountId)
        return f.absolutePath.removeSuffix(".session")
    }

    fun accountDirFor(accountId: String): File = File(accountsDir, accountId)

    fun log(tag: String, message: String) {
        try {
            ensureStructure()
            val logFile = File(logsDir, "lgram.log")
            logFile.appendText("[${System.currentTimeMillis()}] [$tag] $message\n")
        } catch (_: Exception) {
            // навмисно тихо — лог не повинен ронити застосунок
        }
    }
}
