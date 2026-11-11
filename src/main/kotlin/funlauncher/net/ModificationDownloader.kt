package funlauncher.net

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.nio.file.*
import java.security.MessageDigest

class ModificationDownloader {
    suspend fun download(modFile: File, destination: java.io.File, onProgress: (Float, String) -> Unit) =
        downloadVerified(modFile.url, destination.toPath(), modFile.size.toLong(), mapOf("sha512" to modFile.hashes.sha512), onProgress)

    suspend fun downloadVerified(url: String, destination: Path, size: Long, hashes: Map<String, String>,
                                onProgress: (Float, String) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        require(size >= 0) { "Некорректный размер файла" }
        val algorithm = if (!hashes["sha512"].isNullOrBlank()) "sha512" else "sha1"
        val expected = hashes[algorithm] ?: error("Отсутствует контрольная сумма")
        require(expected.matches(Regex(if (algorithm == "sha512") "[a-fA-F0-9]{128}" else "[a-fA-F0-9]{40}")))
        Files.createDirectories(destination.toAbsolutePath().parent)
        val temporary = Files.createTempFile(destination.toAbsolutePath().parent, ".download-", ".part")
        try {
            val digest = MessageDigest.getInstance(if (algorithm == "sha512") "SHA-512" else "SHA-1")
            var received = 0L
            Network.client.prepareGet(url).execute { response ->
                check(response.status.value in 200..299) { "HTTP "+response.status }
                val channel = response.bodyAsChannel()
                Files.newOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = channel.readAvailable(buffer, 0, buffer.size)
                        if (count == -1) break
                        received += count
                        check(received <= size) { "Размер файла превышает ожидаемый" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(if (size > 0) received.toFloat() / size else 0f, "Скачивание...")
                    }
                }
            }
            check(received == size) { "Размер файла не совпадает" }
            check(digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, true)) { "Контрольная сумма не совпадает" }
            currentCoroutineContext().ensureActive()
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            }
            onProgress(1f, "Завершено")
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        fun safeResolve(root: Path, relative: String): Path {
            require(relative.isNotBlank() && !relative.contains('\\') && !relative.contains(':')) { "Недопустимый путь: "+relative }
            val base = root.toAbsolutePath().normalize()
            val result = base.resolve(relative).normalize()
            require(result.startsWith(base) && result != base) { "Путь выходит за каталог установки" }
            var current: Path? = result
            while (current != null && current.startsWith(base)) {
                require(!Files.isSymbolicLink(current)) { "Символические ссылки запрещены" }
                current = current.parent
            }
            return result
        }
    }
}
