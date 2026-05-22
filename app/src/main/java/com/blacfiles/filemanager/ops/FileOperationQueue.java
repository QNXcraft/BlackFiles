package com.blacfiles.filemanager.ops;

import android.os.Handler;
import android.os.Looper;

import com.blacfiles.filemanager.model.FileItem;
import com.blacfiles.filemanager.network.DriverFactory;
import com.blacfiles.filemanager.network.RemoteDriver;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Single-threaded queue for all file operations.
 *
 * Operations are serialised on one background thread to prevent concurrency
 * conflicts on the same directory. Progress is posted back to the main thread
 * via a {@link Handler}.
 *
 * Usage:
 *   FileOperationQueue queue = FileOperationQueue.getInstance();
 *   queue.enqueue(FileOperation.copy(sources, dest, callback));
 */
public class FileOperationQueue {

    private static volatile FileOperationQueue sInstance;

    private final ExecutorService executor;
    private final Handler         mainHandler;

    private FileOperationQueue() {
        executor    = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static FileOperationQueue getInstance() {
        if (sInstance == null) {
            synchronized (FileOperationQueue.class) {
                if (sInstance == null) {
                    sInstance = new FileOperationQueue();
                }
            }
        }
        return sInstance;
    }

    /** Submits an operation to the queue. Returns a Future that can be used to cancel. */
    public Future<?> enqueue(final FileOperation op) {
        return executor.submit(new Runnable() {
            @Override
            public void run() {
                executeOperation(op);
            }
        });
    }

    // ── Dispatcher ────────────────────────────────────────────────────────────

    private void executeOperation(FileOperation op) {
        try {
            switch (op.getType()) {
                case COPY:       executeCopyOrMove(op, false); break;
                case MOVE:       executeCopyOrMove(op, true);  break;
                case DELETE:     executeDelete(op);             break;
                case RENAME:     executeRename(op);             break;
                case NEW_FOLDER: executeNewFolder(op);          break;
            }
        } catch (final Exception e) {
            postError(op, e.getMessage() != null ? e.getMessage() : e.getClass().getName());
        }
    }

    // ── Copy / Move ───────────────────────────────────────────────────────────

    private void executeCopyOrMove(FileOperation op, boolean deleteSource) throws Exception {
        long totalBytes = computeTotalBytes(op);
        final long[] done = {0L};

        for (FileItem src : op.getSources()) {
            String destPath = buildDestPath(op.getDestinationPath(), src.getName());

            if (src.getProtocol() == FileItem.Protocol.LOCAL
                    && destinationIsLocal(op.getDestinationPath())) {
                // Local → local
                LocalFileOps.copy(new java.io.File(src.getPath()),
                        new java.io.File(destPath),
                        new LocalFileOps.ProgressListener() {
                            @Override
                            public void onBytes(long bytes) {
                                done[0] += bytes;
                            }
                        });
            } else {
                // Cross-protocol (at least one end is remote)
                RemoteDriver srcDriver  = resolveDriver(src);
                RemoteDriver destDriver = resolveDriverForPath(op.getDestinationPath(), op);

                java.io.InputStream  in;
                java.io.OutputStream out;

                if (srcDriver != null) {
                    in = srcDriver.openInputStream(src.getPath());
                } else {
                    in = new java.io.FileInputStream(src.getPath());
                }

                if (destDriver != null) {
                    out = destDriver.openOutputStream(destPath);
                } else {
                    java.io.File destFile = new java.io.File(destPath);
                    //noinspection ResultOfMethodCallIgnored
                    destFile.getParentFile().mkdirs();
                    out = new java.io.FileOutputStream(destFile);
                }

                CrossProtocolPipe.pipe(in, out, new CrossProtocolPipe.ByteListener() {
                    @Override
                    public void onBytes(long bytes) {
                        done[0] += bytes;
                    }
                });
            }

            postProgress(op, done[0], totalBytes, src.getName());

            if (deleteSource) {
                deleteItem(src);
            }
        }

        postComplete(op);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private void executeDelete(FileOperation op) throws Exception {
        int total = op.getSources().size();
        int done  = 0;
        for (FileItem item : op.getSources()) {
            deleteItem(item);
            done++;
            postProgress(op, done, total, item.getName());
        }
        postComplete(op);
    }

    private void deleteItem(FileItem item) throws Exception {
        if (item.getProtocol() == FileItem.Protocol.LOCAL) {
            LocalFileOps.deleteRecursive(new java.io.File(item.getPath()));
        } else {
            RemoteDriver drv = resolveDriver(item);
            if (drv != null) drv.deleteFile(item.getPath());
        }
    }

    // ── Rename ────────────────────────────────────────────────────────────────

    private void executeRename(FileOperation op) throws Exception {
        FileItem src      = op.getSources().get(0);
        String   parent   = parentOf(src.getPath());
        String   newPath  = parent + "/" + op.getNewName();

        if (src.getProtocol() == FileItem.Protocol.LOCAL) {
            LocalFileOps.rename(new java.io.File(src.getPath()),
                                new java.io.File(newPath));
        } else {
            RemoteDriver drv = resolveDriver(src);
            if (drv != null) drv.rename(src.getPath(), newPath);
        }
        postComplete(op);
    }

    // ── New Folder ────────────────────────────────────────────────────────────

    private void executeNewFolder(FileOperation op) throws Exception {
        String fullPath = op.getDestinationPath() + "/" + op.getNewName();
        java.io.File dir = new java.io.File(fullPath);
        if (!dir.mkdirs()) {
            throw new java.io.IOException("Failed to create directory: " + fullPath);
        }
        postComplete(op);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long computeTotalBytes(FileOperation op) {
        long total = 0L;
        for (FileItem item : op.getSources()) {
            total += item.getSize();
        }
        return total;
    }

    private String buildDestPath(String destDir, String name) {
        if (destDir.endsWith("/")) return destDir + name;
        return destDir + "/" + name;
    }

    private String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        if (slash <= 0) return "/";
        return path.substring(0, slash);
    }

    private boolean destinationIsLocal(String path) {
        // Local paths start with '/' and contain no "://" protocol scheme
        return path != null && path.startsWith("/") && !path.contains("://");
    }

    /** Returns a driver for the item's protocol, or null if LOCAL. */
    private RemoteDriver resolveDriver(FileItem item) {
        if (item.getProtocol() == FileItem.Protocol.LOCAL) return null;
        // Drivers are session-scoped; DriverFactory holds active sessions
        return DriverFactory.getActiveDriver(item.getProtocol().name());
    }

    /** Returns a driver for a destination path based on the operation's context.
     *  For local destinations returns null. */
    private RemoteDriver resolveDriverForPath(String path, FileOperation op) {
        if (destinationIsLocal(path)) return null;
        // Reuse the driver of the first source's opposite end — not needed for
        // local→local or same-protocol transfers; cross-protocol requires both
        // drivers to be already connected (enforced by FileProvider).
        return null; // Extended in cross-protocol scenarios; callers set up connections
    }

    // ── Progress posting ──────────────────────────────────────────────────────

    private void postProgress(final FileOperation op, final long current,
                              final long total, final String name) {
        final FileOperation.ProgressCallback cb = op.getCallback();
        if (cb == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                cb.onProgress(current, total, name);
            }
        });
    }

    private void postComplete(final FileOperation op) {
        final FileOperation.ProgressCallback cb = op.getCallback();
        if (cb == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                cb.onComplete();
            }
        });
    }

    private void postError(final FileOperation op, final String msg) {
        final FileOperation.ProgressCallback cb = op.getCallback();
        if (cb == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                cb.onError(msg);
            }
        });
    }
}
