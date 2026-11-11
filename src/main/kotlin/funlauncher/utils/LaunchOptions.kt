package funlauncher.utils

data class LaunchOptions(
    val arguments: List<String>,
    val environment: Map<String, String>,
    val argumentErrors: List<String>,
    val environmentErrors: List<String>,
    val risks: List<String>
) {
    val valid get() = argumentErrors.isEmpty() && environmentErrors.isEmpty()
}

/** Splits JVM arguments without invoking a shell; quoted spaces and Windows paths are preserved. */
fun parseJavaArguments(text: String): List<String> {
    require('\u0000' !in text) { "Аргументы содержат недопустимый нулевой символ." }
    val result = mutableListOf<String>()
    val token = StringBuilder()
    var quote: Char? = null
    var started = false
    for (char in text) {
        when {
            quote != null && char == quote -> quote = null
            quote != null -> token.append(char)
            char == '"' || char == '\'' -> { quote = char; started = true }
            char.isWhitespace() -> if (started) { result.add(token.toString()); token.clear(); started = false }
            else -> { token.append(char); started = true }
        }
    }
    require(quote == null) { "В аргументах Java обнаружена незакрытая кавычка." }
    if (started) result.add(token.toString())
    return result
}

fun validateLaunchOptions(javaArgs: String, envVars: String, maxRamMb: Int): LaunchOptions {
    val argErrors = mutableListOf<String>()
    val envErrors = mutableListOf<String>()
    val risks = mutableListOf<String>()
    val arguments = try { parseJavaArguments(javaArgs) } catch (e: IllegalArgumentException) {
        argErrors.add(e.message ?: "Некорректные аргументы Java."); emptyList()
    }
    arguments.forEachIndexed { index, arg ->
        val prefix = "Аргумент ${index + 1}: "
        when {
            arg.startsWith("-Xmx") || arg.startsWith("-XX:MaxRAM") || arg.startsWith("-XX:MaxHeapSize") ->
                argErrors.add(prefix + "задайте лимит памяти через шкалу ОЗУ.")
            arg.startsWith("-Xms") -> {
                val match = Regex("-Xms([0-9]+)([kKmMgG]?)").matchEntire(arg)
                val size = match?.groupValues?.get(1)?.toDoubleOrNull()?.times(when (match.groupValues[2].lowercase()) {
                    "g" -> 1024.0; "m" -> 1.0; "k" -> 1.0 / 1024; else -> 1.0 / (1024 * 1024)
                })
                if (size == null || size <= 0 || size > maxRamMb) argErrors.add(prefix + "начальная память -Xms должна быть положительной и не превышать лимит ОЗУ.")
            }
            arg.startsWith("-D") -> if (!Regex("-D[^=\\s]+(=.*)?").matches(arg)) argErrors.add(prefix + "ожидается -Dимя=значение.")
            arg.startsWith("-XX:") -> if (!Regex("-XX:([+-][A-Za-z][A-Za-z0-9]*|[A-Za-z][A-Za-z0-9]*=.+)").matches(arg)) argErrors.add(prefix + "неверный формат параметра -XX.")
            arg.startsWith("-javaagent:") || arg.startsWith("-agentlib:") || arg.startsWith("-agentpath:") -> {
                if (arg.substringAfter(':').substringBefore('=').isBlank()) argErrors.add(prefix + "не указан агент Java.")
                else risks.add("Аргументы Java подключают агент: он может выполнять сторонний код или открывать отладочный доступ. Используйте только доверенные агенты.")
            }
            arg.matches(Regex("-X(ss|mn)[0-9]+[kKmMgG]?")) || arg == "-Xint" || arg == "-Xmixed" || arg == "-Xcomp" ||
                arg == "-ea" || arg == "-da" || arg == "-esa" || arg == "-dsa" || arg == "-server" ||
                arg.startsWith("-verbose") || arg.startsWith("-Xlog:") || arg.startsWith("--add-opens=") ||
                arg.startsWith("--add-exports=") || arg.startsWith("--add-modules=") -> Unit
            else -> argErrors.add(prefix + "неподдерживаемый параметр. Используйте параметры JVM (-D, -X, -XX), а не команды или аргументы игры.")
        }
        if (arg.startsWith("-XX:OnError=") || arg.startsWith("-XX:OnOutOfMemoryError="))
            risks.add("Параметры Java содержат команду, выполняемую при ошибке JVM. Проверьте её содержимое.")
    }
    val environment = linkedMapOf<String, String>()
    val names = mutableSetOf<String>()
    val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    envVars.lineSequence().forEachIndexed { index, line ->
        if (line.isBlank()) return@forEachIndexed
        val name = line.substringBefore('=').trim()
        val key = if (windows) name.uppercase() else name
        when {
            '\u0000' in line || '=' !in line || !name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) ->
                envErrors.add("Строка ${index + 1}: ожидается ИМЯ=значение, без пробелов в имени.")
            !names.add(key) -> envErrors.add("Строка ${index + 1}: имя переменной повторяется.")
            name.uppercase() in setOf("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS") ->
                envErrors.add("Строка ${index + 1}: параметры JVM следует задавать в поле «Аргументы Java», а не через $name.")
            else -> {
                environment[name] = line.substringAfter('=').trim()
                if (name.uppercase() in setOf("PATH", "JAVA_HOME", "LD_PRELOAD", "LD_LIBRARY_PATH", "DYLD_INSERT_LIBRARIES", "DYLD_LIBRARY_PATH"))
                    risks.add("Переменная $name изменяет поиск Java, программ или библиотек. Неверное значение может нарушить запуск игры.")
            }
        }
    }
    return LaunchOptions(arguments, environment, argErrors, envErrors, risks.distinct())
}
