#ifndef MATERIA_UPDATER_H
#define MATERIA_UPDATER_H
#define UNICODE
#define _UNICODE
#include <windows.h>
#include <commctrl.h>
#include <winhttp.h>
#include <bcrypt.h>
#include <shellapi.h>
#include <stdio.h>
#include <stdlib.h>
#include <wchar.h>
#ifndef WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY
#define WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY 4
#endif

/* Small Win32 boundary; update policy and file transaction live in Kotlin. */
static wchar_t *mu_wide(const char *s) {
    int n = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, s, -1, NULL, 0);
    wchar_t *p = (wchar_t *)calloc(n ? n : 1, sizeof(wchar_t));
    if (n) MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, s, -1, p, n);
    return p;
}
static char *mu_utf8(const wchar_t *s) {
    int n = WideCharToMultiByte(CP_UTF8, 0, s, -1, NULL, 0, NULL, NULL);
    char *p = (char *)calloc(n ? n : 1, 1);
    if (n) WideCharToMultiByte(CP_UTF8, 0, s, -1, p, n, NULL, NULL);
    return p;
}
static inline void mu_free(void *p) { free(p); }
static inline char *mu_request_path(void) {
    int n; LPWSTR *argv = CommandLineToArgvW(GetCommandLineW(), &n);
    char *result = mu_utf8(n == 2 ? argv[1] : L""); LocalFree(argv); return result;
}
static inline char *mu_fullpath(const char *path) {
    wchar_t *w = mu_wide(path), buffer[32768];
    DWORD n = GetFullPathNameW(w, 32768, buffer, NULL); free(w);
    return mu_utf8(n > 0 && n < 32768 ? buffer : L"");
}
static inline int mu_exists(const char *path) {
    wchar_t *w = mu_wide(path); DWORD a = GetFileAttributesW(w); free(w);
    return a != INVALID_FILE_ATTRIBUTES;
}
static inline int mu_plain_file(const char *path) {
    wchar_t *w = mu_wide(path); DWORD a = GetFileAttributesW(w); free(w);
    return a != INVALID_FILE_ATTRIBUTES && !(a & (FILE_ATTRIBUTE_DIRECTORY | FILE_ATTRIBUTE_REPARSE_POINT));
}
static inline int mu_mkdir(const char *path) {
    wchar_t *w = mu_wide(path); int ok = CreateDirectoryW(w, NULL); free(w); return ok;
}
static inline int mu_move(const char *from, const char *to) {
    wchar_t *a = mu_wide(from), *b = mu_wide(to); int ok = MoveFileW(a, b); free(a); free(b); return ok;
}
static int mu_delete_w(const wchar_t *path) {
    DWORD a = GetFileAttributesW(path);
    if (a == INVALID_FILE_ATTRIBUTES) return 1;
    if ((a & FILE_ATTRIBUTE_DIRECTORY) && !(a & FILE_ATTRIBUTE_REPARSE_POINT)) {
        size_t n = wcslen(path) + 3; wchar_t *pattern = (wchar_t *)calloc(n, sizeof(wchar_t));
        swprintf(pattern, n, L"%ls\\*", path);
        WIN32_FIND_DATAW data; HANDLE find = FindFirstFileW(pattern, &data); free(pattern);
        int ok = 1;
        if (find != INVALID_HANDLE_VALUE) {
            do {
                if (!wcscmp(data.cFileName, L".") || !wcscmp(data.cFileName, L"..")) continue;
                size_t len = wcslen(path) + wcslen(data.cFileName) + 2;
                wchar_t *child = (wchar_t *)calloc(len, sizeof(wchar_t));
                swprintf(child, len, L"%ls\\%ls", path, data.cFileName);
                if (!mu_delete_w(child)) ok = 0; free(child);
            } while (FindNextFileW(find, &data));
            FindClose(find);
        }
        if (!ok) return 0;
    }
    SetFileAttributesW(path, FILE_ATTRIBUTE_NORMAL);
    return (a & FILE_ATTRIBUTE_DIRECTORY) ? RemoveDirectoryW(path) : DeleteFileW(path);
}
static inline int mu_delete(const char *path) { wchar_t *w = mu_wide(path); int ok = mu_delete_w(w); free(w); return ok; }
static inline char *mu_read(const char *path) {
    wchar_t *w = mu_wide(path); FILE *f = _wfopen(w, L"rb"); free(w); if (!f) return NULL;
    fseek(f, 0, SEEK_END); long n = ftell(f); rewind(f);
    if (n < 0 || n > 16 * 1024 * 1024) { fclose(f); return NULL; }
    char *p = (char *)calloc(n + 1, 1);
    if (fread(p, 1, n, f) != (size_t)n) { free(p); p = NULL; }
    fclose(f); return p;
}
static inline int mu_write(const char *path, const char *text) {
    wchar_t *w = mu_wide(path); FILE *f = _wfopen(w, L"wb"); free(w); if (!f) return 0;
    int ok = fwrite(text, 1, strlen(text), f) == strlen(text); fclose(f); return ok;
}
static inline void *mu_lock(const char *path) {
    wchar_t *w = mu_wide(path);
    HANDLE h = CreateFileW(w, GENERIC_WRITE, 0, NULL, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL); free(w);
    return h == INVALID_HANDLE_VALUE ? NULL : h;
}
static inline void mu_unlock(void *handle) { if (handle) CloseHandle(handle); }
static inline int mu_wait_parent(unsigned long pid) {
    HANDLE h = OpenProcess(SYNCHRONIZE, FALSE, pid);
    if (!h) return GetLastError() == ERROR_INVALID_PARAMETER;
    DWORD result = WaitForSingleObject(h, 60000); CloseHandle(h); return result == WAIT_OBJECT_0;
}
static inline unsigned long mu_error(void) { return GetLastError(); }
static inline unsigned long mu_pid(void) { return GetCurrentProcessId(); }

