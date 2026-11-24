package funlauncher.update

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.Properties
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object LauncherBuildState : Table("launcher_build_state") {
    val id = integer("id")
    val number = long("build_number")
    val commit = text("commit_hash")
    val lastChangelog = text("last_changelog").nullable()
    override val primaryKey = PrimaryKey(id)
}

@Serializable
internal data class LauncherChangelog(val previous: Long, val current: Long, val changes: List<String>) {
    companion object {
        fun latest(): LauncherChangelog? = transaction {
            LauncherBuildState.selectAll().singleOrNull()?.get(LauncherBuildState.lastChangelog)?.let {
                Json.decodeFromString<LauncherChangelog>(it)
            }
        }

        fun onStartup(): LauncherChangelog? {
            val props = Properties()
            LauncherChangelog::class.java.getResourceAsStream("/app.properties")?.use(props::load)
            val number = props.getProperty("buildNumber")?.toLongOrNull() ?: return null
            val commit = props.getProperty("gitHash") ?: return null
            return record(number, commit) { previousCommit ->
                LauncherChangelog::class.java.getResourceAsStream("/launcher-commits.tsv")?.bufferedReader()?.use {
                    changesSince(previousCommit, it.lineSequence())
                }.orEmpty()
            }
        }

        internal fun record(number: Long, commit: String, changes: (String) -> List<String>): LauncherChangelog? = transaction {
            val state = LauncherBuildState
            val previous = state.selectAll().singleOrNull()
            when {
                previous == null -> {
                    state.insert {
                        it[id] = 1
                        it[state.number] = number
                        it[state.commit] = commit
                    }
                    null
                }
                number <= previous[state.number] -> null
                else -> {
                    val changelog = LauncherChangelog(previous[state.number], number, changes(previous[state.commit]))
                    state.update {
                        it[state.number] = number
                        it[state.commit] = commit
                        it[lastChangelog] = Json.encodeToString(changelog)
                    }
                    changelog
                }
            }
        }

        // Git emits children before parents in topological order. Exclude everything
        // reachable from the previous build, including ancestors on merged branches.
        internal fun changesSince(previous: String, lines: Sequence<String>): List<String> {
            val excluded = hashSetOf(previous)
            val changes = mutableListOf<String>()
            var found = false
            for (line in lines) {
                // Read trailers from the right so tabs in the subject remain intact.
                val fields = line.split('\t')
                if (fields.size < 3) continue
                val (hash, parents, subject) = fields
                if (hash == previous) found = true
                if (hash in excluded) excluded.addAll(parents.split(' '))
                else {
                    val translations = if (fields.size >= 5) fields.takeLast(2).filter { it.isNotBlank() } else emptyList()
                    changes += if (translations.isNotEmpty()) translations.joinToString("\n")
                        else if (fields.size >= 5) fields.subList(2, fields.size - 2).joinToString(" ")
                        else subject
                }
            }
            // A rewritten branch has no reliable range. Show its newest commit.
            return if (found) changes else changes.take(1)
        }
    }
}
