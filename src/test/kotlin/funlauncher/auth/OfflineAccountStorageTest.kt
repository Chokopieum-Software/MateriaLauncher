package funlauncher.auth

import funlauncher.database.dao.AccountDao
import funlauncher.database.dao.Accounts
import java.nio.file.Files
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*

class OfflineAccountStorageTest {
    @Test fun uuidMatchesMinecraftOfflineIdentity() {
        val account = OfflineAccount.create(" Notch ")
        assertEquals("Notch", account.username)
        assertEquals("b50ad385-829d-3141-a216-7e7d7539ba7f", account.uuid)
        assertEquals(account, OfflineAccount.create("Notch"))
        assertNotEquals(account.uuid, OfflineAccount.create("Steve").uuid)
        assertFailsWith<IllegalArgumentException> { OfflineAccount.create("  ") }
    }

    @Test fun secondOfflineProfileSurvivesDeletingFirst() = withDatabase {
        val dao = AccountDao()
        val first = OfflineAccount.create("FirstPlayer")
        val second = OfflineAccount.create("SecondPlayer")
        dao.add(first)
        dao.add(second)
        assertEquals(setOf(first, second), dao.getAll().toSet())
        dao.delete(first)
        assertEquals(listOf(second), dao.getAll())
    }

    @Test fun repairsExistingZeroUuidsWithoutDroppingRows() = withDatabase {
        val zero = "00000000-0000-0000-0000-000000000000"
        transaction {
            for (name in listOf("FirstPlayer", "SecondPlayer")) {
                Accounts.insert {
                    it[username] = name
                    it[uuid] = zero
                    it[accessToken] = "0"
                    it[isLicensed] = false
                    it[type] = "offline"
                }
            }
        }
        val dao = AccountDao()
        val expected = listOf(OfflineAccount.create("FirstPlayer"), OfflineAccount.create("SecondPlayer"))
        assertEquals(expected.toSet(), dao.getAll().toSet())
        transaction { assertEquals(expected.map { it.uuid }.toSet(), Accounts.selectAll().map { it[Accounts.uuid] }.toSet()) }
        assertEquals(expected.toSet(), dao.getAll().toSet()) // Migration is idempotent.
        dao.delete(expected.first())
        assertEquals(listOf(expected.last()), dao.getAll())
    }

    @Test fun legacyInsertAndDeletionDoNotAffectAnotherIdentity() = withDatabase {
        val dao = AccountDao()
        val zero = "00000000-0000-0000-0000-000000000000"
        dao.add(OfflineAccount("FirstPlayer", zero, "0"))
        assertEquals(listOf(OfflineAccount.create("FirstPlayer")), dao.getAll())

        val shared = OfflineAccount.uuidFor("FirstPlayer")
        val other = OfflineAccount("OtherPlayer", shared, "0")
        dao.add(other)
        dao.delete(OfflineAccount.create("FirstPlayer"))
        assertEquals(listOf(other), dao.getAll())
    }

    private fun withDatabase(block: () -> Unit) {
        val file = Files.createTempFile("materia-offline-test", ".db")
        val db = Database.connect("jdbc:sqlite:$file", "org.sqlite.JDBC")
        try {
            transaction(db) { SchemaUtils.create(Accounts) }
            block()
        } finally {
            TransactionManager.closeAndUnregister(db)
            Files.deleteIfExists(file)
        }
    }
}
