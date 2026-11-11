/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.game

import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.auth.Account
import funlauncher.managers.PathManager
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString

data class LaunchPayload(
    val command: List<String>,
    val arguments: List<String>,
    val environment: Map<String, String>,
    val workDir: String
)

/**
 * Handles the final steps of launching the game: extracting natives and building the launch command.
 */
class GameLauncher(
    private val versionInfo: VersionInfo,
    private val build: MinecraftBuild,
    private val pathManager: PathManager
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val nativesDir: Path = pathManager.getNativesDir(build)
    private val globalLibrariesDir: Path = pathManager.getGlobalLibrariesDir()
    private val globalVersionsDir: Path = pathManager.getGlobalVersionsDir()
    private val globalAssetsDir: Path = pathManager.getGlobalAssetsDir()
    private val gameDir: Path = File(build.installPath).toPath()

    suspend fun createLaunchPayload(
        account: Account,
        javaPath: String,
        maxRamMb: Int,
        customJavaArgs: String,
        envVars: String
    ): LaunchPayload {
        val options = funlauncher.utils.validateLaunchOptions(customJavaArgs, envVars, maxRamMb)
        require(options.valid) { (options.argumentErrors + options.environmentErrors).joinToString("\n") }
        extractNatives()
        val commandList = buildLaunchCommand(account, javaPath, maxRamMb, customJavaArgs)

        val envMap = options.environment
        
        logger.info("--- LAUNCH PAYLOAD ---")
        logger.info("Command: <hidden: contains authentication credentials>")
        logger.info("WorkDir: ${gameDir.toAbsolutePath()}")
        logger.info("Environment: $envMap")
        logger.info("----------------------")

        return LaunchPayload(
            command = commandList,
            arguments = emptyList(), // Not used directly with ProcessBuilder
            environment = envMap,
            workDir = gameDir.toAbsolutePath().toString()
        )
    }

    private fun extractNatives() {
        logger.info("Extracting natives...")
        if (nativesDir.exists()) nativesDir.toFile().deleteRecursively()
        nativesDir.toFile().mkdirs()

        val osName = getOsName()
        val arch = getArch()

        versionInfo.libraries
            .filter { it.natives != null && isRuleApplicable(it.rules ?: emptyList()) }
            .forEach { lib ->
                val nativeKey = lib.natives?.get(osName)?.replace("\${arch}", arch)
                val nativePath = lib.downloads?.classifiers?.get(nativeKey)?.path
                    ?: if (lib.downloads == null && nativeKey != null) getArtifactPath(lib.name, nativeKey) else null
                nativePath?.let { path ->
                    val jarPath = globalLibrariesDir.resolve(path)
                    if (jarPath.exists()) {
                        extractJarContents(jarPath)
                    } else {
                        logger.warn("Native JAR not found for extraction: ${jarPath.pathString}")
                    }
                }
            }

    }

    private fun extractJarContents(jarPath: Path) {
        try {
            JarFile(jarPath.toFile()).use { jar ->
                jar.entries().asSequence()
                    .filterNot { it.isDirectory || it.name.contains("META-INF") }
                    .forEach { entry ->
                        val outFile = nativesDir.resolve(entry.name.substringAfterLast('/'))
                        Files.copy(jar.getInputStream(entry), outFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        logger.debug("Extracted ${entry.name} from ${jarPath.name} to ${outFile.pathString}")
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to extract ${jarPath.name}", e)
        }
    }

    private fun buildLaunchCommand(account: Account, javaPath: String, maxRamMb: Int, customJavaArgs: String): List<String> {
        val osName = getOsName()
        val isWindows = osName == "windows"
        
        val classpath = buildClasspathList()
        val replacements = createReplacementsMap(classpath.joinToString(File.pathSeparator), account)
        
        val replacePlaceholders = { str: String ->
            var result = str
            replacements.forEach { (k, v) -> result = result.replace("\${$k}", v) }
            result
        }

        val finalCommand = mutableListOf<String>()
        finalCommand.add(javaPath.ifBlank { "java" })
        finalCommand.add("-Xmx${maxRamMb}M")

        // Modern argument format (1.13+)
        if (versionInfo.arguments != null) {
            val jvmArgs = processArguments(versionInfo.arguments.jvm)
            if (jvmArgs.none { it.startsWith("-Djava.library.path=") }) {
                finalCommand.add("-Djava.library.path=" + nativesDir.toAbsolutePath())
            }
            if (jvmArgs.none { it in listOf("-cp", "-classpath", "--class-path") || it.startsWith("--class-path=") }) {
                finalCommand.addAll(listOf("-cp", classpath.joinToString(File.pathSeparator)))
            }
            finalCommand.addAll(jvmArgs.map(replacePlaceholders))
            if (customJavaArgs.isNotBlank()) {
                finalCommand.addAll(funlauncher.utils.parseJavaArguments(customJavaArgs).map(replacePlaceholders))
            }
            finalCommand.add(versionInfo.mainClass ?: "net.minecraft.client.main.Main")
            val gameArgsSource = versionInfo.arguments.game
            val legacyArgs = versionInfo.gameArguments?.let { funlauncher.utils.parseJavaArguments(it) } ?: emptyList()
            val processedGameArgs = (legacyArgs + processArguments(gameArgsSource)).map(replacePlaceholders)
            finalCommand.addAll(filterUndesiredArgs(processedGameArgs))
        } else { // Legacy argument format (pre-1.13)
            finalCommand.add("-Djava.library.path=\${natives_directory}")
            finalCommand.add("-cp")
            finalCommand.add(classpath.joinToString(File.pathSeparator))

            if (customJavaArgs.isNotBlank()) {
                finalCommand.addAll(funlauncher.utils.parseJavaArguments(customJavaArgs).map(replacePlaceholders))
            }
            finalCommand.add(versionInfo.mainClass ?: "net.minecraft.client.main.Main")
            versionInfo.gameArguments?.let { args ->
                finalCommand.addAll(funlauncher.utils.parseJavaArguments(args).map(replacePlaceholders))
            }
        }

        // Apply replacements to the whole command list
        val fullyReplacedCommand = finalCommand.map(replacePlaceholders).toMutableList()

        if (osName != "osx") {
            fullyReplacedCommand.removeAll { it == "-XstartOnFirstThread" }
        }

        logger.info("--- FINAL LAUNCH COMMAND ---")
        logger.info("Command: <hidden: contains authentication credentials>")
        logger.info("----------------------------")

        return fullyReplacedCommand
    }

    private fun buildClasspathList(): List<String> {
        val cpList = mutableListOf<String>()

        versionInfo.libraries.forEach { lib ->
            if (isRuleApplicable(lib.rules ?: emptyList())) {
                if (lib.downloads?.artifact == null &&
                    (lib.downloads != null || lib.natives != null)) return@forEach
                val path = lib.downloads?.artifact?.path ?: getArtifactPath(lib.name)
                cpList.add(globalLibrariesDir.resolve(path).toAbsolutePath().toString())
            }
        }

        val gameVersionForJar = when (build.type) {
            BuildType.FABRIC -> build.version.split("-fabric-").first()
            BuildType.FORGE -> build.version.split("-forge-").first()
            BuildType.QUILT -> build.version.split("-quilt-").first()
            BuildType.NEOFORGE -> build.version.split("-neoforge-").first()
            else -> versionInfo.id
        }
        val clientJarPath = globalVersionsDir.resolve(gameVersionForJar).resolve("$gameVersionForJar.jar")
        cpList.add(clientJarPath.toAbsolutePath().toString())
        return cpList
    }

    private fun createReplacementsMap(classpath: String, account: Account): Map<String, String> = mapOf(
        "natives_directory" to nativesDir.toAbsolutePath().toString(),
        "launcher_name" to "MateriaKraft",
        "launcher_version" to "1.0",
        "classpath" to classpath,
        "library_directory" to globalLibrariesDir.toAbsolutePath().toString(),
        "classpath_separator" to File.pathSeparator,
        "auth_player_name" to account.username,
        "version_name" to versionInfo.id,
        "game_directory" to gameDir.toAbsolutePath().toString(),
        "assets_root" to globalAssetsDir.toAbsolutePath().toString(),
        "assets_index_name" to (versionInfo.assetIndex?.id ?: "legacy"),
        "auth_uuid" to (account.uuid ?: UUID.nameUUIDFromBytes(account.username.toByteArray()).toString()),
        "auth_access_token" to (account.accessToken ?: "0"),
        "user_properties" to "{}",
        "user_type" to if (account.isLicensed) "msa" else "legacy",
        "version_type" to build.type.name,
        "clientid" to "clientId",
        "auth_xuid" to "xuid",
        "resolution_width" to "854",
        "resolution_height" to "480"
    )

    private fun processArguments(args: List<JsonElementWrapper>?): List<String> {
        return args?.flatMap { wrapper ->
            when (wrapper) {
                is JsonElementWrapper.StringValue -> listOf(wrapper.value)
                is JsonElementWrapper.ObjectValue -> {
                    if (isRuleApplicable(wrapper.rules)) {
                        wrapper.getValues()
                    } else {
                        emptyList()
                    }
                }
            }
        } ?: emptyList()
    }

    private fun filterUndesiredArgs(args: List<String>): List<String> {
        val finalArgs = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            when {
                arg.startsWith("--quickPlay") -> i += 2
                arg == "--demo" -> {
                    i++
                    if (i < args.size && (args[i].equals("true", true) || args[i].equals("false", true))) {
                        i++
                    }
                }
                else -> {
                    finalArgs.add(arg)
                    i++
                }
            }
        }
        return finalArgs
    }

    private fun getArtifactPath(name: String, classifier: String? = null): String = mavenArtifactPath(name, classifier)

    private fun isRuleApplicable(rules: List<VersionInfo.Rule>): Boolean = rulesApply(rules, getOsName())

    private fun getOsName(): String = when {
        System.getProperty("os.name").lowercase().contains("win") -> "windows"
        System.getProperty("os.name").lowercase().contains("mac") -> "osx"
        else -> "linux"
    }

    private fun getArch(): String = nativeArch()
}
