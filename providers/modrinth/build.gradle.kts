plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":catalog-provider-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
    implementation("io.ktor:ktor-client-core:3.6.1")
    implementation("io.ktor:ktor-client-cio:3.6.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.6.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.1")
    implementation("io.ktor:ktor-client-logging:3.6.1")
    testImplementation(kotlin("test-junit"))
}

kotlin { jvmToolchain(25) }
