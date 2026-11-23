/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import funlauncher.managers.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class SettingsManager(pathManager: PathManager) {
    private val settingsPath = pathManager.getAppDataDirectory().resolve("settings.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun loadSettings(): AppSettings = withContext(Dispatchers.IO) {
        if (!settingsPath.exists()) return@withContext AppSettings()
        try {
            json.decodeFromString<AppSettings>(settingsPath.readText())
        } catch (e: Exception) {
            e.printStackTrace() // Для отладки
            AppSettings()
        }
    }

    suspend fun saveSettings(settings: AppSettings) = withContext(Dispatchers.IO) {
        settingsPath.parent.createDirectories()
        val temporary = java.nio.file.Files.createTempFile(settingsPath.parent, "settings-", ".tmp")
        try {
            temporary.writeText(json.encodeToString(settings))
            try {
                java.nio.file.Files.move(temporary, settingsPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(temporary, settingsPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            java.nio.file.Files.deleteIfExists(temporary)
        }
    }
}
