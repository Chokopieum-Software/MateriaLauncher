package funlauncher.auth

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.tools.ToolProvider
import kotlin.test.*

class CallbackLauncherTest {
    @Test fun launcherJarPreservesLongClasspathAndCallback() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        val root = Files.createTempDirectory("materia-callback-")
        try {
            // java.exe's -jar path is decoded using the Windows system code page.
            // Exercise Unicode in the manifest classpath, independently of that launcher limitation.
            val classes = Files.createDirectories(root.resolve("classes игрок with spaces"))
            val source = classes.resolve("InitKt.java")
            Files.writeString(source, """
                public class InitKt {
                    public static void main(String[] args) throws Exception {
                        if (args.length > 1) java.nio.file.Files.writeString(java.nio.file.Path.of(args[1]), args[0]);
                        else System.out.print(args[0]);
                    }
                }
            """.trimIndent())
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", classes.toString(), source.toString()))
            val javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe")
            val classpath = classes.toString() + ";" + (1..500).joinToString(";") {
                classes.resolve("dependency-$it.jar").toString()
            }
            assertTrue(classpath.length > 20000)
            val command = MicrosoftCallback.sourceLaunchCommand(javaExecutable, classpath, root)
            assertTrue(command.length < 1000, "Windows protocol command must remain short")
            val launcherJar = Regex("-jar \"([^\"]+)\"").find(command)!!.groupValues[1]
            val uri = "${MicrosoftCallback.redirectUri}?state=${MicrosoftCallback.randomValue()}&code=test%2Bcode"
            val process = ProcessBuilder(javaExecutable.toString(), "-jar", launcherJar, uri)
                .redirectErrorStream(true).start()
            assertTrue(process.waitFor(15, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), output)
            assertEquals(uri, output)
            assertEquals(command, MicrosoftCallback.sourceLaunchCommand(javaExecutable, classpath, root))

            val scheme = "materia-callback-test-${java.util.UUID.randomUUID().toString().replace("-", "") }"
            val key = "Software\\Classes\\$scheme"
            val result = root.resolve("shell-result.txt")
            try {
                val hiddenJava = javaExecutable.resolveSibling("javaw.exe")
                val shellCommand = MicrosoftCallback.sourceLaunchCommand(hiddenJava, classpath, root) + " \"$result\""
                WindowsProtocolRegistration.register(key, shellCommand)
                val shellUri = "$scheme://auth?state=test&code=synthetic%2Bcode"
                val handle = com.sun.jna.platform.win32.Shell32.INSTANCE.ShellExecute(
                    null, "open", shellUri, null, null, 0)
                assertTrue(handle.toLong() > 32, "ShellExecute failed")
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (!Files.exists(result) && System.nanoTime() < deadline) Thread.sleep(50)
                assertTrue(Files.exists(result), "Windows did not start the URI handler")
                val received = java.net.URI(Files.readString(result))
                assertEquals(scheme, received.scheme)
                assertEquals("auth", received.host)
                assertTrue(received.path.orEmpty() in setOf("", "/"))
                assertEquals(java.net.URI(shellUri).rawQuery, received.rawQuery)
            } finally {
                for (suffix in listOf("\\shell\\open\\command", "\\shell\\open", "\\shell", "")) {
                    if (com.sun.jna.platform.win32.Advapi32Util.registryKeyExists(com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER, key + suffix))
                        com.sun.jna.platform.win32.Advapi32Util.registryDeleteKey(com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER, key + suffix)
                }
            }
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
    }
}
