package com.blacfiles.filemanager.ui;

import android.app.Fragment;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.blacfiles.filemanager.R;
import com.blacfiles.filemanager.model.FileItem;
import com.blacfiles.filemanager.ops.FileOperation;
import com.blacfiles.filemanager.ops.FileOperationQueue;
import com.blacfiles.filemanager.prefs.AppPreferences;
import com.blacfiles.filemanager.prefs.FavoritesManager;
import com.blacfiles.filemanager.ui.adapter.FileListAdapter;
import com.blacfiles.filemanager.ui.dialog.OperationProgressDialog;
import com.blacfiles.filemanager.ui.dialog.RenameDialog;

import java.util.List;

/**
 * Core fragment displaying the current directory's file list.
 *
 * Navigation path: set via {@link #navigate(String, String)} from MainActivity.
 * The fragment also holds the clipboard state for copy/move operations.
 */
public class FileListFragment extends Fragment implements RenameDialog.OnRenameListener {

    /** Callback interface implemented by MainActivity. */
    public interface Host {
        void onPathChanged(String newPath);
        void onSelectionChanged(int count);
    }

    private static final String ARG_PATH          = "path";
    private static final String ARG_CONNECTION_ID = "connection_id";

    // ── Views ─────────────────────────────────────────────────────────────────
    private ListView        listView;
    private View            loadingOverlay;
    private TextView        emptyLabel;
    private FileListAdapter adapter;

    // ── State ─────────────────────────────────────────────────────────────────
    private String          currentPath;
    private String          connectionId;  // null for local
    private FileProvider    fileProvider;
    private AppPreferences  prefs;
    private FavoritesManager favorites;

