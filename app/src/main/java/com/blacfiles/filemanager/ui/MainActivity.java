package com.blacfiles.filemanager.ui;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import com.blacfiles.filemanager.R;
import com.blacfiles.filemanager.keyboard.KeyboardController;
import com.blacfiles.filemanager.model.RemoteConnection;
import com.blacfiles.filemanager.network.DriverFactory;
import com.blacfiles.filemanager.network.RemoteDriver;
import com.blacfiles.filemanager.prefs.AppPreferences;
import com.blacfiles.filemanager.prefs.FavoritesManager;
import com.blacfiles.filemanager.sort.SortMode;
import com.blacfiles.filemanager.ui.adapter.DrawerAdapter;
import com.blacfiles.filemanager.ui.dialog.RemoteConnectionDialog;

import android.support.v4.widget.DrawerLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application entry point.
 *
 * Responsibilities:
 *  - Hosts the DrawerLayout + Navigation Drawer
 *  - Manages the FileListFragment lifecycle (navigate / back stack)
 *  - Routes ALL KeyEvent inputs through KeyboardController
 *  - Handles drawer item clicks: local paths, remote connections, favorites
 *  - Implements FileListFragment.Host interface
 *  - Implements RemoteConnectionDialog.OnConnectionSavedListener
 *  - Implements RenameDialog.OnRenameListener (delegated to active fragment)
 */
