/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.key.KeyEvent
import org.jetbrains.jewel.window.utils.clientRegion
import ui.theme.availableThemes
import ui.theme.enableWindowsAcrylic
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import funlauncher.*
import funlauncher.auth.AccountManager
import funlauncher.database.DatabaseManager
import funlauncher.game.VersionMetadataFetcher
import funlauncher.managers.BuildManager
import funlauncher.managers.CacheManager
import funlauncher.managers.JavaManager
import funlauncher.managers.PathManager
import funlauncher.net.JavaDownloader
import funlauncher.utils.LogCollector
import funlauncher.update.LauncherChangelog
import ui.dialogs.LauncherChangelogDialog
import kotlinx.coroutines.*
import org.chokopieum.software.materia_launcher.generated.resources.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import splash.createAndShowSplashScreen
import state.AppState
import state.Screen
import ui.App
import ui.screens.wizard.FirstRunWizard
import ui.theme.AnimatedAppTheme
import ui.viewmodel.AppViewModel
import ui.widgets.ImageLoader
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import java.util.*
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.system.exitProcess
import java.io.File
import javax.swing.JOptionPane
import ui.theme.rememberSystemDarkTheme
import androidx.compose.ui.graphics.Color
import funlauncher.SettingsManager
import funlauncher.Theme
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.intui.window.decoratedWindow
import ui.widgets.TitleBarDownloads
import ui.screens.TitleBarActions
import ui.viewmodel.HomeViewModel
import androidx.compose.ui.window.WindowPlacement

// Глобальные переменные для менеджеров, которые должны быть инициализированы до запуска Compose UI
private lateinit var globalPathManager: PathManager
private lateinit var globalSettingsManager: SettingsManager
private lateinit var globalAccountManager: AccountManager
private lateinit var globalBuildManager: BuildManager
private lateinit var globalJavaManager: JavaManager
private lateinit var globalJavaDownloader: JavaDownloader
private lateinit var globalCacheManager: CacheManager
private lateinit var globalVersionMetadataFetcher: VersionMetadataFetcher

