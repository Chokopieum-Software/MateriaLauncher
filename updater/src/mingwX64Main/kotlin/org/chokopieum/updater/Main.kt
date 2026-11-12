@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.chokopieum.updater

import kotlinx.cinterop.*
import updater.win32.*

private val managed = listOf("app", "runtime", "updater", "materia-launcher.exe", "update.properties")
private fun read(path: String): String {
    val pointer = mu_read(path) ?: error("Не удалось прочитать $path")
    return try { pointer.toKString() } finally { mu_free(pointer) }
}
private fun text(pointer: CPointer<ByteVar>?): String =
    pointer?.let { try { it.toKString() } finally { mu_free(it) } } ?: error("Win32: ${mu_error()}")
private fun properties(text: String) = text.lineSequence().filter { '=' in it && !it.startsWith('#') }
    .associate { it.substringBefore('=').trim() to it.substringAfter('=').trimEnd('\r') }
private fun exists(path: String) = mu_exists(path) != 0
private fun file(path: String) = mu_plain_file(path) != 0
private fun mkdir(path: String) { check(mu_mkdir(path) != 0) { "Не удалось создать $path (Win32 ${mu_error()})" } }
private fun move(from: String, to: String) {
    check(mu_move(from, to) != 0) { "Не удалось переместить $from → $to (Win32 ${mu_error()}). Закройте другие экземпляры лаунчера." }
}
private fun progress(message: String) = mu_progress(message, -1)

internal fun validateListing(names: String, details: String) {
    val entries = names.lineSequence().filter { it.isNotBlank() }.toList()
    check(entries.isNotEmpty() && entries.size <= 100_000) { "Некорректное число файлов в архиве" }
    entries.forEach { entry ->
        val name = entry.trimEnd('\r').removeSuffix("/")
        val segments = name.split('/')
        check(name.isNotEmpty() && !name.startsWith('/') && '\\' !in name && ':' !in name &&
            segments.none { it == ".." || it == "." || it.isEmpty() || it.endsWith('.') || it.endsWith(' ') } &&
            segments.first() in managed) { "Недопустимый путь в архиве: $name" }
    }
    val listing = details.lineSequence().filter { it.isNotBlank() }.toList()
    check(listing.size == entries.size && listing.all { it.startsWith('-') || it.startsWith('d') }) {
        "Архив содержит ссылки или специальные файлы"
    }
    val sizePattern = Regex("^\\S+\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+")
    val total = listing.sumOf { line ->
        sizePattern.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: error("Не удалось проверить размер архива")
    }
    check(total in 1..4_294_967_296L) { "Недопустимый размер обновления" }
}

internal fun replace(target: String, staged: String, backup: String) {
    mkdir(backup)
    val saved = mutableListOf<String>()
    val installed = mutableListOf<String>()
    try {
        for (name in managed) if (exists("$target/$name")) {
            move("$target/$name", "$backup/$name")
            saved += name
        }
        for (name in managed) {
            move("$staged/$name", "$target/$name")
            installed += name
        }
    } catch (failure: Throwable) {
        var restored = true
        for (name in installed.asReversed()) if (mu_delete("$target/$name") == 0) restored = false
        for (name in saved.asReversed()) {
            if (mu_move("$backup/$name", "$target/$name") == 0) restored = false
        }
        if (!restored) error("Не удалось восстановить файлы. Резервная копия: $backup\n${failure.message}")
        throw failure
    }
}

private fun update(requestPath: String) {
    val request = properties(read(requestPath))
    val target = text(mu_fullpath(request.getValue("target"))).replace('\\', '/')
    val parent = target.substringBeforeLast('/')
    check(target.isNotBlank() && target != parent && file("$target/materia-launcher.exe")) { "Каталог лаунчера не найден" }
    val lock = mu_lock("$target/.update.lock") ?: error("Обновление уже запущено или нет прав на запись в каталог лаунчера")
    try {
        progress("Ожидание закрытия лаунчера…")
        check(mu_wait_parent(request.getValue("pid").toUInt()) != 0) { "Лаунчер не закрылся за 60 секунд. Файлы не изменены." }
        val work = "$parent/.materia-update-${mu_pid()}"
        mkdir(work)
        val backup = "$work/backup"
        try {
            val url = request.getValue("url")
            check(Regex("https://nightly\\.link/Chokopieum-Software/MateriaLauncher/suites/[0-9]+/artifacts/[0-9]+").matches(url)) {
                "Недопустимый источник обновления"
            }
            val hash = request.getValue("sha256")
            check(hash.matches(Regex("[a-fA-F0-9]{64}"))) { "Отсутствует SHA-256 обновления" }
            progress("Загрузка последней успешной сборки sunrise-dev…")
            check(mu_download(url, "$work/launcher.zip", hash) != 0) {
                "Ошибка HTTPS-загрузки или SHA-256 архива (Win32 ${mu_error()}). Установленные файлы не изменены."
            }
            progress("Проверка архива…")
            check(mu_tar("$work/launcher.zip", work, "$work/names.txt", 0) != 0 &&
                mu_tar("$work/launcher.zip", work, "$work/details.txt", 1) != 0) { "Не удалось прочитать ZIP средствами Windows tar.exe" }
            validateListing(read("$work/names.txt"), read("$work/details.txt"))
            val staged = "$work/staged"
            mkdir(staged)
            progress("Распаковка файлов…")
            check(mu_tar("$work/launcher.zip", staged, "$work/extract.log", 2) != 0) { "Ошибка распаковки: $work/extract.log" }
            for (name in listOf("materia-launcher.exe", "app/materia-launcher.cfg", "runtime/lib/modules", "updater/MateriaUpdater.exe", "update.properties")) {
                check(file("$staged/$name")) { "В обновлении отсутствует $name" }
            }
            val metadata = properties(read("$staged/update.properties"))
            check(metadata["runId"] == request["runId"] && metadata["branch"] == "sunrise-dev") { "Архив не соответствует выбранной сборке" }
            progress("Замена файлов приложения…")
            replace(target, staged, backup)
            progress("Запуск обновлённого лаунчера…")
            check(mu_launch("$target/materia-launcher.exe", target) != 0) { "Обновление установлено, но запуск не удался. Запустите лаунчер вручную." }
            mu_delete(work)
        } catch (failure: Throwable) {
            // Preserve logs and any remaining backup for recovery, never delete on failure.
            error("${failure.message}\nФайлы диагностики: $work")
        }
    } finally { mu_unlock(lock) }
}

private fun worker(data: COpaquePointer?) {
    val ref = data!!.asStableRef<String>()
    val request = ref.get()
    try {
        update(request)
        mu_done("")
    } catch (failure: Throwable) {
        val message = failure.message ?: failure.toString()
        mu_write("${request.substringBeforeLast('/')}/updater.log", failure.stackTraceToString())
        mu_done("Обновление не выполнено.\n$message")
    } finally { ref.dispose() }
}

fun main() {
    val path = text(mu_request_path()).replace('\\', '/')
    if (path.isBlank()) return
    try {
        // Parse before signalling readiness, so the launcher remains open for bad requests.
        val request = properties(read(path))
        listOf("target", "pid", "url", "sha256", "runId").forEach { request.getValue(it) }
        val ref = StableRef.create(path)
        if (mu_ui(staticCFunction(::worker), ref.asCPointer(), "${path.substringBeforeLast('/')}/ready") == 0) ref.dispose()
    } catch (failure: Throwable) {
        mu_write("${path.substringBeforeLast('/')}/updater.log", failure.stackTraceToString())
    }
}
