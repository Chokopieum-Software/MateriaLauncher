package funlauncher.auth

import funlauncher.database.dao.AccountDao
import funlauncher.database.dao.Accounts
import java.nio.file.Files
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*

class MicrosoftAccountStorageTest {
    @Test fun discardsTokensAndUpdatesExistingIdentityAfterRename() {
        val file = Files.createTempFile("materia-auth-test", ".db")
        val db = Database.connect("jdbc:sqlite:$file", "org.sqlite.JDBC")
        try {
            transaction(db) { SchemaUtils.create(Accounts) }
            val dao = AccountDao()
            val original = MicrosoftAccount("Player", "123456781234123412341234567890ab", "secret-token")
            dao.saveMicrosoft(original)
            transaction(db) { assertEquals("", Accounts.selectAll().single()[Accounts.accessToken]) }
            val renamed = original.copy(username = "Renamed", skinUrl = "https://example.org/skin")
            dao.saveMicrosoft(renamed)
            assertEquals(listOf(renamed.copy(accessToken = "")), dao.getAll())

            // Simulate a token persisted by an earlier launcher version.
            transaction(db) { Accounts.update { it[accessToken] = "legacy-token" } }
            dao.clearMicrosoftTokens()
            transaction(db) { assertEquals("", Accounts.selectAll().single()[Accounts.accessToken]) }
        } finally {
            TransactionManager.closeAndUnregister(db)
            Files.deleteIfExists(file)
        }
    }
}
