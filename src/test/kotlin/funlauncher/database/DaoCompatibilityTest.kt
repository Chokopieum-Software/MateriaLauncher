package funlauncher.database

import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.AppSettings
import funlauncher.SettingsManager
import funlauncher.managers.BuildManager
import funlauncher.managers.PathManager
import kotlinx.coroutines.runBlocking
import funlauncher.auth.OfflineAccount
import funlauncher.database.dao.AccountDao
import funlauncher.database.dao.Accounts
import funlauncher.database.dao.BuildDao
import funlauncher.database.dao.Builds
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DaoCompatibilityTest {
    @Test
    fun instanceSettingsAreCapturedOnce() = runBlocking {
        val root = Files.createTempDirectory("materia-instance-settings-")
        val database = Database.connect("jdbc:sqlite:${root.resolve("test.db")}", "org.sqlite.JDBC")
        try {
            transaction(database) { SchemaUtils.create(Builds) }
            val paths = PathManager(root)
            val settingsManager = SettingsManager(paths)
            val initial = AppSettings(javaPath = "java-original", maxRamMb = 2048, javaArgs = "-Dtest=original", envVars = "TEST=original")
            settingsManager.saveSettings(initial)
            val manager = BuildManager(paths)
            manager.addBuild("First", "1.21.1", BuildType.VANILLA, null)
            BuildDao().add(MinecraftBuild("Legacy", "1.21.1", BuildType.VANILLA, root.resolve("legacy").toString()))
            val before = manager.loadBuilds().associateBy { it.name }
            assertEquals(initial.maxRamMb, before.getValue("Legacy").maxRamMb)

            val changed = initial.copy(javaPath = "java-new", maxRamMb = 8192, javaArgs = "-Dtest=new", envVars = "TEST=new")
            settingsManager.saveSettings(changed)
            val after = manager.loadBuilds().associateBy { it.name }
            assertEquals(before, after)
            val first = after.getValue("First")
            assertEquals(initial.javaPath, first.javaPath)
            assertEquals(initial.maxRamMb, first.maxRamMb)
            assertEquals(initial.javaArgs, first.javaArgs)
            assertEquals(initial.envVars, first.envVars)
            manager.addBuild("Second", "1.21.1", BuildType.VANILLA, null)
            val second = manager.loadBuilds().single { it.name == "Second" }
            assertEquals(changed.javaPath, second.javaPath)
            assertEquals(changed.maxRamMb, second.maxRamMb)
            assertEquals(changed.javaArgs, second.javaArgs)
            assertEquals(changed.envVars, second.envVars)
        } finally {
            TransactionManager.closeAndUnregister(database)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun accountsAndBuildsRoundTripThroughSqlite() {
        val dbFile = Files.createTempFile("materia-dao-test-", ".db")
        val database = Database.connect("jdbc:sqlite:$dbFile", "org.sqlite.JDBC")
        try {
            transaction(database) { SchemaUtils.create(Accounts, Builds) }

            val accounts = AccountDao()
            val account = OfflineAccount("TestPlayer", "00000000-0000-0000-0000-000000000001", "0")
            accounts.add(account)
            assertTrue(accounts.exists(account.username))
            assertEquals(account, accounts.getAll().single())
            accounts.delete(account)
            assertFalse(accounts.exists(account.username))

            val builds = BuildDao()
            val build = MinecraftBuild("Test build", "1.21.1", BuildType.VANILLA, "test-instance")
            builds.add(build)
            assertTrue(builds.exists(build.name))
            assertEquals(build, builds.getAll().single())
            val renamed = build.copy(name = "Renamed build", maxRamMb = 2048)
            builds.update(build.name, renamed)
            assertFalse(builds.exists(build.name))
            assertEquals(renamed, builds.getAll().single())
            builds.delete(renamed.name)
            assertTrue(builds.getAll().isEmpty())
        } finally {
            TransactionManager.closeAndUnregister(database)
            Files.deleteIfExists(dbFile)
        }
    }
}
