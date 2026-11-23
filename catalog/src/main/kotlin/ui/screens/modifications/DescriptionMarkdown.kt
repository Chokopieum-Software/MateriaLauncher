package ui.screens.modifications

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Normalizes mixed Modrinth Markdown/HTML for the native Markdown renderer. */
internal object DescriptionMarkdown {
    fun prepare(source: String, base: String): String {
        val html = DescriptionHtml.render(source.ifBlank { "Нет описания." }, base, "inherit", "transparent", "inherit")
        return children(Jsoup.parse(html).body()).trim()
    }

    private fun escape(text: String): String = text.replace("\\", "\\\\")
        .replace(Regex("([`*\\[\\]_<])"), "\\\\$1")

    private fun children(node: Node): String = node.childNodes().joinToString("") { render(it) }

    private fun render(node: Node): String {
        if (node is TextNode) return escape(node.text())
        if (node !is Element) return ""
        val content by lazy { children(node) }
        return when (node.normalName()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> "\n\n${"#".repeat(node.normalName().last().digitToInt())} ${content.trim()}\n\n"
            "p", "div", "section", "details" -> "\n\n${content.trim()}\n\n"
            "summary", "strong", "b" -> "**${content.trim()}**"
            "em", "i" -> "*${content.trim()}*"
            "del", "s" -> "~~${content.trim()}~~"
            "br" -> "  \n"
            "hr" -> "\n\n---\n\n"
            "pre" -> {
                val code = node.wholeText().trimEnd('\n')
                val fence = "`".repeat(maxOf(3, (Regex("`+").findAll(code).maxOfOrNull { it.value.length } ?: 0) + 1))
                "\n\n$fence\n$code\n$fence\n\n"
            }
            "code", "kbd" -> {
                val code = node.wholeText()
                val fence = "`".repeat((Regex("`+").findAll(code).maxOfOrNull { it.value.length } ?: 0) + 1)
                "$fence $code $fence"
            }
            "a" -> {
                val url = DescriptionHtml.browserLink(node.attr("href"), node.baseUri())
                if (url == null) content else "[$content](<${url.replace(">", "%3E")}>)"
            }
            "img" -> {
                val url = DescriptionHtml.browserLink(node.attr("src"), node.baseUri())
                if (url == null) "" else "![${escape(node.attr("alt"))}](<${url.replace(">", "%3E")}>)"
            }
            "ul", "ol" -> {
                val start = node.attr("start").toIntOrNull() ?: 1
                "\n\n" + node.children().filter { it.normalName() == "li" }.mapIndexed { index, item ->
                    val marker = if (node.normalName() == "ol") "${start + index}. " else "- "
                    val lines = children(item).trim().lines()
                    marker + lines.firstOrNull().orEmpty() + lines.drop(1).joinToString("") { "\n${" ".repeat(marker.length)}$it" }
                }.joinToString("\n") + "\n\n"
            }
            "blockquote" -> "\n\n" + content.trim().lines().joinToString("\n") { "> $it" } + "\n\n"
            "table" -> {
                val rows = node.select("tr").map { row -> row.children().filter { it.normalName() in setOf("th", "td") }.map {
                    children(it).trim().replace("|", "\\|").replace(Regex("\\s*\n\\s*"), " ")
                } }
                val width = rows.maxOfOrNull { it.size } ?: 0
                if (width == 0) "" else {
                    fun row(cells: List<String>) = "| " + (0 until width).joinToString(" | ") { cells.getOrElse(it) { "" } } + " |"
                    "\n\n" + (listOf(row(rows.first()), row(List(width) { "---" })) + rows.drop(1).map(::row)).joinToString("\n") + "\n\n"
                }
            }
            else -> content
        }
    }
}
