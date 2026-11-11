# How to Build Materia Launcher

This guide will help you build Materia Launcher from the source code.

## 1. System Requirements

Ensure your system meets the following requirements:

*   **Operating System**:
    *   Windows 10 or higher
    *   macOS
    *   Linux
*   **Java**:
    *   An installed or portable version of JBRSDK 25 (full JDK with javac, jlink and jpackage). Set JAVA_HOME to the JDK directory.
*   **Free Space**:
    *   A minimum of 2 GB of free disk space for downloading libraries and dependencies.

## 2. Cloning the Repository

First, clone the project repository to your local machine using Git:

```bash
git clone https://github.com/Chokopieum-Software/MateriaKraft-Launcher.git
cd MateriaKraft-Launcher
```

## 3. Building the Project

The project uses the Gradle Wrapper (`gradlew`) for building.

Open a terminal or command prompt in the root directory of the project.

### Build and Package the Launcher

**For Windows:**
```bash
gradlew.bat createDistributable
```

**For macOS and Linux:**
```bash
./gradlew createDistributable
```

After successfully running the command, you will find the ready-to-run distributables in the `build/compose/binaries/main/app/` directory.

## JBRSDK and IntelliJ IDEA

Use the full [JetBrains Runtime SDK 25](https://github.com/JetBrains/JetBrainsRuntime/releases), rather than a runtime-only distribution: packaging requires `jmods`, `jlink`, and `jpackage`.

1. Extract JBRSDK and point `JAVA_HOME` at the directory containing `bin/java.exe`.
2. Add that directory under IntelliJ IDEA Project Structure → SDKs and select the SDK for this project.
3. Under Settings → Build Tools → Gradle, use the Gradle Wrapper and this JDK (or `GRADLE_LOCAL_JAVA_HOME` with `java.home=...` in `.gradle/config.properties`).
4. Open a new terminal or restart the IDE after changing environment variables.

Verify with `gradlew.bat --version`, then `gradlew.bat build`. Package the application directory with `gradlew.bat createDistributable`.

## Windows installer and versioning

`build.properties` is the source of the application and installer version. Alpha
builds currently use `version=0.1.0` and `channel=Alpha`; the first stable release
will use `version=1.0.0` and `channel=Stable`. Increase the numeric version deliberately
for each distributed update. Gradle never increments or rewrites it.

The version must have three numeric components, within Windows limits
(major/minor <= 255, patch <= 65535). The channel is shown in the application;
Windows uses the numeric version only. Older experimental `1.0.N` packages may
need to be uninstalled before installing the `0.x` Alpha builds.

Build on Windows with the full JBRSDK 25 and Inno Setup 6+:

```powershell
.\gradlew.bat test createDistributable
& "C:\Program Files\Inno Setup 7\ISCC.exe" installer.iss
```

Adjust the ISCC path to your installed Inno Setup version (6 or 7).

The installer is `build/inno-setup/MateriaLauncher-Setup.exe`. Gradle creates only
the application directory; Inno Setup is the only installer builder.

The Windows installers workflow runs on push, pull request, and manual dispatch.
It tests the project, builds the application, compiles Inno Setup, and uploads the
installer as a GitHub Actions artifact for 30 days. Download it from the run's
Artifacts section. It does not publish GitHub Releases.

The build number is `GITHUB_RUN_NUMBER` in CI and `Local` locally; it is separate
from the version. `-PbuildNumber=...` overrides it. `BUILD_SOURCE` records the
build origin. For a custom numeric version, pass `-PappVersion=X.Y.Z` to Gradle
and `/DMyAppVersion=X.Y.Z` to ISCC together.