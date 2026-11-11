package funlauncher.auth

import funlauncher.managers.PathManager
import java.awt.Desktop
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/** Custom-scheme activation is forwarded to the waiting process over TCP, never HTTP. */
object MicrosoftCallback {
    const val CLIENT_ID = "706fbc67-be98-4ded-bbe7-cc50097e2e2d"
    const val SCHEME = "msal$CLIENT_ID"
    val redirectUri: URI = URI("$SCHEME://auth")
    private val directory get() = PathManager.getDefaultAppDataDirectory().resolve("auth-callbacks")

    internal fun parameters(value: String): Map<String, String> {
        val uri = URI(value)
        require(uri.scheme.equals(SCHEME, ignoreCase = true) && uri.host.equals("auth", ignoreCase = true) && uri.port == -1 &&
            uri.userInfo == null && uri.path.orEmpty() in setOf("", "/") && uri.fragment == null) { "Некорректный адрес ответа Microsoft." }
        val result = linkedMapOf<String, String>()
        for (part in uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }) {
            val pair = part.split('=', limit = 2)
            val key = URLDecoder.decode(pair[0], UTF_8)
            require(key !in result) { "Повторяющийся параметр ответа Microsoft." }
            result[key] = URLDecoder.decode(pair.getOrElse(1) { "" }, UTF_8)
        }
        require(result["state"]?.matches(Regex("[A-Za-z0-9_-]{43}")) == true) { "Некорректный state Microsoft." }
        return result
    }

    /** Called before logging/UI initialization: authorization codes must never appear in logs. */
    fun handleActivation(args: Array<String>): Boolean {
        val value = args.firstOrNull { it.startsWith("$SCHEME:", ignoreCase = true) } ?: return false
        runCatching {
            val state = parameters(value).getValue("state")
            val port = Files.readString(directory.resolve("$state.port")).trim().toInt()
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), 3000)
                socket.soTimeout = 3000
                java.io.DataOutputStream(socket.getOutputStream()).writeUTF(value)
            }
        }
        return true // Unsolicited/expired callbacks must not open another launcher window.
    }

    fun randomValue(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(UTF_8)))

    fun awaitCode(state: String, authorizationUrl: () -> URI): String =
        receiveCode(state, directory) { Desktop.getDesktop().browse(authorizationUrl()) }

    internal fun receiveCode(state: String, callbackDirectory: java.nio.file.Path, openBrowser: () -> Unit): String {
        Files.createDirectories(callbackDirectory)
        ServerSocket(0, 8, InetAddress.getLoopbackAddress()).use { server ->
            val endpoint = callbackDirectory.resolve("$state.port")
            Files.writeString(endpoint, server.localPort.toString())
            try {
                openBrowser()
                val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(3)
                while (System.nanoTime() < deadline) {
                    server.soTimeout = ((deadline - System.nanoTime()) / 1_000_000).toInt().coerceAtLeast(1)
                    val response = server.accept().use { socket ->
                        socket.soTimeout = 3000
                        runCatching { parameters(java.io.DataInputStream(socket.getInputStream()).readUTF()) }.getOrNull()
                    } ?: continue
                    if (response["state"] != state) continue
                    if (response.containsKey("error")) throw IllegalStateException("Вход Microsoft отменён или отклонён (${response["error"]}).")
                    return response["code"]?.takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("Microsoft не вернул код авторизации.")
                }
                error("Время ожидания входа Microsoft истекло.")
            } catch (_: java.net.SocketTimeoutException) {
                error("Время ожидания входа Microsoft истекло. Попробуйте ещё раз.")
            } finally {
                Files.deleteIfExists(endpoint)
            }
        }
    }

    internal fun launchCommand(): String {
        val packaged = System.getProperty("jpackage.app-path")
        if (!packaged.isNullOrBlank()) return "\"$packaged\" \"%1\""
        val javaExecutable = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "javaw.exe")
        val classpath = System.getProperty("java.class.path").split(java.io.File.pathSeparator)
            .joinToString(java.io.File.pathSeparator) { java.io.File(it).absolutePath }
        require('"' !in classpath && Files.exists(javaExecutable)) { "Не удалось определить команду запуска лаунчера." }
        return sourceLaunchCommand(javaExecutable, classpath,
            PathManager.getDefaultAppDataDirectory().resolve("auth-launchers"))
    }

    /** Keep the Windows protocol command short, independent of the IDE's expanded classpath. */
    internal fun sourceLaunchCommand(javaExecutable: java.nio.file.Path, classpath: String,
                                     launcherDirectory: java.nio.file.Path): String {
        Files.createDirectories(launcherDirectory)
        val identity = MessageDigest.getInstance("SHA-256")
            .digest("$javaExecutable\n$classpath".toByteArray(UTF_8)).joinToString("") { "%02x".format(it) }
        val launcherJar = launcherDirectory.resolve("callback-$identity.jar").toAbsolutePath()
        // Manifest URLs preserve Unicode independently of the Windows launcher argument-file encoding.
        if (!Files.exists(launcherJar)) {
            val manifest = java.util.jar.Manifest()
            manifest.mainAttributes[java.util.jar.Attributes.Name.MANIFEST_VERSION] = "1.0"
            manifest.mainAttributes[java.util.jar.Attributes.Name.MAIN_CLASS] = "InitKt"
            manifest.mainAttributes[java.util.jar.Attributes.Name.CLASS_PATH] =
                classpath.split(java.io.File.pathSeparator).joinToString(" ") {
                    java.io.File(it).absoluteFile.toURI().toASCIIString()
                }
            val temporary = Files.createTempFile(launcherDirectory, "callback-", ".tmp")
            try {
                java.util.jar.JarOutputStream(Files.newOutputStream(temporary), manifest).use { }
                Files.move(temporary, launcherJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temporary) }
        }
        return "\"$javaExecutable\" -jar \"$launcherJar\" \"%1\""
    }

    fun ensureRegistered() {
        check(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "Автоматическая настройка протокола Microsoft поддерживается только в Windows."
        }
        val command = launchCommand()
        val key = "Software\\Classes\\$SCHEME"
        if (WindowsProtocolRegistration.isRegistered(key, command)) return
        var approved = false
        val ask = Runnable {
            approved = JOptionPane.showConfirmDialog(null,
                "Настроить переход $redirectUri в Materia Launcher?\n" +
                    "Обработчик отсутствует или указывает на другую копию программы.\n" +
                    "Будет изменена регистрация протокола только для текущего пользователя Windows.",
                "Настройка входа Microsoft", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION
        }
        if (SwingUtilities.isEventDispatchThread()) ask.run() else SwingUtilities.invokeAndWait(ask)
        check(approved) { "Настройка протокола отменена. Вход Microsoft не выполнен." }
        WindowsProtocolRegistration.register(key, command)
    }
}
