package funlauncher.modpack

import funlauncher.BuildType
import funlauncher.MinecraftBuild
import java.nio.file.Path

interface CatalogBuilds {
    suspend fun loadBuilds(): List<MinecraftBuild>
    fun getBuildPath(buildName: String): Path
    suspend fun addBuild(name: String, version: String, type: BuildType, imagePath: String?): Path
}
