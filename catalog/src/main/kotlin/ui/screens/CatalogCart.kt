package ui.screens

import androidx.compose.runtime.*
import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.net.*
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

internal val ModificationType.usesCart: Boolean
    get() = this == ModificationType.MODS || this == ModificationType.SHADERS || this == ModificationType.RESOURCE_PACKS

internal data class CartItem(
    val provider: CatalogProvider,
    val title: String,
    val type: ModificationType,
    val version: Version
) {
    fun compatible(build: MinecraftBuild) =
        !(type == ModificationType.SHADERS && build.type == BuildType.VANILLA) &&
            ModificationInstaller.isCompatible(version, build, type.installDir)
}

/** The launcher owns the cart so navigation does not interrupt a checkout. */
class CatalogCart {
    private val contents = mutableStateListOf<CartItem>()
    internal val items: List<CartItem> get() = contents
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set

    internal fun add(item: CartItem) {
        require(item.type.usesCart)
        if (busy) return
        val index = contents.indexOfFirst {
            it.provider.id == item.provider.id && it.type == item.type && it.version.projectId == item.version.projectId
        }
        if (index < 0) contents.add(item) else contents[index] = item
        status = ""
    }

    internal fun remove(item: CartItem) {
        if (!busy) {
            contents.remove(item)
            status = ""
        }
    }

    internal fun install(build: MinecraftBuild, scope: CoroutineScope) {
        if (busy || contents.isEmpty() || contents.any { !it.compatible(build) }) return
        val batch = contents.toList()
        busy = true
        status = "Установка в ${build.name}…"
        scope.launch {
            val task = DownloadManager.startTask(status, coroutineContext.job)
            try {
                for (group in batch.groupBy { it.provider.id to it.type }.values) {
                    val first = group.first()
                    withContext(Dispatchers.IO) {
                        first.provider.open().use { session ->
                            ModificationInstaller(session, ModificationDownloader()).installBatch(
                                group.map { it.version }, build, first.type.installDir
                            ) { progress, message -> DownloadManager.updateTask(task.id, progress, message) }
                        }
                    }
                    contents.removeAll(group.toSet())
                }
                status = "Установлено в ${build.name}: ${batch.size}"
            } catch (cancelled: CancellationException) {
                status = "Установка отменена"
                throw cancelled
            } catch (failure: Exception) {
                status = "Ошибка установки: ${failure.message}"
            } finally {
                busy = false
                DownloadManager.removeTask(task.id)
            }
        }
    }
}
