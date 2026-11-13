@file:Suppress("DEPRECATION")
import org.gradle.jvm.tasks.Jar
import java.util.*
import org.gradle.api.artifacts.transform.*
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.objectweb.asm.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.ow2.asm:asm:9.10.1") }
}

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    kotlin("plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
}

val isTestBuild = project.hasProperty("testbuild")

/**
 * Skiko's Direct3D resize workaround renders synchronously inside reshape().
 * WINDOW Compose layers resize while recording a picture, so this re-enters the
 * same native PictureRecorder and crashes the VM. Schedule the existing frame
 * dispatcher instead.
 * Remove this transform once the upstream resize path no longer renders inline.
 */
abstract class SkikoResizeTransform : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        ZipFile(input).use { zip ->
            val target = "org/jetbrains/skiko/SkiaLayer.class"
            if (zip.getEntry(target) == null) {
                outputs.file(input)
                return
            }
            val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
            var replacements = 0
            ClassReader(zip.getInputStream(zip.getEntry(target)).use { it.readBytes() })
                .accept(object : ClassVisitor(Opcodes.ASM9, writer) {
                    override fun visitMethod(access: Int, name: String, descriptor: String,
                                             signature: String?, exceptions: Array<out String>?): MethodVisitor {
                        val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
                        if (name != "reshape" || descriptor != "(IIII)V") return delegate
                        return object : MethodVisitor(Opcodes.ASM9, delegate) {
                            override fun visitMethodInsn(opcode: Int, owner: String, name: String,
                                                         descriptor: String, isInterface: Boolean) {
                                if (owner == "org/jetbrains/skiko/redrawer/Redrawer" &&
                                    name == "renderImmediately" && descriptor == "()V") {
                                    visitInsn(Opcodes.ICONST_0)
                                    super.visitMethodInsn(opcode, owner, "needRender", "(Z)V", isInterface)
                                    replacements++
                                } else super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                            }
                        }
                    }
                }, 0)
            check(replacements == 1) {
                "Skiko resize implementation changed; review SkikoResizeTransform before upgrading"
            }
            ZipOutputStream(outputs.file("resize-safe-${input.name}").outputStream()).use { out ->
                zip.entries().asSequence().forEach { entry ->
                    out.putNextEntry(ZipEntry(entry.name).apply { time = 0 })
                    if (!entry.isDirectory) {
                        if (entry.name == target) out.write(writer.toByteArray())
                        else zip.getInputStream(entry).use { it.copyTo(out) }
                    }
                    out.closeEntry()
                }
            }
        }
    }
}

val artifactType = ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
dependencies.registerTransform(SkikoResizeTransform::class) {
    from.attribute(artifactType, ArtifactTypeDefinition.JAR_TYPE)
    to.attribute(artifactType, "materia-resize-safe-jar")
}
configurations.matching { it.name == "runtimeClasspath" || it.name == "testRuntimeClasspath" }.configureEach {
    attributes.attribute(artifactType, "materia-resize-safe-jar")
}

fun fetchGitHash(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "HEAD")
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }.trim()
    } catch (e: Exception) {
        "unknown"
    }
}

// --- Логика определения версии ---
val buildProps = Properties().apply {
    project.file("build.properties").inputStream().use { load(it) }
}
val appVersion = providers.gradleProperty("appVersion")
    .getOrElse(buildProps.getProperty("version"))
require(appVersion.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))) {
    "appVersion must be MAJOR.MINOR.PATCH without leading zeroes"
}
val versionParts = appVersion.split('.').map { it.toIntOrNull() }
require(versionParts[0] in 0..255 && versionParts[1] in 0..255 &&
    versionParts[2] in 0..65535 && versionParts.any { it != 0 }) {
    "appVersion must be Windows-compatible (255.255.65535 maximum, greater than zero)"
}
val appBuildNumber = providers.gradleProperty("buildNumber")
    .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER"))
    .getOrElse("Local")
val buildSource = providers.environmentVariable("BUILD_SOURCE").getOrElse("Local")
val releaseChannel = buildProps.getProperty("channel", "Stable")
val gitHash = fetchGitHash()

// The native updater is shipped beside the launcher, including in both installers.
afterEvaluate {
    tasks.named("createDistributable") {
        if (System.getProperty("os.name").startsWith("Windows")) {
            dependsOn(":updater:bundleUpdater")
            inputs.dir(project(":updater").layout.buildDirectory.dir("bundle"))
        }
        inputs.property("updateRunId", providers.environmentVariable("GITHUB_RUN_ID").getOrElse("Local"))
        inputs.property("updateBranch", providers.environmentVariable("GITHUB_REF_NAME").getOrElse("Local"))
        doLast {
            val applicationDir = layout.buildDirectory.dir("compose/binaries/main/app/materia-launcher").get().asFile
            if (System.getProperty("os.name").startsWith("Windows")) copy {
                from(project(":updater").layout.buildDirectory.dir("bundle"))
                into(applicationDir.resolve("updater"))
            }
            val metadata = Properties().apply {
                setProperty("version", appVersion)
                setProperty("buildNumber", appBuildNumber)
                setProperty("runId", providers.environmentVariable("GITHUB_RUN_ID").getOrElse("Local"))
                setProperty("branch", providers.environmentVariable("GITHUB_REF_NAME").getOrElse("Local"))
                setProperty("commit", gitHash)
            }
            applicationDir.resolve("update.properties").outputStream().use { metadata.store(it, "Launcher update identity") }
        }
    }
}

