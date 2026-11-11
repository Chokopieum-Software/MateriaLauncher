@file:Suppress("DEPRECATION")
import org.gradle.jvm.tasks.Jar
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    kotlin("plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
}

val isTestBuild = project.hasProperty("testbuild")

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
fun getVersionInfo(): Pair<String, String> {
    val buildPropsFile = project.file("build.properties")
    val buildProps = Properties()
    if (buildPropsFile.exists()) {
        FileInputStream(buildPropsFile).use { buildProps.load(it) }
    }
    
    // Получаем текущий номер и увеличиваем на 1
    var buildNumber = buildProps.getProperty("buildNumber", "0").toInt()
    buildNumber++
    
    // Сохраняем обновленный номер
    buildProps.setProperty("buildNumber", buildNumber.toString())
    FileOutputStream(buildPropsFile).use { buildProps.store(it, "Auto-incremented by Gradle") }
    
    // Версию можно задать в build.properties (version=1.0.0), иначе будет "1.0.0" по умолчанию
    val version = buildProps.getProperty("version", "1.0.0")
    
    return Pair(version, buildNumber.toString())
}

val (appVersion, appBuildNumber) = getVersionInfo()
val gitHash = fetchGitHash()

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
    implementation("ca.weblite:webview:1.14.0")
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
            val osName = System.getProperty("os.name")
            if (osName.lowercase(Locale.getDefault()).contains("win")) {
                packageVersion = "1.0.$appBuildNumber"
            } else {
                packageVersion = appVersion
            }
            description = "Modern launcher for Minecraft"
            vendor = "Chokopieum Software"
            copyright = "© 2025 Chokopieum Software"
            
            // Встраиваем лицензию в установщик
            licenseFile.set(project.file("LICENSE"))

            includeAllModules = true

            // Собираем только EXE для Windows со встроенной JDK
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)

            windows {
                menu = true
                shortcut = true
                upgradeUuid = "019b375c-3319-7eec-8098-e50668c43b5a"
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
    from(coverFiles) { into("covers") }
    from(layout.buildDirectory.dir("generated/coverResources"))
    doLast {
        val props = Properties()
        props.setProperty("version", appVersion)
        props.setProperty("buildNumber", appBuildNumber)
        props.setProperty("buildSource", "Local")
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
    providers.gradleProperty("testJavaExecutable").orNull?.let { executable = it }
}