static void mu_title(wchar_t *title) { _snwprintf(title, 100, L"Materia Updater (%lu)", GetCurrentProcessId()); }
static BOOL CALLBACK mu_find_window(HWND window, LPARAM result) {
    DWORD pid = 0; GetWindowThreadProcessId(window, &pid);
    wchar_t className[64]; GetClassNameW(window, className, 64);
    if (pid == GetCurrentProcessId() && !wcscmp(className, L"MateriaUpdater")) { *(HWND *)result = window; return FALSE; }
    return TRUE;
}
typedef struct { wchar_t *text; int percent; int done; } MU_EVENT;
static void mu_post(const char *text, int percent, int done) {
    MU_EVENT *e = (MU_EVENT *)calloc(1, sizeof(MU_EVENT));
    e->text = mu_wide(text); e->percent = percent; e->done = done;
    /* cinterop may emit each inline wrapper into a separate translation unit. */
    HWND window = NULL; EnumWindows(mu_find_window, (LPARAM)&window);
    if (!window || !PostMessageW(window, WM_APP + 1, 0, (LPARAM)e)) { free(e->text); free(e); }
}
static inline void mu_progress(const char *text, int percent) { mu_post(text, percent, 0); }
static inline void mu_done(const char *error) { mu_post(error, 100, error[0] ? 1 : 2); }
static LRESULT CALLBACK mu_window_proc(HWND h, UINT m, WPARAM w, LPARAM l) {
    if (m == WM_APP + 1) {
        MU_EVENT *e = (MU_EVENT *)l;
        HWND mu_label = GetDlgItem(h, 101), mu_bar = GetDlgItem(h, 102);
        SetWindowTextW(mu_label, e->text);
        SendMessageW(mu_bar, PBM_SETMARQUEE, e->percent < 0, 30);
        if (e->percent >= 0) {
            SetWindowLongPtrW(mu_bar, GWL_STYLE, GetWindowLongPtrW(mu_bar, GWL_STYLE) & ~PBS_MARQUEE);
            SendMessageW(mu_bar, PBM_SETPOS, e->percent, 0);
        } else SetWindowLongPtrW(mu_bar, GWL_STYLE, GetWindowLongPtrW(mu_bar, GWL_STYLE) | PBS_MARQUEE);
        if (e->done) { SetPropW(h, L"finished", (HANDLE)1); EnableMenuItem(GetSystemMenu(h, FALSE), SC_CLOSE, MF_ENABLED); }
        if (e->done == 2) PostMessageW(h, WM_CLOSE, 0, 0);
        free(e->text); free(e); return 0;
    }
    if (m == WM_CLOSE) { if (GetPropW(h, L"finished")) DestroyWindow(h); return 0; }
    if (m == WM_DESTROY) { PostQuitMessage(0); return 0; }
    return DefWindowProcW(h, m, w, l);
}
typedef struct { void (*callback)(void *); void *data; } MU_WORK;
static DWORD WINAPI mu_worker(void *p) { MU_WORK *work = (MU_WORK *)p; work->callback(work->data); free(work); return 0; }
static inline int mu_ui(void (*callback)(void *), void *data, const char *ready) {
    SetProcessDPIAware();
    INITCOMMONCONTROLSEX controls = { sizeof(controls), ICC_PROGRESS_CLASS }; InitCommonControlsEx(&controls);
    HINSTANCE instance = GetModuleHandleW(NULL);
    WNDCLASSW wc = {0}; wc.lpfnWndProc = mu_window_proc; wc.hInstance = instance;
    wc.lpszClassName = L"MateriaUpdater"; wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    wc.hCursor = LoadCursorW(NULL, IDC_ARROW); RegisterClassW(&wc);
    wchar_t title[100]; mu_title(title);
    HWND mu_window = CreateWindowExW(0, wc.lpszClassName, title, WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU,
        CW_USEDEFAULT, CW_USEDEFAULT, 580, 245, NULL, NULL, instance, NULL);
    if (!mu_window) return 0;
    HWND mu_label = CreateWindowExW(0, L"STATIC", L"", WS_CHILD | WS_VISIBLE, 24, 24, 520, 115, mu_window, (HMENU)101, instance, NULL);
    HWND mu_bar = CreateWindowExW(0, PROGRESS_CLASSW, L"", WS_CHILD | WS_VISIBLE | PBS_MARQUEE,
        24, 151, 520, 24, mu_window, (HMENU)102, instance, NULL);
    HFONT font = (HFONT)GetStockObject(DEFAULT_GUI_FONT); SendMessageW(mu_label, WM_SETFONT, (WPARAM)font, TRUE);
    EnableMenuItem(GetSystemMenu(mu_window, FALSE), SC_CLOSE, MF_GRAYED);
    ShowWindow(mu_window, SW_SHOW); ShowWindow(mu_window, SW_SHOW); UpdateWindow(mu_window);
    MU_WORK *work = (MU_WORK *)calloc(1, sizeof(MU_WORK)); work->callback = callback; work->data = data;
    HANDLE thread = CreateThread(NULL, 0, mu_worker, work, CREATE_SUSPENDED, NULL);
    wchar_t observedTitle[100]; GetWindowTextW(mu_window, observedTitle, 100);
    char *observed = mu_utf8(observedTitle), readiness[512];
    snprintf(readiness, sizeof(readiness), "ready: visible=%d title=%s", IsWindowVisible(mu_window), observed);
    free(observed);
    if (!thread || !mu_write(ready, readiness)) { if (thread) { TerminateThread(thread, 1); CloseHandle(thread); } free(work); return 0; }
    ResumeThread(thread);
    MSG msg; while (GetMessageW(&msg, NULL, 0, 0) > 0) { TranslateMessage(&msg); DispatchMessageW(&msg); }
    WaitForSingleObject(thread, INFINITE); CloseHandle(thread); return 1;
}

