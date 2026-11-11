package ui.screens

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class PackMetadataTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun description(source: String) = json.decodeFromString<PackMcMeta>(source).pack.description.toPackDescription()

    @Test fun acceptsAllDescriptionShapes() {
        assertEquals("Simple", description("""{"pack":{"description":"Simple","pack_format":15}}"""))
        assertEquals("Please enable this pack", description("""{"pack":{"description":{"translate":"fo.resourcePack.modmenuhelper","fallback":"§cPlease enable this pack§r"},"min_format":[97,1],"max_format":[97,1]}}"""))
        assertEquals("unavailable.key", description("""{"pack":{"description":{"translate":"unavailable.key"}}}"""))
        assertEquals("Hello world!", description("""{"pack":{"description":[{"text":"Hello ","color":"red"},{"text":"world","extra":[{"text":"!"}]}]}}"""))
        assertEquals("", description("""{"pack":{}}"""))
    }

    @Test fun substitutesTranslationArguments() {
        assertEquals("Hello Alex", description("""{"pack":{"description":{"translate":"hello","fallback":"Hello %s","with":[{"text":"Alex"}]}}}"""))
        assertEquals("B A %", description("""{"pack":{"description":{"translate":"hello","fallback":"%2\u0024s %1\u0024s %%","with":["A","B"]}}}"""))
    }

    @Test fun readsZipDirectoryAndDisabledPack() = runBlocking {
        val root = Files.createTempDirectory("pack-metadata-")
        val metadata = """{"pack":{"description":{"text":"Pack","extra":[{"text":" description"}]},"min_format":[97,1]}}"""
        try {
            val directory = root.resolve("folder")
            Files.createDirectories(directory)
            Files.writeString(directory.resolve("pack.mcmeta"), metadata)
            assertEquals("Pack description", parseResourcePack(directory.toFile()).name)
            val zip = root.resolve("pack.zip.disabled")
            ZipOutputStream(Files.newOutputStream(zip)).use {
                it.putNextEntry(ZipEntry("pack.mcmeta"))
                it.write(metadata.toByteArray())
                it.closeEntry()
            }
            val pack = parseResourcePack(zip.toFile())
            assertEquals("Pack description", pack.name)
            assertFalse(pack.isEnabled)
        } finally { root.toFile().deleteRecursively() }
    }
}
