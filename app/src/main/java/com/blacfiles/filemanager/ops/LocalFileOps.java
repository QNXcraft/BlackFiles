package com.blacfiles.filemanager.ops;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Low-level local file system operations implemented with plain Java IO.
 * All methods are synchronous and must be called from a background thread.
 */
public final class LocalFileOps {

    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB

    private LocalFileOps() {}

    /** Listener receiving byte-count increments during copy. */
    public interface ProgressListener {
        void onBytes(long bytes);
    }

    // ── Copy ──────────────────────────────────────────────────────────────────

    /**
     * Recursively copies {@code src} to {@code dest}.
     * If src is a directory, dest will be created as a mirror directory tree.
     */
    public static void copy(File src, File dest, ProgressListener listener) throws IOException {
        if (src.isDirectory()) {
            copyDirectory(src, dest, listener);
        } else {
            copyFile(src, dest, listener);
        }
    }

    private static void copyFile(File src, File dest, ProgressListener listener) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        InputStream  in  = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dest);
        try {
            pipe(in, out, listener);
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private static void copyDirectory(File src, File dest, ProgressListener listener) throws IOException {
        if (!dest.exists() && !dest.mkdirs()) {
            throw new IOException("Cannot create directory: " + dest.getAbsolutePath());
        }
        String[] children = src.list();
        if (children != null) {
            for (String child : children) {
                copy(new File(src, child), new File(dest, child), listener);
            }
        }
    }

    // ── Move ──────────────────────────────────────────────────────────────────

    /**
     * Attempts a fast rename first; falls back to copy+delete across file systems.
     */
    public static void move(File src, File dest, ProgressListener listener) throws IOException {
        if (!src.renameTo(dest)) {
            copy(src, dest, listener);
            deleteRecursive(src);
        }
    }

    // ── Rename ────────────────────────────────────────────────────────────────

    public static void rename(File src, File dest) throws IOException {
        if (!src.renameTo(dest)) {
            throw new IOException("Rename failed: " + src + " → " + dest);
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /** Recursively deletes a file or directory tree. */
    public static void deleteRecursive(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("Failed to delete: " + file.getAbsolutePath());
        }
    }

    // ── Internal pipe ─────────────────────────────────────────────────────────

    private static void pipe(InputStream in, OutputStream out,
                              ProgressListener listener) throws IOException {
        byte[] buf   = new byte[BUFFER_SIZE];
        int    read;
        while ((read = in.read(buf)) != -1) {
            out.write(buf, 0, read);
            if (listener != null) {
                listener.onBytes(read);
            }
        }
        out.flush();
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try { c.close(); } catch (IOException ignored) {}
        }
    }
}
