/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.runtime.*
import funlauncher.JavaInfo
import funlauncher.managers.JavaManager
import funlauncher.managers.PathManager
import kotlinx.coroutines.*
import java.lang.management.ManagementFactory

@Composable
internal fun rememberBuildJavaCheck(javaManager: JavaManager, selection: String): Pair<String, JavaInfo?> {
    val check by produceState<Pair<String, JavaInfo?>>("" to null, selection) {
        value = selection to if (selection.isBlank()) null
            else runCatching { javaManager.getJavaInfo(selection) }.getOrNull()
    }
    return check
}

@Composable
internal fun rememberBuildJavaInstallations(pathManager: PathManager, enabled: Boolean = true): List<JavaInfo> {
    val javaManager = remember { JavaManager(pathManager) }
    var installations by remember { mutableStateOf<List<JavaInfo>>(emptyList()) }
    LaunchedEffect(enabled) {
        if (enabled) installations = javaManager.findJavaInstallations().let { it.launcher + it.system }
    }
    return installations
}

@Composable
internal fun rememberBuildTotalRamMb(): Int = remember {
    ((ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).totalMemorySize / (1024 * 1024)).toInt()
}
