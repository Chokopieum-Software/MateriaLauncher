package funlauncher.auth

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import funlauncher.managers.PathManager
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Windows credentials have a small size limit, so only the encryption key lives there. */
internal object SecureAuthStore {
    private const val SERVICE = "MateriaLauncher"
    private val cache get() = PathManager.getDefaultAppDataDirectory().resolve("msal-cache.enc")

    fun get(name: String): String? = Keyring.create().use {
        try { it.getPassword(SERVICE, name) }
        catch (e: PasswordAccessException) {
            if (e.message == "Error code 1168") null else throw e
        }
    }
    fun set(name: String, value: String) = Keyring.create().use { it.setPassword(SERVICE, name, value) }
    fun delete(name: String) = Keyring.create().use {
        try { it.deletePassword(SERVICE, name) }
        catch (e: PasswordAccessException) { if (e.message != "Error code 1168") throw e }
    }

    fun readCache(): String? {
        if (!Files.exists(cache)) return get("MateriaAuthCache") // Migrate the previous keyring cache.
        val key = get("CacheEncryptionKey") ?: error("Ключ сессии Microsoft отсутствует. Очистите сессию и войдите заново.")
        val data = Files.readAllBytes(cache)
        require(data.size >= 28) { "Повреждён кеш Microsoft." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(Base64.getDecoder().decode(key), "AES"), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
    }

    fun writeCache(value: String) {
        val key = get("CacheEncryptionKey") ?: Base64.getEncoder().encodeToString(
            ByteArray(32).also { SecureRandom().nextBytes(it) }
        ).also { set("CacheEncryptionKey", it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(Base64.getDecoder().decode(key), "AES"), GCMParameterSpec(128, nonce))
        Files.createDirectories(cache.parent)
        val temp = Files.createTempFile(cache.parent, "msal-", ".tmp")
        try {
            Files.write(temp, nonce + cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
            Files.move(temp, cache, ATOMIC_MOVE, REPLACE_EXISTING)
            delete("MateriaAuthCache")
        } finally { Files.deleteIfExists(temp) }
    }

    fun clearCache() {
        Files.deleteIfExists(cache)
        delete("MateriaAuthCache")
        delete("CacheEncryptionKey")
    }
}
