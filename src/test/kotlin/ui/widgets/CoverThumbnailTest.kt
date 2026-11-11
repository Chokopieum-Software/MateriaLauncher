package ui.widgets

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class CoverThumbnailTest {
    @Test
    fun largeCoverIsCenterCroppedAndDecodedAtCardSize() {
        val file = Files.createTempFile("materia-cover", ".png").toFile()
        val source = BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB)
        val graphics = source.createGraphics()
        graphics.color = Color.RED
        graphics.fillRect(0, 0, 2400, 1200)
        graphics.color = Color.BLUE
        graphics.fillRect(600, 0, 1200, 1200)
        graphics.dispose()
        try {
            ImageIO.write(source, "png", file)
            val result = decodeCoverThumbnail(file, 240, 240)
            try {
                assertEquals(240, result.width)
                assertEquals(240, result.height)
                assertEquals(Color.BLUE.rgb, result.getRGB(0, 120))
                assertEquals(Color.BLUE.rgb, result.getRGB(239, 120))
            } finally { result.flush() }
        } finally {
            source.flush()
            Files.deleteIfExists(file.toPath())
        }
    }
}
