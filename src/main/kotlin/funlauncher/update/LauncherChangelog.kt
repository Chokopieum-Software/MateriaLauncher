package funlauncher.update

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.Properties

internal object LauncherBuildState : Table("launcher_build_state") {
    val id = integer("id")
    val number = long("build_number")
    val commit = text("commit_hash")
    val lastChangelog = text("last_changelog").nullable()
    override val primaryKey = PrimaryKey(id)
}

internal object LauncherChangelog {
    val bundled: String by lazy {
        checkNotNull(javaClass.getResourceAsStream("/CHANGELOG.md")).bufferedReader().use { it.readText() }
    }

    // Each release starts with a level-two heading. Ignore the document title and introduction.
    private fun releases(markdown: String): List<String> =
        markdown.replace("\r\n", "\n").split(Regex("(?m)(?=^## )"))
            .map { it.trim() }.filter { it.startsWith("## ") }

    fun changesSince(previous: String?, current: String): String {
        val sections = releases(current)
        require(sections.isNotEmpty()) { "В журнале нет записей о версиях." }
        val oldSections = previous?.let(::releases).orEmpty()
        return (if (oldSections.isEmpty()) sections.take(1) else sections - oldSections.toSet())
            .joinToString("\n\n")
    }

    fun onStartup(existingInstallation: Boolean): String? {
        val props = Properties()
        javaClass.getResourceAsStream("/app.properties")?.use(props::load)
        val number = props.getProperty("buildNumber")?.toLongOrNull() ?: return null
        return record(number, props.getProperty("gitHash").orEmpty(), bundled, existingInstallation)
    }

    internal fun record(number: Long, hash: String, markdown: String, existingInstallation: Boolean): String? = transaction {
        val state = LauncherBuildState
        val previous = state.selectAll().singleOrNull()
        if (previous != null && number <= previous[state.number]) return@transaction null
        // Older installations have JSON release notes in this column. They have no Markdown baseline.
        val changes = changesSince(previous?.get(state.lastChangelog), markdown)
        if (previous == null) {
            state.insert {
                it[id] = 1
                it[state.number] = number
                it[commit] = hash
                it[lastChangelog] = markdown
            }
        } else {
            state.update {
                it[state.number] = number
                it[commit] = hash
                it[lastChangelog] = markdown
            }
        }
        changes.takeIf { it.isNotBlank() && (previous != null || existingInstallation) }
    }
}