/* Exercise cross-thread progress delivery in the native integration tests. */
static void mu_ui_test_worker(void *result) {
    mu_progress("Progress test", 50);
    HWND window = NULL; EnumWindows(mu_find_window, (LPARAM)&window);
    for (int i = 0; i < 100 && window; ++i) {
        wchar_t label[100]; GetWindowTextW(GetDlgItem(window, 101), label, 100);
        if (!wcscmp(label, L"Progress test") && SendMessageW(GetDlgItem(window, 102), PBM_GETPOS, 0, 0) == 50) {
            *(int *)result = 1; break;
        }
        Sleep(10);
    }
    mu_done("");
}
static inline int mu_test_ui(const char *ready) {
    int result = 0; return mu_ui(mu_ui_test_worker, &result, ready) && result;
}

/* WinHTTP follows the nightly.link HTTPS redirect to GitHub's artifact storage. */
static inline int mu_download(const char *url, const char *file, const char *sha256) {
    wchar_t *wurl = mu_wide(url), host[512], path[4096];
    URL_COMPONENTS parts = {0}; parts.dwStructSize = sizeof(parts);
    parts.lpszHostName = host; parts.dwHostNameLength = 512; parts.lpszUrlPath = path; parts.dwUrlPathLength = 4096;
    HINTERNET session = NULL, connection = NULL, request = NULL;
    FILE *output = NULL; BCRYPT_ALG_HANDLE alg = NULL; BCRYPT_HASH_HANDLE hash = NULL;
    unsigned char actual[32], buffer[131072]; int ok = 0;
    if (strlen(sha256) != 64 || !WinHttpCrackUrl(wurl, 0, 0, &parts) || parts.nScheme != INTERNET_SCHEME_HTTPS) goto cleanup;
    host[parts.dwHostNameLength] = 0; path[parts.dwUrlPathLength] = 0;
    session = WinHttpOpen(L"MateriaUpdater", WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY, NULL, NULL, 0);
    if (!session) goto cleanup;
    WinHttpSetTimeouts(session, 30000, 30000, 30000, 60000);
    connection = WinHttpConnect(session, host, parts.nPort, 0); if (!connection) goto cleanup;
    request = WinHttpOpenRequest(connection, L"GET", path, NULL, NULL, NULL, WINHTTP_FLAG_SECURE); if (!request) goto cleanup;
    if (!WinHttpSendRequest(request, NULL, 0, NULL, 0, 0, 0) || !WinHttpReceiveResponse(request, NULL)) goto cleanup;
    DWORD status = 0, length = sizeof(status);
    if (!WinHttpQueryHeaders(request, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER, NULL, &status, &length, NULL) || status != 200) goto cleanup;
    wchar_t sizeText[64]; length = sizeof(sizeText);
    unsigned long long size = 0, received = 0;
    if (WinHttpQueryHeaders(request, WINHTTP_QUERY_CONTENT_LENGTH, NULL, sizeText, &length, NULL)) size = _wcstoui64(sizeText, NULL, 10);
    if (size > 4294967296ULL || BCryptOpenAlgorithmProvider(&alg, BCRYPT_SHA256_ALGORITHM, NULL, 0) < 0 ||
        BCryptCreateHash(alg, &hash, NULL, 0, NULL, 0, 0) < 0) goto cleanup;
    wchar_t *wfile = mu_wide(file); output = _wfopen(wfile, L"wb"); free(wfile); if (!output) goto cleanup;
    DWORD count = 0; ULONGLONG last = 0;
    for (;;) {
        if (!WinHttpReadData(request, buffer, sizeof(buffer), &count)) goto cleanup;
        if (!count) break;
        received += count; if (received > 4294967296ULL) goto cleanup;
        if (fwrite(buffer, 1, count, output) != count || BCryptHashData(hash, buffer, count, 0) < 0) goto cleanup;
        ULONGLONG now = GetTickCount64();
        if (now - last > 100) {
            char text[256]; snprintf(text, sizeof(text), "Downloading: %.1f MB%s", received / 1048576.0, size ? " (HTTPS)" : "");
            mu_progress(text, size ? (int)(received * 100 / size) : -1); last = now;
        }
    }
    if ((size && received != size) || BCryptFinishHash(hash, actual, 32, 0) < 0) goto cleanup;
    char hex[65]; for (int i = 0; i < 32; ++i) snprintf(hex + i * 2, 3, "%02x", actual[i]);
    ok = _stricmp(hex, sha256) == 0;
cleanup:
    if (output && fclose(output) != 0) ok = 0;
    if (hash) BCryptDestroyHash(hash); if (alg) BCryptCloseAlgorithmProvider(alg, 0);
    if (request) WinHttpCloseHandle(request); if (connection) WinHttpCloseHandle(connection); if (session) WinHttpCloseHandle(session);
    free(wurl); return ok;
}

