package funlauncher.update

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LauncherChangelogTest {
    private val old = "## Alpha 0.1.0\n\n- First release"
    private val middle = "## Alpha 0.2.0\n\n- Second release"
    private val newest = "## Alpha 0.3.0\n\n- Third release"

    @Test fun bundledHistoryContainsInstalledRelease() {
        kotlin.test.assertTrue(LauncherChangelog.changesSince(null, LauncherChangelog.bundled).startsWith("## "))
    }

    @Test fun previewsSkippedReleasesAndIgnoresLineEndings() {
        val updated = "# History\n\n$newest\n\n$middle\n\n$old"
        assertEquals("$newest\n\n$middle", LauncherChangelog.changesSince(old, updated))
        assertEquals("", LauncherChangelog.changesSince(updated, updated.replace("\n", "\r\n")))
        kotlin.test.assertFailsWith<IllegalArgumentException> { LauncherChangelog.changesSince(old, "404") }
    }

    @Test fun showsInstalledChangesOnceAndMigratesLegacyNotes() {
        val file = Files.createTempFile("launcher-history", ".db")
        val db = Database.connect("jdbc:sqlite:$file", "org.sqlite.JDBC")
        try {
            transaction(db) { SchemaUtils.create(LauncherBuildState) }
            assertNull(LauncherChangelog.record(10, "first", old, false))
            val next = "$middle\n\n$old"
            assertEquals(middle, LauncherChangelog.record(11, "second", next, true))
            assertNull(LauncherChangelog.record(11, "second", next, true))
            assertNull(LauncherChangelog.record(10, "first", old, true))
            assertNull(LauncherChangelog.record(12, "same-notes", next, true))
            assertEquals(newest, LauncherChangelog.changesSince("""{"previous":9,"changes":[]}""", "$newest\n\n$next"))
        } finally {
            TransactionManager.closeAndUnregister(db)
            Files.deleteIfExists(file)
        }
    }
}
