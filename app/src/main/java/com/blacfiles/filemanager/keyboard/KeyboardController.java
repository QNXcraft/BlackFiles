package com.blacfiles.filemanager.keyboard;

import android.view.KeyEvent;

import com.blacfiles.filemanager.ui.FileListFragment;

/**
 * Stateless keyboard event handler for all BlackBerry physical QWERTY hotkeys.
 *
 * Called from {@code MainActivity.dispatchKeyEvent()} before the event is
 * dispatched to the view hierarchy.  Returns {@code true} if the event was
 * consumed and should not propagate further.
 *
 * Hotkey table (ACTION_DOWN only; repeat events are accepted for D-pad):
 *
 *   Space / Enter      → open focused item (activate)
 *   C                  → copy selected items
 *   X                  → cut / move selected items
 *   V                  → paste from clipboard
 *   Del / Backspace    → delete selected items  (Backspace also navigates up if
 *                        no selection mode is active)
 *   A                  → select all
 *   D                  → deselect all
 *   R                  → rename selected (single item)
 *   H                  → toggle hidden files visibility
 *   D-pad UP/DOWN      → move focus up / down in list
 *   D-pad LEFT / ALT+← → navigate to parent directory
 *
 * See .agents/keyboard.md for full rationale and KeyEvent constant references.
 */
public final class KeyboardController {

    private KeyboardController() {}

    /**
     * Handle the key event for the active fragment.
     *
     * @param keyCode  from {@code KeyEvent.getKeyCode()}
     * @param event    the full event object
     * @param fragment the currently visible {@link FileListFragment}
     * @return {@code true} if consumed
     */
    public static boolean handle(int keyCode, KeyEvent event,
                                  FileListFragment fragment) {
        if (fragment == null) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        switch (keyCode) {

            // ── Open / activate ───────────────────────────────────────────────
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                fragment.activateFocused();
                return true;

            // ── Copy ──────────────────────────────────────────────────────────
            case KeyEvent.KEYCODE_C:
                if (!isTextInputFocused(fragment)) {
                    fragment.copySelected();
                    return true;
                }
                return false;

            // ── Cut / Move ────────────────────────────────────────────────────
            case KeyEvent.KEYCODE_X:
                if (!isTextInputFocused(fragment)) {
                    fragment.cutSelected();
                    return true;
                }
                return false;

            // ── Paste ─────────────────────────────────────────────────────────
            case KeyEvent.KEYCODE_V:
                if (!isTextInputFocused(fragment)) {
                    fragment.paste();
                    return true;
                }
                return false;

            // ── Delete / Navigate-up ──────────────────────────────────────────
            case KeyEvent.KEYCODE_DEL:
            case KeyEvent.KEYCODE_FORWARD_DEL:
                if (fragment.isSelectionMode()) {
                    fragment.deleteSelected();
                    return true;
                }
                // Backspace with no selection = go up one directory
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    return fragment.navigateUp();
                }
                return false;

            // ── Select all ────────────────────────────────────────────────────
            case KeyEvent.KEYCODE_A:
                if (!isTextInputFocused(fragment)) {
                    fragment.selectAll();
                    return true;
                }
                return false;

            // ── Deselect all ──────────────────────────────────────────────────
            case KeyEvent.KEYCODE_D:
                if (!isTextInputFocused(fragment)) {
                    fragment.deselectAll();
                    return true;
                }
                return false;

            // ── Rename ────────────────────────────────────────────────────────
            case KeyEvent.KEYCODE_R:
                if (!isTextInputFocused(fragment)) {
                    fragment.renameSelected();
                    return true;
                }
                return false;

            // ── Toggle hidden files ───────────────────────────────────────────
            case KeyEvent.KEYCODE_H:
                if (!isTextInputFocused(fragment)) {
                    fragment.toggleHiddenFiles();
                    return true;
                }
                return false;

            // ── D-pad navigation ──────────────────────────────────────────────
            case KeyEvent.KEYCODE_DPAD_DOWN:
                fragment.moveFocusDown();
                return true;

            case KeyEvent.KEYCODE_DPAD_UP:
                fragment.moveFocusUp();
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                return fragment.navigateUp();

            default:
                return false;
        }
    }

    /**
     * Returns true if an EditText (or other text input) currently has focus —
     * in that case we must not intercept character keys so the user can type.
     */
    private static boolean isTextInputFocused(FileListFragment fragment) {
        if (fragment.getActivity() == null) return false;
        android.view.View focused = fragment.getActivity().getCurrentFocus();
        return focused instanceof android.widget.EditText
                || focused instanceof android.widget.AutoCompleteTextView;
    }
}
