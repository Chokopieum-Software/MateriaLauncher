package ui.screens.modifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import funlauncher.net.Project
import funlauncher.net.SearchResult

@Composable
fun ModificationList(
    viewModel: ModificationsViewModel
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(viewModel.searchResult?.hits ?: emptyList()) { hit ->
                ModificationCard(hit) {
                    viewModel.selectProject(hit)
                }
            }

            if (viewModel.hasMoreResults) {
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
                                Text("Загрузить еще")
                            }
                        }
                    }
                }
            }
        }
    }
}