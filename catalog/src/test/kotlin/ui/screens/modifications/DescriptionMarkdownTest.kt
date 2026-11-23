package ui.screens.modifications

import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdown
import kotlin.test.*

class DescriptionMarkdownTest {
    private fun prepare(source: String) = DescriptionMarkdown.prepare(source, "https://modrinth.com/mod/example")

    @Test fun preservesFormattingTablesAndHtmlImages() {
        val result = prepare("""# Heading

**Bold**, *italic* and ~~removed~~.

| Name | Value |
| --- | --- |
| One | Two |

<div align="center"><a href="/mod/other"><img src="https://example.org/badge.svg" alt="Badge"></a></div>

<details><summary>More</summary><p>Details text</p></details>
""")
        assertContains(result, "# Heading")
        assertContains(result, "**Bold**")
        assertContains(result, "*italic*")
        assertContains(result, "~~removed~~")
        assertContains(result, "| Name | Value |")
        assertContains(result, "![Badge](<https://example.org/badge.svg>)")
        assertContains(result, "https://modrinth.com/mod/other")
        assertContains(result, "**More**")
        assertContains(result, "Details text")
        assertFalse(result.contains("<div"))
        assertIs<State.Success>(parseMarkdown(result))
    }

    @Test fun preservesCodeListsAndLiteralMarkdownCharacters() {
        val result = prepare("""- Parent
  - Child

```java
String x = "a_b * c";
```

Literal \*star\* and \[brackets\].
""")
        assertContains(result, "- Parent")
        assertContains(result, "- Child")
        assertContains(result, "String x = \"a_b * c\";")
        assertContains(result, "\\*star\\*")
        assertContains(result, "\\[brackets\\]")
        assertIs<State.Success>(parseMarkdown(result))
    }

    @Test fun removesActiveContentAndNonWebImages() {
        val result = prepare("""<script>alert('bad')</script><iframe src="https://example.org"></iframe>
<img src="file:///secret"><a href="javascript:alert(1)">Label</a>
""")
        for (forbidden in listOf("alert", "iframe", "file:///", "javascript:")) assertFalse(result.contains(forbidden))
        assertContains(result, "Label")
    }

    @Test fun suppliesEmptyDescriptionPlaceholder() {
        assertEquals("Нет описания.", prepare(""))
    }
}
