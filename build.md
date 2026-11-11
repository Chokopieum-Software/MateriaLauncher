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
gradlew.bat packageDistributable
```

**For macOS and Linux:**
```bash
./gradlew packageDistributable
```

After successfully running the command, you will find the ready-to-run distributables in the `build/compose/binaries/main/app/` directory.

## JBRSDK and IntelliJ IDEA

Use the full [JetBrains Runtime SDK 25](https://github.com/JetBrains/JetBrainsRuntime/releases), rather than a runtime-only distribution: packaging requires `jmods`, `jlink`, and `jpackage`.

1. Extract JBRSDK and point `JAVA_HOME` at the directory containing `bin/java.exe`.
2. Add that directory under IntelliJ IDEA Project Structure → SDKs and select the SDK for this project.
3. Under Settings → Build Tools → Gradle, use the Gradle Wrapper and this JDK (or `GRADLE_LOCAL_JAVA_HOME` with `java.home=...` in `.gradle/config.properties`).
4. Open a new terminal or restart the IDE after changing environment variables.

Verify with `gradlew.bat --version`, then `gradlew.bat build`. Package the application directory with `gradlew.bat createDistributable`.