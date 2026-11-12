package funlauncher

import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

/** An OS lock, released even if the JVM crashes or is forcibly terminated. */
internal class SingleInstanceLock private constructor(private val channel: FileChannel) : AutoCloseable {
    override fun close() = channel.close()

    companion object {
        /** Returns null only when another instance owns the lock; I/O errors propagate. */
        fun acquire(directory: Path): SingleInstanceLock? {
            Files.createDirectories(directory)
            val channel = FileChannel.open(directory.resolve("launcher.lock"), CREATE, WRITE)
            try {
                val lock = try {
                    channel.tryLock()
                } catch (_: OverlappingFileLockException) {
                    null
                }
                if (lock == null) {
                    channel.close()
                    return null
                }
                // Never delete the file: replacing it could allow two processes to own different locks.
                return SingleInstanceLock(channel)
            } catch (failure: Throwable) {
                channel.close()
                throw failure
            }
        }
    }
}
