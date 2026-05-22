package com.blacfiles.filemanager.ui;

import android.os.Handler;
import android.os.Looper;

import com.blacfiles.filemanager.model.FileItem;
import com.blacfiles.filemanager.network.DriverFactory;
import com.blacfiles.filemanager.network.RemoteDriver;
import com.blacfiles.filemanager.prefs.AppPreferences;
import com.blacfiles.filemanager.sort.FileSorter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Middle-man between the UI and the file-system / network drivers.
 *
 * Listing is always performed on a single background thread and results
 * are posted back to the main thread via a Handler.
 *
 * Hidden-file filtering and sorting are applied before delivering results.
 */
public class FileProvider {

    public interface Callback {
        void onResult(List<FileItem> items);
        void onError(String message);
    }

    private final AppPreferences    prefs;
    private final ExecutorService   executor = Executors.newSingleThreadExecutor();
    private final Handler           mainHandler = new Handler(Looper.getMainLooper());

    public FileProvider(AppPreferences prefs) {
        this.prefs = prefs;
    }

    /**
     * Lists the contents of a local directory path asynchronously.
     */
    public void listLocal(final String path, final Callback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    File dir = new File(path);
                    File[] files = dir.listFiles();
                    List<FileItem> items = new ArrayList<FileItem>();
                    if (files != null) {
                        for (File f : files) {
                            items.add(FileItem.fromLocalFile(f));
                        }
                    }
                    deliverResult(items, callback);
                } catch (final Exception e) {
                    postError(e.getMessage(), callback);
                }
            }
        });
    }

    /**
     * Lists the contents of a remote path using the driver registered for connectionId.
     * The driver must already be connected.
     */
    public void listRemote(final String connectionId, final String remotePath,
                           final Callback callback) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    RemoteDriver driver = DriverFactory.getActiveDriver(connectionId);
                    if (driver == null) {
                        postError("No active connection: " + connectionId, callback);
                        return;
                    }
                    List<FileItem> items = driver.listFiles(remotePath);
                    deliverResult(items, callback);
                } catch (final Exception e) {
                    postError(e.getMessage(), callback);
                }
            }
        });
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void deliverResult(List<FileItem> items, final Callback callback) {
        // Filter hidden files if needed
        boolean showHidden = prefs.isShowHiddenFiles();
        List<FileItem> filtered = items;
        if (!showHidden) {
            filtered = new ArrayList<FileItem>();
            for (FileItem item : items) {
                if (!item.isHidden()) filtered.add(item);
            }
        }

        // Sort
        FileSorter.sort(filtered, prefs.getSortMode());

        final List<FileItem> result = filtered;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onResult(result);
            }
        });
    }

    private void postError(final String msg, final Callback callback) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(msg != null ? msg : "Unknown error");
            }
        });
    }
}
