/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.game

import funlauncher.managers.PathManager
import funlauncher.net.Network
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.plugins.timeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText

@Serializable
data class ForgeVersion(
    val mcVersion: String,
    val forgeVersion: String,
    val isRecommended: Boolean = false,
    val isLatest: Boolean = false
)

class VersionManager(pathManager: PathManager) {

    private val json = Network.json
    private val client = Network.client
    private val cacheDir: Path = pathManager.getCacheDir()

    private fun log(message: String) {
        println("[VersionManager] $message")
    }

    suspend fun getMinecraftVersions(): List<MinecraftVersion> {
        val cacheFile = cacheDir.resolve("vanilla_versions.json")
        if (!cacheFile.exists()) {
            log("Кэш версий Minecraft не найден.")
            return emptyList()
        }
        try {
            val manifest = json.decodeFromString<VersionManifest>(cacheFile.readText())
            return manifest.versions.map { MinecraftVersion(it.id, it.type, it.releaseTime) }
        } catch (e: Exception) {
            log("Ошибка чтения кэша версий Minecraft: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getFabricGameVersions(): List<FabricGameVersion> {
        val cacheFile = cacheDir.resolve("fabric_game_versions.json")
        if (!cacheFile.exists()) {
            log("Кэш версий игр Fabric не найден.")
            return emptyList()
        }
        try {
            return json.decodeFromString(cacheFile.readText())
        } catch (e: Exception) {
            log("Ошибка чтения кэша версий игр Fabric: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getFabricLoaderVersions(gameVersion: String, forceRefresh: Boolean = false): List<FabricVersion> = withContext(Dispatchers.IO) {
        val safeVersion = gameVersion.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val cacheFile = cacheDir.resolve("fabric_loader_$safeVersion.json")
        val cached = runCatching {
            json.decodeFromString<List<FabricVersion>>(cacheFile.readText())
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        if (!forceRefresh && cached != null &&
            System.currentTimeMillis() - java.nio.file.Files.getLastModifiedTime(cacheFile).toMillis() < 60 * 60 * 1000) {
            return@withContext cached
        }
        val url = "https://meta.fabricmc.net/v2/versions/loader/$gameVersion"
        try {
            val response = client.get(url) {
                timeout { requestTimeoutMillis = 15000; connectTimeoutMillis = 5000 }
            }
            check(response.status.value in 200..299) { "Fabric HTTP ${response.status.value}" }
            val versions = response.body<List<FabricLoaderApiResponse>>().map { FabricVersion(it.loader.version, it.loader.stable) }
            if (versions.isNotEmpty()) runCatching {
                cacheDir.createDirectories()
                cacheFile.writeText(json.encodeToString(versions))
            }.onFailure { log("Не удалось сохранить кэш Fabric: ${it.message}") }
            versions
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Ошибка при получении версий загрузчика Fabric для $gameVersion: ${e.message}")
            cached ?: throw e
        }
    }

    suspend fun getForgeVersions(): List<ForgeVersion> {
        // 1. Попытка прочитать новый упрощенный формат от локального сервера
        val simpleCacheFile = cacheDir.resolve("forge_versions_simple.json")
        if (simpleCacheFile.exists()) {
            try {
                val versionsList = json.decodeFromString<List<String>>(simpleCacheFile.readText())
                return versionsList.map { versionString ->
                    // Ожидаемый формат: "1.20.1-47.1.0" (mcVersion-forgeVersion)
                    val mcVersion = versionString.substringBeforeLast('-')
                    val forgeVersion = versionString.substringAfterLast('-')
                    ForgeVersion(mcVersion, forgeVersion)
                }
            } catch (e: Exception) {
                log("Ошибка чтения упрощенного кэша Forge: ${e.message}. Пробуем старый формат.")
            }
        }

        // 2. Резервная логика (старый формат)
        val cacheFile = cacheDir.resolve("forge_versions.json")
        if (!cacheFile.exists()) {
            log("Кэш версий Forge не найден.")
            return emptyList()
        }
        try {
            val response = json.decodeFromString<JsonObject>(cacheFile.readText())
            val promos = response["promos"] as JsonObject
            val versions = mutableListOf<ForgeVersion>()

            promos.entries.forEach { (key, value) ->
                val mcVersion = key.substringBeforeLast('-')
                val type = key.substringAfterLast('-')
                val forgeVersion = value.jsonPrimitive.content

                val existing = versions.find { it.mcVersion == mcVersion }
                if (existing != null) {
                    versions.remove(existing)
                    versions.add(existing.copy(
                        isRecommended = existing.isRecommended || type == "recommended",
                        isLatest = existing.isLatest || type == "latest"
                    ))
                } else {
                    versions.add(ForgeVersion(
                        mcVersion = mcVersion,
                        forgeVersion = forgeVersion,
                        isRecommended = type == "recommended",
                        isLatest = type == "latest"
                    ))
                }
            }
            return versions
        } catch (e: Exception) {
            log("Ошибка при получении версий Forge: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getQuiltGameVersions(): List<QuiltGameVersion> {
        val cacheFile = cacheDir.resolve("quilt_game_versions.json")
        if (!cacheFile.exists()) {
            log("Кэш версий игр Quilt не найден.")
            return emptyList()
        }
        try {
            return json.decodeFromString(cacheFile.readText())
        } catch (e: Exception) {
            log("Ошибка чтения кэша версий игр Quilt: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getQuiltLoaderVersions(gameVersion: String): List<QuiltVersion> {
        val url = "https://meta.quiltmc.org/v3/versions/loader/$gameVersion"
        try {
            val response = client.get(url).body<List<QuiltLoaderApiResponse>>()
            return response.map { QuiltVersion(it.loader.version) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Ошибка при получении версий загрузчика Quilt для $gameVersion: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getNeoForgeVersions(): List<NeoForgeVersion> {
        // 1. Попытка прочитать новый упрощенный формат от локального сервера
        val simpleCacheFile = cacheDir.resolve("neoforge_versions_simple.json")
        if (simpleCacheFile.exists()) {
            try {
                val versionsList = json.decodeFromString<List<String>>(simpleCacheFile.readText())
                return versionsList.mapNotNull { neoVersion ->
                    getMcVersionFromNeoForgeVersion(neoVersion)?.let { mcVersion ->
                        NeoForgeVersion(mcVersion, neoVersion)
                    }
                }
            } catch (e: Exception) {
                log("Ошибка чтения упрощенного кэша NeoForge: ${e.message}. Пробуем старый формат.")
            }
        }

        // 2. Резервная логика (старый формат XML)
        val cacheFile = cacheDir.resolve("neoforge_versions.xml")
        if (!cacheFile.exists()) {
            log("Кэш версий NeoForge не найден.")
            return emptyList()
        }
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(cacheFile.readBytes()))
            val versions = doc.getElementsByTagName("version")
            val versionList = mutableListOf<String>()
            for (i in 0 until versions.length) {
                versionList.add(versions.item(i).textContent)
            }
            return versionList.mapNotNull { neoVersion ->
                getMcVersionFromNeoForgeVersion(neoVersion)?.let { mcVersion ->
                    NeoForgeVersion(mcVersion, neoVersion)
                }
            }
        } catch (e: Exception) {
            log("Ошибка при получении версий NeoForge: ${e.message}")
            return emptyList()
        }
    }

    private fun getMcVersionFromNeoForgeVersion(versionString: String): String? {
        if (versionString.startsWith("0")) return null // April Fools versions

        val parts = versionString.split('.')
        return try {
            if (parts.first().toInt() >= 26) {
                var mcVersion = "${parts[0]}.${parts[1]}"
                if (parts[2] != "0") {
                    mcVersion += ".${parts[2]}"
                }
                val snapshotIdentifier = versionString.split('+').getOrNull(1)
                if (snapshotIdentifier != null) {
                    mcVersion += "-$snapshotIdentifier"
                }
                mcVersion
            } else {
                "1.${parts[0]}.${parts[1]}"
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
