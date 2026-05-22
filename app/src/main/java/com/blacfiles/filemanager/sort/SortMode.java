package com.blacfiles.filemanager.sort;

/**
 * All supported sort modes for the file list.
 */
public enum SortMode {
    NAME_ASC,
    NAME_DESC,
    DATE_ASC,
    DATE_DESC,
    SIZE_ASC,
    SIZE_DESC,
    TYPE        // Grouped by file extension, then alphabetically within each group
}
