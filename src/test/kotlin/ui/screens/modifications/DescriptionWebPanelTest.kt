package ui.screens.modifications

import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.*
import org.junit.Assume.assumeTrue

class DescriptionWebPanelTest {
    @Test fun systemWebViewRendersHtmlAndInterceptsNavigation() = runBlocking {
        assumeTrue(System.getProperty("os.name").startsWith("Windows") && WebView2Runtime.installedVersion() != null)
        lateinit var panel: DescriptionWebPanel
        lateinit var frame: JFrame
        val clicked = CompletableDeferred<String>()
        val popup = CompletableDeferred<String>()
        val html = DescriptionHtml.render("""<details open><summary>More</summary><p>Visible</p></details>

[External](https://example.org/destination)
""", "https://modrinth.com/mod/test", "#ffffff", "#111111", "#00ff00")
        SwingUtilities.invokeAndWait {
            panel = DescriptionWebPanel()
            panel.showHtml(html, "https://modrinth.com/mod/test", { url ->
                if (url.endsWith("popup")) popup.complete(url) else clicked.complete(url)
            }, { clicked.completeExceptionally(IllegalStateException("Failed to load HTML")) })
            frame = JFrame("Materia WebView2 regression test")
            frame.add(panel)
            frame.setSize(500, 350)
            frame.isVisible = true
        }
        suspend fun evaluate(script: String): String = withContext(Dispatchers.IO) {
            panel.browser.evalAsync(script).get(3, TimeUnit.SECONDS)
        }
        try {
            withTimeout(30_000) {
                while (true) {
                    val loaded = runCatching { evaluate("return document.querySelector('summary')?.textContent;") }.getOrNull()
                    if (loaded == "\"More\"") break
                    delay(100)
                }
            }
            evaluate("document.querySelector('a').click(); return true;")
            assertEquals("https://example.org/destination", withTimeout(5_000) { clicked.await() })
            assertEquals("\"More\"", evaluate("return document.querySelector('summary').textContent;"), "External link must not replace the description")
            evaluate("window.open('https://example.org/popup', '_blank'); return true;")
            assertEquals("https://example.org/popup", withTimeout(5_000) { popup.await() })
        } finally {
            SwingUtilities.invokeAndWait { panel.dispose(); frame.dispose() }
        }
    }

    @Test fun onlyLocalDescriptionAndFragmentsMayNavigate() {
        val file = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "description.html").toUri().toString()
        assertTrue(DescriptionWebPanel.isDocumentNavigation(file, file))
        assertTrue(DescriptionWebPanel.isDocumentNavigation(file+"#section", file))
        assertFalse(DescriptionWebPanel.isDocumentNavigation("https://example.org", file))
        assertFalse(DescriptionWebPanel.isDocumentNavigation("file:///C:/other.html", file))
    }

    @Test fun rejectsMissingRuntimeVersions() {
        for (version in listOf("", "0.0.0.0", " ", "not a version")) assertFalse(WebView2Runtime.isInstalledVersion(version))
        assertTrue(WebView2Runtime.isInstalledVersion("142.0.3595.94"))
    }
}
