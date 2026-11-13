package ui.screens.modifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import funlauncher.MinecraftBuild
import funlauncher.net.ModificationInstaller
import funlauncher.net.Version
import org.jetbrains.compose.resources.stringResource
import org.chokopieum.software.materia_launcher.generated.resources.Res
import org.chokopieum.software.materia_launcher.generated.resources.cancel
import org.chokopieum.software.materia_launcher.generated.resources.no_compatible_builds
import org.chokopieum.software.materia_launcher.generated.resources.select_a_build

@Composable
fun InstallModificationDialog(
    builds: List<MinecraftBuild>,
    version: Version,
    directory: String,
    onDismiss: () -> Unit,
    onInstall: (MinecraftBuild) -> Unit
) {
    val candidates = builds.map { it to ModificationInstaller.incompatibilityReasons(version, it, directory) }
        .sortedBy { it.second.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.select_a_build)) },
        text = {
            LazyColumn {
                if (candidates.none { it.second.isEmpty() }) {
                    item { Text(stringResource(Res.string.no_compatible_builds), modifier = Modifier.padding(bottom = 12.dp)) }
                }
                items(candidates) { (build, reasons) ->
                    Column(modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = reasons.isEmpty()) { onInstall(build) }.padding(12.dp)) {
                        Text(build.name, style = MaterialTheme.typography.titleSmall)
                        reasons.forEach { reason ->
                            Text(reason, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}
