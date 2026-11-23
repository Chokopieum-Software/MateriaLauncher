plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.chokopieum.software.core"
version = "1.0-SNAPSHOT"

dependencies {
    implementation("io.ktor:ktor-client-core:3.6.1")
    implementation("io.ktor:ktor-client-cio:3.6.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.6.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.1")
    implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
}

kotlin {
    jvmToolchain(25)
}
