plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "org.chokopieum.software.core"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
}

kotlin {
    jvmToolchain(25)
}
