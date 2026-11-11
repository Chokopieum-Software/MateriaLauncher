/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.AppSettings
import java.lang.management.ManagementFactory
import ui.widgets.RamSelector
import ui.widgets.LaunchOptionsFields
import funlauncher.utils.validateLaunchOptions

@Composable
fun LaunchSettingsDialog(
    currentSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var maxRam by remember { mutableStateOf(currentSettings.maxRamMb) }
    var javaArgs by remember { mutableStateOf(currentSettings.javaArgs) }
    var envVars by remember { mutableStateOf(currentSettings.envVars) }
    val options = validateLaunchOptions(javaArgs, envVars, maxRam)
    var confirmRisk by remember { mutableStateOf(false) }
    val save = {
        onSave(currentSettings.copy(maxRamMb = maxRam, javaArgs = javaArgs, envVars = envVars))
        onDismiss()
    }
    if (confirmRisk) {
        AlertDialog(
            onDismissRequest = { confirmRisk = false },
            title = { Text("Подтверждение параметров запуска") },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                options.risks.forEach { Text(it, Modifier.padding(bottom = 12.dp)) }
            } },
            confirmButton = { Button(onClick = { confirmRisk = false; save() }) { Text("Сохранить, несмотря на риски") } },
            dismissButton = { TextButton(onClick = { confirmRisk = false }) { Text("Вернуться к настройкам") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Глобальные настройки запуска") },
        text = {
            Column(modifier = Modifier.heightIn(max = 550.dp).verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
                val totalSystemMemory = remember {
                    (ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean).totalMemorySize / (1024 * 1024)
                }
                RamSelector(
                    currentRam = maxRam,
                    totalRam = totalSystemMemory.toInt(),
                    onRamChange = { maxRam = it }
                )
                Spacer(Modifier.height(16.dp))
                LaunchOptionsFields(javaArgs, { javaArgs = it }, envVars, { envVars = it }, maxRam)
            }
        },
        confirmButton = {
            Button(enabled = options.valid, onClick = {
                if (options.risks.isNotEmpty()) confirmRisk = true else save()
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
