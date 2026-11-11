package funlauncher.modpack

import funlauncher.BuildType
import funlauncher.managers.BuildManager
import funlauncher.net.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

class ModpackInstaller(
    private val buildManager: BuildManager,
    private val scope: CoroutineScope
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun install(version: Version, onComplete: () -> Unit, onError: (String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val task = DownloadManager.startTask("Установка модпака: "+version.name, coroutineContext.job)
            var staging: File? = null
            try {
                val root = Files.createTempDirectory("materia-modpack-")
                staging = root.toFile()
                val archive = root.resolve("pack.mrpack")
                val content = root.resolve("content")
                Files.createDirectories(content)
                val downloader = ModificationDownloader()
                val file = version.files.firstOrNull { it.filename.endsWith(".mrpack") } ?: error("Нет файла .mrpack")
                downloader.download(file, archive.toFile()) { progress, status -> DownloadManager.updateTask(task.id, progress * .1f, status) }
                val manifest = ZipFile(archive.toFile()).use { zip ->
                    val entry = zip.getEntry("modrinth.index.json") ?: error("Нет манифеста модпака")
                    val index = zip.getInputStream(entry).bufferedReader().use { json.decodeFromString<ModrinthIndex>(it.readText()) }
                    require(index.formatVersion == 1 && index.game == "minecraft") { "Неподдерживаемый формат модпака" }
                    // Apply client overrides after common overrides, independently of archive order.
                    for (prefix in listOf("overrides/", "client-overrides/")) {
                        for (item in zip.entries().asSequence().filter { it.name.startsWith(prefix) }) {
                            currentCoroutineContext().ensureActive()
                            val relative = item.name.removePrefix(prefix)
                            if (relative.isEmpty()) continue
                            val target = ModificationDownloader.safeResolve(content, relative)
                            if (item.isDirectory) Files.createDirectories(target) else {
                                Files.createDirectories(target.parent)
                                zip.getInputStream(item).use { input -> Files.newOutputStream(target).use { input.copyTo(it) } }
                            }
                        }
                    }
                    index
                }
                val clientFiles = manifest.files.filter { it.env?.get("client") != "unsupported" }
                for ((i, item) in clientFiles.withIndex()) {
                    val destination = ModificationDownloader.safeResolve(content, item.path)
                    require(item.downloads.isNotEmpty()) { "Нет URL файла: "+item.path }
                    var failure: Exception? = null
                    var success = false
                    for (url in item.downloads) {
                        try {
                            downloader.downloadVerified(url, destination, item.fileSize, item.hashes)
                            success = true
                            break
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { failure = e }
                    }
                    if (!success) throw failure ?: IllegalStateException("Не удалось скачать файл")
                    DownloadManager.updateTask(task.id, .1f + (i + 1f) / clientFiles.size * .8f, item.path)
                }
                val deps = manifest.dependencies
                val (type, loader) = when {
                    deps.quiltLoader != null -> BuildType.QUILT to deps.quiltLoader
                    deps.neoforge != null -> BuildType.NEOFORGE to deps.neoforge
                    deps.fabricLoader != null -> BuildType.FABRIC to deps.fabricLoader
                    deps.forge != null -> BuildType.FORGE to deps.forge
                    else -> BuildType.VANILLA to null
                }
                val buildVersion = if (loader == null) deps.minecraft else deps.minecraft+"-"+type.name.lowercase()+"-"+loader
                require(manifest.name.isNotBlank() && manifest.name.trim() !in listOf(".", "..") && manifest.name.none { it in "/\\:*?\"<>|" }) { "Недопустимое имя сборки" }
                var name = manifest.name.trim()
                var suffix = 1
                while (buildManager.loadBuilds().any { it.name.equals(name, true) }) name = manifest.name+" ("+(suffix++)+")"
                currentCoroutineContext().ensureActive()
                // Finish the short commit even if the screen is closed during it.
                withContext(NonCancellable) {
                    val target = buildManager.getBuildPath(name)
                    require(!Files.exists(target)) { "Каталог сборки уже существует" }
                    Files.createDirectories(target.parent)
                    Files.move(content, target)
                    try {
                        buildManager.addBuild(name, buildVersion, type, null)
                    } catch (e: Exception) {
                        target.toFile().deleteRecursively()
                        throw e
                    }
                }
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError("Ошибка установки модпака: "+e.message) }
            } finally {
                staging?.deleteRecursively()
                DownloadManager.removeTask(task.id)
            }
        }
    }
}
