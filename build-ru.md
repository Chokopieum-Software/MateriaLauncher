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
gradlew.bat createDistributable
```

**Для macOS и Linux:**
```bash
./gradlew createDistributable
```

После успешного выполнения команды вы найдете готовые для запуска дистрибутивы в каталоге `build/compose/binaries/main/app/`.

## JBRSDK и IntelliJ IDEA

Используйте полный [JetBrains Runtime SDK 25](https://github.com/JetBrains/JetBrainsRuntime/releases), а не только runtime: для упаковки нужны `jmods`, `jlink` и `jpackage`.

1. Распакуйте JBRSDK и задайте `JAVA_HOME` на каталог, содержащий `bin/java.exe`.
2. В IntelliJ IDEA выберите этот каталог в Project Structure → SDKs и назначьте SDK проекту.
3. В Settings → Build Tools → Gradle используйте Gradle Wrapper и этот JDK (либо `GRADLE_LOCAL_JAVA_HOME`, если путь записан в `.gradle/config.properties` как `java.home=...`).
4. После изменения переменных окружения откройте новый терминал или перезапустите IDE.

Проверка: `gradlew.bat --version`, затем `gradlew.bat build`. Упаковка каталога приложения: `gradlew.bat createDistributable`.

## Установщик Windows и версии

Единый источник версии — `build.properties`: сейчас `version=0.1.0` и
`channel=Alpha`. Для первого стабильного релиза задайте `version=1.0.0` и
`channel=Stable`. Повышайте числовую версию вручную перед распространением
обновления. Gradle больше не изменяет этот файл. Номер сборки отдельно:
`GITHUB_RUN_NUMBER` в CI, `Local` локально, либо `-PbuildNumber=...`.

Установщик создаёт только Inno Setup 6+. После `gradlew.bat test createDistributable`
выполните `ISCC.exe installer.iss`. Результат:
`build/inno-setup/MateriaLauncher-Setup.exe`.

### Онлайн-установщик (Inno Setup 7+)

Выполните `ISCC.exe installer-online.iss`. Результат:
`build/inno-setup/MateriaLauncher-Online-Setup.exe`. Для его компиляции
не нужны Gradle, JDK или локально собранный дистрибутив приложения.

При установке он скачивает ZIP через nightly.link из последнего успешного
запуска `create_installer_package.yml` в ветке `sunrise-dev`, распаковывает приложение
в каталог установки и регистрирует его удаление, ярлыки и обработчик входа
Microsoft. Java Runtime входит в скачиваемый дистрибутив. Нужен доступ к
nightly.link и хранилищу артефактов GitHub.

Workflow публикует для этого артефакт с постоянным именем
`materia-launcher-app-windows-x64`: в корне ZIP находятся
`materia-launcher.exe`, `app/` и `runtime/`. Сначала должен успешно завершиться
хотя бы один запуск обновлённого workflow в `sunrise-dev`. Артефакты хранятся 30 дней;
если доступного артефакта нет, установка завершится ошибкой загрузки.

Адрес можно заменить при компиляции:
`ISCC.exe /DArtifactUrl="https://example.org/materia-launcher.zip" installer-online.iss`.
ZIP должен иметь ту же структуру. `AppVersion` обозначает версию самого
онлайн-установщика из `build.properties`, а не версию скачиваемой сборки.
`ExternalSize` в скрипте — предварительная оценка распакованного размера (1 ГиБ)
для расчёта места и прогресса. Файлы устанавливаются штатным механизмом Inno Setup
с поддержкой удаления; SHA-256 не закреплён, поскольку содержимое latest меняется.

### Обновление установленного лаунчера

В «Настройки → О программе» нажмите «Проверить обновления», затем «Обновить».
Проверяется последняя успешная сборка workflow `create_installer_package.yml`
в ветке `sunrise-dev`; сравниваются номер сборки и ID запуска Actions.
Обновление требует первоначальной установки новой сборки, включающей
`update.properties` и `updater/MateriaUpdater.exe`. При запуске из IDE или JAR
автообновление отключено.

`MateriaUpdater.exe` — отдельная программа на Kotlin/Native (Windows x64)
с окном и полосой прогресса Win32. JVM и установленная Java ей не нужны.
HTTPS-загрузка выполняется WinHTTP; распаковка — системным `tar.exe`
(Windows 10 1803+). Обновлятор копируется во временную папку, подтверждает
готовность окна, затем лаунчер закрывается. Скачивание закреплено за выбранным
артефактом через nightly.link, SHA-256 проверяется по данным GitHub API.

Перед заменой проверяются пути архива, отсутствие ссылок, структура дистрибутива,
ветка и ID сборки. Обновляются только `app`, `runtime`, `updater`,
`materia-launcher.exe` и `update.properties`; остальные файлы каталога установки
не затрагиваются. Старые компоненты временно переносятся в резервную папку;
при ошибке замены выполняется откат. При успешном обновлении лаунчер запускается
снова. При ошибке доступны журнал во временной папке и диагностические файлы
`.materia-update-*` рядом с установкой. Резервные копии при ошибках сохраняются.
Временную папку `materia-updater-*` можно удалить после завершения обновлятора.

Сборка только обновлятора: `gradlew.bat :updater:bundleUpdater`.
Результат: `updater/build/bundle/MateriaUpdater.exe`. `createDistributable`
автоматически включает его в Windows-дистрибутив. Нативные тесты:
`gradlew.bat :updater:mingwX64Test`.

GitHub Actions автоматически выполняет тесты и сборку при push, pull request
и ручном запуске. Установщик доступен в Artifacts соответствующего запуска
30 дней; публикации GitHub Release нет. Подробности и параметры переопределения
версии приведены в `build.md`.

Ранние экспериментальные пакеты `1.0.N` могут потребовать удаления перед
установкой Alpha `0.x`, поскольку Windows считает их версию более высокой.
