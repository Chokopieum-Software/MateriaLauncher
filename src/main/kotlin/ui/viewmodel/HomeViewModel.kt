package ui.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import funlauncher.MinecraftBuild
import funlauncher.auth.Account
import funlauncher.openFolder // Keep this import as it's used in onOpenFolderClick
import funlauncher.managers.PathManager
import funlauncher.AppSettings
import funlauncher.auth.AccountManager

class HomeViewModel(
    private val appViewModel: AppViewModel,
    val pathManager: PathManager,
    val globalSettings: AppSettings
) {
    var searchQuery by mutableStateOf("")
        private set

    val filteredBuilds by derivedStateOf {
        if (searchQuery.isBlank()) {
            appViewModel.buildList
        } else {
            appViewModel.buildList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val builds = appViewModel.buildList

    val runningBuild: MinecraftBuild? by derivedStateOf { appViewModel.runningBuild }
    val isLaunchingBuildId: String? by derivedStateOf { appViewModel.isLaunchingBuildId }
    val currentAccount: Account? by derivedStateOf { appViewModel.currentAccount }
    val buildsPendingDeletion: Set<String> by derivedStateOf { appViewModel.buildsPendingDeletion.toSet() }

    val accountManager: AccountManager get() = appViewModel.accountManager


    fun onSearchQueryChanged(query: String) {
        searchQuery = query
    }

    fun onLaunchClick(build: MinecraftBuild) {
        appViewModel.onLaunchClick(build)
    }

    fun onOpenFolderClick(build: MinecraftBuild) {
        openFolder(build.installPath)
    }

    fun onAddBuildClick() {
        appViewModel.showAddBuildDialog = true
    }

    fun onDeleteBuildClick(build: MinecraftBuild) {
        appViewModel.onDeleteBuildClick(build)
    }

    fun onSettingsBuildClick(build: MinecraftBuild) {
        appViewModel.showBuildSettingsScreen = build
    }

    fun onBuildsReordered(from: Int, to: Int) {
        appViewModel.onBuildsReordered(from, to)
    }

    fun onAccountSelected(account: Account) {
        appViewModel.onAccountSelected(account)
    }
}
