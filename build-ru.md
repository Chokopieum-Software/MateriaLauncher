# Как собрать Materia Launcher

Это руководство поможет вам собрать Materia Launcher из исходного кода.

## 1. Системные требования

Убедитесь, что ваша система соответствует следующим требованиям:

*   **Операционная система**:
    *   Windows 10 или выше
    *   macOS
    *   Linux
*   **Java**:
    *   JBRSDK 25 — полный JDK с javac, jlink и jpackage. Переменная JAVA_HOME должна указывать на каталог JDK.
*   **Свободное место**:
    *   Минимум 2 ГБ свободного места на диске для загрузки библиотек и зависимостей.

## 2. Клонирование репозитория

Сначала клонируйте репозиторий проекта на свой локальный компьютер с помощью Git:

```bash
git clone https://github.com/Chokopieum-Software/MateriaKraft-Launcher.git
cd MateriaKraft-Launcher
```

## 3. Сборка проекта

Проект использует Gradle Wrapper (`gradlew`) для сборки.

Откройте терминал или командную строку в корневом каталоге проекта.

### Сборка и упаковка лаунчера

**Для Windows:**
```bash
gradlew.bat packageDistributable
```

**Для macOS и Linux:**
```bash
./gradlew packageDistributable
```

После успешного выполнения команды вы найдете готовые для запуска дистрибутивы в каталоге `build/compose/binaries/main/app/`.

## JBRSDK и IntelliJ IDEA

Используйте полный [JetBrains Runtime SDK 25](https://github.com/JetBrains/JetBrainsRuntime/releases), а не только runtime: для упаковки нужны `jmods`, `jlink` и `jpackage`.

1. Распакуйте JBRSDK и задайте `JAVA_HOME` на каталог, содержащий `bin/java.exe`.
2. В IntelliJ IDEA выберите этот каталог в Project Structure → SDKs и назначьте SDK проекту.
3. В Settings → Build Tools → Gradle используйте Gradle Wrapper и этот JDK (либо `GRADLE_LOCAL_JAVA_HOME`, если путь записан в `.gradle/config.properties` как `java.home=...`).
4. После изменения переменных окружения откройте новый терминал или перезапустите IDE.

Проверка: `gradlew.bat --version`, затем `gradlew.bat build`. Упаковка каталога приложения: `gradlew.bat createDistributable`.