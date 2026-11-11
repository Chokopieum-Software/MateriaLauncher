package ui.screens.modifications

import ca.weblite.webview.WebViewPopupEvent
import ca.weblite.webview.WebViewPopupHandler
import ca.weblite.webview.swing.WebViewComponent
import com.sun.jna.platform.win32.Kernel32
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Embeds the installed Edge WebView2 Runtime through a small JNI bridge. */
internal class DescriptionWebPanel(surfaceColor: Color = Color.DARK_GRAY) : JPanel(BorderLayout()) {
    internal val browser = WebViewComponent.create(WebViewComponent.Mode.HEAVYWEIGHT)
    private val directory = Files.createTempDirectory("materia-description-")
    @Volatile private var documentUrl = ""
    @Volatile private var baseUrl = "https://modrinth.com/"
    @Volatile private var disposed = false
    private var currentHtml: String? = null
    private var loadWatchdog: javax.swing.Timer? = null
    private var onLink: (String) -> Unit = {}

    init {
        setSurfaceColor(surfaceColor)
        browser.setDebug(false)
        browser.setDefaultContextMenuEnabled(false)
        browser.setDialogHandler(null)
        browser.setDownloadHandler(null)
        browser.setNavigationHandler { event ->
            if (isDocumentNavigation(event.url(), documentUrl)) true else {
                requestExternalLink(event.url())
                false
            }
        }
        browser.setPopupHandler(object : WebViewPopupHandler {
            override fun popupRequested(event: WebViewPopupEvent): Boolean {
                requestExternalLink(event.targetUrl())
                return false
            }
        })
        add(browser, BorderLayout.CENTER)
    }

    fun setSurfaceColor(color: Color) {
        // Also colour the bridge's private heavyweight Canvas, whose default is white.
        fun apply(component: Component) {
            component.background = color
            if (component is Container) component.components.forEach(::apply)
        }
        apply(this)
        apply(browser)
        if (System.getProperty("os.name").startsWith("Windows", true)) {
            // Set before the native controller is created on first paint. This covers
            // about:blank and the gap before the HTML background is painted.
            Kernel32.INSTANCE.SetEnvironmentVariable("WEBVIEW2_DEFAULT_BACKGROUND_COLOR", "FF%02X%02X%02X".format(color.red, color.green, color.blue))
        }
    }

    private fun requestExternalLink(url: String) {
        DescriptionHtml.browserLink(url, baseUrl)?.let { link ->
            SwingUtilities.invokeLater { if (!disposed) onLink(link) }
        }
    }

    fun showHtml(html: String, base: String, onLink: (String) -> Unit, onError: () -> Unit) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { showHtml(html, base, onLink, onError) }
            return
        }
        if (disposed) return
        this.onLink = onLink
        baseUrl = base
        if (currentHtml == html) return
        try {
            val file = Files.createTempFile(directory, "description-", ".html")
            Files.writeString(file, html)
            documentUrl = file.toUri().toASCIIString()
            currentHtml = html
            browser.setUrl(documentUrl)
            loadWatchdog?.stop()
            val started = System.nanoTime()
            loadWatchdog = javax.swing.Timer(500) {
                if (disposed) loadWatchdog?.stop() else {
                    browser.evalAsync("return document.readyState;").thenAccept { state ->
                        if (state == "\"complete\"") loadWatchdog?.stop()
                    }.exceptionally { null }
                    if (System.nanoTime() - started > 20_000_000_000L) {
                        loadWatchdog?.stop()
                        onError()
                    }
                }
            }.also { it.start() }
        } catch (e: Exception) { onError() }
    }

    fun dispose() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(::dispose)
            return
        }
        if (disposed) return
        disposed = true
        loadWatchdog?.stop()
        browser.dispose()
        removeAll()
        directory.toFile().deleteRecursively()
    }

    companion object {
        internal fun isDocumentNavigation(url: String, document: String): Boolean {
            if (url == "about:blank") return true
            if (document.isBlank()) return false
            return runCatching {
                val target = java.net.URI(url.substringBefore('#'))
                val expected = java.net.URI(document)
                target.scheme == "file" && Path.of(target).normalize() == Path.of(expected).normalize()
            }.getOrDefault(false)
        }
    }
}
