package ui.screens.modifications

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import funlauncher.MinecraftBuild
import funlauncher.net.ModrinthCategoryTag
import funlauncher.net.ModrinthLoaderTag
import org.jetbrains.compose.resources.stringResource
import org.chokopieum.software.materia_launcher.generated.resources.Res
import org.chokopieum.software.materia_launcher.generated.resources.categories
import org.chokopieum.software.materia_launcher.generated.resources.excluded
import org.chokopieum.software.materia_launcher.generated.resources.included
import org.chokopieum.software.materia_launcher.generated.resources.loaders
import org.chokopieum.software.materia_launcher.generated.resources.versions
import ui.screens.FilterState
import ui.widgets.ImageLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterPanel(
    viewModel: ModificationsViewModel
) {
    var showAllCategoriesList by remember { mutableStateOf(false) }
    var showAllLoadersList by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(250.dp)
            .padding(16.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 2.dp,
            onClick = {
                viewModel.showVersionSelectionDialog = true
            }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.List,
                    contentDescription = "Выбрать версии",
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (viewModel.selectedVersions.isEmpty()) "Выбрать версии" else viewModel.selectedVersions.joinToString(),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.categoriesExpanded = !viewModel.categoriesExpanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(Res.string.categories), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.categoriesExpanded = !viewModel.categoriesExpanded }) {
                Icon(
                    imageVector = if (viewModel.categoriesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (viewModel.categoriesExpanded) "Свернуть категории" else "Развернуть категории"
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(visible = viewModel.categoriesExpanded) {
            Column {
                val categoriesToDisplay = if (showAllCategoriesList) viewModel.filteredCategories else viewModel.filteredCategories.take(7)
                categoriesToDisplay.forEach { category ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 2.dp
                    ) {
                        val categoryIcon = ImageLoader.rememberImagePainterFromUrl(category.icon)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val currentState = viewModel.selectedCategories[category.name]
                                val nextState = when (currentState) {
                                    null -> FilterState.INCLUDED
                                    FilterState.INCLUDED -> FilterState.EXCLUDED
                                    FilterState.EXCLUDED -> null
                                }
                                if (nextState == null) {
                                    viewModel.selectedCategories.remove(category.name)
                                } else {
                                    viewModel.selectedCategories[category.name] = nextState
                                }
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            categoryIcon?.let {
                                Image(
                                    painter = it,
                                    contentDescription = category.pretty_name,
                                    modifier = Modifier.size(24.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                                )
                            } ?: Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = category.pretty_name,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(category.pretty_name ?: category.name, modifier = Modifier.weight(1f).padding(start = 4.dp))
                            AnimatedContent(targetState = viewModel.selectedCategories[category.name]) { state ->
                                when (state) {
                                    FilterState.INCLUDED -> Icon(
                                        Icons.Default.Check,
                                        contentDescription = stringResource(Res.string.included),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    FilterState.EXCLUDED -> Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(Res.string.excluded),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    null -> Spacer(Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
                if (viewModel.filteredCategories.size > 7) {
                    TextButton(onClick = { showAllCategoriesList = !showAllCategoriesList }) {
                        Text(if (showAllCategoriesList) "Свернуть" else "Показать больше (${viewModel.filteredCategories.size - 7})")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                viewModel.loadersExpanded = !viewModel.loadersExpanded
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.loaders),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = { viewModel.loadersExpanded = !viewModel.loadersExpanded },
            ) {
                Icon(
                    imageVector = if (viewModel.loadersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (viewModel.loadersExpanded) "Свернуть загрузчики" else "Развернуть загрузчики"
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(visible = viewModel.loadersExpanded) {
            Column {
                val loadersToDisplay = if (showAllLoadersList) viewModel.filteredLoaders else viewModel.filteredLoaders.take(7)
                loadersToDisplay.forEach { loader ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 2.dp
                    ) {
                        val loaderIcon = ImageLoader.rememberImagePainterFromUrl(loader.icon)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val currentState = viewModel.selectedLoaders[loader.name]
                                val nextState = when (currentState) {
                                    null -> FilterState.INCLUDED
                                    FilterState.INCLUDED -> FilterState.EXCLUDED
                                    FilterState.EXCLUDED -> null
                                }
                                if (nextState == null) {
                                    viewModel.selectedLoaders.remove(loader.name)
                                } else {
                                    viewModel.selectedLoaders[loader.name] = nextState
                                }
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            loaderIcon?.let {
                                Image(
                                    painter = it,
                                    contentDescription = loader.pretty_name,
                                    modifier = Modifier.size(24.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                                )
                            } ?: Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = loader.pretty_name,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(loader.pretty_name ?: loader.name, modifier = Modifier.weight(1f).padding(start = 4.dp))
                            AnimatedContent(targetState = viewModel.selectedLoaders[loader.name]) { state ->
                                when (state) {
                                    FilterState.INCLUDED -> Icon(
                                        Icons.Default.Check,
                                        contentDescription = stringResource(Res.string.included),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    FilterState.EXCLUDED -> Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(Res.string.excluded),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    null -> Spacer(Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
                if (viewModel.filteredLoaders.size > 7) {
                    TextButton(onClick = { showAllLoadersList = !showAllLoadersList }) {
                        Text(if (showAllLoadersList) "Свернуть" else "Показать больше (${viewModel.filteredLoaders.size - 7})")
                    }
                }
            }
        }
    }
}