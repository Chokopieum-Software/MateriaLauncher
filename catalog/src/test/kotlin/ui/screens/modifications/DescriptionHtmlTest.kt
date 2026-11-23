package ui.screens.modifications

import org.jsoup.Jsoup
import kotlin.test.*

class DescriptionHtmlTest {
    private fun render(source: String) = Jsoup.parse(DescriptionHtml.render(source, "https://modrinth.com/mod/test", "#ffffff", "#111111", "#00ff00"))

    @Test fun rendersMarkdownAndHtmlTogether() {
        val doc = render("""# Title

**Bold** and ~~removed~~

| A | B |
|---|---|
| 1 | 2 |

<div align="center"><a href="https://example.org"><img src="https://example.org/badge.svg" width="100"></a></div>

<details><summary>More</summary><p>Details text</p></details>
""")
        assertEquals("Title", doc.selectFirst("h1")?.text())
        assertEquals("Bold", doc.selectFirst("strong")?.text())
        assertEquals(1, doc.select("table").size)
        assertEquals("center", doc.selectFirst("div")?.attr("align"))
        assertEquals("100", doc.selectFirst("img")?.attr("width"))
        assertEquals("More", doc.selectFirst("details summary")?.text())
    }

    @Test fun removesActiveContentAndDangerousUrls() {
        val doc = render("""<script>alert(1)</script><iframe src="https://example.org"></iframe>
<img src="file:///secret" onerror="alert(1)"><a href="javascript:alert(1)">bad</a>
<form action="https://example.org"><input></form><meta http-equiv="refresh" content="0; url=https://example.org">
""")
        assertTrue(doc.select("script,iframe,form,input,meta[http-equiv=refresh]").isEmpty())
        assertTrue(doc.select("[onerror],a[href],img[src]").isEmpty())
    }

    @Test fun resolvesLinksAndRequiresWebScheme() {
        assertEquals("https://example.org/path", DescriptionHtml.browserLink("https://example.org/path", "https://modrinth.com"))
        assertEquals("https://modrinth.com/mod/other", DescriptionHtml.browserLink("/mod/other", "https://modrinth.com/mod/test"))
        for (url in listOf("javascript:alert(1)", "file:///secret", "data:text/html,test", "https://user:pass@example.org")) {
            assertNull(DescriptionHtml.browserLink(url, "https://modrinth.com"))
        }
    }
}
