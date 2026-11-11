package funlauncher.auth

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsProtocolRegistrationTest {
    @Test fun preservesQuotedLaunchCommand() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        val key = "Software\\MateriaLauncherProtocolTests\\${UUID.randomUUID()}"
        val command = "\"C:\\Program Files\\Materia\\javaw.exe\" -cp \"C:\\Users\\Игрок\\launcher.jar;C:\\Other Path\\core.jar\" InitKt \"%1\""
        try {
            assertFalse(WindowsProtocolRegistration.isRegistered(key, command))
            WindowsProtocolRegistration.register(key, command)
            assertEquals(command, Advapi32Util.registryGetStringValue(HKEY_CURRENT_USER, "$key\\shell\\open\\command", ""))
            assertEquals("", Advapi32Util.registryGetStringValue(HKEY_CURRENT_USER, key, "URL Protocol"))
            assertTrue(WindowsProtocolRegistration.isRegistered(key, command))
            assertFalse(WindowsProtocolRegistration.isRegistered(key, "$command --other"))
            // An IDE classpath can exceed reg.exe's command-line limits.
            val longCommand = command + " -cp \"" + "C:\\Каталог с пробелами\\dependency.jar;".repeat(500) + "\""
            WindowsProtocolRegistration.register(key, longCommand)
            assertTrue(WindowsProtocolRegistration.isRegistered(key, longCommand))
            WindowsProtocolRegistration.register(key, command)
            assertTrue(WindowsProtocolRegistration.isRegistered(key, command))
        } finally {
            for (suffix in listOf("\\shell\\open\\command", "\\shell\\open", "\\shell", "")) {
                if (Advapi32Util.registryKeyExists(HKEY_CURRENT_USER, key + suffix))
                    Advapi32Util.registryDeleteKey(HKEY_CURRENT_USER, key + suffix)
            }
        }
    }
}
