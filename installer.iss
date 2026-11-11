#define MyAppName "Materia Launcher"
#ifndef MyAppVersion
  #define VersionFile FileOpen("build.properties")
  #define VersionLine ""
  #define VersionIndex 0
  #sub ReadVersion
    #define VersionLine FileRead(VersionFile)
    #if Pos("version=", VersionLine) == 1
      #define public MyAppVersion Copy(VersionLine, 9)
    #endif
  #endsub
  #for {VersionIndex = 0; !FileEof(VersionFile); VersionIndex++} ReadVersion
  #expr FileClose(VersionFile)
#endif
#ifndef MyAppVersion
  #error Missing version in build.properties
#endif
#define MyAppPublisher "Chokopieum Software"
#define MyAppExeName "materia-launcher.exe"
#define MyAppDirName "MateriaLauncher"

[Setup]
; AppId uniquely identifies this application.
; Keep this ID stable across releases to upgrade existing Inno Setup installations.
AppId={{019b375c-3319-7eec-8098-e50668c43b5a}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppDirName}
DisableProgramGroupPage=yes
LicenseFile=LICENSE
; Install for the current user without administrator privileges.
PrivilegesRequired=lowest
OutputDir=build\inno-setup
OutputBaseFilename=MateriaLauncher-Setup
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Путь к собранным файлам Compose Desktop (createDistributable)
Source: "build\compose\binaries\main\app\materia-launcher\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
