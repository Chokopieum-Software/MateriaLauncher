package funlauncher.game

import funlauncher.BuildType
import funlauncher.MinecraftBuild
import funlauncher.auth.MicrosoftAccount
import funlauncher.managers.BuildManager
import funlauncher.managers.PathManager
import funlauncher.net.Network
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*
import kotlin.test.*

class LaunchMetadataTest {
    @Test fun cachedInstallerProfileInheritsVanillaAndExtractsNatives() = runBlocking {
        val root = Files.createTempDirectory("cached-forge")
        try {
            val paths = PathManager(root)
            val parent = VersionInfo("1.12.2", gameArguments = "--username \${auth_player_name}")
            val child = VersionInfo("installed-forge", inheritsFrom = "1.12.2",
                mainClass = "net.minecraft.launchwrapper.Launch",
                gameArguments = "--username \${auth_player_name} --tweakClass ForgeTweaker",
                libraries = listOf(VersionInfo.Library("test:native:1", natives = mapOf(
                    "windows" to "natives-\${arch}", "linux" to "natives-\${arch}", "osx" to "natives-\${arch}"))))
            for (info in listOf(parent, child)) {
                val file = paths.getGlobalVersionsDir().resolve(info.id).resolve("${info.id}.json")
                file.parent.createDirectories()
                file.writeText(Network.json.encodeToString(info))
            }
            val native = paths.getGlobalLibrariesDir().resolve(mavenArtifactPath("test:native:1", "natives-${nativeArch()}"))
            native.parent.createDirectories()
            ZipOutputStream(Files.newOutputStream(native)).use {
                it.putNextEntry(ZipEntry("test-native.bin")); it.write(byteArrayOf(1, 2, 3)); it.closeEntry()
            }
            val build = MinecraftBuild("test", "1.12.2-forge-14.23.5.2864", BuildType.FORGE, root.toString(), modloaderVersion = child.id)
            val fetcher = VersionMetadataFetcher(BuildManager(paths), paths)
            val resolved = fetcher.getVersionInfo(build)
            assertNull(resolved.inheritsFrom)
            assertNull(resolved.arguments)
            val command = GameLauncher(resolved, build, paths).createLaunchPayload(MicrosoftAccount("Player", "uuid", "secret-token"), "java", 1024, "", "").command
            assertEquals("ForgeTweaker", command[command.indexOf("--tweakClass") + 1])
            assertContentEquals(byteArrayOf(1, 2, 3), paths.getNativesDir(build).resolve("test-native.bin").readBytes())
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun rulesRespectPlatformFeaturesAndOrder() {
        val windowsOnly = VersionInfo.Rule("allow", VersionInfo.OS("windows"))
        assertFalse(rulesApply(listOf(windowsOnly), "linux"))
        assertTrue(rulesApply(listOf(windowsOnly), "windows"))
        assertFalse(rulesApply(listOf(VersionInfo.Rule("allow", features = mapOf("is_demo_user" to true))), "windows"))
        assertTrue(rulesApply(listOf(VersionInfo.Rule("allow", features = mapOf("is_demo_user" to false))), "windows"))
        val restricted = VersionInfo.Rule("allow", VersionInfo.OS("windows", "x86", "^10\\."))
        assertTrue(rulesApply(listOf(restricted), "windows", "x86", "10.0"))
        assertFalse(rulesApply(listOf(restricted), "windows", "amd64", "10.0"))
        assertFalse(rulesApply(listOf(restricted), "windows", "x86", "11.0"))
        assertFalse(rulesApply(listOf(windowsOnly, VersionInfo.Rule("disallow")), "windows"))
        assertTrue(rulesApply(emptyList(), "linux"))
    }

    @Test fun nativeClassifiersAndMavenPaths() {
        assertEquals("64", nativeArch("amd64"))
        assertEquals("32", nativeArch("x86"))
        assertEquals("64", nativeArch("aarch64"))
        assertEquals("a/b/c/1/c-1-client.jar", mavenArtifactPath("a.b:c:1:client"))
        assertEquals("a/b/c/1/c-1-natives-windows.jar", mavenArtifactPath("a.b:c:1", "natives-windows"))
    }

    @Test fun installerIdsAndLegacyArguments() {
        val root = Files.createTempDirectory("installer-profile")
        try {
            val paths = PathManager(root)
            val fetcher = VersionMetadataFetcher(BuildManager(paths), paths)
            for (profile in listOf("""{"version":"1.20.1-forge-47.2.0"}""", """{"versionInfo":{"id":"1.12.2-forge1.12.2-14.23.5.2864"}}""")) {
                val jar = root.resolve("installer.jar")
                ZipOutputStream(Files.newOutputStream(jar)).use {
                    it.putNextEntry(ZipEntry("install_profile.json"))
                    it.write(profile.toByteArray())
                    it.closeEntry()
                }
                assertTrue(fetcher.readInstallerVersion(jar).contains("forge"))
            }
            assertNull(fetcher.mergeArguments(null, null))
            val profile = Network.json.decodeFromString<VersionProfile>("""{"id":"forge","inheritsFrom":"1.12.2","mainClass":"net.minecraft.launchwrapper.Launch","libraries":[],"minecraftArguments":"--tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker"}""")
            assertEquals("--tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker", profile.gameArguments)
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun legacyAndMixedProfilesBuildCompleteCommands() = runBlocking {
        val root = Files.createTempDirectory("launch with spaces")
        try {
            val paths = PathManager(root)
            val build = MinecraftBuild("test", "1.12.2-forge-14.23.5.2864", BuildType.FORGE, root.toString())
            for (arguments in listOf(null, VersionInfo.Arguments(jvm = listOf(JsonElementWrapper.StringValue("-Dloader=true"))))) {
                val info = VersionInfo("forge", mainClass = "net.minecraft.launchwrapper.Launch",
                    libraries = listOf(VersionInfo.Library("example:loader:1:client")), arguments = arguments,
                    gameArguments = "--username \"\${auth_player_name}\" --accessToken \${auth_access_token} --tweakClass ForgeTweaker")
                val command = GameLauncher(info, build, paths).createLaunchPayload(
                    MicrosoftAccount("Player Name", "uuid", "secret-token"), "java", 1024, "", "").command
                assertTrue(command[command.indexOf("-cp") + 1].contains("loader-1-client.jar"))
                assertTrue(command.any { it.startsWith("-Djava.library.path=") })
                assertEquals("Player Name", command[command.indexOf("--username") + 1])
                assertEquals("ForgeTweaker", command[command.indexOf("--tweakClass") + 1])
                assertFalse(command.any { it.contains("\${") })
            }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun modernLoaderPlaceholdersAreResolved() = runBlocking {
        val root = Files.createTempDirectory("modern-launch")
        try {
            val paths = PathManager(root)
            val build = MinecraftBuild("test", "1.20.1-neoforge-20.1.1", BuildType.NEOFORGE, root.toString())
            val info = VersionInfo("neoforge", mainClass = "cpw.mods.bootstraplauncher.BootstrapLauncher", arguments = VersionInfo.Arguments(
                jvm = listOf("-DlibraryDirectory=\${library_directory}", "-DlegacyClassPath=\${classpath}", "-Dseparator=\${classpath_separator}", "-cp", "\${classpath}").map { JsonElementWrapper.StringValue(it) }))
            val command = GameLauncher(info, build, paths).createLaunchPayload(MicrosoftAccount("Player", "uuid", "secret-token"), "java", 1024, "", "").command
            assertFalse(command.any { it.contains("\${") })
            assertTrue(command.contains("-Dseparator=${File.pathSeparator}"))
            assertTrue(command.contains("-DlibraryDirectory=${paths.getGlobalLibrariesDir().toAbsolutePath()}"))
        } finally { root.toFile().deleteRecursively() }
    }
}
