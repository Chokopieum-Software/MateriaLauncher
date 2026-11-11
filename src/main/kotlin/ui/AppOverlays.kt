package ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import funlauncher.managers.PathManager
import state.AppState
import ui.dialogs.*
import ui.screens.BuildSettingsScreen
import ui.screens.JavaManagerWindow
import ui.viewmodel.AppViewModel
import ui.widgets.GameConsole

@Composable
fun AppOverlays(
    createButtonBounds: Rect? = null,
    viewModel: AppViewModel,
    appState: AppState,
    pathManager: PathManager
) {
    if (viewModel.showJavaManagerWindow) {
        JavaManagerWindow(
            onCloseRequest = { viewModel.showJavaManagerWindow = false },
            javaManager = viewModel.javaManager,
            javaDownloader = viewModel.javaDownloader,
            appSettings = appState.settings
        )
    }

    if (viewModel.showAddBuildDialog) {
        AddBuildDialog(
            sourceBounds = createButtonBounds,
            pathManager = pathManager,
            onDismiss = { viewModel.showAddBuildDialog = false },
            onAdd = { name, version, type, imagePath ->
                viewModel.onAddBuild(name, version, type.name, imagePath)
            }
        )
    }

    viewModel.showBuildSettingsScreen?.let { build ->
        BuildSettingsScreen(
            build = build,
            pathManager = pathManager,
            globalSettings = appState.settings,
            onDismiss = { viewModel.showBuildSettingsScreen = null },
            onSave = { newName, newVersion, newType, newImagePath, javaPath, maxRam, javaArgs, envVars ->
                viewModel.onSaveBuildSettings(newName, newVersion, newType, newImagePath, javaPath, maxRam, javaArgs, envVars)
            }
        )
    }

    // This should be a separate component, but for now let's keep it here
    if (viewModel.showGameConsole) {
        GameConsole(output = viewModel.gameOutput)
    }

    viewModel.showRamWarningDialog?.let { build ->
        RamWarningDialog(
            build = build,
            message = viewModel.ramWarningMessage,
            onDismiss = {
                viewModel.showRamWarningDialog = null
                viewModel.isLaunchingBuildId = null
            },
            onConfirm = { viewModel.onConfirmRamWarning(build) }
        )
    }

    viewModel.errorDialogMessage?.let { message ->
        ErrorDialog(
            message = message,
            onDismiss = { viewModel.errorDialogMessage = null }
        )
    }

    viewModel.buildToDelete?.let { build ->
        ConfirmDeleteDialog(
            build = build,
            onDismiss = { viewModel.buildToDelete = null },
            onConfirm = { viewModel.onConfirmDelete(build) }
        )
    }

    if (viewModel.showTestBuildWarning) {
        TestBuildWarningDialog(
            title = viewModel.testBuildTitle ?: "",
            message = viewModel.testBuildMessage ?: "",
            onDismiss = { viewModel.showTestBuildWarning = false }
        )
    }
}
