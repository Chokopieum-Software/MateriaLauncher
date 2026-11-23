package ui.screens

import java.nio.file.Files
import kotlin.test.*

class ProviderConsentStoreTest {
    @Test fun persistsOnlyExplicitAcceptanceForTheSameProviderAndRevision() {
        val directory = Files.createTempDirectory("provider-consent")
        try {
            val store = ProviderConsentStore(directory)
            assertFalse(store.isAccepted("modrinth", "v1"))
            store.accept("modrinth", "v1")
            store.accept("modrinth", "v1")
            val reopened = ProviderConsentStore(directory)
            assertTrue(reopened.isAccepted("modrinth", "v1"))
            assertFalse(reopened.isAccepted("modrinth", "v2"))
            assertFalse(reopened.isAccepted("other", "v1"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
