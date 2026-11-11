package ui.widgets

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import funlauncher.managers.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object ImageLoader {
    private val coverCache = object : LinkedHashMap<String, Painter>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Painter>?) = size > 64
    }
    private val inMemoryCache = ConcurrentHashMap<String, Painter>()
    private lateinit var cacheManager: CacheManager

    fun init(cacheManager: CacheManager) {
        this.cacheManager = cacheManager
    }

    private fun hashKey(key: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            digest.update(key.toByteArray())
            val messageDigest = digest.digest()
            val hexString = StringBuilder()
            for (aMessageDigest in messageDigest) {
                var h = Integer.toHexString(0xFF and aMessageDigest.toInt())
                while (h.length < 2) h = "0$h"
                hexString.append(h)
            }
            hexString.toString()
        } catch (e: Exception) {
            key.hashCode().toString()
        }
    }

    @Composable
    fun rememberImagePainter(filePath: String?, targetSize: IntSize): Painter? {
        var painter by remember(filePath, targetSize) { mutableStateOf<Painter?>(null) }
        val density = LocalDensity.current

        LaunchedEffect(filePath, targetSize, density.density) {
            if (filePath == null || targetSize.width <= 0 || targetSize.height <= 0) {
                painter = null
                return@LaunchedEffect
            }

            painter = withContext(Dispatchers.IO) {
                val file = File(filePath)
                val key = "$filePath:${file.lastModified()}:${file.length()}:${targetSize.width}x${targetSize.height}:${density.density}"
                synchronized(coverCache) { coverCache[key] }?.let { return@withContext it }
                if (file.exists()) {
                    try {
                        file.inputStream().use { stream ->
                            if (filePath.endsWith(".svg", ignoreCase = true)) {
                                loadSvgPainter(stream, density)
                            } else {
                                val thumbnail = decodeCoverThumbnail(file, targetSize.width, targetSize.height)
                                try { BitmapPainter(thumbnail.toComposeImageBitmap()) }
                                finally { thumbnail.flush() }
                            }.also {
                                synchronized(coverCache) { coverCache[key] = it }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                } else {
                    null
                }
            }
        }
        return painter
    }

    @Composable
    fun rememberImagePainterFromUrl(url: String?): Painter? {
        var painter by remember(url) { mutableStateOf<Painter?>(null) }
        val density = LocalDensity.current

        LaunchedEffect(url) {
            if (url == null || url.isBlank()) {
                painter = null
                return@LaunchedEffect
            }

            // Если URL - это SVG контент
            if (url.trim().startsWith("<svg", ignoreCase = true)) {
                val key = hashKey(url)
                if (inMemoryCache.containsKey(key)) {
                    painter = inMemoryCache[key]
                    return@LaunchedEffect
                }

                painter = withContext(Dispatchers.IO) {
                    try {
                        ByteArrayInputStream(url.toByteArray()).use { stream ->
                            loadSvgPainter(stream, density).also {
                                inMemoryCache[key] = it
                            }
                        }
                    } catch (e: Exception) {
                        println("ImageLoader: Error parsing SVG content: ${e.message}")
                        null
                    }
                }
                return@LaunchedEffect
            }

            if (inMemoryCache.containsKey(url)) {
                painter = inMemoryCache[url]
                return@LaunchedEffect
            }

            painter = withContext(Dispatchers.IO) {
                val cacheFile = cacheManager.getImageCacheFile(url)

                if (cacheFile.exists()) {
                    try {
                        val bytes = cacheFile.readBytes()
                        ByteArrayInputStream(bytes).use { stream ->
                            val isSvg = isSvgContent(bytes)
                            if (isSvg) {
                                loadSvgPainter(stream, density)
                            } else {
                                BitmapPainter(loadImageBitmap(stream))
                            }.also {
                                inMemoryCache[url] = it
                            }
                        }
                    } catch (e: Exception) {
                        println("ImageLoader: Error loading from disk cache: ${e.message}")
                        cacheFile.delete()
                        null
                    }
                } else {
                    null
                } ?: try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "MateriaKraft-Launcher")
                    connection.connect()

                    if (connection.responseCode == 200) {
                        connection.inputStream.use { input ->
                            val buffer = ByteArrayOutputStream()
                            input.copyTo(buffer)
                            val bytes = buffer.toByteArray()

                            try {
                                cacheFile.parentFile?.mkdirs()
                                cacheFile.writeBytes(bytes)
                            } catch (e: Exception) {
                                println("ImageLoader: Failed to save to cache: ${e.message}")
                            }

                            val isSvg = isSvgContent(bytes) || url.endsWith(".svg", ignoreCase = true)

                            ByteArrayInputStream(bytes).use { stream ->
                                if (isSvg) {
                                    loadSvgPainter(stream, density)
                                } else {
                                    BitmapPainter(loadImageBitmap(stream))
                                }
                            }.also {
                                inMemoryCache[url] = it
                            }
                        }
                    } else {
                        println("ImageLoader: Server returned ${connection.responseCode} for URL: $url")
                        null
                    }
                } catch (e: Exception) {
                    println("ImageLoader: Error downloading image from URL: $url. Error: ${e.message}")
                    null
                }
            }
        }
        return painter
    }

    private fun isSvgContent(bytes: ByteArray): Boolean {
        val prefix = String(bytes.take(100).toByteArray()).trim()
        return prefix.startsWith("<svg", ignoreCase = true) || prefix.startsWith("<?xml", ignoreCase = true)
    }
}
