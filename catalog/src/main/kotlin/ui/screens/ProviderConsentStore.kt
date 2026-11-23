package ui.screens

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

class ProviderConsentStore(private val directory: Path) {
    private fun receipt(providerId: String, revision: String): Path {
        val identifier = Regex("[A-Za-z0-9_-]+")
        require(providerId.matches(identifier) && revision.matches(identifier))
        return directory.resolve(providerId).resolve(revision)
    }

    fun isAccepted(providerId: String, revision: String): Boolean =
        Files.isRegularFile(receipt(providerId, revision))

    fun accept(providerId: String, revision: String) {
        val file = receipt(providerId, revision)
        Files.createDirectories(file.parent)
        try {
            Files.createFile(file)
        } catch (alreadyExists: FileAlreadyExistsException) {
            if (!Files.isRegularFile(file)) throw alreadyExists
        }
    }
}