group = "org.chokopieum.software"
version = appVersion

dependencies {
    implementation(project(":core"))
    implementation(project(":ui-components"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.components:components-resources:1.12.2")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.compose.material3:material3:1.9.0")

    // Jewel for custom window decorations
    implementation("org.jetbrains.jewel:jewel-foundation:0.42.0-263.7307.200")
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:0.42.0-263.7307.200")
    implementation("org.jetbrains.jewel:jewel-decorated-window:0.42.0-263.7307.200")
    implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:0.42.0-263.7307.200")


    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("com.microsoft.azure:msal4j:1.26.0")
    implementation("com.github.javakeyring:java-keyring:1.0.4")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("com.google.code.gson:gson:2.15.0")
    implementation("com.akuleshov7:ktoml-core-jvm:0.7.1")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.46.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-coil3:0.46.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("io.coil-kt.coil3:coil-svg:3.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("io.ktor:ktor-client-core:3.6.1")
    implementation("io.ktor:ktor-client-cio:3.6.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.6.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.1")
    implementation("io.ktor:ktor-client-logging:3.6.1")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.apache.commons:commons-lang3:3.21.0")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")

    // SLF4J
    implementation("org.slf4j:slf4j-api:2.0.20")
    implementation("org.slf4j:slf4j-simple:2.0.20")

    // Exposed for SQLite
    implementation("org.jetbrains.exposed:exposed-core:1.6.1")
    implementation("org.jetbrains.exposed:exposed-dao:1.6.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.6.1")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.24.0")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("net.java.dev.jna:jna-platform:5.18.1")
    testImplementation(kotlin("test-junit"))
}

kotlin {
    jvmToolchain(25)
}

compose.desktop {
    application {
        mainClass = "InitKt"

        nativeDistributions {
            packageName = "materia-launcher"
            packageVersion = appVersion
            description = "Modern launcher for Minecraft"
            vendor = "Chokopieum Software"
            copyright = "© 2025 Chokopieum Software"
            
            // Встраиваем лицензию в установщик
            licenseFile.set(project.file("LICENSE"))

            includeAllModules = true

            // Installers are built by Inno Setup from createDistributable output.

            windows {
                if (isTestBuild) {
                    console = true
                }
                iconFile.set(project.file("src/main/resources/logo.ico"))
            }
        }
    }
}

afterEvaluate {
    tasks.named("packageUberJarForCurrentOS", Jar::class) {
        archiveFileName.set("materia-launcher.jar")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

val coverFiles = fileTree("src/main/composeResources/drawable/cover") {
    include("*.png", "*.jpg", "*.jpeg")
}
// Nested folders in drawable are not supported by the Compose accessor generator.
// Package covers separately, leaving the requested source directory intact.
val prepareUiResources by tasks.registering(Sync::class) {
    from("src/main/composeResources") { exclude("drawable/cover/**") }
    into(layout.buildDirectory.dir("generated/uiResources"))
    includeEmptyDirs = false
}
compose.resources {
    customDirectory("main", layout.dir(prepareUiResources.map { it.destinationDir }))
}
val generateCoverIndex by tasks.registering {
    inputs.files(coverFiles)
    val indexFile = layout.buildDirectory.file("generated/coverResources/covers/index.txt")
    outputs.file(indexFile)
    doLast {
        indexFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(coverFiles.files.map { it.name }.sorted().joinToString("\n"))
        }
    }
}

tasks.named<Copy>("processResources") {
    dependsOn(generateCoverIndex)
    inputs.property("appVersion", appVersion)
    inputs.property("appBuildNumber", appBuildNumber)
    inputs.property("buildSource", buildSource)
    inputs.property("gitHash", gitHash)
    inputs.property("isTestBuild", isTestBuild)
    inputs.property("releaseChannel", releaseChannel)
    from(coverFiles) { into("covers") }
    from(layout.buildDirectory.dir("generated/coverResources"))
    doLast {
        val props = Properties()
        props.setProperty("version", appVersion)
        props.setProperty("channel", releaseChannel)
        props.setProperty("buildNumber", appBuildNumber)
        props.setProperty("buildSource", buildSource)
        props.setProperty("gitHash", gitHash)
        props.setProperty("isTestBuild", isTestBuild.toString())

        val wrapperProps = Properties()
        project.file("gradle/wrapper/gradle-wrapper.properties").inputStream().use { wrapperProps.load(it) }
        val gradleVersion = wrapperProps.getProperty("distributionUrl").substringAfterLast("gradle-").substringBefore("-bin")
        props.setProperty("gradleVersion", gradleVersion)

        file("$destinationDir/app.properties").writer().use {
            props.store(it, "Generated by Gradle")
        }
    }
}
// build.gradle.kts

compose.resources {

    packageOfResClass = "org.chokopieum.software.materia_launcher.generated.resources"

    // Если нужно, чтобы ресурсы были видны в других модулях
    publicResClass = true
    generateResClass = always
}

// Allow regression checks against the same trimmed runtime used by IDEA Run.
tasks.withType<Test>().configureEach {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    providers.gradleProperty("testJavaExecutable").orNull?.let { executable = it }
}
