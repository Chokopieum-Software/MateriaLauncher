package ui.screens.modifications

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

internal object WebView2Runtime {
    const val DOWNLOAD_URL = "https://developer.microsoft.com/microsoft-edge/webview2/"
    private const val CLIENT = "Microsoft\\EdgeUpdate\\Clients\\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}"

    fun installedVersion(): String? {
        if (!System.getProperty("os.name").startsWith("Windows", true)) return null
        val locations = listOf(
            WinReg.HKEY_CURRENT_USER to "Software\\$CLIENT",
            WinReg.HKEY_LOCAL_MACHINE to "SOFTWARE\\WOW6432Node\\$CLIENT",
            WinReg.HKEY_LOCAL_MACHINE to "SOFTWARE\\$CLIENT"
        )
        return locations.firstNotNullOfOrNull { (root, path) ->
            runCatching { Advapi32Util.registryGetStringValue(root, path, "pv") }
                .getOrNull()?.takeIf(::isInstalledVersion)
        }
    }

    internal fun isInstalledVersion(version: String): Boolean =
        version.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) && version.split('.').any { it.toLongOrNull()?.let { n -> n > 0 } == true }

    fun requireAvailable() {
        check(System.getProperty("os.name").startsWith("Windows", true)) {
            "Просмотр HTML через WebView2 доступен в Windows."
        }
        check(installedVersion() != null) {
            "Microsoft Edge WebView2 Runtime не найден. Установите Evergreen Runtime с сайта Microsoft и повторите проверку."
        }
    }
}
