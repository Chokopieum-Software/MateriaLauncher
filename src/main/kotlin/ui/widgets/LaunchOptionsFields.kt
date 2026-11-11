package ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.utils.validateLaunchOptions

@Composable
fun LaunchOptionsFields(javaArgs: String, onJavaArgsChange: (String) -> Unit,
    envVars: String, onEnvVarsChange: (String) -> Unit, maxRamMb: Int) {
    val options = validateLaunchOptions(javaArgs, envVars, maxRamMb)
    OutlinedTextField(javaArgs, onJavaArgsChange, Modifier.fillMaxWidth().heightIn(min = 100.dp),
        label = { Text("Аргументы Java") }, isError = options.argumentErrors.isNotEmpty(),
        supportingText = { Text(options.argumentErrors.joinToString("\n").ifEmpty {
            "Например: -XX:+UseG1GC -Dfile.encoding=UTF-8. Значения с пробелами заключайте в кавычки."
        }) })
    OutlinedTextField(envVars, onEnvVarsChange, Modifier.fillMaxWidth().heightIn(min = 100.dp),
        label = { Text("Переменные среды") }, isError = options.environmentErrors.isNotEmpty(),
        supportingText = { Text(options.environmentErrors.joinToString("\n").ifEmpty {
            "Одна переменная ИМЯ=значение на строку. Например: MY_SETTING=value"
        }) })
}
