package com.blacfiles.filemanager.ops;

import com.blacfiles.filemanager.model.FileItem;

import java.util.List;

/**
 * Immutable value object describing a pending file operation.
 *
 * Progress is reported back to the caller via the {@link ProgressCallback}
 * interface, which is always invoked on a background thread — callers must
 * marshal to the UI thread themselves (typically via a Handler).
 */
public class FileOperation {

    /** Callback delivered on the worker thread during long-running operations. */
    public interface ProgressCallback {
        /**
         * @param current  number of bytes (or items) processed so far
         * @param total    total bytes (or items) — may be 0 if unknown
         * @param itemName human-readable name of the item currently being processed
         */
        void onProgress(long current, long total, String itemName);

        /** Called when the operation completes successfully. */
        void onComplete();

        /**
         * Called on failure.  The operation stops at the first error.
         * @param message  human-readable error description
         */
        void onError(String message);
    }

    private final FileOperationType    type;
    private final List<FileItem>       sources;
    private final String               destinationPath; // null for DELETE
    private final String               newName;         // only for RENAME / NEW_FOLDER
    private final ProgressCallback     callback;

    private FileOperation(FileOperationType type,
                          List<FileItem> sources,
                          String destinationPath,
                          String newName,
                          ProgressCallback callback) {
        this.type            = type;
        this.sources         = sources;
        this.destinationPath = destinationPath;
        this.newName         = newName;
        this.callback        = callback;
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static FileOperation copy(List<FileItem> sources, String destPath,
                                     ProgressCallback cb) {
        return new FileOperation(FileOperationType.COPY, sources, destPath, null, cb);
    }

    public static FileOperation move(List<FileItem> sources, String destPath,
                                     ProgressCallback cb) {
        return new FileOperation(FileOperationType.MOVE, sources, destPath, null, cb);
    }

    public static FileOperation delete(List<FileItem> sources, ProgressCallback cb) {
        return new FileOperation(FileOperationType.DELETE, sources, null, null, cb);
    }

    public static FileOperation rename(FileItem source, String newName,
                                       ProgressCallback cb) {
        List<FileItem> list = new java.util.ArrayList<FileItem>();
        list.add(source);
        return new FileOperation(FileOperationType.RENAME, list, null, newName, cb);
    }

    public static FileOperation newFolder(String parentPath, String folderName,
                                          ProgressCallback cb) {
        return new FileOperation(FileOperationType.NEW_FOLDER,
                new java.util.ArrayList<FileItem>(), parentPath, folderName, cb);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FileOperationType  getType()            { return type; }
    public List<FileItem>     getSources()         { return sources; }
    public String             getDestinationPath() { return destinationPath; }
    public String             getNewName()         { return newName; }
    public ProgressCallback   getCallback()        { return callback; }
}
