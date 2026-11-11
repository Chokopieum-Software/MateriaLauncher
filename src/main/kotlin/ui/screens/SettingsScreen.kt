/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import funlauncher.AppSettings
import funlauncher.Theme
import funlauncher.auth.AccountManager
import funlauncher.managers.CacheManager
import ui.widgets.RamSelector
import ui.widgets.ThemePicker
import ui.widgets.LaunchOptionsFields
import funlauncher.utils.validateLaunchOptions
import java.lang.management.ManagementFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import funlauncher.openUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.chokopieum.software.materia_launcher.generated.resources.GitHub
import org.chokopieum.software.materia_launcher.generated.resources.MLicon
import org.chokopieum.software.materia_launcher.generated.resources.Res
import org.jetbrains.compose.resources.painterResource
import ui.viewmodel.AccountViewModel
import java.io.File
import java.net.URI
import java.util.*

object AppInfo {
    val version: String
    val buildNumber: String
    val buildSource: String
    val gradleVersion: String
    val osInfo: String
    val renderApi: String

    init {
        val props = Properties()
        try {
            this.javaClass.classLoader.getResourceAsStream("app.properties").use { stream ->
                if (stream != null) {
                    props.load(stream)
                }
            }
        } catch (e: Exception) {
            // Файл может отсутствовать в IDE, это нормально
        }
        version = listOf(props.getProperty("version", "Dev"), props.getProperty("channel", ""))
            .filter { it.isNotBlank() && it != "Stable" }.joinToString(" ")
        buildNumber = props.getProperty("buildNumber", "Local")
        buildSource = props.getProperty("buildSource", "IDE")
        gradleVersion = props.getProperty("gradleVersion", "N/A")
        osInfo = retrieveOsInfo()
        renderApi = System.getProperty("skiko.renderApi", "Unknown")
    }

    private fun retrieveOsInfo(): String {
        val osName = System.getProperty("os.name")
        val osArch = System.getProperty("os.arch")
        if (osName.startsWith("Linux")) {
            return try {
                val osReleaseFile = File("/etc/os-release")
                if (osReleaseFile.exists()) {
                    val properties = Properties()
                    properties.load(osReleaseFile.inputStream())
                    val prettyName = properties.getProperty("PRETTY_NAME", osName)
                    "$prettyName ($osArch)"
                } else {
                    "$osName ($osArch)"
                }
            } catch (e: Exception) {
                "$osName ($osArch)"
            }
        }
        return "$osName ($osArch)"
    }
}