/* Execute native Windows tar.exe directly, without cmd/PowerShell or a shell. */
static inline int mu_tar(const char *zip, const char *directory, const char *listing, int mode) {
    wchar_t system[32768], executable[32768]; GetSystemDirectoryW(system, 32768);
    swprintf(executable, 32768, L"%ls\\tar.exe", system);
    wchar_t *z = mu_wide(zip), *d = mu_wide(directory), *l = mu_wide(listing);
    /* tar converts argv through the ANSI code page on some Windows versions.
       Keep argv ASCII: open the ZIP with Win32 Unicode APIs and stream stdin;
       set the Unicode extraction directory through CreateProcessW instead of -C. */
    wchar_t command[32768], workingDirectory[32768];
    DWORD directoryLength = GetFullPathNameW(d, 32768, workingDirectory, NULL);
    if (!directoryLength || directoryLength >= 32768) { free(z); free(d); free(l); return 0; }
    if (mode == 2) swprintf(command, 32768, L"\"%ls\" -xf - --no-same-owner --no-same-permissions", executable);
    else swprintf(command, 32768, L"\"%ls\" %ls -", executable, mode == 1 ? L"-tvf" : L"-tf");
    SECURITY_ATTRIBUTES security = { sizeof(security), NULL, TRUE };
    HANDLE output = CreateFileW(l, GENERIC_WRITE, FILE_SHARE_READ, &security, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    HANDLE input = CreateFileW(z, GENERIC_READ, FILE_SHARE_READ, &security, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    STARTUPINFOW startup = {0}; startup.cb = sizeof(startup); startup.dwFlags = STARTF_USESTDHANDLES;
    startup.hStdOutput = output; startup.hStdError = output; startup.hStdInput = input;
    PROCESS_INFORMATION process = {0}; int ok = 0;
    if (output != INVALID_HANDLE_VALUE && input != INVALID_HANDLE_VALUE && CreateProcessW(executable, command, NULL, NULL, TRUE, CREATE_NO_WINDOW, NULL, workingDirectory, &startup, &process)) {
        if (WaitForSingleObject(process.hProcess, 300000) == WAIT_OBJECT_0) {
            DWORD code = 1; GetExitCodeProcess(process.hProcess, &code); ok = code == 0;
        } else { TerminateProcess(process.hProcess, 1); WaitForSingleObject(process.hProcess, 5000); }
        CloseHandle(process.hThread); CloseHandle(process.hProcess);
    }
    if (output != INVALID_HANDLE_VALUE) CloseHandle(output); if (input != INVALID_HANDLE_VALUE) CloseHandle(input);
    free(z); free(d); free(l); return ok;
}
static inline int mu_launch(const char *exe, const char *directory) {
    wchar_t *w = mu_wide(exe), *d = mu_wide(directory); STARTUPINFOW startup = {0}; startup.cb = sizeof(startup);
    PROCESS_INFORMATION process = {0};
    int ok = CreateProcessW(w, NULL, NULL, NULL, FALSE, 0, NULL, d, &startup, &process);
    if (ok) { CloseHandle(process.hThread); CloseHandle(process.hProcess); } free(w); free(d); return ok;
}
#endif
