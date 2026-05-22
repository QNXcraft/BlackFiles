package com.blacfiles.filemanager.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.blacfiles.filemanager.model.RemoteConnection;
import com.blacfiles.filemanager.sort.SortMode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralised SharedPreferences wrapper.
 *
 * Keys and defaults are defined as constants here so nothing is hard-coded
 * elsewhere in the codebase.
 */
public class AppPreferences {

    private static final String PREFS_NAME        = "blacfiles_prefs";
    private static final String KEY_SHOW_HIDDEN   = "show_hidden_files";
    private static final String KEY_SORT_MODE     = "sort_mode";
    private static final String KEY_CONNECTIONS   = "remote_connections";
    private static final String KEY_FAVORITES     = "favorites";

    private final SharedPreferences prefs;

    public AppPreferences(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Hidden files ─────────────────────────────────────────────────────────

    public boolean isShowHiddenFiles() {
        return prefs.getBoolean(KEY_SHOW_HIDDEN, false);
    }

    public void setShowHiddenFiles(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, show).apply();
    }

    // ── Sort mode ─────────────────────────────────────────────────────────────

    public SortMode getSortMode() {
        String name = prefs.getString(KEY_SORT_MODE, SortMode.NAME_ASC.name());
        try {
            return SortMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return SortMode.NAME_ASC;
        }
    }

    public void setSortMode(SortMode mode) {
        prefs.edit().putString(KEY_SORT_MODE, mode.name()).apply();
    }

    // ── Remote connections ────────────────────────────────────────────────────

    public List<RemoteConnection> getConnections() {
        List<RemoteConnection> list = new ArrayList<RemoteConnection>();
        String json = prefs.getString(KEY_CONNECTIONS, null);
        if (json == null) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(connectionFromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Corrupt data — return empty list
        }
        return list;
    }

    public void saveConnections(List<RemoteConnection> connections) {
        JSONArray arr = new JSONArray();
        for (RemoteConnection c : connections) {
            arr.put(connectionToJson(c));
        }
        prefs.edit().putString(KEY_CONNECTIONS, arr.toString()).apply();
    }

    /** Appends a single new connection and persists. */
    public void addConnection(RemoteConnection connection) {
        List<RemoteConnection> list = getConnections();
        list.add(connection);
        saveConnections(list);
    }

    /** Removes by id and persists. */
    public void removeConnection(String id) {
        List<RemoteConnection> list = getConnections();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (id.equals(list.get(i).getId())) {
                list.remove(i);
            }
        }
        saveConnections(list);
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    /** Returns list of favorite path strings (local absolute paths or remote URIs). */
    public List<String> getFavorites() {
        List<String> list = new ArrayList<String>();
        String json = prefs.getString(KEY_FAVORITES, null);
        if (json == null) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
        } catch (JSONException e) {
            // ignore
        }
        return list;
    }

    public void saveFavorites(List<String> favorites) {
        JSONArray arr = new JSONArray();
        for (String f : favorites) {
            arr.put(f);
        }
        prefs.edit().putString(KEY_FAVORITES, arr.toString()).apply();
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private static JSONObject connectionToJson(RemoteConnection c) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id",             c.getId());
            obj.put("displayName",    c.getDisplayName());
            obj.put("protocol",       c.getProtocol().name());
            obj.put("host",           c.getHost());
            obj.put("port",           c.getPort());
            obj.put("username",       c.getUsername());
            obj.put("password",       c.getPassword());
            obj.put("initialPath",    c.getInitialPath());
            obj.put("privateKeyPath", c.getPrivateKeyPath() != null ? c.getPrivateKeyPath() : "");
        } catch (JSONException e) {
            // Should never happen for these types
        }
        return obj;
    }

    private static RemoteConnection connectionFromJson(JSONObject obj) throws JSONException {
        RemoteConnection c = new RemoteConnection();
        c.setId(obj.getString("id"));
        c.setDisplayName(obj.getString("displayName"));
        c.setProtocol(RemoteConnection.Protocol.valueOf(obj.getString("protocol")));
        c.setHost(obj.getString("host"));
        c.setPort(obj.getInt("port"));
        c.setUsername(obj.getString("username"));
        c.setPassword(obj.getString("password"));
        c.setInitialPath(obj.getString("initialPath"));
        String pk = obj.optString("privateKeyPath", "");
        c.setPrivateKeyPath(pk.length() > 0 ? pk : null);
        return c;
    }
}
