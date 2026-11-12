; Online installer: downloads the latest successful sunrise-dev artifact at install time.
#if Ver < EncodeVer(7, 0, 0)
  #error This script requires Inno Setup 7 or newer.
#endif
#ifndef ArtifactUrl
  #define ArtifactUrl "https://nightly.link/Chokopieum-Software/MateriaLauncher/workflows/create_installer_package/sunrise-dev/materia-launcher-app-windows-x64.zip"
#endif
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
#define AuthScheme "msal706fbc67-be98-4ded-bbe7-cc50097e2e2d"

[Setup]
; AppId uniquely identifies this application.
; Keep this ID stable across releases to upgrade existing Inno Setup installations.
AppId={{019b375c-3319-7eec-8098-e50668c43b5a}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
; This is the bootstrapper version; the downloaded launcher may be newer.
AppVerName={#MyAppName} (Online)
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppDirName}
DisableProgramGroupPage=yes
LicenseFile=LICENSE
; Install for the current user without administrator privileges.
PrivilegesRequired=lowest
OutputDir=build\inno-setup
OutputBaseFilename=MateriaLauncher-Online-Setup
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
MinVersion=10.0
ArchiveExtraction=full

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "authprotocol"; Description: "{cm:RegisterAuthProtocol}"; GroupDescription: "{cm:AdditionalIcons}"

[CustomMessages]
english.RegisterAuthProtocol=Register the Microsoft sign-in URI handler
russian.RegisterAuthProtocol=Зарегистрировать URI для входа через Microsoft
english.WebView2Missing=Microsoft Edge WebView2 Runtime was not found. HTML descriptions of mods may be unavailable until you install the Evergreen Runtime from https://developer.microsoft.com/microsoft-edge/webview2/. Installation will continue.
russian.WebView2Missing=Microsoft Edge WebView2 Runtime не найден. HTML-описания модов могут быть недоступны до установки Evergreen Runtime с https://developer.microsoft.com/microsoft-edge/webview2/. Установка будет продолжена.

[Registry]
Root: HKCU; Subkey: "Software\Classes\{#AuthScheme}"; ValueType: string; ValueName: ""; ValueData: "URL:Materia Microsoft login"; Tasks: authprotocol
Root: HKCU; Subkey: "Software\Classes\{#AuthScheme}"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""; Tasks: authprotocol
Root: HKCU; Subkey: "Software\Classes\{#AuthScheme}\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: authprotocol

[Files]
; ZIP contains materia-launcher.exe, app/ and runtime/ at its root.
Source: "{#ArtifactUrl}"; DestName: "materia-launcher-app.zip"; DestDir: "{app}"; ExternalSize: 1073741824; Flags: external download extractarchive ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[UninstallDelete]
; Self-updates may introduce files absent from the original Inno uninstall log.
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"
Type: filesandordirs; Name: "{app}\updater"
Type: files; Name: "{app}\.update.lock"

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
const
  WebView2ClientKey = 'Software\Microsoft\EdgeUpdate\Clients\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}';
  AuthProtocolKey = 'Software\Classes\{#AuthScheme}';

function HasWebView2At(Root: Integer): Boolean;
var
  Version: String;
  PackedVersion: Int64;
begin
  Result := RegQueryStringValue(Root, WebView2ClientKey, 'pv', Version) and
    StrToVersion(Version, PackedVersion) and (PackedVersion > 0);
end;

function InitializeSetup(): Boolean;
var
  Available: Boolean;
begin
  Available := HasWebView2At(HKCU32) or HasWebView2At(HKLM32);
  if IsWin64 then
    Available := Available or HasWebView2At(HKCU64) or HasWebView2At(HKLM64);
  if not Available then
  begin
    Log('Microsoft Edge WebView2 Runtime not found; continuing installation.');
    if not WizardSilent then
      MsgBox(CustomMessage('WebView2Missing'), mbInformation, MB_OK);
  end;
  Result := True;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  Command: String;
begin
  if CurUninstallStep = usUninstall then
    if RegQueryStringValue(HKCU, AuthProtocolKey + '\shell\open\command', '', Command) then
      { Leave a handler registered by another installation or IDE launch intact. }
      if Command = ExpandConstant('"{app}\{#MyAppExeName}" "%1"') then
        RegDeleteKeyIncludingSubkeys(HKCU, AuthProtocolKey);
end;
