package funlauncher.managers

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

internal object DefaultBuildCovers {
    fun ensurePath(current: String?, backgrounds: Path): String? {
        val available = !current.isNullOrBlank() && runCatching {
            val file = Path.of(current)
            Files.isRegularFile(file) && Files.isReadable(file) && Files.size(file) > 0
        }.getOrDefault(false)
        return if (available) current else randomPath(backgrounds)
    }

    private val names by lazy {
        javaClass.getResourceAsStream("/covers/index.txt")?.bufferedReader()?.use { reader ->
            reader.readLines().filter { it.isNotBlank() && '/' !in it && '\\' !in it }
        }.orEmpty()
    }

    fun randomPath(backgrounds: Path): String? {
        val name = names.randomOrNull() ?: return null
        val directory = backgrounds.resolve("cover")
        Files.createDirectories(directory)
        val destination = directory.resolve(name)
        if (!destination.exists() || Files.size(destination) == 0L) {
            val temporary = Files.createTempFile(directory, "cover-", ".tmp")
            try {
                val stream = checkNotNull(javaClass.getResourceAsStream("/covers/$name")) { "Missing cover: $name" }
                stream.use { Files.copy(it, temporary, StandardCopyOption.REPLACE_EXISTING) }
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
        return destination.toAbsolutePath().toString()
    }
}
