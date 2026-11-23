package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import funlauncher.net.CatalogProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.chokopieum.software.catalog.resources.*
import org.jetbrains.compose.resources.stringResource
import java.io.IOException

@Composable
internal fun WithProviderConsent(
    provider: CatalogProvider,
    store: ProviderConsentStore,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val legal = provider.legal
    if (legal == null) {
        content()
        return
    }
    var accepted by remember(store, provider.id, legal) { mutableStateOf<Boolean?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    LaunchedEffect(store, provider.id, legal) {
        accepted = withContext(Dispatchers.IO) { store.isAccepted(provider.id, legal.revision) }
    }
    when (accepted) {
        true -> content()
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        false -> {
            val scope = rememberCoroutineScope()
            val uriHandler = LocalUriHandler.current
            AlertDialog(
                onDismissRequest = { if (!saving) onBack() },
                title = { Text(stringResource(Res.string.provider_terms_title, provider.displayName)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.provider_terms_intro, provider.displayName))
                        TextButton(onClick = { uriHandler.openUri(legal.termsUrl) }) {
                            Text(stringResource(Res.string.provider_terms_link))
                        }
                        TextButton(onClick = { uriHandler.openUri(legal.privacyUrl) }) {
                            Text(stringResource(Res.string.provider_privacy_link))
                        }
                        if (saveFailed) Text(stringResource(Res.string.provider_consent_error),
                            color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton = {
                    Button(enabled = !saving, onClick = {
                        saving = true
                        saveFailed = false
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { store.accept(provider.id, legal.revision) }
                                accepted = true
                            } catch (_: IOException) {
                                saveFailed = true
                            } finally {
                                saving = false
                            }
                        }
                    }) { Text(stringResource(Res.string.provider_accept)) }
                },
                dismissButton = {
                    TextButton(enabled = !saving, onClick = onBack) { Text(stringResource(Res.string.back)) }
                }
            )
        }
    }
}
