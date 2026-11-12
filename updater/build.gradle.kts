plugins { kotlin("multiplatform") }

kotlin {
    sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
    mingwX64 {
        compilations.getByName("main").cinterops.create("win32") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/win32.def"))
            includeDirs("src/nativeInterop/cinterop")
        }
        binaries.executable {
            baseName = "MateriaUpdater"
            entryPoint = "org.chokopieum.updater.main"
            linkerOpts("-lwinhttp", "-lbcrypt", "-lcomctl32", "-lshell32", "-mwindows")
        }
    }
}

tasks.register<Sync>("bundleUpdater") {
    dependsOn("linkReleaseExecutableMingwX64")
    from(layout.buildDirectory.file("bin/mingwX64/releaseExecutable/MateriaUpdater.exe"))
    into(layout.buildDirectory.dir("bundle"))
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
    environment("MATERIA_UPDATER_TEST_ZIP", project.file("src/mingwX64Test/fixtures/payload.zip").absolutePath)
}
