package ui.screens.modifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

@Composable
fun ModificationList(
    viewModel: ModificationsViewModel,
    installEnabled: Boolean,
    onInstall: (funlauncher.net.Hit) -> Unit
) {
    if (viewModel.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (viewModel.searchResult?.hits?.isEmpty() == true) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Ничего не найдено\nПроверьте подключение к интернету или измените запрос",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val hits = viewModel.searchResult?.hits.orEmpty()
        val listState = rememberLazyListState()
        if (hits.isNotEmpty() && viewModel.hasMoreResults && !viewModel.pageLoadFailed) {
            LaunchedEffect(listState, hits.size) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                    .first { it >= hits.size - 3 }
                viewModel.loadNextPage()
            }
        }
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hits) { hit ->
                ModificationCard(hit, installEnabled, onInstall = { onInstall(hit) }) {
                    viewModel.selectProject(hit)
                }
            }

            if (viewModel.isLoadingMore || viewModel.pageLoadFailed) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (viewModel.isLoadingMore) {
                            CircularProgressIndicator()
                        } else {
                            Button(onClick = { viewModel.loadNextPage() }) {
                                Text("Повторить загрузку")
                            }
                        }
                    }
                }
            }
        }
    }
}
