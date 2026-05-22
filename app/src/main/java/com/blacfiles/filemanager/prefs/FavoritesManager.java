package com.blacfiles.filemanager.prefs;

import android.content.Context;

import java.util.List;

/**
 * Thin helper that manages the favorites list inside AppPreferences.
 * Keeps add/remove/list logic isolated so callers don't touch raw preference
 * lists directly.
 */
public class FavoritesManager {

    private final AppPreferences prefs;

    public FavoritesManager(Context context) {
        prefs = new AppPreferences(context);
    }

    /** Returns all favorited paths in insertion order. */
    public List<String> getAll() {
        return prefs.getFavorites();
    }

    /** Adds path if not already present. */
    public void add(String path) {
        List<String> list = prefs.getFavorites();
        if (!list.contains(path)) {
            list.add(path);
            prefs.saveFavorites(list);
        }
    }

    /** Removes path. No-op if not in favorites. */
    public void remove(String path) {
        List<String> list = prefs.getFavorites();
        list.remove(path);
        prefs.saveFavorites(list);
    }

    /** Returns true if path is in favorites. */
    public boolean contains(String path) {
        return prefs.getFavorites().contains(path);
    }
}
