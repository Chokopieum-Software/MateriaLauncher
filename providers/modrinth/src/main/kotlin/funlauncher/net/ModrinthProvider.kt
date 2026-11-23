package funlauncher.net

import funlauncher.managers.CacheManager
import funlauncher.modpack.CatalogBuilds
import funlauncher.modpack.ModpackInstaller
import kotlinx.coroutines.CoroutineScope

class ModrinthProvider(private val cache: CacheManager) : CatalogProvider {
    override val id = "modrinth"
    override val displayName = "Modrinth"
    override val legal = ProviderLegal(
        revision = "terms-2026-08-13_privacy-2023-11-17",
        termsUrl = "https://modrinth.com/legal/terms",
        privacyUrl = "https://modrinth.com/legal/privacy"
    )
    override fun open(): CatalogSession = ModrinthApi(cache)

    override fun installModpack(version: Version, builds: CatalogBuilds, scope: CoroutineScope,
                                onComplete: () -> Unit, onError: (String) -> Unit) {
        ModpackInstaller(builds, scope).install(version, onComplete, onError)
    }
}