private enum class SettingsSection(val title: String, val icon: ImageVector) {
    Appearance("Внешний вид", Icons.Default.Palette),
    Launch("Запуск игры", Icons.Default.PlayArrow),
    Cache("Кэш", Icons.Default.Refresh), // Новая секция для кэша
    About("О программе", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onOpenJavaManager: () -> Unit,
    accountManager: AccountManager,
    coroutineScope: CoroutineScope,
    cacheManager: CacheManager,
    startupSettings: AppSettings,
    saving: Boolean,
    saveError: String?,
    saved: Boolean,
    onRetrySave: () -> Unit
) {
    var currentSection by remember { mutableStateOf(SettingsSection.Appearance) }
    val accountViewModel = remember { AccountViewModel(accountManager, coroutineScope) }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxHeight().width(200.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Настройки",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.primary
            )
            SettingsSection.values().forEach { section ->
                val isSelected = currentSection == section
                TextButton(
                    onClick = { currentSection = section },
                    modifier = Modifier.fillMaxWidth().semantics { selected = isSelected },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(section.icon, contentDescription = section.title)
                        Spacer(Modifier.width(16.dp))
                        Text(section.title)
                    }
                }
            }
        }

        Divider(modifier = Modifier.fillMaxHeight().width(1.dp))

        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedContent(
            targetState = currentSection,
            modifier = Modifier.widthIn(max = 860.dp).fillMaxSize().clipToBounds(),
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                ((fadeIn(tween(200, delayMillis = 40)) +
                    slideInVertically(tween(240)) { direction * (it / 40).coerceAtLeast(1) }) togetherWith
                    (fadeOut(tween(120)) +
                        slideOutVertically(tween(160)) { -direction * (it / 40).coerceAtLeast(1) }))
                    .using(null)
            },
            label = "Settings section"
        ) { section ->
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(section.title, style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface)
            if (currentSettings.language != startupSettings.language ||
                currentSettings.useBorderlessWindow != startupSettings.useBorderlessWindow) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Text("Некоторые изменения вступят в силу после перезапуска приложения.", Modifier.padding(16.dp))
                }
            }
            when {
                saving -> Text("Сохранение…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                saveError != null -> OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(saveError, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetrySave) { Text("Повторить сохранение") }
                    }
                }
                saved -> Text("Настройки сохранены", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val save: (AppSettings) -> Unit = { if (!saving) onSave(it) }
            Box(Modifier.weight(1f)) { when (section) {
                SettingsSection.Appearance -> AppearanceSettings(currentSettings, save, saving)
                SettingsSection.Launch -> LaunchSettings(currentSettings, save, onOpenJavaManager, accountViewModel, saving)
                SettingsSection.Cache -> CacheSettings(cacheManager)
                SettingsSection.About -> AboutScreen()
            } }
        }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettings(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
    saving: Boolean
) {
    val languages = remember { mapOf("ru" to "Русский", "en" to "English") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Тема", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                ThemePicker(currentSettings.theme, enabled = !saving,
                    onSelect = { theme -> if (theme != currentSettings.theme) onSave(currentSettings.copy(theme = theme)) })
            }
        }
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Язык (требуется перезапуск)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!saving) expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = languages[currentSettings.language] ?: currentSettings.language,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !saving,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        languages.forEach { (code, name) ->
                            DropdownMenuItem(
                                enabled = !saving,
                                text = { Text(name) },
                                onClick = {
                                    if (currentSettings.language != code) {
                                        onSave(currentSettings.copy(language = code))

                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Без системной рамки (требуется перезапуск)", modifier = Modifier.weight(1f))
                    Switch(
                        enabled = !saving,
                        checked = currentSettings.useBorderlessWindow,
                        onCheckedChange = {
                            onSave(currentSettings.copy(useBorderlessWindow = it))

                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LaunchSettings(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onOpenJavaManager: () -> Unit,
    accountViewModel: AccountViewModel,
    saving: Boolean
) {
    var maxRam by remember(currentSettings.maxRamMb) { mutableStateOf(currentSettings.maxRamMb) }
    var javaArgs by remember(currentSettings.javaArgs) { mutableStateOf(currentSettings.javaArgs) }
    var envVars by remember(currentSettings.envVars) { mutableStateOf(currentSettings.envVars) }
    var advanced by remember { mutableStateOf(false) }
    var confirmRisk by remember { mutableStateOf(false) }
    val options = validateLaunchOptions(javaArgs, envVars, maxRam)
    val saveLaunch = { onSave(currentSettings.copy(maxRamMb = maxRam, javaArgs = javaArgs, envVars = envVars)) }
    val totalRam = remember {
        (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)
            ?.totalMemorySize?.div(1024 * 1024)?.toInt() ?: 8192
    }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    val accountError by accountViewModel.errorMessage.collectAsState()

    accountError?.let { message ->
        AlertDialog(
            onDismissRequest = { accountViewModel.clearErrorMessage() },
            title = { Text("Ошибка аккаунта Microsoft") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { accountViewModel.clearErrorMessage() }) { Text("OK") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Запускать консоль при запуске игры", modifier = Modifier.weight(1f))
                    Switch(
                        enabled = !saving,
                        checked = currentSettings.showConsoleOnLaunch,
                        onCheckedChange = { onSave(currentSettings.copy(showConsoleOnLaunch = it)) }
                    )
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Параметры по умолчанию", style = MaterialTheme.typography.titleMedium)
                Text("Применяются к сборкам без собственных значений памяти, аргументов Java и переменных среды.",
                    style = MaterialTheme.typography.bodySmall)
                RamSelector(maxRam, totalRam, { maxRam = it })
                TextButton(onClick = { advanced = !advanced }) {
                    Text(if (advanced) "Скрыть дополнительные параметры" else "Дополнительно")
                }
                if (advanced) LaunchOptionsFields(javaArgs, { javaArgs = it }, envVars, { envVars = it }, maxRam)
                Button(enabled = !saving && options.valid &&
                    (maxRam != currentSettings.maxRamMb || javaArgs != currentSettings.javaArgs || envVars != currentSettings.envVars),
                    onClick = { if (options.risks.isNotEmpty()) confirmRisk = true else saveLaunch() }) {
                    Text("Сохранить параметры запуска")
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Java", style = MaterialTheme.typography.titleMedium)
                Text("Java выбирается автоматически по версии игры, если в настройках сборки не указан путь.")
                Button(onClick = onOpenJavaManager) { Text("Управление Java") }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Аккаунты", style = MaterialTheme.typography.titleMedium)
                Text("Очистка сессии потребует повторного входа в аккаунты Microsoft.", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { showLogoutConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Очистить сессию Microsoft")
                }
            }
        }
    }

    if (confirmRisk) {
        AlertDialog(onDismissRequest = { confirmRisk = false },
            title = { Text("Подтверждение параметров запуска") },
            text = { Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                options.risks.forEach { Text(it, Modifier.padding(bottom = 12.dp)) }
            } },
            confirmButton = { Button(onClick = { confirmRisk = false; saveLaunch() }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { confirmRisk = false }) { Text("Отмена") } })
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("Подтверждение") },
            text = { Text("Вы уверены, что хотите выйти из всех аккаунтов Microsoft и очистить сохраненную сессию? Вам потребуется войти заново.") },
            confirmButton = {
                Button(
                    onClick = {
                        accountViewModel.logoutFromMicrosoft()
                        showLogoutConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Да, очистить")
                }
            },
            dismissButton = {
                Button(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun CacheSettings(cacheManager: CacheManager) {
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    val statusFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
    val status by statusFlow.collectAsState()
    var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Метаданные версий", style = MaterialTheme.typography.titleMedium)
                Text("Обновляет списки версий игры и загрузчиков. Установленные сборки не изменяются.")
                status?.let { Text(it, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                if (updating) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(enabled = !updating, onClick = {
                    updating = true
                    error = false
                    statusFlow.value = "Обновление метаданных…"
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                cacheManager.refreshMetadata { message ->
                                    statusFlow.value = message
                                }
                            }
                            statusFlow.value = "Метаданные версий обновлены"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = true
                            statusFlow.value = "Не удалось обновить метаданные. Проверьте подключение и повторите попытку."
                        } finally {
                            updating = false
                        }
                    }
                }) { Text(if (updating) "Обновление…" else if (error) "Повторить" else "Обновить метаданные") }
            }
        }
    }
}

@Composable
private fun AboutScreen() {
    var showTechInfo by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Image(painterResource(Res.drawable.MLicon), contentDescription = null,
                                modifier = Modifier.padding(12.dp).size(56.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Materia", style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("Лаунчер Minecraft", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text("Версия ${AppInfo.version}  ·  Сборка ${AppInfo.buildNumber}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(onClick = {
                        openUri(URI("https://github.com/Chokopieum-Software/MateriaKraft-Launcher"))
                    }) {
                        Icon(painterResource(Res.drawable.GitHub), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Проект на GitHub")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
            OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showTechInfo = !showTechInfo }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(16.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Техническая информация", modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface)
                    Icon(if (showTechInfo) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showTechInfo) "Свернуть" else "Развернуть")
                }
                AnimatedVisibility(showTechInfo) {
                    SelectionContainer {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HorizontalDivider()
                            listOf("Источник" to AppInfo.buildSource, "Gradle" to AppInfo.gradleVersion,
                                "Система" to AppInfo.osInfo, "Рендеринг" to AppInfo.renderApi).forEach { (label, value) ->
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(label, Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "Не является официальным продуктом Minecraft. Не одобрено и не связано с Mojang или Microsoft.",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
