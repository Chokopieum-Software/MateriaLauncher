package funlauncher.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

data class LauncherUpdate(val runId: Long, val buildNumber: Long, val commit: String, val url: String, val sha256: String)

object LauncherUpdates {
    private const val REPOSITORY = "Chokopieum-Software/MateriaLauncher"
    private const val WORKFLOW = "create_installer_package.yml"
    private const val BRANCH = "sunrise-dev"
    private const val ARTIFACT = "materia-launcher-app-windows-x64"
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL).build()

    fun installation(): Path? {
        if (!System.getProperty("os.name").startsWith("Windows")) return null
        val executable = System.getProperty("jpackage.app-path") ?: return null
        return Path.of(executable).toAbsolutePath().parent.takeIf {
            Files.isRegularFile(it.resolve("update.properties"))
        }
    }

    /** Checks successful branch runs, never pull requests or an in-progress payload. */
    fun check(target: Path): LauncherUpdate? {
        val installed = Properties().apply { Files.newInputStream(target.resolve("update.properties")).use(::load) }
        require(installed.getProperty("branch") == BRANCH) { "Обновления доступны только для $BRANCH" }
        val runs = get("/actions/workflows/$WORKFLOW/runs?branch=$BRANCH&status=success&per_page=10")
            .getAsJsonArray("workflow_runs")
        val run = runs.map { it.asJsonObject }.firstOrNull {
            it.get("head_branch").asString == BRANCH && it.get("event").asString != "pull_request"
        } ?: error("Успешные сборки $BRANCH пока не найдены")
        val runId = run.get("id").asLong
        val build = run.get("run_number").asLong
        if (runId == installed.getProperty("runId")?.toLongOrNull() ||
            build <= (installed.getProperty("buildNumber")?.toLongOrNull() ?: 0)) return null
        val artifact = get("/actions/runs/$runId/artifacts?per_page=100").getAsJsonArray("artifacts")
            .map { it.asJsonObject }.firstOrNull { it.get("name").asString == ARTIFACT && !it.get("expired").asBoolean }
            ?: error("Артефакт последней сборки отсутствует или срок его хранения истёк")
        val digest = artifact.get("digest")?.takeUnless { it.isJsonNull }?.asString?.removePrefix("sha256:")
        require(digest != null && digest.matches(Regex("[a-fA-F0-9]{64}"))) {
            "GitHub не предоставил SHA-256 артефакта; обновление недоступно"
        }
        return LauncherUpdate(runId, build, run.get("head_sha").asString,
            "https://nightly.link/$REPOSITORY/suites/${run.get("check_suite_id").asLong}/artifacts/${artifact.get("id").asLong}", digest)
    }

    private fun get(endpoint: String): JsonObject {
        val response = http.send(HttpRequest.newBuilder(URI("https://api.github.com/repos/$REPOSITORY$endpoint"))
            .timeout(Duration.ofSeconds(30)).header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MateriaLauncher").GET().build(), HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            if (response.statusCode() == 403 || response.statusCode() == 429)
                "GitHub ограничил частоту запросов. Повторите позже."
            else "Не удалось проверить обновления: HTTP ${response.statusCode()}"
        }
        return JsonParser.parseString(response.body()).asJsonObject
    }

    /** Copy the tiny native executable out of the installation before replacing it. */
    fun start(target: Path, update: LauncherUpdate) {
        val source = target.resolve("updater/MateriaUpdater.exe")
        check(Files.isRegularFile(source)) { "Нативный обновлятор отсутствует. Переустановите лаунчер." }
        val directory = Files.createTempDirectory("materia-updater-")
        val executable = Files.copy(source, directory.resolve("MateriaUpdater.exe"))
        val values = mapOf("target" to target.toAbsolutePath().toString(), "pid" to ProcessHandle.current().pid().toString(),
            "runId" to update.runId.toString(), "url" to update.url, "sha256" to update.sha256)
        check(values.values.none { '\n' in it || '\r' in it }) { "Недопустимый путь установки" }
        val request = directory.resolve("request.txt")
        Files.writeString(request, values.entries.joinToString("\n") { "${it.key}=${it.value}" })
        val process = ProcessBuilder(executable.toString(), request.toString()).directory(directory.toFile())
            .redirectErrorStream(true).redirectOutput(directory.resolve("process.log").toFile()).start()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!Files.exists(directory.resolve("ready"))) {
            check(process.isAlive && System.nanoTime() < deadline) {
                process.destroy()
                "Не удалось открыть обновлятор. Лаунчер остаётся открыт. Журнал: $directory"
            }
            Thread.sleep(100)
        }
    }
}
