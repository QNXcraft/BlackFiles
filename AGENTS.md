# AGENTS.md — BlacFiles File Manager
## Version-controlled institutional knowledge for AI agent collaboration

---

## Agent Session Checklist
Before any task, an agent MUST:
1. Read this file in full.
2. Confirm `minSdkVersion 18` / `targetSdkVersion 18` in `app/build.gradle`.
3. Confirm Java source/target compatibility is `VERSION_1_7`.
4. Confirm zero AndroidX / Jetpack imports in any Java file.
5. Confirm no lambda (`->`) or stream (`.stream()`) syntax anywhere.

---

## Target Constraints (Absolute)

| Constraint             | Value                                |
|------------------------|--------------------------------------|
| OS                     | Android 4.3 — API Level 18           |
| Runtime                | BlackBerry 10 Android Runtime        |
| Java source/target     | 1.7 (Java 7)                         |
| UI paradigm            | View-based XML — NO Jetpack Compose  |
| Support library        | None — plain `android.*` APIs only   |
| DrawerLayout source    | `android.support.v4.widget.DrawerLayout` (bundled in support-v4) |
| Hardware               | BB Passport 1440×1440, Classic 720×720 (1:1 aspect ratio) |

---

## Project Directory Layout

```
BlacFiles/
├── app/
│   ├── build.gradle                        # compileSdk 18, Java 1.7
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/blacfiles/filemanager/
│       │   ├── model/
│       │   │   ├── FileItem.java           # Unified local+remote file model
│       │   │   └── RemoteConnection.java   # Connection profile value object
│       │   ├── prefs/
│       │   │   ├── AppPreferences.java     # SharedPreferences wrapper
│       │   │   └── FavoritesManager.java   # Favorites list helper
│       │   ├── ops/
│       │   │   ├── FileOperationType.java  # COPY/MOVE/DELETE/RENAME/NEW_FOLDER
│       │   │   ├── FileOperation.java      # Immutable operation + ProgressCallback
│       │   │   ├── FileOperationQueue.java # Single-threaded ExecutorService queue
│       │   │   ├── LocalFileOps.java       # Java IO: copy/move/rename/delete
│       │   │   └── CrossProtocolPipe.java  # InputStream→OutputStream pipe
│       │   ├── sort/
│       │   │   ├── SortMode.java           # Enum of all sort strategies
│       │   │   └── FileSorter.java         # Collections.sort + Comparators
│       │   ├── network/
│       │   │   ├── RemoteDriver.java       # Interface for all protocol drivers
│       │   │   ├── SessionCache.java       # In-memory LRU directory listing cache
│       │   │   ├── SftpDriver.java         # JSch 0.1.55
│       │   │   ├── ScpDriver.java          # JSch SCP exec channel
│       │   │   ├── FtpDriver.java          # Apache Commons Net 3.6
│       │   │   ├── WebDavDriver.java       # sardine-android 0.8
│       │   │   ├── SmbDriver.java          # jcifs-ng 2.1.6
│       │   │   └── DriverFactory.java      # Registry + factory for active drivers
│       │   ├── keyboard/
│       │   │   └── KeyboardController.java # Stateless BB10 QWERTY hotkey handler
│       │   └── ui/
│       │       ├── BreadcrumbBar.java      # Custom HorizontalScrollView crumb widget
│       │       ├── FileProvider.java       # Background listing + filter + sort
│       │       ├── FileListFragment.java   # Main file list Fragment
│       │       ├── MainActivity.java       # DrawerLayout host + KeyEvent router
│       │       ├── adapter/
│       │       │   ├── FileListAdapter.java  # BaseAdapter with multi-select
│       │       │   └── DrawerAdapter.java    # Sectioned drawer BaseAdapter
│       │       └── dialog/
│       │           ├── RemoteConnectionDialog.java  # New connection form
│       │           ├── RenameDialog.java             # Single EditText rename
│       │           └── OperationProgressDialog.java  # File op progress
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── fragment_file_list.xml
│           │   ├── item_file.xml
│           │   ├── item_drawer_entry.xml
│           │   ├── item_drawer_section_header.xml
│           │   └── dialog_remote_connection.xml
│           ├── drawable/
│           │   ├── list_item_selector.xml
│           │   └── drawer_item_selector.xml
│           └── values/
│               ├── colors.xml
│               ├── strings.xml
│               └── styles.xml
├── .agents/
│   ├── protocols.md    # Network library versions, BB10 TLS quirks, timeout settings
│   ├── keyboard.md     # Full hotkey table, KeyEvent constants, focus model
│   └── sorting.md      # Comparator rules, edge cases
├── build.gradle        # Root: AGP 1.5.0
├── settings.gradle
└── gradle/wrapper/
    └── gradle-wrapper.properties  # Gradle 2.14.1
```

