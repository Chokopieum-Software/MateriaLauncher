plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
        //   id("application")
}

group = "org.chokopieum.software.ui"
version = "1.0-SNAPSHOT"


dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
    implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
    implementation("org.jetbrains.compose.ui:ui:1.12.2")
    implementation("org.jetbrains.compose.components:components-ui-tooling-preview:1.12.2")
    implementation("org.jetbrains.compose.components:components-resources:1.12.2")
}

compose.resources {
    packageOfResClass = "org.chokopieum.software.ui.generated.resources"
    publicResClass = true
    generateResClass = always
}

// Задаём главный класс по умолчанию
//application {
//    mainClass.set("org.chokopieum.software.ui.MateriaCircularProgressIndicatorPreviewKt")
//}

// Отдельная задача для запуска превью индикатора загрузки
//tasks.register<JavaExec>("runProgressIndicatorPreview") {
//    group = "application"
 //   description = "Run MateriaCircularProgressIndicator Preview"
 //   classpath = sourceSets["main"].runtimeClasspath
 //   mainClass.set("org.chokopieum.software.ui.MateriaCircularProgressIndicatorPreviewKt")
//}

kotlin {
    jvmToolchain(25)
}
