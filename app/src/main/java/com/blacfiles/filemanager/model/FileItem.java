package com.blacfiles.filemanager.model;

/**
 * Unified model representing a file or directory entry, whether local or remote.
 * Kept as a plain Java 7 value object — no Parcelable (not needed across processes).
 */
public class FileItem {

    /** Protocols supported as the source of a FileItem. */
    public enum Protocol {
        LOCAL, SFTP, SCP, FTP, FTPS, WEBDAV, SMB
    }

    private final String name;
    private final String path;          // Full absolute path or remote URI path
    private final long   size;          // Byte count; 0 for directories
    private final long   lastModified;  // Epoch millis
    private final boolean directory;
    private final boolean hidden;       // true if name starts with '.'
    private final Protocol protocol;

    public FileItem(String name, String path, long size, long lastModified,
                    boolean directory, boolean hidden, Protocol protocol) {
        this.name         = name;
        this.path         = path;
        this.size         = size;
        this.lastModified = lastModified;
        this.directory    = directory;
        this.hidden       = hidden;
        this.protocol     = protocol;
    }

    /** Convenience factory for local java.io.File entries. */
    public static FileItem fromLocalFile(java.io.File file) {
        return new FileItem(
                file.getName(),
                file.getAbsolutePath(),
                file.isDirectory() ? 0L : file.length(),
                file.lastModified(),
                file.isDirectory(),
                file.getName().startsWith("."),
                Protocol.LOCAL
        );
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public String getName()         { return name; }
    public String getPath()         { return path; }
    public long   getSize()         { return size; }
    public long   getLastModified() { return lastModified; }
    public boolean isDirectory()    { return directory; }
    public boolean isHidden()       { return hidden; }
    public Protocol getProtocol()   { return protocol; }

    /** Returns the file extension in lower-case, or "" for directories / no extension. */
    public String getExtension() {
        if (directory) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase();
    }

    @Override
    public String toString() {
        return "FileItem{name='" + name + "', path='" + path + "', dir=" + directory + "}";
    }
}