---

## Network Protocol Library Table

| Protocol | Library                         | Version | API 18 Notes |
|----------|---------------------------------|---------|--------------|
| SFTP     | `com.jcraft:jsch`               | 0.1.55  | Java 7 compatible; disable StrictHostKeyChecking |
| SCP      | `com.jcraft:jsch`               | 0.1.55  | Uses exec channel + SCP wire protocol |
| FTP      | `commons-net:commons-net`       | 3.6     | Passive mode required for NAT/BB10 Wi-Fi |
| FTPS     | `commons-net:commons-net`       | 3.6     | FTPSClient with `true` for implicit TLS |
| WebDAV   | `com.thegrizzlylabs.sardine-android:sardine-android` | 0.8 | HttpURLConnection-based, Java 7 safe |
| SMB      | `eu.agno3.jcifs:jcifs-ng`       | 2.1.6   | Java 7 fork; disable DFS; SMB1 fallback |

See `.agents/protocols.md` for connection timeout settings and known BB10 TLS quirks.

---

## Global Keyboard Hotkey Table

| Key                | Action                                   |
|--------------------|------------------------------------------|
| `Space` / `Enter`  | Open focused file or directory           |
| `C`                | Copy selected items to clipboard         |
| `X`                | Cut selected items (move clipboard)      |
| `V`                | Paste clipboard to current directory     |
| `Backspace`        | Navigate to parent directory             |
| `Del`              | Delete selected items                    |
| `A`                | Select all items                         |
| `D`                | Deselect all items                       |
| `R`                | Rename selected item (single selection)  |
| `H`                | Toggle hidden file visibility            |
| `D-pad Down`       | Move list focus down                     |
| `D-pad Up`         | Move list focus up                       |
| `D-pad Left`       | Navigate to parent directory             |

Hotkeys are suppressed when an `EditText` has focus.
Entry point: `MainActivity.dispatchKeyEvent()` → `KeyboardController.handle()`.
See `.agents/keyboard.md` for KeyEvent constants and focus model details.

---

## Critical API 18 Rules (Never Violate)

1. **No lambdas** (`->`) — use anonymous inner classes.
2. **No streams** — use `for` loops.
3. **No `Optional`** — use null checks.
4. **No `default` interface methods** — Java 7 does not support them.
5. **No AndroidX** — use `android.support.v4.*` or bare `android.*` APIs.
6. **No `RecyclerView`** — use `ListView` with `BaseAdapter`.
7. **No `ViewBinding`** — use `findViewById`.
8. **No `Fragment` from AndroidX** — use `android.app.Fragment`.
9. **No `PopupMenu` from AndroidX** — use `android.widget.PopupMenu`.
10. **`DrawerLayout`** requires `android.support.v4` — add support-v4 dependency if not present.

---

## Structural Change Protocol

When any agent:
- Adds a new network protocol driver → add a row to the Library Table above and update `DriverFactory.java` + `.agents/protocols.md`.
- Adds a new keyboard hotkey → update the Hotkey Table above and `.agents/keyboard.md`.
- Changes the package structure → update the Directory Layout above.
- Adds a new preference key → document it in `AppPreferences.java` and this file.
