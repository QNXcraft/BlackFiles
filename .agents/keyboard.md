# .agents/keyboard.md
## Keyboard Controller — Implementation Details

### Entry Point
`MainActivity.dispatchKeyEvent(KeyEvent)` → `KeyboardController.handle(keyCode, event, fragment)`

### Guard Condition
`KeyboardController.isTextInputFocused(fragment.getView())` — returns `true` if any
`EditText` in the fragment's view tree currently has focus.  When `true`, **all** character
keys (A/C/D/H/R/V/X) are passed through to the system; only navigation keys (D-pad) are
intercepted.

### KeyEvent Constants (android.view.KeyEvent)

| Constant                    | Value | Key              |
|-----------------------------|-------|------------------|
| `KEYCODE_SPACE`             | 62    | Space bar        |
| `KEYCODE_ENTER`             | 66    | Enter / Return   |
| `KEYCODE_DPAD_CENTER`       | 23    | D-pad centre     |
| `KEYCODE_DPAD_DOWN`         | 20    | D-pad down       |
| `KEYCODE_DPAD_UP`           | 19    | D-pad up         |
| `KEYCODE_DPAD_LEFT`         | 21    | D-pad left       |
| `KEYCODE_DEL`               | 67    | Backspace / Del  |
| `KEYCODE_FORWARD_DEL`       | 112   | Forward Delete   |
| `KEYCODE_A`                 | 29    | A                |
| `KEYCODE_C`                 | 31    | C                |
| `KEYCODE_D`                 | 32    | D                |
| `KEYCODE_H`                 | 36    | H                |
| `KEYCODE_R`                 | 46    | R                |
| `KEYCODE_V`                 | 50    | V                |
| `KEYCODE_X`                 | 52    | X                |

### Full Hotkey Table

| Key(s)                    | Action                                  | Notes                          |
|---------------------------|-----------------------------------------|-------------------------------|
| Space / Enter / DpadCenter| Open focused item (file or directory)   | Always intercepted             |
| DpadDown                  | Move list focus down one row            | Always intercepted             |
| DpadUp                    | Move list focus up one row              | Always intercepted             |
| DpadLeft / Backspace      | Navigate to parent directory            | Backspace: guard EditText focus|
| C                         | Copy selected items                     | Suppressed in EditText         |
| X                         | Cut selected items                      | Suppressed in EditText         |
| V                         | Paste to current directory              | Suppressed in EditText         |
| Del / ForwardDel          | Delete selected items                   | Suppressed in EditText         |
| A                         | Select all                              | Suppressed in EditText         |
| D                         | Deselect all                            | Suppressed in EditText         |
| R                         | Rename (single selection only)          | Suppressed in EditText         |
| H                         | Toggle hidden file visibility           | Suppressed in EditText         |

### Focus Model
- `ListView` items are focusable via `android:focusable="true"` on `item_file.xml` root.
- `item_file.xml` root has `android:descendantFocusability="blocksDescendants"` to prevent
  CheckBox from stealing focus during D-pad navigation.
- `ListView.setItemsCanFocus(true)` must be called in `FileListFragment.onViewCreated()`.
- `ListView.getSelectedItemPosition()` tracks keyboard focus; use `setSelection(pos)` to
  move focus programmatically.

### BB10 Physical Keyboard Notes
- BB Passport / Classic physical QWERTY generates standard `KEYCODE_*` events.
- There is no soft-keyboard on BB10; `windowSoftInputMode="adjustResize"` is set for
  compatibility only.
- The BB10 Back gesture / hardware Back button maps to `KEYCODE_BACK` — handled by
  `MainActivity.onBackPressed()`.
