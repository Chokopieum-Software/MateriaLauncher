package funlauncher.managers

import funlauncher.net.Network
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

class CacheManager(
    pathManager: PathManager,
    private val settingsManager: funlauncher.SettingsManager? = null // Optional for now to avoid breaking changes immediately, but needed for ETag storage
) {
    private val cacheDir = pathManager.getCacheDir().toFile()
    private val imageCacheDir = File(cacheDir, "images").also { it.mkdirs() }
    private val metaCacheFile = File(cacheDir, "meta_cache.json")
    private val etagFile = File(cacheDir, "meta_etag.txt")

    // URL сервера кэширования (localhost:8080)
    private val cacheServerUrl = "http://localhost:8080/getcache/all"

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun getJsonCacheFile(key: String): File {
        val hashedKey = sha256(key)
        return File(cacheDir, "$hashedKey.json")
    }

    fun getImageCacheFile(url: String): File {
        val hashedKey = sha256(url)
        return File(imageCacheDir, hashedKey)
    }

    suspend inline fun <reified T> getOrFetch(key: String, crossinline fetcher: suspend () -> T): T? {
        val cacheFile = getJsonCacheFile(key)

        if (cacheFile.exists()) {
            try {
                val cachedData = json.decodeFromString<T>(cacheFile.readText())
                println("CacheManager: Успешно загружены данные из кэша для ключа: $key")
                return cachedData
            } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
                println("CacheManager: Ошибка чтения или десериализации кэша для ключа: $key. Удаление файла. Ошибка: ${e.stackTraceToString()}")
                cacheFile.delete()
            }
        }

        return try {
            println("CacheManager: Данные для ключа: $key не найдены в кэше или кэш поврежден. Выполняем fetcher().")
            val fetchedData = fetcher()
            cacheFile.writeText(json.encodeToString(fetchedData))
            println("CacheManager: Успешно получены и закэшированы данные для ключа: $key")
            fetchedData
        } catch (e: CancellationException) {
            // This is not an error, but a normal cancellation of a coroutine.
            // We re-throw it to let the coroutine system handle it.
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("CacheManager: Ошибка при выполнении fetcher() для ключа: $key. Ошибка: ${e.stackTraceToString()}")
            null
        }
    }

    /**
     * Обновляет метаданные кэша.
     * Сначала пытается получить данные с локального сервера (localhost:8080).
     * Если сервер недоступен, использует резервную логику (скачивание с официальных серверов).
     */
    suspend fun refreshMetadata(onStatusUpdate: (String) -> Unit) {
        if (updateMetadataCache()) {
            onStatusUpdate("Metadata updated from local server.")
        } else {
            onStatusUpdate("Local cache server unavailable. Using official servers...")
            updateFromOfficialServers(onStatusUpdate)
        }
    }

    /**
     * Пытается обновить кэш метаданных с локального сервера.
     * Возвращает true, если обновление прошло успешно (200 OK) или данные актуальны (304 Not Modified).
     * Возвращает false, если сервер недоступен или произошла ошибка.
     */
    private suspend fun updateMetadataCache(): Boolean {
        println("CacheManager: Попытка обновления метаданных с сервера $cacheServerUrl")
        try {
            val savedEtag = if (etagFile.exists()) etagFile.readText().trim() else null
            
            val response = Network.client.get(cacheServerUrl) {
                if (savedEtag != null) {
                    header(HttpHeaders.IfNoneMatch, savedEtag)
                }
            }

            return when (response.status) {
                HttpStatusCode.NotModified -> {
                    println("CacheManager: Кэш актуален (304 Not Modified). Используем локальные данные.")
                    true
                }
                HttpStatusCode.OK -> {
                    val jsonString = response.bodyAsText()
                    val newEtag = response.headers[HttpHeaders.ETag]

                    // Не подтверждаем новую версию кэша до успешного разбора и записи данных.
                    processServerResponse(jsonString)

                    // 1. Сохраняем JSON в файл
                    metaCacheFile.writeText(jsonString)

                    // 2. Сохраняем новый ETag
                    if (newEtag != null) {
                        etagFile.writeText(newEtag)
                    }
                    
                    // 3. Разбираем и сохраняем отдельные файлы кэша для совместимости со старой логикой

                    println("CacheManager: Кэш успешно обновлен с сервера.")
                    true
                }
                HttpStatusCode.ServiceUnavailable -> {
                    println("CacheManager: Сервер кэширования еще не готов (503).")
                    false
                }
                else -> {
                    println("CacheManager: Ошибка сервера: ${response.status}. Будет использована резервная логика.")
                    false
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("CacheManager: Не удалось подключиться к серверу кэширования: ${e.message}. Будет использована резервная логика.")
            return false
        }
    }

    private suspend fun updateFromOfficialServers(onStatusUpdate: (String) -> Unit) {
        val oneMinuteAgo = System.currentTimeMillis() - 60 * 1000

        fetchAndCacheIfOld(
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
            File(cacheDir, "vanilla_versions.json"),
            "Vanilla",
            onStatusUpdate,
            oneMinuteAgo
        )
        fetchAndCacheIfOld(
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json",
            File(cacheDir, "forge_versions.json"),
            "Forge",
            onStatusUpdate,
            oneMinuteAgo
        )
        fetchAndCacheIfOld(
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml",
            File(cacheDir, "neoforge_versions.xml"),
            "NeoForge",
            onStatusUpdate,
            oneMinuteAgo
        )
        fetchAndCacheIfOld(
            "https://meta.fabricmc.net/v2/versions/game",
            File(cacheDir, "fabric_game_versions.json"),
            "Fabric Game Versions",
            onStatusUpdate,
            oneMinuteAgo
        )
        fetchAndCacheIfOld(
            "https://meta.quiltmc.org/v3/versions/game",
            File(cacheDir, "quilt_game_versions.json"),
            "Quilt Game Versions",
            onStatusUpdate,
            oneMinuteAgo
        )
    }

    private suspend fun fetchAndCacheIfOld(url: String, file: File, name: String, onStatusUpdate: (String) -> Unit, thresholdTime: Long) {
        if (file.exists() && file.lastModified() > thresholdTime) {
            println("Skipping $name metadata fetch (recently updated).")
            return
        }
        fetchAndCache(url, file, name, onStatusUpdate)
    }

    private suspend fun fetchAndCache(url: String, file: File, name: String, onStatusUpdate: (String) -> Unit) {
        try {
            onStatusUpdate("Fetching $name metadata...")
            println("Fetching $name metadata from $url...")
            val response = Network.client.get(url)
            check(response.status.value in 200..299) { "HTTP ${response.status.value}: $name" }
            val data = response.body<ByteArray>()
            file.writeBytes(data)
            println("Successfully cached $name metadata to ${file.absolutePath}")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onStatusUpdate("Failed to fetch $name metadata.")
            println("Failed to fetch or cache $name metadata: ${e.message}")
            throw e
        }
    }

    /**
     * Разбирает ответ от сервера и сохраняет его в файлы, ожидаемые VersionManager и VersionMetadataFetcher.
     */
    private fun processServerResponse(jsonString: String) {
        try {
            val root = json.decodeFromString<kotlinx.serialization.json.JsonObject>(jsonString)
            
            // Сохраняем vanilla_versions.json
            root["vanilla"]?.let { 
                File(cacheDir, "vanilla_versions.json").writeText(json.encodeToString(it)) 
            }

            // Сохраняем fabric_game_versions.json
            root["fabric"]?.let {
                File(cacheDir, "fabric_game_versions.json").writeText(json.encodeToString(it))
            }

            // Сохраняем quilt_game_versions.json
            root["quilt"]?.let {
                File(cacheDir, "quilt_game_versions.json").writeText(json.encodeToString(it))
            }

            // Сохраняем forge_versions_simple.json
            root["forge"]?.let {
                 File(cacheDir, "forge_versions_simple.json").writeText(json.encodeToString(it))
            }

            // Сохраняем neoforge_versions_simple.json
            root["neoforge"]?.let {
                File(cacheDir, "neoforge_versions_simple.json").writeText(json.encodeToString(it))
            }

        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("CacheManager: Ошибка при разборе ответа сервера: ${e.message}")
            throw e
        }
    }

    suspend fun prefetchCaches(vararg prefetchTasks: Pair<String, suspend () -> Any>) = coroutineScope {
        // Сначала пробуем обновить с локального сервера
        updateMetadataCache()

        for ((key, fetcher) in prefetchTasks) {
            async {
                getOrFetch(key, fetcher)
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
