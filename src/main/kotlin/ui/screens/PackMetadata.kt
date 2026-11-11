package ui.screens

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable internal data class PackMcMeta(val pack: PackInfo)
@Serializable internal data class PackInfo(val description: JsonElement = JsonNull)

/** Plain text for the launcher; translation fallbacks are used without a game language registry. */
internal fun JsonElement.toPackDescription(): String {
    fun plain(element: JsonElement): String = when (element) {
        JsonNull -> ""
        is JsonPrimitive -> element.content
        is JsonArray -> element.joinToString("") { plain(it) }
        is JsonObject -> {
            val text = element["text"]?.let { plain(it) }
            val translated = element["fallback"]?.let { plain(it) }
                ?: element["translate"]?.let { plain(it) }.orEmpty()
            val arguments = (element["with"] as? JsonArray)?.map { plain(it) }.orEmpty()
            var argumentIndex = 0
            val content = text ?: Regex("%(?:(\\d+)\\$)?s|%%").replace(translated) { match ->
                if (match.value == "%%") "%" else {
                    val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: argumentIndex++
                    arguments.getOrNull(index) ?: match.value
                }
            }
            content + (element["extra"] as? JsonArray)?.joinToString("") { plain(it) }.orEmpty()
        }
    }
    return plain(this).replace(Regex("§[0-9a-fk-orx]", RegexOption.IGNORE_CASE), "")
}
