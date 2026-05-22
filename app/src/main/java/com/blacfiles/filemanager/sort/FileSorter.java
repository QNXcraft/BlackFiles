package com.blacfiles.filemanager.sort;

import com.blacfiles.filemanager.model.FileItem;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic sorting utility for {@link FileItem} lists.
 *
 * Rules applied universally:
 *  - Directories always sort before files (except for SIZE / TYPE modes where
 *    directories group at the top within their bucket).
 *  - All string comparisons are case-insensitive.
 *  - No lambdas or streams — Java 7 anonymous Comparator classes only.
 */
public final class FileSorter {

    private FileSorter() {}

    public static void sort(List<FileItem> items, SortMode mode) {
        switch (mode) {
            case NAME_ASC:  Collections.sort(items, NAME_ASC_CMP);  break;
            case NAME_DESC: Collections.sort(items, NAME_DESC_CMP); break;
            case DATE_ASC:  Collections.sort(items, DATE_ASC_CMP);  break;
            case DATE_DESC: Collections.sort(items, DATE_DESC_CMP); break;
            case SIZE_ASC:  Collections.sort(items, SIZE_ASC_CMP);  break;
            case SIZE_DESC: Collections.sort(items, SIZE_DESC_CMP); break;
            case TYPE:      Collections.sort(items, TYPE_CMP);      break;
        }
    }

    // ── Comparators ───────────────────────────────────────────────────────────

    /** Directories first; then A→Z by name. */
    private static final Comparator<FileItem> NAME_ASC_CMP = new Comparator<FileItem>() {
        @Override
        public int compare(FileItem a, FileItem b) {
            int dirCmp = dirFirst(a, b);
            if (dirCmp != 0) return dirCmp;
            return a.getName().compareToIgnoreCase(b.getName());
        }
    };

    private static final Comparator<FileItem> NAME_DESC_CMP = new Comparator<FileItem>() {
        @Override
        public int compare(FileItem a, FileItem b) {
            int dirCmp = dirFirst(a, b);
            if (dirCmp != 0) return dirCmp;
            return b.getName().compareToIgnoreCase(a.getName());
        }
    };

    /** Directories first; then oldest → newest. */
    private static final Comparator<FileItem> DATE_ASC_CMP = new Comparator<FileItem>() {
        @Override
        public int compare(FileItem a, FileItem b) {
            int dirCmp = dirFirst(a, b);
            if (dirCmp != 0) return dirCmp;
            return Long.valueOf(a.getLastModified()).compareTo(b.getLastModified());
        }
    };

    private static final Comparator<FileItem> DATE_DESC_CMP = new Comparator<FileItem>() {
        @Override
        public int compare(FileItem a, FileItem b) {
            int dirCmp = dirFirst(a, b);
            if (dirCmp != 0) return dirCmp;
            return Long.valueOf(b.getLastModified()).compareTo(a.getLastModified());
        }
    };

    /** Directories (size=0) first; then smallest → largest. */
    private static final Comparator<FileItem> SIZE_ASC_CMP = new Comparator<FileItem>() {
        @Override
        public int compare(FileItem a, FileItem b) {
            int dirCmp = dirFirst(a, b);
            if (dirCmp != 0) return dirCmp;
            return Long.valueOf(a.getSize()).compareTo(b.getSize());
        }
    };

    private static final Comparator<FileItem> SIZE_DESC_CMP = new Comparator<FileItem>() {
        @Override
        public int compare(FileItem a, FileItem b) {
            int dirCmp = dirFirst(a, b);
            if (dirCmp != 0) return dirCmp;
            return Long.valueOf(b.getSize()).compareTo(a.getSize());
        }
    };

    /**
     * Directories first; then grouped by extension A→Z;
     * within each extension group, sorted A→Z by name.
     */
    private static final Comparator<FileItem> TYPE_CMP = new Comparator<FileItem>() {
        @Override
        public int compare(FileItem a, FileItem b) {
            int dirCmp = dirFirst(a, b);
            if (dirCmp != 0) return dirCmp;
            int extCmp = a.getExtension().compareTo(b.getExtension());
            if (extCmp != 0) return extCmp;
            return a.getName().compareToIgnoreCase(b.getName());
        }
    };

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Returns negative if a is a directory and b is not; positive for the reverse; 0 if both same. */
    private static int dirFirst(FileItem a, FileItem b) {
        if (a.isDirectory() && !b.isDirectory()) return -1;
        if (!a.isDirectory() && b.isDirectory()) return  1;
        return 0;
    }
}
