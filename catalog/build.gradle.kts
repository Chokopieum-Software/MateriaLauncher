plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":catalog-provider-api"))
    implementation(project(":ui-components"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.components:components-resources:1.12.2")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.46.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-coil3:0.46.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("io.coil-kt.coil3:coil-svg:3.6.3")
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.24.0")
    implementation("org.jsoup:jsoup:1.21.2")
    testImplementation(kotlin("test-junit"))
}

compose.resources {
    packageOfResClass = "org.chokopieum.software.catalog.resources"
}

kotlin { jvmToolchain(25) }
