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
    @Test
    fun onlyIncreasingBuildsShowChangesSinceTheRecordedCommit() {
        val file = Files.createTempFile("launcher-history", ".db")
        val db = Database.connect("jdbc:sqlite:$file", "org.sqlite.JDBC")
        try {
            transaction(db) { SchemaUtils.create(LauncherBuildState) }
            fun record(number: Long, hash: String) = LauncherChangelog.record(number, hash) { listOf(it) }
            assertNull(record(10, "first"))
            assertNull(LauncherChangelog.latest())
            assertNull(record(9, "older"))
            assertEquals(LauncherChangelog(10, 13, listOf("first")), record(13, "later"))
            assertNull(record(13, "later"))
            assertEquals(LauncherChangelog(10, 13, listOf("first")), LauncherChangelog.latest())
            assertEquals(LauncherChangelog(13, 14, listOf("later")), record(14, "later"))
            assertEquals(LauncherChangelog(13, 14, listOf("later")), LauncherChangelog.latest())
        } finally {
            TransactionManager.closeAndUnregister(db)
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun savesReleaseNotesOnFreshAndLegacyInstallationsAndOnlyPromptsOnUpgrade() {
        for (existing in listOf(false, true)) {
            val file = Files.createTempFile("launcher-release", ".db")
            val db = Database.connect("jdbc:sqlite:$file", "org.sqlite.JDBC")
            try {
                transaction(db) { SchemaUtils.create(LauncherBuildState) }
                val expected = LauncherChangelog(38, 41, listOf("Release notes"))
                val prompt = LauncherChangelog.record(41, "new", 38L to "old", existing) {
                    assertEquals("old", it)
                    expected.changes
                }
                assertEquals(if (existing) expected else null, prompt)
                assertEquals(expected, LauncherChangelog.latest())
                assertNull(LauncherChangelog.record(41, "new", 38L to "old", true) { error("Repeated read") })
                val next = LauncherChangelog.record(45, "latest", 44L to "skipped", true) { listOf(it) }
                assertEquals(LauncherChangelog(41, 45, listOf("new")), next)
            } finally {
                TransactionManager.closeAndUnregister(db)
                Files.deleteIfExists(file)
            }
        }
    }

    @Test
    fun prefersBilingualNotesAndFallsBackToCommitSubjects() {
        val history = sequenceOf("c\tb\tInternal title\tКнопка в настройках\tButton in Settings",
            "b\ta\tLegacy title\t\t", "a\t\tRoot\t\t")
        assertEquals(listOf("Кнопка в настройках\nButton in Settings", "Legacy title"),
            LauncherChangelog.changesSince("a", history))
    }

    @Test
    fun includesNewlyMergedCommitsEvenWhenListedAfterPreviousBuild() {
        val history = sequenceOf("d\tb c\tMerge", "b\ta\tPrevious", "c\ta\tFeature", "a\t\tRoot")
        assertEquals(listOf("Merge", "Feature"), LauncherChangelog.changesSince("b", history))
        assertEquals(emptyList(), LauncherChangelog.changesSince("d", history))
    }
}
