package funlauncher.managers

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class DefaultBuildCoversTest {
    @Test
    fun missingAndEmptyCoversAreReplacedButExistingCoverIsKept() {
        val directory = Files.createTempDirectory("materia-repair-cover")
        try {
            val valid = directory.resolve("custom.png")
            Files.write(valid, byteArrayOf(1, 2, 3))
            assertEquals(valid.toString(), DefaultBuildCovers.ensurePath(valid.toString(), directory))
            val empty = Files.createFile(directory.resolve("empty.png"))
            listOf(null, "", directory.resolve("missing.png").toString(), empty.toString()).forEach { path ->
                val replacement = Path.of(assertNotNull(DefaultBuildCovers.ensurePath(path, directory)))
                assertTrue(Files.isRegularFile(replacement))
                assertTrue(Files.size(replacement) > 0)
                assertTrue(replacement.startsWith(directory.resolve("cover")))
                assertEquals(replacement.toString(), DefaultBuildCovers.ensurePath(replacement.toString(), directory))
            }
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test
    fun packagedCoversCanBeSelectedOutsideSourceDirectory() {
        val directory = Files.createTempDirectory("materia-packaged-cover")
        var selected: Path? = null
        try {
            selected = Path.of(assertNotNull(DefaultBuildCovers.randomPath(directory)))
            assertTrue(selected.startsWith(directory.resolve("cover")))
            assertTrue(Files.size(selected) > 0)
            val names = javaClass.getResourceAsStream("/covers/index.txt")!!.bufferedReader().use { it.readLines() }
            assertTrue(selected.fileName.toString() in names)
            names.forEach { name ->
                assertNotNull(javaClass.getResourceAsStream("/covers/$name")).close()
            }
        } finally {
            selected?.let { Files.deleteIfExists(it) }
            Files.deleteIfExists(directory.resolve("cover"))
            Files.deleteIfExists(directory)
        }
    }
}
