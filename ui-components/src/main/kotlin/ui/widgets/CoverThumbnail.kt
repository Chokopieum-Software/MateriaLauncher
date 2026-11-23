package ui.widgets

import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.min
import kotlin.math.roundToInt

/** Crop and subsample at decode time so a large screenshot never becomes a full-size bitmap. */
internal fun decodeCoverThumbnail(file: File, width: Int, height: Int): BufferedImage {
    require(width > 0 && height > 0)
    ImageIO.createImageInputStream(file).use { input ->
        requireNotNull(input) { "Cannot open image: $file" }
        val readers = ImageIO.getImageReaders(input)
        require(readers.hasNext()) { "Unsupported cover: $file" }
        val reader = readers.next()
        try {
            reader.input = input
            val sourceWidth = reader.getWidth(0)
            val sourceHeight = reader.getHeight(0)
            val ratio = width.toDouble() / height
            val cropWidth = min(sourceWidth, (sourceHeight * ratio).roundToInt()).coerceAtLeast(1)
            val cropHeight = min(sourceHeight, (sourceWidth / ratio).roundToInt()).coerceAtLeast(1)
            val parameters = reader.defaultReadParam.apply {
                sourceRegion = Rectangle((sourceWidth - cropWidth) / 2, (sourceHeight - cropHeight) / 2, cropWidth, cropHeight)
                val sample = min(cropWidth / width, cropHeight / height).coerceAtLeast(1)
                setSourceSubsampling(sample, sample, 0, 0)
            }
            val decoded = reader.read(0, parameters)
            val scale = min(1.0, min(width.toDouble() / cropWidth, height.toDouble() / cropHeight))
            val result = BufferedImage((cropWidth * scale).roundToInt().coerceAtLeast(1),
                (cropHeight * scale).roundToInt().coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
            val graphics = result.createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                graphics.drawImage(decoded, 0, 0, result.width, result.height, null)
            } finally {
                graphics.dispose()
                decoded.flush()
            }
            return result
        } finally {
            reader.dispose()
        }
    }
}
