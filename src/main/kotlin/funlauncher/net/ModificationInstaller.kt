package funlauncher.net

import kotlinx.coroutines.ensureActive
import funlauncher.MinecraftBuild
import java.nio.file.Path

class ModificationInstaller(private val api: ModrinthApi, private val downloader: ModificationDownloader) {
    suspend fun install(version: Version, build: MinecraftBuild, directory: String, world: String? = null, onProgress: (Float, String) -> Unit) {
        val reasons = incompatibilityReasons(version, build, directory)
        require(reasons.isEmpty()) { reasons.joinToString("; ") }
        require(directory != "datapacks" || world != null) { "Выберите мир для датапака" }
        val resolved = linkedMapOf<String, Version>()
        val visiting = mutableSetOf<String>()
        suspend fun resolve(current: Version) {
            if (current.id in resolved || !visiting.add(current.id)) return
            for (dependency in current.dependencies.filter { it.dependencyType == "required" }) {
                val candidate = dependency.versionId?.let { api.getVersion(it) }
                    ?: dependency.projectId?.let { id -> api.getProjectVersions(id).firstOrNull { isCompatible(it, build, directory) } }
                    ?: error("Не удалось разрешить обязательную зависимость")
                val dependencyReasons = incompatibilityReasons(candidate, build, directory)
                require(dependencyReasons.isEmpty()) { "Несовместимая зависимость ${candidate.name}: ${dependencyReasons.joinToString("; ")}" }
                resolve(candidate)
            }
            visiting.remove(current.id)
            resolved[current.id] = current
        }
        resolve(version)
        require(resolved.values.map { it.projectId }.distinct().size == resolved.size) { "Зависимости требуют разные версии одного проекта" }
        val root = if (directory == "datapacks") {
            val save = ModificationDownloader.safeResolve(Path.of(build.installPath).resolve("saves"), world!!)
            require(java.nio.file.Files.isDirectory(save)) { "Мир не найден" }
            save.resolve("datapacks")
        } else Path.of(build.installPath).resolve(directory)
        val files = resolved.values.map { item ->
            val file = item.files.firstOrNull { it.primary } ?: item.files.firstOrNull() ?: error("Версия не содержит файлов")
            require(!file.filename.contains('/') && !file.filename.contains('\\')) { "Недопустимое имя файла" }
            file to ModificationDownloader.safeResolve(root, file.filename)
        }
        require(files.map { it.second }.distinct().size == files.size) { "Совпадают имена файлов зависимостей" }
        java.nio.file.Files.createDirectories(root)
        val staging = java.nio.file.Files.createTempDirectory(root, ".materia-mods-")
        try {
            // Verify the complete dependency set before touching installed files.
            for ((index, pair) in files.withIndex()) {
                downloader.download(pair.first, staging.resolve(index.toString()).toFile(), onProgress)
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            for ((index, pair) in files.withIndex()) {
                val destination = ModificationDownloader.safeResolve(root, pair.first.filename)
                java.nio.file.Files.createDirectories(destination.parent)
                try {
                    java.nio.file.Files.move(staging.resolve(index.toString()), destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                    java.nio.file.Files.move(staging.resolve(index.toString()), destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } finally {
            staging.toFile().deleteRecursively()
        }
    }

    companion object {
        fun isCompatible(version: Version, build: MinecraftBuild, directory: String): Boolean =
            incompatibilityReasons(version, build, directory).isEmpty()

        fun incompatibilityReasons(version: Version, build: MinecraftBuild, directory: String): List<String> {
            // Imported Fabric/Quilt profiles put the loader before the Minecraft version.
            val profileVersion = Regex("^(?:fabric|quilt)-loader-[^-]+-(.+)$")
                .matchEntire(build.version)?.groupValues?.get(1) ?: build.version
            val minecraft = profileVersion.substringBefore("-fabric-").substringBefore("-forge-")
                .substringBefore("-neoforge-").substringBefore("-quilt-")
            return buildList {
                if (minecraft !in version.gameVersions) {
                    add("Версия Minecraft не совпадает: в инстансе $minecraft; поддерживаются: ${version.gameVersions.joinToString().ifEmpty { "не указаны" }}")
                }
                if (directory !in setOf("resourcepacks", "shaderpacks", "datapacks") &&
                    version.loaders.none { it.equals(build.type.name, true) }) {
                    add("Загрузчик не совпадает: в инстансе ${build.type.name}; поддерживаются: ${version.loaders.joinToString().ifEmpty { "не указаны" }}")
                }
            }
        }
    }
}
