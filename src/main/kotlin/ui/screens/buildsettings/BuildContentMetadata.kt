/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import com.akuleshov7.ktoml.Toml
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile
import androidx.compose.ui.graphics.toComposeImageBitmap

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

// -- Mods --
@Serializable private data class FabricModJson(val id: String, val version: String, val name: String? = null, val icon: String? = null)
@Serializable data class ModsToml(val mods: List<ModTomlInfo> = emptyList())
@Serializable data class ModTomlInfo(val modId: String, val version: String, val displayName: String, val logoFile: String? = null)
data class ModInfo(val id: String, val name: String, val version: String, val icon: ImageBitmap?, val file: File, val isEnabled: Boolean)

// -- Resource Packs --
data class ResourcePackInfo(val name: String, val icon: ImageBitmap?, val file: File, val isEnabled: Boolean)

// -- Worlds --
data class WorldInfo(val name: String, val icon: ImageBitmap?, val file: File)

internal suspend fun parseModFile(modFile: File): ModInfo = withContext(Dispatchers.IO) {
    val isEnabled = !modFile.name.endsWith(".disabled")
    val actualFile = if (isEnabled) modFile else File(modFile.parent, modFile.name.removeSuffix(".disabled"))
    try {
        if (!actualFile.exists()) throw IllegalStateException("Original mod file not found for ${modFile.name}")
        ZipFile(actualFile).use { zip ->
            zip.getEntry("fabric.mod.json")?.let {
                val fabricMod = json.decodeFromString<FabricModJson>(zip.getInputStream(it).bufferedReader().readText())
                val imageBitmap = fabricMod.icon?.let { iconPath -> zip.readImage(iconPath) }
                return@withContext ModInfo(fabricMod.id, fabricMod.name ?: fabricMod.id, fabricMod.version, imageBitmap, modFile, isEnabled)
            }
            zip.getEntry("META-INF/mods.toml")?.let {
                val modsToml = Toml.decodeFromString(ModsToml.serializer(), zip.getInputStream(it).bufferedReader().readText())
                modsToml.mods.firstOrNull()?.let { modInfoToml ->
                    val imageBitmap = modInfoToml.logoFile?.let { iconPath -> zip.readImage(iconPath) }
                    return@withContext ModInfo(modInfoToml.modId, modInfoToml.displayName, modInfoToml.version, imageBitmap, modFile, isEnabled)
                }
            }
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { e.printStackTrace() }
    return@withContext ModInfo(modFile.nameWithoutExtension.removeSuffix(".jar"), modFile.name, "N/A", null, modFile, isEnabled)
}

internal suspend fun parseResourcePack(packFile: File): ResourcePackInfo = withContext(Dispatchers.IO) {
    val isEnabled = !packFile.name.endsWith(".disabled")
    val actualFile = packFile
    var packName = actualFile.name.removeSuffix(".disabled").removeSuffix(".zip")
    var icon: ImageBitmap? = null

    try {
        if (actualFile.isDirectory) {
            val metaFile = File(actualFile, "pack.mcmeta")
            val iconFile = File(actualFile, "pack.png")
            if (metaFile.exists()) json.decodeFromString<PackMcMeta>(metaFile.readText()).pack.description.toPackDescription().takeIf { it.isNotBlank() }?.let { packName = it }
            if (iconFile.exists()) icon = iconFile.readBytes().toImageBitmap()
        } else if (actualFile.isFile) {
            ZipFile(actualFile).use { zip ->
                zip.getEntry("pack.mcmeta")?.let {
                    json.decodeFromString<PackMcMeta>(zip.getInputStream(it).bufferedReader().readText()).pack.description.toPackDescription().takeIf { it.isNotBlank() }?.let { packName = it }
                }
                zip.getEntry("pack.png")?.let { icon = zip.readImage("pack.png") }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) { e.printStackTrace() }
    return@withContext ResourcePackInfo(packName, icon, packFile, isEnabled)
}

internal suspend fun parseWorld(worldDir: File): WorldInfo = withContext(Dispatchers.IO) {
    var icon: ImageBitmap? = null
    try {
        val iconFile = File(worldDir, "icon.png")
        if (iconFile.exists()) {
            icon = iconFile.readBytes().toImageBitmap()
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { e.printStackTrace() }
    // TODO: Read level name from level.dat NBT file
    return@withContext WorldInfo(worldDir.name, icon, worldDir)
}

private fun ZipFile.readImage(path: String): ImageBitmap? = getEntry(path)?.let {
    runCatching { getInputStream(it).readBytes().toImageBitmap() }.getOrNull()
}
private fun ByteArray.toImageBitmap(): ImageBitmap = org.jetbrains.skia.Image.makeFromEncoded(this).toComposeImageBitmap()