@OptIn(ExperimentalResourceApi::class)
fun main(args: Array<String>) {
    if (funlauncher.auth.MicrosoftCallback.handleActivation(args)) return
    val instanceLock = try {
        SingleInstanceLock.acquire(PathManager.getDefaultAppDataDirectory())
    } catch (e: Exception) {
        e.printStackTrace()
        JOptionPane.showMessageDialog(
            null,
            "Не удалось получить блокировку запуска лаунчера:\n${e.message}",
            "Materia — ошибка запуска",
            JOptionPane.ERROR_MESSAGE
        )
        exitProcess(1)
    }
    if (instanceLock == null) {
        System.err.println("Materia is already running.")
        exitProcess(0)
    }
    Runtime.getRuntime().addShutdownHook(Thread({ instanceLock.close() }, "launcher-instance-lock"))
    // Инициализируем сборщик логов при запуске приложения
    LogCollector.init()

    val os = System.getProperty("os.name").lowercase()
    val isWindows = os.contains("win")
    val isUiTest = args.contains("--uitest")

    val appDataDir = PathManager.getDefaultAppDataDirectory().toFile()
    val openGLFlag = File(appDataDir, "use_opengl.flag")

    try {
        if (isUiTest) {
            System.setProperty("skiko.renderApi", "SOFTWARE")
            println("Using render API: SOFTWARE (UI Test)")
        } else if (isWindows) {
            if (openGLFlag.exists()) {
                System.setProperty("skiko.renderApi", "OPENGL")
                println("Using render API: OPENGL (Fallback via flag)")
            } else {
                // Avoid unsupported DirectX12 initialization; allow an explicit renderer override.
                val renderApi = System.getProperty("skiko.renderApi") ?: "OPENGL"
                System.setProperty("skiko.renderApi", renderApi)
                println("Using render API: $renderApi (Windows)")
            }
        }

        runApplication(isUiTest)

    } catch (e: Throwable) {
        val isDirectXError = e.stackTraceToString().contains("org.jetbrains.skiko.RenderException") ||
                e.stackTraceToString().contains("DirectX") || e.stackTraceToString().contains("DIRECT3D")

        if (isWindows && !openGLFlag.exists() && isDirectXError) {
            println("DirectX initialization failed. Creating OpenGL fallback flag.")
            openGLFlag.createNewFile()
            JOptionPane.showMessageDialog(
                null,
                "Не удалось запустить приложение с использованием DirectX.\n" +
                        "При следующем запуске будет использован OpenGL.\n" +
                        "Пожалуйста, перезапустите лаунчер.",
                "Ошибка графики",
                JOptionPane.ERROR_MESSAGE
            )
            exitProcess(1)
        } else {
            // Если это не ошибка DirectX или флаг уже существует, просто выводим ошибку
            println("An unexpected error occurred:")
            e.printStackTrace()
            JOptionPane.showMessageDialog(
                null,
                "Произошла непредвиденная ошибка:\n${e.message}",
                "Критическая ошибка",
                JOptionPane.ERROR_MESSAGE
            )
            exitProcess(1)
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
private fun runApplication(isUiTest: Boolean) {
    val statusLabel = JLabel("Initializing...")
    val splash = createAndShowSplashScreen(statusLabel)

    globalPathManager = PathManager(PathManager.getDefaultAppDataDirectory())
    val (initialSettings, startupChangelog) = runBlocking(Dispatchers.IO) {
        SwingUtilities.invokeLater { statusLabel.text = "Loading configuration..." }
        globalPathManager.createRequiredDirectories()
        DatabaseManager.init(globalPathManager)

        globalSettingsManager = SettingsManager(globalPathManager)
        val settings = globalSettingsManager.loadSettings()
        Locale.setDefault(Locale.forLanguageTag(settings.language))

        globalBuildManager = BuildManager(globalPathManager)
        globalAccountManager = AccountManager(globalPathManager)
        globalJavaManager = JavaManager(globalPathManager)
        globalJavaDownloader = JavaDownloader(globalPathManager, globalJavaManager)
        globalCacheManager = CacheManager(globalPathManager, globalSettingsManager) // Передаем SettingsManager
        globalVersionMetadataFetcher = VersionMetadataFetcher(globalBuildManager, globalPathManager)

        ImageLoader.init(globalCacheManager)
        settings to LauncherChangelog.onStartup(existingInstallation = settings.setupCompleted)
    }

    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            // Обновляем метаданные через CacheManager
            globalCacheManager.refreshMetadata { status ->
                SwingUtilities.invokeLater { statusLabel.text = status }
            }
        }.onFailure {
            println("Warning: Failed to refresh metadata: ${it.stackTraceToString()}")
        }
    }

    application {
        var changelog by remember { mutableStateOf(startupChangelog) }
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
        var appState by remember { mutableStateOf<AppState?>(null) }
        val scope = rememberCoroutineScope()
        
        // Состояние, отслеживающее, нужно ли показывать Мастер настройки. 
        // Изменяется на false, когда настройка завершена.
        var showFirstRunWizard by remember { mutableStateOf(!initialSettings.setupCompleted) }

        val icon = painterResource(Res.drawable.materia_logo)

        LaunchedEffect(currentScreen) {
            if (currentScreen is Screen.Splash) {
                scope.launch(Dispatchers.IO) {
                    if (showFirstRunWizard) {
                        withContext(Dispatchers.Main) {
                            currentScreen = Screen.FirstRunWizard
                        }
                    } else {
                        SwingUtilities.invokeLater { statusLabel.text = "Loading UI..." }
                        val settingsJob = async { globalSettingsManager.loadSettings() }
                        val buildsJob = async { globalBuildManager.loadBuilds() }
                        val accountsJob = async { globalAccountManager.loadAccounts() }

                        val loadedState = AppState(
                            settings = settingsJob.await(),
                            builds = buildsJob.await(),
                            accounts = accountsJob.await()
                        )
                        withContext(Dispatchers.Main) {
                            appState = loadedState
                            currentScreen = Screen.MainApp(loadedState)
                        }
                    }
                }
            }
        }

        val state = (currentScreen as? Screen.MainApp)?.state
        val viewModel = if (state != null) remember(state) {
            AppViewModel(
                appState = state,
                buildManager = globalBuildManager,
                accountManager = globalAccountManager,
                javaManager = globalJavaManager,
                javaDownloader = globalJavaDownloader,
                versionMetadataFetcher = globalVersionMetadataFetcher,
                onSettingsChange = { newSettings ->
                    globalSettingsManager.saveSettings(newSettings)
                    appState = appState?.copy(settings = newSettings)
                }
            )
        } else null
        val homeViewModel = if (viewModel != null && state != null) remember(viewModel) {
            HomeViewModel(viewModel, globalPathManager, state.settings)
        } else null
        var wizardTheme by remember { mutableStateOf(initialSettings.theme) }
        val currentAppState = appState
        val theme = currentAppState?.settings?.theme ?: wizardTheme
        val isDarkTheme = when (theme) {
            Theme.Light -> false
            Theme.Dark -> true
            Theme.System, Theme.Acrylic -> rememberSystemDarkTheme()
        }
        val themeDefinition = if (isDarkTheme) {
            JewelTheme.darkThemeDefinition()
        } else {
            JewelTheme.lightThemeDefinition()
        }

        IntUiTheme(theme = themeDefinition, styling = ComponentStyling.decoratedWindow(), swingCompatMode = true) {
            val windowState = rememberWindowState(width = 1280.dp, height = 720.dp, placement = WindowPlacement.Maximized)
            val close = {
                viewModel?.cancelScope()
                exitApplication()
            }
            val handleKey: (KeyEvent) -> Boolean = {
                if (it.isCtrlPressed && it.key == Key.Grave && it.type == KeyEventType.KeyDown) {
                    LogCollector.saveLogsToDesktop()
                    true
                } else {
                    false
                }
            }
            val content: @Composable (Boolean) -> Unit = { acrylic ->
                AnimatedAppTheme(theme, acrylicActive = acrylic) {
                    androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(),
                        color = if (acrylic) Color.Transparent else androidx.compose.material3.MaterialTheme.colorScheme.surface) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (currentScreen == Screen.FirstRunWizard) {
                                Box(Modifier.widthIn(max = 600.dp)) {
                                    FirstRunWizard(
                                        accountManager = globalAccountManager,
                                        initialTheme = wizardTheme,
                                        onThemeChange = { wizardTheme = it },
                                        onWizardComplete = {
                                            globalSettingsManager.saveSettings(initialSettings.copy(theme = wizardTheme, setupCompleted = true))
                                            showFirstRunWizard = false
                                            currentScreen = Screen.Splash
                                        }
                                    )
                                }
                            } else if (viewModel != null && homeViewModel != null && currentAppState != null) {
                                App(
                                    viewModel = viewModel,
                                    appState = currentAppState,
                                    pathManager = globalPathManager,
                                    cacheManager = globalCacheManager,
                                    homeViewModel = homeViewModel,
                                    onExitForUpdate = {
                                        viewModel.cancelScope()
                                        exitApplication()
                                    }
                                )
                                changelog?.let { LauncherChangelogDialog(it) { changelog = null } }
                            }
                        }
                        if (currentScreen != Screen.Splash) {
                            LaunchedEffect(splash) { splash?.dispose() }
                        }
                    }
                }
            }
            DecoratedWindow(onCloseRequest = close, state = windowState, title = "Materia",
                icon = icon, onKeyEvent = handleKey) {
                SideEffect { window.minimumSize = java.awt.Dimension(1280, 720) }
                val useAcrylic = theme == Theme.Acrylic && theme in availableThemes &&
                    windowState.placement != WindowPlacement.Fullscreen && !windowState.isMinimized
                var acrylicActive by remember(window, useAcrylic, isDarkTheme) { mutableStateOf(false) }
                DisposableEffect(window, useAcrylic, isDarkTheme) {
                    val restore = if (useAcrylic) enableWindowsAcrylic(window, isDarkTheme) else null
                    acrylicActive = restore != null
                    onDispose { restore?.invoke() }
                }
                TitleBar {
                    if (homeViewModel != null && viewModel != null) {
                        TitleBarActions(homeViewModel, theme, Modifier.align(Alignment.Start)) { clientRegion(it) }
                        TitleBarDownloads(viewModel, theme, Modifier.align(Alignment.End)) { clientRegion(it) }
                    }
                }
                content(acrylicActive)
            }
        }
    }
}
