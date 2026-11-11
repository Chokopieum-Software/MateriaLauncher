package ui.screens

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.*

class MarkdownImagesTest {
    @Test fun loadsNetworkPngAndSvgWithDefaultLoader() = runBlocking {
        val png = ByteArrayOutputStream().apply { ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", this) }.toByteArray()
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="80" height="20"><rect width="80" height="20" fill="red"/></svg>""".toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        for ((path, bytes, type) in listOf(Triple("/image.png", png, "image/png"), Triple("/badge.svg", svg, "image/svg+xml"))) {
            server.createContext(path) { exchange ->
                exchange.responseHeaders.set("Content-Type", type)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.set("Location", "/image.png")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.start()
        val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
        try {
            for (path in listOf("/image.png", "/badge.svg", "/redirect")) {
                val request = ImageRequest.Builder(PlatformContext.INSTANCE)
                    .data("http://127.0.0.1:"+server.address.port+path).build()
                val result = loader.execute(request)
                assertIs<SuccessResult>(result, "Image failed: "+path+" "+result)
                assertTrue(result.image.width > 0 && result.image.height > 0)
            }
        } finally { loader.shutdown(); server.stop(0) }
    }
}
