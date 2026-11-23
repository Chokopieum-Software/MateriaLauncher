plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
}

dependencies {
    api(project(":core"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("io.ktor:ktor-client-core:3.6.1")
}

kotlin { jvmToolchain(25) }
