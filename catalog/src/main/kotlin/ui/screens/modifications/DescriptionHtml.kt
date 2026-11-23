package ui.screens.modifications

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.net.URI

internal object DescriptionHtml {
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).build()

    fun browserLink(value: String, base: String): String? = runCatching {
        val uri = URI(base).resolve(value.trim())
        uri.takeIf { it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null }?.toASCIIString()
    }.getOrNull()

    fun render(markdown: String, base: String, foreground: String, background: String, accent: String): String {
        val source = Jsoup.parseBodyFragment(renderer.render(parser.parse(markdown)), base)
        source.select("script,style,iframe,object,embed,form,input,button,meta,link,base").remove()
        val allowed = Safelist.relaxed()
            .addTags("details", "summary", "div", "span", "picture", "source", "del", "s", "hr", "kbd", "sup", "sub")
            .addAttributes(":all", "id", "align", "title")
            .addAttributes("details", "open")
            .addAttributes("img", "width", "height")
            .addProtocols("img", "src", "https", "http")
            .preserveRelativeLinks(true)
        val clean = Jsoup.parseBodyFragment(Jsoup.clean(source.body().html(), base, allowed), base)
        for (image in clean.select("img[src]")) {
            val url = browserLink(image.attr("src"), base)
            if (url == null) image.removeAttr("src") else image.attr("src", url)
        }
        for (link in clean.select("a[href]")) {
            val href = link.attr("href")
            if (!href.startsWith("#")) {
                val url = browserLink(href, base)
                if (url == null) link.removeAttr("href") else link.attr("href", url)
            }
        }
        return """<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: http:; style-src 'unsafe-inline'; script-src 'none'; base-uri 'none'; form-action 'none'">
            <style>
            :root { color-scheme: normal; background: $background; }
            body { margin: 0; padding: 8px 12px 24px; font: 15px/1.6 system-ui, sans-serif; color: $foreground; background: $background; overflow-wrap: anywhere; }
            a { color: $accent; text-decoration: none; } a:hover { text-decoration: underline; }
            img { max-width: 100%; height: auto; vertical-align: middle; }
            table { border-collapse: collapse; max-width: 100%; display: block; overflow-x: auto; }
            td,th { border: 1px solid #80808060; padding: 6px 12px; }
            pre { overflow-x: auto; padding: 12px; background: #80808020; border-radius: 8px; }
            code,kbd { font-family: monospace; background: #80808020; border-radius: 4px; padding: 2px 4px; }
            pre code { background: none; padding: 0; }
            blockquote { border-left: 4px solid #80808080; margin-left: 0; padding-left: 16px; }
            details { border: 1px solid #80808060; border-radius: 8px; padding: 8px 12px; margin: 12px 0; }
            summary { cursor: pointer; } h1,h2 { border-bottom: 1px solid #80808040; padding-bottom: 6px; }
            </style></head><body>${clean.body().html()}</body></html>""".trimIndent()
    }
}
