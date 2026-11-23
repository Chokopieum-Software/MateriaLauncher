package ui.screens.modifications

import androidx.compose.runtime.Composable
import funlauncher.MinecraftBuild
import funlauncher.net.Version

@Composable
fun InstallModificationDialog(
    builds: List<MinecraftBuild>,
    version: Version,
    directory: String,
    onDismiss: () -> Unit,
    onInstall: (MinecraftBuild) -> Unit
) {
    BuildSelectionDialog(builds, onDismiss, version = version, directory = directory) { build ->
        if (build != null) onInstall(build)
    }
}
