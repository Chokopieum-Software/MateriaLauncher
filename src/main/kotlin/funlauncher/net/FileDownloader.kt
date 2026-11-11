/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.net

import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.game.rulesApply
import funlauncher.game.nativeArch
import funlauncher.game.mavenArtifactPath
import funlauncher.game.AssetIndex
import funlauncher.game.VersionInfo
import funlauncher.managers.BuildManager
import funlauncher.managers.PathManager
import funlauncher.utils.Hashing
import io.ktor.client.call.*
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.io.IOException
import java.net.URI
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.*

internal fun downloadFailure(url: String, description: String, cause: Exception): IOException {
    val host = URI(url).host ?: url
    val isDnsFailure = generateSequence<Throwable>(cause) { it.cause }
        .any { it is UnresolvedAddressException || it is UnknownHostException }
    val detail = if (isDnsFailure) {
        "Не удалось определить IP-адрес сервера $host. Проверьте подключение к интернету, DNS и настройки VPN/прокси."
    } else {
        cause.message?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName
    }
    return IOException("Не удалось скачать $description с $host. $detail", cause)
}

/**
 * Handles downloading of game files, including libraries and assets.
 */
class FileDownloader(
    private val versionInfo: VersionInfo,
    private val pathManager: PathManager,
    private val buildManager: BuildManager,
    private val transferMeter: TransferMeter = TransferMeter()
) {
    private val client = Network.client
    private val json = Network.json

    private val globalAssetsDir: Path = pathManager.getGlobalAssetsDir()
    private val globalVersionsDir: Path = pathManager.getGlobalVersionsDir()
    private val globalLibrariesDir: Path = pathManager.getGlobalLibrariesDir()

    private val availableCores = Runtime.getRuntime().availableProcessors()
    private val librarySemaphore = Semaphore(availableCores.coerceIn(2, 8))
    private val assetSemaphore = Semaphore((availableCores * 2).coerceIn(4, 16))

    private fun log(message: String) = println("[FileDownloader] $message")

    suspend fun downloadRequiredFiles(build: MinecraftBuild, onProgress: (Float, String) -> Unit) {
        // 1. Download Client JAR
        val gameVersionForJar = getGameVersionForJar(build)
        val clientJarPath = globalVersionsDir.resolve(gameVersionForJar).resolve("$gameVersionForJar.jar")
        versionInfo.downloads?.client?.let {
            transferMeter.beginPhase(mapOf(clientJarPath.toString() to it.size.takeIf { size -> size > 0 }))
            downloadFile(it.url, clientJarPath, "Client JAR", it.size, it.sha1)
        } ?: log("Client JAR download URL not found for version ${versionInfo.id}, assuming it's provided by a modloader.")

        // 2. Download Libraries
        val libraries = versionInfo.libraries.filter { isRuleApplicable(it.rules ?: emptyList()) }
        val libCounter = AtomicInteger(0)
        val librarySizes = buildMap<String, Long?> {
            for (lib in libraries) {
                lib.downloads?.artifact?.let { artifact ->
                    put(globalLibrariesDir.resolve(artifact.path).toString(), artifact.size.takeIf { it > 0 })
                }
                if (lib.downloads == null && lib.natives == null) {
                    put(globalLibrariesDir.resolve(getArtifactPath(lib.name)).toString(), null)
                }
                lib.natives?.get(getOsName())?.replace("\${arch}", getArch())?.let { classifier ->
                    val artifact = lib.downloads?.classifiers?.get(classifier)
                    if (artifact != null) {
                        put(globalLibrariesDir.resolve(artifact.path).toString(), artifact.size.takeIf { it > 0 })
                    } else if (lib.downloads == null) {
                        put(globalLibrariesDir.resolve(getArtifactPath(lib.name, classifier)).toString(), null)
                    }
                }
            }
        }
        transferMeter.beginPhase(librarySizes)
        coroutineScope {
            libraries.map { lib ->
                async(Dispatchers.IO) {
                    librarySemaphore.acquire()
                    try {
                        downloadLibrary(lib)
                        val c = libCounter.incrementAndGet()
                        onProgress(c.toFloat() / libraries.size * 0.5f, "Libs: ${lib.name.substringAfterLast(':')}")
                    } finally {
                        librarySemaphore.release()
                    }
                }
            }.awaitAll()
        }

        // 3. Download Asset Index and Assets
        versionInfo.assetIndex?.let { assetIndexInfo ->
            val idxFile = globalAssetsDir.resolve("indexes").resolve("${assetIndexInfo.id}.json")
            transferMeter.beginPhase(mapOf(idxFile.toString() to null))
            downloadFile(assetIndexInfo.url, idxFile, "Asset Index")
            val idx = json.decodeFromString<AssetIndex>(idxFile.readText())
            downloadAssetsInParallel(idx) { progress, status -> onProgress(0.5f + progress * 0.5f, status) }
        }
    }

    private suspend fun downloadLibrary(lib: VersionInfo.Library) {
        val defaultRepo = "https://libraries.minecraft.net/"

        // Main artifact
        lib.downloads?.artifact?.let { artifact ->
            val path = globalLibrariesDir.resolve(artifact.path)
            downloadFile(artifact.url, path, "Lib: ${lib.name}", artifact.size, artifact.sha1)
        }
        // Only legacy entries without native artifacts need a Maven-coordinate fallback.
        // A downloads block containing classifiers alone declares no main JAR.
        if (lib.downloads == null && lib.natives == null) {
            val artifactPath = getArtifactPath(lib.name)
            val path = globalLibrariesDir.resolve(artifactPath)
            val url = (lib.url ?: defaultRepo) + artifactPath
            downloadFile(url, path, "Lib: ${lib.name}")
        }

        // Native artifact handling
        if (lib.natives != null) {
            // Select native artifacts from version metadata
            lib.natives.get(getOsName())?.let { classifierTemplate ->
                val classifier = classifierTemplate.replace("\${arch}", getArch())
                if (lib.downloads != null) {
                    lib.downloads.classifiers?.get(classifier)?.let { nativeArtifact ->
                        val path = globalLibrariesDir.resolve(nativeArtifact.path)
                        downloadFile(nativeArtifact.url, path, "Native: ${lib.name}", nativeArtifact.size, nativeArtifact.sha1)
                    }
                } else {
                    // Legacy handling for old versions (1.5.2, etc.)
                    val artifactPath = getArtifactPath(lib.name, classifier)
                    val path = globalLibrariesDir.resolve(artifactPath)
                    val url = (lib.url ?: defaultRepo) + artifactPath
                    downloadFile(url, path, "Native: ${lib.name}")
                }
            }

        }
    }

    private suspend fun downloadAssetsInParallel(idx: AssetIndex, onProgress: (Float, String) -> Unit) {
        val assets = idx.objects.entries.toList()
        val counter = AtomicInteger(0)
        transferMeter.beginPhase(assets.associate { (_, asset) ->
            globalAssetsDir.resolve("objects").resolve(asset.hash.substring(0, 2))
                .resolve(asset.hash).toString() to asset.size
        })
        coroutineScope {
            assets.map { (_, asset) ->
                async(Dispatchers.IO) {
                    assetSemaphore.acquire()
                    try {
                        val p = asset.hash.substring(0, 2)
                        val path = globalAssetsDir.resolve("objects").resolve(p).resolve(asset.hash)
                        downloadFile(
                            "https://resources.download.minecraft.net/$p/${asset.hash}",
                            path,
                            "Asset",
                            asset.size,
                            asset.hash
                        )
                        val c = counter.incrementAndGet()
                        if (c % 100 == 0) onProgress(c.toFloat() / assets.size, "Assets: $c/${assets.size}")
                    } finally {
                        assetSemaphore.release()
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun downloadFile(url: String, path: Path, desc: String, size: Long? = null, sha1: String? = null) {
        currentCoroutineContext().ensureActive()
        // Generated/loader metadata uses zero and blank hashes for unknown values.
        val expectedSize = size?.takeIf { it > 0 }
        val expectedSha1 = sha1?.trim()?.takeIf { it.isNotEmpty() }
        if (path.exists()) {
            if (path.fileSize() == 0L || (expectedSize != null && path.fileSize() != expectedSize)) {
                log("File $desc has wrong size. Deleting and redownloading.")
                path.deleteIfExists()
            } else if (expectedSha1 != null && !Hashing.getSha1(path).equals(expectedSha1, ignoreCase = true)) {
                log("File $desc has wrong hash. Deleting and redownloading.")
                path.deleteIfExists()
            } else {
                transferMeter.complete(path.toString())
                return // File is valid
            }
        }

        val maxRetries = 3
        val retryDelay = 3000L

        for (attempt in 1..maxRetries) {
            currentCoroutineContext().ensureActive()
            transferMeter.retry(path.toString(), expectedSize)
            var lastReceived = 0L
            try {
                log("Downloading $desc (attempt $attempt/$maxRetries)...")
                path.parent.createDirectories()
                val resp = client.get(url) {
                    onDownload { bytesReceived, _ ->
                        transferMeter.received(path.toString(), (bytesReceived - lastReceived).coerceAtLeast(0))
                        lastReceived = bytesReceived
                    }
                    timeout {
                        // Large JARs may take longer than the metadata client's one-minute limit.
                        requestTimeoutMillis = 10 * 60 * 1000L
                        connectTimeoutMillis = 30_000L
                        socketTimeoutMillis = 60_000L
                    }
                }

                if (resp.status.value == 200) {
                    path.writeBytes(resp.body())
                    log("Successfully downloaded $desc")
                    // Verify after download
                    if (path.fileSize() == 0L || (expectedSize != null && path.fileSize() != expectedSize)) {
                        throw Exception("Downloaded file size mismatch")
                    }
                    if (expectedSha1 != null && !Hashing.getSha1(path).equals(expectedSha1, ignoreCase = true)) {
                        throw Exception("Downloaded file hash mismatch")
                    }
                    transferMeter.complete(path.toString())
                    return
                }
                throw Exception("HTTP ${resp.status.value}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                runCatching { path.deleteIfExists() }
                val failure = downloadFailure(url, desc, e)
                log("Error downloading $desc from $url: ${failure.message}")
                if (attempt < maxRetries) {
                    delay(retryDelay)
                } else {
                    throw failure
                }
            }
        }
    }

    private fun getArtifactPath(name: String, classifier: String? = null): String = mavenArtifactPath(name, classifier)

    private fun getGameVersionForJar(build: MinecraftBuild): String = when (build.type) {
        BuildType.FABRIC -> build.version.split("-fabric-").first()
        BuildType.FORGE -> build.version.split("-forge-").first()
        BuildType.QUILT -> build.version.split("-quilt-").first()
        BuildType.NEOFORGE -> build.version.split("-neoforge-").first()
        else -> versionInfo.id
    }

    private fun isRuleApplicable(rules: List<VersionInfo.Rule>): Boolean = rulesApply(rules, getOsName())

    private fun getOsName(): String = when {
        System.getProperty("os.name").lowercase().contains("win") -> "windows"
        System.getProperty("os.name").lowercase().contains("mac") -> "osx"
        else -> "linux"
    }

    private fun getArch(): String = nativeArch()
}
