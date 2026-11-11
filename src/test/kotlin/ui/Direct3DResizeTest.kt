package ui

import java.lang.reflect.Proxy
import javax.swing.SwingUtilities
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkiaLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Direct3DResizeTest {
    @Test
    fun resizingDuringPictureRecordingRequestsAnotherFrameWithoutRenderingInline() {
        SwingUtilities.invokeAndWait {
            // No window/graphics device is created: exercise the real SkiaLayer
            // resize path with a redrawer standing in for an active frame.
            val layer = object : SkiaLayer(renderApi = GraphicsApi.DIRECT3D) {
                override fun isShowing() = true
            }
            val managerField = SkiaLayer::class.java.getDeclaredField("redrawerManager")
                .apply { isAccessible = true }
            val manager = managerField.get(layer)
            val redrawerField = manager.javaClass.getDeclaredField("redrawer")
                .apply { isAccessible = true }
            val redrawerType = Class.forName("org.jetbrains.skiko.redrawer.Redrawer")
            var scheduledFrames = 0
            var boundsSynced = 0
            val redrawer = Proxy.newProxyInstance(redrawerType.classLoader, arrayOf(redrawerType)) { _, method, args ->
                when (method.name) {
                    "renderImmediately" -> error("Resize re-entered picture recording")
                    "needRender" -> {
                        assertFalse(args!![0] as Boolean)
                        scheduledFrames++
                    }
                    "syncBounds" -> boundsSynced++
                }
                null
            }
            redrawerField.set(manager, redrawer)
            try {
                repeat(20) { layer.setSize(300 + it, 200 + it) }
                assertEquals(20, scheduledFrames)
                assertEquals(20, boundsSynced)
            } finally {
                redrawerField.set(manager, null)
            }
        }
    }
}