public class MainActivity extends Activity
        implements FileListFragment.Host,
                   RemoteConnectionDialog.OnConnectionSavedListener,
                   com.blacfiles.filemanager.ui.dialog.RenameDialog.OnRenameListener {

    // Payload prefix to distinguish remote connections in drawer payloads
    private static final String PAYLOAD_ADD_REMOTE  = "ACTION:ADD_REMOTE";
    private static final String PAYLOAD_LOCAL_INT   = "LOCAL:INTERNAL";
    private static final String PAYLOAD_LOCAL_SD    = "LOCAL:SD";

    private DrawerLayout    drawerLayout;
    private ListView        drawerList;
    private DrawerAdapter   drawerAdapter;
    private BreadcrumbBar   breadcrumbBar;
    private ImageButton     btnToggleHidden;

    private AppPreferences  prefs;
    private FavoritesManager favorites;

    // Background thread for establishing remote connections
    private final ExecutorService connectExecutor = Executors.newSingleThreadExecutor();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs     = new AppPreferences(this);
        favorites = new FavoritesManager(this);

        drawerLayout    = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerList      = (ListView)     findViewById(R.id.drawer_list);
        breadcrumbBar   = (BreadcrumbBar) findViewById(R.id.breadcrumb_bar);
        btnToggleHidden = (ImageButton)  findViewById(R.id.btn_toggle_hidden);

        // Drawer
        drawerAdapter = new DrawerAdapter(this);
        drawerList.setAdapter(drawerAdapter);
        drawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                onDrawerItemClicked(pos);
                drawerLayout.closeDrawers();
            }
        });
        populateDrawer();

        // Toolbar buttons
        ((ImageButton) findViewById(R.id.btn_drawer_open)).setOnClickListener(
                new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (drawerLayout.isDrawerOpen(android.view.Gravity.START)) {
                    drawerLayout.closeDrawers();
                } else {
                    drawerLayout.openDrawer(android.view.Gravity.START);
                }
            }
        });

        ((ImageButton) findViewById(R.id.btn_sort)).setOnClickListener(
                new View.OnClickListener() {
            @Override public void onClick(View v) { showSortMenu(v); }
        });

        btnToggleHidden.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                FileListFragment f = activeFragment();
                if (f != null) f.toggleHiddenFiles();
            }
        });

        breadcrumbBar.setOnCrumbClickListener(new BreadcrumbBar.OnCrumbClickListener() {
            @Override public void onCrumbClick(String path) {
                navigateLocal(path);
            }
        });

        // Start on internal storage
        if (savedInstanceState == null) {
            navigateLocal(Environment.getExternalStorageDirectory().getAbsolutePath());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DriverFactory.releaseAll();
        connectExecutor.shutdownNow();
    }

    // ── KeyEvent routing ──────────────────────────────────────────────────────

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        FileListFragment fragment = activeFragment();
        if (fragment != null) {
            boolean consumed = KeyboardController.handle(
                    event.getKeyCode(), event, fragment);
            if (consumed) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(android.view.Gravity.START)) {
            drawerLayout.closeDrawers();
            return;
        }
        FileListFragment f = activeFragment();
        if (f != null && f.navigateUp()) return;
        super.onBackPressed();
    }

    // ── FileListFragment.Host ─────────────────────────────────────────────────

    @Override
    public void onPathChanged(String newPath) {
        breadcrumbBar.setPath(newPath);
    }

    @Override
    public void onSelectionChanged(int count) {
        // Could update a contextual toolbar title — no-op for now
    }

    // ── RemoteConnectionDialog.OnConnectionSavedListener ─────────────────────

    @Override
    public void onConnectionSaved(RemoteConnection connection) {
        populateDrawer();
        Toast.makeText(this, "Connection saved: " + connection.getDisplayName(),
                Toast.LENGTH_SHORT).show();
    }

    // ── RenameDialog.OnRenameListener ─────────────────────────────────────────

    @Override
    public void onRenamed(String newName) {
        FileListFragment f = activeFragment();
        if (f != null) f.onRenamed(newName);
    }

    // ── Navigation helpers ────────────────────────────────────────────────────

    private void navigateLocal(String path) {
        FragmentManager fm = getFragmentManager();
        FileListFragment current = activeFragment();

        if (current == null) {
            FileListFragment f = FileListFragment.newInstance(path, null);
            fm.beginTransaction()
              .replace(R.id.content_frame, f, "file_list")
              .commit();
        } else {
            current.navigate(path, null);
        }
        breadcrumbBar.setPath(path);
    }

    private void navigateRemote(final RemoteConnection conn) {
        // Connect on background thread, then navigate on UI thread
        connectExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    RemoteDriver driver = DriverFactory.getDriver(conn);
                    if (!driver.isConnected()) driver.connect();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            FragmentManager fm = getFragmentManager();
                            FileListFragment f = FileListFragment.newInstance(
                                    conn.getInitialPath(), conn.getId());
                            fm.beginTransaction()
                              .replace(R.id.content_frame, f, "file_list")
                              .addToBackStack(null)
                              .commit();
                            breadcrumbBar.setPath(conn.getInitialPath());
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this,
                                    "Connection failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    // ── Drawer ────────────────────────────────────────────────────────────────

    private void populateDrawer() {
        List<DrawerAdapter.DrawerItem> items = new ArrayList<DrawerAdapter.DrawerItem>();

        // ── Favorites ─────────────────────────────────────────────────────────
        items.add(new DrawerAdapter.DrawerItem(getString(R.string.section_favorites)));
        List<String> favList = favorites.getAll();
        for (String fav : favList) {
            String label = fav.substring(fav.lastIndexOf('/') + 1);
            if (label.isEmpty()) label = fav;
            items.add(new DrawerAdapter.DrawerItem(label, R.drawable.ic_star, fav));
        }
        if (favList.isEmpty()) {
            items.add(new DrawerAdapter.DrawerItem("(No favorites yet)",
                    R.drawable.ic_star, null));
        }

        // ── Local Storage ─────────────────────────────────────────────────────
        items.add(new DrawerAdapter.DrawerItem(getString(R.string.section_local)));
        items.add(new DrawerAdapter.DrawerItem(
                getString(R.string.internal_storage),
                R.drawable.ic_storage,
                PAYLOAD_LOCAL_INT));

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            items.add(new DrawerAdapter.DrawerItem(
                    getString(R.string.sd_card),
                    R.drawable.ic_sd_card,
                    PAYLOAD_LOCAL_SD));
        }

        // ── Network ───────────────────────────────────────────────────────────
        items.add(new DrawerAdapter.DrawerItem(getString(R.string.section_network)));
        List<RemoteConnection> connections = prefs.getConnections();
        for (RemoteConnection conn : connections) {
            items.add(new DrawerAdapter.DrawerItem(
                    conn.getDisplayName(),
                    R.drawable.ic_network,
                    "REMOTE:" + conn.getId()));
        }
        items.add(new DrawerAdapter.DrawerItem(
                getString(R.string.add_connection),
                R.drawable.ic_add,
                PAYLOAD_ADD_REMOTE));

        drawerAdapter.setItems(items);
    }

    private void onDrawerItemClicked(int position) {
        DrawerAdapter.DrawerItem item = drawerAdapter.getItem(position);
        if (item == null || item.payload == null) return;

        if (PAYLOAD_ADD_REMOTE.equals(item.payload)) {
            new RemoteConnectionDialog().show(getFragmentManager(), "add_remote");

        } else if (PAYLOAD_LOCAL_INT.equals(item.payload)) {
            navigateLocal(Environment.getExternalStorageDirectory().getAbsolutePath());

        } else if (PAYLOAD_LOCAL_SD.equals(item.payload)) {
            navigateLocal("/mnt/extsd"); // Common BB10 SD path

        } else if (item.payload.startsWith("REMOTE:")) {
            String connId = item.payload.substring(7);
            List<RemoteConnection> conns = prefs.getConnections();
            for (RemoteConnection c : conns) {
                if (c.getId().equals(connId)) {
                    navigateRemote(c);
                    return;
                }
            }
        } else {
            // Favorites — treat as local path
            navigateLocal(item.payload);
        }
    }

    // ── Sort menu ─────────────────────────────────────────────────────────────

    private void showSortMenu(View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        popup.getMenu().add(0, 0, 0, R.string.sort_name_asc);
        popup.getMenu().add(0, 1, 1, R.string.sort_name_desc);
        popup.getMenu().add(0, 2, 2, R.string.sort_date_desc);
        popup.getMenu().add(0, 3, 3, R.string.sort_date_asc);
        popup.getMenu().add(0, 4, 4, R.string.sort_size_desc);
        popup.getMenu().add(0, 5, 5, R.string.sort_size_asc);
        popup.getMenu().add(0, 6, 6, R.string.sort_type);

        popup.setOnMenuItemClickListener(
                new android.widget.PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem menuItem) {
                SortMode[] modes = {
                    SortMode.NAME_ASC, SortMode.NAME_DESC,
                    SortMode.DATE_DESC, SortMode.DATE_ASC,
                    SortMode.SIZE_DESC, SortMode.SIZE_ASC,
                    SortMode.TYPE
                };
                int idx = menuItem.getItemId();
                if (idx >= 0 && idx < modes.length) {
                    prefs.setSortMode(modes[idx]);
                    FileListFragment f = activeFragment();
                    if (f != null) f.refresh();
                }
                return true;
            }
        });
        popup.show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FileListFragment activeFragment() {
        return (FileListFragment) getFragmentManager()
                .findFragmentByTag("file_list");
    }
}
