/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.net

import funlauncher.*
import funlauncher.managers.JavaManager
import funlauncher.managers.PathManager
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipInputStream
import kotlin.io.path.*

// Модель данных, где все поля необязательные для отладки
@Serializable
private data class ZuluBundle(
    val name: String? = null,
    var download_url: String? = null, // var, чтобы можно было изменить
    val latest: Boolean? = null,
    val category: String? = null,
    val os: String? = null,
    val arch: String? = null,
    val hw_bitness: Int? = null,
    val java_version: List<Int>? = null,
    val abi: String? = null,
    val javafx: Boolean? = null,
    val release_status: String? = null,
    val package_type: String? = null,
    val checksum: String? = null,
    val size: Long? = null
)

class JavaDownloader(
    private val pathManager: PathManager,
    private val javaManager: JavaManager
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000 // 30 секунд
            connectTimeoutMillis = 30000
        }
    }
    private val jdksDir = pathManager.getAppDataDirectory().resolve("jdks")

    private fun log(message: String) = println("[JavaDownloader] $message")

    fun downloadAndUnpack(version: Int, onComplete: (Result<JavaInfo>) -> Unit) {
        ApplicationScope.scope.launch {
            val task = DownloadManager.startTask("Java $version", coroutineContext.job)
            try {
                if (!jdksDir.exists()) jdksDir.createDirectories()

                DownloadManager.updateTask(task.id, 0.05f, "Поиск дистрибутива...")
                val bundle = findAsset(version) ?: throw Exception("Не удалось найти подходящий дистрибутив Azul Zulu JRE $version")
                log("Выбран для скачивания: ${bundle.name}")

                val archivePath = jdksDir.resolve(bundle.name!!) // Имя не будет null после успешного findAsset
                val tempUnpackDir = jdksDir.resolve("temp_${System.currentTimeMillis()}")
                val destDir = jdksDir.resolve("azul-zulu-$version-jre")

                try {
                    log("Скачивание с ${bundle.download_url} в ${archivePath.absolutePathString()}")
                    downloadFile(bundle.download_url!!, archivePath, task.transferMeter) { progress, status -> // URL не будет null
                        DownloadManager.updateTask(task.id, progress, status)
                    }

                    DownloadManager.updateTask(task.id, 0.9f, "Распаковка...")
                    task.transferMeter.beginPhase(emptyMap(), estimateAvailable = false)
                    log("Распаковка ${archivePath.fileName} в ${tempUnpackDir.absolutePathString()}")
                    if (destDir.exists()) destDir.toFile().deleteRecursively()
                    if (tempUnpackDir.exists()) tempUnpackDir.toFile().deleteRecursively()
                    tempUnpackDir.createDirectories()

                    withContext(Dispatchers.IO) {
                        unpack(archivePath, tempUnpackDir)
                    }
                    log("Перемещение из временной папки в ${destDir.absolutePathString()}")
                    withContext(Dispatchers.IO) {
                        moveFromNestedDirectory(tempUnpackDir, destDir)
                    }

                    DownloadManager.updateTask(task.id, 0.99f, "Поиск java...")
                    val javaPath = withContext(Dispatchers.IO) {
                        findJavaIn(destDir)
                    } ?: throw Exception("Не удалось найти java в распакованной папке: $destDir")
                    log("Найден исполняемый файл: ${javaPath.absolutePathString()}")

                    val javaInfo = javaManager.getJavaInfo(javaPath.toString(), isManaged = true)
                    if (javaInfo != null) {
                        DownloadManager.updateTask(task.id, 1.0f, "Завершено")
                        onComplete(Result.success(javaInfo))
                    } else {
                        throw Exception("Не удалось получить информацию о Java после установки.")
                    }
                } finally {
                    withContext(Dispatchers.IO) {
                        archivePath.deleteIfExists()
                        tempUnpackDir.toFile().deleteRecursively()
                    }
                    log("Очистка временных файлов завершена.")
                }
            } catch (e: Exception) {
                DownloadManager.updateTask(task.id, 0f, "Ошибка: ${e.message}")
                onComplete(Result.failure(e))
                delay(5000)
            } finally {
                DownloadManager.removeTask(task.id)
            }
        }
    }

    private suspend fun findAsset(version: Int): ZuluBundle? {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val (osName, extName) = when {
            os.contains("win") -> "windows" to "zip"
            os.contains("mac") -> "macos" to "tar.gz"
            os.contains("linux") -> "linux" to "tar.gz"
            else -> throw UnsupportedOperationException("Неподдерживаемая ОС: $os")
        }

        val archName = when (arch) {
            "x86_64", "amd64" -> "x64"
            "aarch64" -> "aarch64"
            else -> throw UnsupportedOperationException("Неподдерживаемая архитектура: $arch")
        }

        val url = "https://api.azul.com/zulu/download/community/v1.0/bundles?jdk_version=$version&os=${osName}&arch=${archName}&hw_bitness=64&release_status=ga&ext=$extName&bundle_type=jre&javafx=false"
        log("Запрос к Azul Zulu API: $url")

        val bundles = try {
            client.get(url).body<List<ZuluBundle>>()
        } catch (e: Exception) {
            log("Ошибка при запросе к Azul Zulu API: ${e.message}")
            e.printStackTrace()
            return null
        }

        if (bundles.isEmpty()) {
            log("API не вернул бандлов для Java $version.")
            return null
        }

        log("Полученные бандлы (количество: ${bundles.size}): ${bundles.joinToString { it.name ?: "no-name" }}")

        // Сортируем бандлы, чтобы найти самую последнюю версию
        val latestBundle = bundles.maxWithOrNull(compareBy(
            { it.java_version?.getOrNull(0) ?: 0 },
            { it.java_version?.getOrNull(1) ?: 0 },
            { it.java_version?.getOrNull(2) ?: 0 },
            { it.java_version?.getOrNull(3) ?: 0 }
        ))

        if (latestBundle?.name == null) {
            log("Не удалось найти подходящий бандл с именем файла.")
            return null
        }

        // Конструируем URL и обновляем объект
        latestBundle.download_url = "https://cdn.azul.com/zulu/bin/${latestBundle.name}"
        log("Сконструирован URL для скачивания: ${latestBundle.download_url}")

        return latestBundle
    }

    private suspend fun downloadFile(url: String, path: Path, meter: TransferMeter, onProgress: (Float, String) -> Unit) {
        client.prepareGet(url).execute { httpResponse ->
            if (httpResponse.status.value == 520) {
                throw Exception("Ошибка 520: Веб-сервер вернул неизвестную ошибку. Возможно, CDN не может связаться с сервером Azul.")
            }
            if (!httpResponse.status.isSuccess()) {
                throw Exception("Ошибка загрузки: ${httpResponse.status}")
            }

            val channel: ByteReadChannel = httpResponse.body()
            val totalBytes = httpResponse.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
            meter.beginPhase(mapOf(path.toString() to totalBytes.takeIf { it > 0 }))
            var bytesRead = 0L

            FileOutputStream(path.toFile()).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = channel.readAvailable(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    bytesRead += read
                    meter.received(path.toString(), read.toLong())

                    if (totalBytes > 0) {
                        val progress = 0.1f + (bytesRead.toFloat() / totalBytes.toFloat()) * 0.8f
                        onProgress(progress, "Загрузка... ${(bytesRead / 1024 / 1024)} MB")
                    } else {
                        onProgress(0.5f, "Загрузка... ${(bytesRead / 1024 / 1024)} MB")
                    }
                }
            }
        }
    }

    private fun unpack(source: Path, destination: Path) {
        when {
            source.toString().endsWith(".zip") -> unpackZip(source, destination)
            source.toString().endsWith(".tar.gz") -> unpackTarGz(source, destination)
            else -> throw UnsupportedOperationException("Формат архива не поддерживается: $source")
        }
    }

    private fun unpackZip(source: Path, destination: Path) {
        ZipInputStream(Files.newInputStream(source)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = destination.resolve(entry.name).normalize()
                if (!newFile.startsWith(destination)) throw SecurityException("Invalid zip entry")
                if (entry.isDirectory) {
                    newFile.createDirectories()
                } else {
                    newFile.parent.createDirectories()
                    newFile.outputStream().use { fos -> zis.copyTo(fos) }
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun unpackTarGz(source: Path, destination: Path) {
        TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(Files.newInputStream(source)))).use { tais ->
            var entry = tais.nextEntry
            while (entry != null) {
                val newFile = destination.resolve(entry.name).normalize()
                if (!newFile.startsWith(destination)) throw SecurityException("Invalid tar entry")

                when {
                    entry.isDirectory -> newFile.createDirectories()
                    entry.isSymbolicLink -> {
                        newFile.parent.createDirectories()
                        Files.createSymbolicLink(newFile, Paths.get(entry.linkName))
                    }
                    else -> {
                        newFile.parent.createDirectories()
                        newFile.outputStream().use { fos -> tais.copyTo(fos) }
                        if (entry.mode and "111".toInt(8) != 0) {
                            newFile.toFile().setExecutable(true, false)
                        }
                    }
                }
                entry = tais.nextEntry
            }
        }
    }

    private fun moveFromNestedDirectory(sourceDir: Path, destDir: Path) {
        val files = sourceDir.listDirectoryEntries()
        if (files.size == 1 && files.first().isDirectory()) {
            val nestedDir = files.first()
            log("Обнаружена вложенная папка: ${nestedDir.fileName}, перемещаю ее в ${destDir.fileName}")
            nestedDir.moveTo(destDir, overwrite = true)
        } else {
            log("Вложенная папка не найдена, перемещаю содержимое ${sourceDir.fileName} в ${destDir.fileName}")
            sourceDir.moveTo(destDir, overwrite = true)
        }
    }

    private fun findJavaIn(dir: Path): Path? {
        return Files.walk(dir, 5)
            .filter { it.fileName.toString() == "java" || it.fileName.toString() == "java.exe" }
            .findFirst()
            .orElse(null)
    }
}