    // Clipboard
    private List<FileItem>  clipboard;
    private boolean         clipboardIsCut = false;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static FileListFragment newInstance(String path, String connectionId) {
        FileListFragment f = new FileListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PATH,          path);
        args.putString(ARG_CONNECTION_ID, connectionId);
        f.setArguments(args);
        return f;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_file_list, container, false);
        listView      = (ListView) root.findViewById(R.id.file_list);
        loadingOverlay = root.findViewById(R.id.loading_overlay);
        emptyLabel    = (TextView) root.findViewById(R.id.empty_label);

        prefs         = new AppPreferences(getActivity());
        favorites     = new FavoritesManager(getActivity());
        fileProvider  = new FileProvider(prefs);
        adapter       = new FileListAdapter(getActivity());

        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                handleItemClick(position);
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                enterSelectionMode(position);
                return true;
            }
        });

        currentPath  = getArguments().getString(ARG_PATH);
        connectionId = getArguments().getString(ARG_CONNECTION_ID);

        refresh();
        return root;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /** Navigate into a new directory (or reconnect to a remote path). */
    public void navigate(String path, String connId) {
        currentPath  = path;
        connectionId = connId;
        refresh();
        Host host = (Host) getActivity();
        if (host != null) host.onPathChanged(path);
    }

    public String getCurrentPath()   { return currentPath; }
    public String getConnectionId()  { return connectionId; }

    /** Navigate to parent directory. Returns false if already at root. */
    public boolean navigateUp() {
        if (currentPath == null) return false;
        String parent = parentOf(currentPath);
        if (parent.equals(currentPath)) return false;
        navigate(parent, connectionId);
        return true;
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    public void refresh() {
        showLoading(true);
        FileProvider.Callback cb = new FileProvider.Callback() {
            @Override
            public void onResult(List<FileItem> items) {
                showLoading(false);
                adapter.setItems(items);
                emptyLabel.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                if (!items.isEmpty()) listView.requestFocus();
            }
            @Override
            public void onError(String message) {
                showLoading(false);
                Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
            }
        };

        if (connectionId == null || connectionId.isEmpty()) {
            fileProvider.listLocal(currentPath, cb);
        } else {
            fileProvider.listRemote(connectionId, currentPath, cb);
        }
    }

    // ── Selection API (called by KeyboardController) ──────────────────────────

    public void selectAll()   { adapter.selectAll();   notifySelectionChanged(); }
    public void deselectAll() { adapter.deselectAll(); notifySelectionChanged(); }
    public boolean isSelectionMode() { return adapter.isSelectionMode(); }
    public List<FileItem> getSelectedItems() { return adapter.getSelectedItems(); }

    public void exitSelectionMode() {
        adapter.setSelectionMode(false);
        notifySelectionChanged();
    }

    // ── Clipboard API ─────────────────────────────────────────────────────────

    public void copySelected() {
        clipboard      = adapter.getSelectedItems();
        clipboardIsCut = false;
        exitSelectionMode();
        Toast.makeText(getActivity(), clipboard.size() + " item(s) copied", Toast.LENGTH_SHORT).show();
    }

    public void cutSelected() {
        clipboard      = adapter.getSelectedItems();
        clipboardIsCut = true;
        exitSelectionMode();
        Toast.makeText(getActivity(), clipboard.size() + " item(s) cut", Toast.LENGTH_SHORT).show();
    }

    public void paste() {
        if (clipboard == null || clipboard.isEmpty()) return;

        OperationProgressDialog progressDialog = new OperationProgressDialog();
        progressDialog.show(getActivity().getFragmentManager(), "progress");

        FileOperation op = clipboardIsCut
                ? FileOperation.move(clipboard, currentPath, progressDialog)
                : FileOperation.copy(clipboard, currentPath, progressDialog);

        FileOperationQueue.getInstance().enqueue(op);
        clipboard = null;
    }

    public void deleteSelected() {
        List<FileItem> toDelete = adapter.getSelectedItems();
        if (toDelete.isEmpty()) return;

        OperationProgressDialog progressDialog = new OperationProgressDialog() {
            @Override
            public void onComplete() {
                super.onComplete();
                refresh();
            }
        };
        progressDialog.show(getActivity().getFragmentManager(), "progress");
        FileOperationQueue.getInstance().enqueue(
                FileOperation.delete(toDelete, progressDialog));
        exitSelectionMode();
    }

    public void renameSelected() {
        List<FileItem> sel = adapter.getSelectedItems();
        if (sel.size() != 1) return;
        RenameDialog.newInstance(sel.get(0).getName())
                    .show(getActivity().getFragmentManager(), "rename");
    }

    @Override
    public void onRenamed(String newName) {
        List<FileItem> sel = adapter.getSelectedItems();
        if (sel.isEmpty()) return;
        FileItem item = sel.get(0);
        FileOperationQueue.getInstance().enqueue(
                FileOperation.rename(item, newName, new FileOperation.ProgressCallback() {
                    @Override public void onProgress(long c, long t, String n) {}
                    @Override public void onComplete() { refresh(); }
                    @Override public void onError(String msg) {
                        Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
                    }
                }));
        exitSelectionMode();
    }

    public void toggleHiddenFiles() {
        prefs.setShowHiddenFiles(!prefs.isShowHiddenFiles());
        refresh();
    }

    // ── Keyboard focus helpers ─────────────────────────────────────────────────

    /** Returns the index of the currently focused list item, or -1. */
    public int getFocusedPosition() {
        return listView.getSelectedItemPosition();
    }

    public void moveFocusDown() {
        int pos = listView.getSelectedItemPosition();
        if (pos < adapter.getCount() - 1) listView.setSelection(pos + 1);
    }

    public void moveFocusUp() {
        int pos = listView.getSelectedItemPosition();
        if (pos > 0) listView.setSelection(pos - 1);
    }

    public void activateFocused() {
        int pos = listView.getSelectedItemPosition();
        if (pos >= 0) handleItemClick(pos);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void handleItemClick(int position) {
        if (adapter.isSelectionMode()) {
            adapter.toggleSelection(position);
            notifySelectionChanged();
            return;
        }
        FileItem item = adapter.getItem(position);
        if (item.isDirectory()) {
            navigate(item.getPath(), connectionId);
        } else {
            // File open — fire an Intent to the system
            openFile(item);
        }
    }

    private void enterSelectionMode(int firstPosition) {
        adapter.setSelectionMode(true);
        adapter.toggleSelection(firstPosition);
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        Host host = (Host) getActivity();
        if (host != null) host.onSelectionChanged(adapter.getSelectedCount());
    }

    private void showLoading(boolean show) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void openFile(FileItem item) {
        // Use Android's ACTION_VIEW Intent to hand off to a system viewer
        android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_VIEW);
        android.net.Uri uri = android.net.Uri.fromFile(new java.io.File(item.getPath()));
        String mime = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(item.getExtension());
        if (mime == null) mime = "*/*";
        intent.setDataAndType(uri, mime);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(getActivity(), "No app found to open this file",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static String parentOf(String path) {
        if (path == null || path.length() == 0) return "/";
        int slash = path.lastIndexOf('/');
        if (slash <= 0) return "/";
        return path.substring(0, slash);
    }
}
