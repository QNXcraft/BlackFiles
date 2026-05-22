# BlackFiles

**A Modern File Manager for BlackBerry 10 (Android 4.3 / API 18)**

BlackFiles is a full-featured, production-grade file manager designed specifically for BlackBerry 10 devices running the Android 4.3 runtime. Inspired by GNOME Nautilus, it offers a classic, keyboard-driven interface, robust local and remote file operations, and seamless support for legacy hardware.

---

## Features

- **Classic UI**: DrawerLayout navigation, ListView file browser, and toolbar with breadcrumbs
- **Full QWERTY Keyboard Navigation**: All major file actions mapped to physical keys (see Hotkey Table)
- **Local & Remote File Management**:
  - Internal storage & SD card
  - SFTP, SCP, FTP, FTPS, WebDAV, SMB (see Protocol Table)
- **Background File Operations**: Copy, move, delete, rename, and new folder with progress dialogs
- **Favorites & Sorting**: Add favorites, sort by name/date/size/type, toggle hidden files
- **Robust Preferences**: All settings and connections stored in SharedPreferences (no cloud dependency)
- **BB10-Optimized**: 1:1 aspect ratio, Holo theme, no AndroidX, no Material, no Jetpack Compose

---

## Supported Protocols

| Protocol | Library                         | Version |
|----------|---------------------------------|---------|
| SFTP     | com.jcraft:jsch                 | 0.1.55  |
| SCP      | com.jcraft:jsch                 | 0.1.55  |
| FTP      | commons-net:commons-net         | 3.6     |
| FTPS     | commons-net:commons-net         | 3.6     |
| WebDAV   | sardine-android                 | 0.8     |
| SMB      | eu.agno3.jcifs:jcifs-ng         | 2.1.6   |

---

## Keyboard Hotkeys

| Key                | Action                                   |
|--------------------|------------------------------------------|
| Space / Enter      | Open focused file or directory           |
| C                  | Copy selected items                      |
| X                  | Cut selected items (move)                |
| V                  | Paste clipboard                          |
| Del / Backspace    | Delete or navigate up                    |
| A                  | Select all                               |
| D                  | Deselect all                             |
| R                  | Rename                                   |
| H                  | Toggle hidden files                      |
| D-pad (arrows)     | Move focus / navigate                    |

---

## Build & Run

**Requirements:**
- JDK 7 (Java 1.7)
- Android SDK Platform 18 (Android 4.3)
- Gradle 2.14.1 (use the included `./gradlew` wrapper)
- No AndroidX, no Jetpack, no Material Components

**To build:**

```sh
./gradlew clean assembleDebug
```

**APK output:**
- `app/build/outputs/apk/debug/app-debug.apk`

**To install on device:**
- Use `adb install` or sideload via BlackBerry 10 developer tools

---

## Project Structure

- `app/src/main/java/com/blacfiles/filemanager/` — All Java sources
- `app/src/main/res/` — Layouts, drawables, values
- `.agents/` — Protocol, keyboard, and sorting documentation for agent collaboration
- `AGENTS.md` — Full architecture, constraints, and agent session checklist

---

## Why BlackFiles?
- **Legacy Support**: The only modern file manager built for BlackBerry 10’s Android runtime in 2026
- **No Modern Android Dependencies**: 100% compatible with API 18, Java 7, and BB10 hardware quirks
- **Agent-Ready**: Institutional knowledge and architecture docs for AI agent collaboration

---

## License

BlackFiles is released under the MIT License.
