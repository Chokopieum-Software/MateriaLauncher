package funlauncher.auth

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER

/** Use the Unicode registry API: reg.exe reparses and loses embedded command-line quotes. */
internal object WindowsProtocolRegistration {
    fun isRegistered(key: String, command: String): Boolean {
        val commandKey = "$key\\shell\\open\\command"
        return Advapi32Util.registryValueExists(HKEY_CURRENT_USER, key, "URL Protocol") &&
            Advapi32Util.registryValueExists(HKEY_CURRENT_USER, commandKey, "") &&
            Advapi32Util.registryGetStringValue(HKEY_CURRENT_USER, commandKey, "") == command
    }

    fun register(key: String, command: String) {
        try {
            val commandKey = "$key\\shell\\open\\command"
            Advapi32Util.registryCreateKey(HKEY_CURRENT_USER, key)
            Advapi32Util.registrySetStringValue(HKEY_CURRENT_USER, key, "", "URL:Materia Microsoft login")
            Advapi32Util.registrySetStringValue(HKEY_CURRENT_USER, key, "URL Protocol", "")
            Advapi32Util.registryCreateKey(HKEY_CURRENT_USER, commandKey)
            Advapi32Util.registrySetStringValue(HKEY_CURRENT_USER, commandKey, "", command)
            check(isRegistered(key, command)) { "Windows не сохранила обработчик протокола Microsoft." }
        } catch (e: Win32Exception) {
            throw IllegalStateException("Не удалось зарегистрировать протокол Microsoft: ошибка Windows ${e.errorCode}. ${e.message}", e)
        }
    }
}
