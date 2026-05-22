package com.blacfiles.filemanager.network;

import com.blacfiles.filemanager.model.FileItem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Common interface for all remote protocol drivers.
 *
 * All methods are synchronous and must be called from a background thread.
 * Implementations must be thread-safe with respect to a single Session —
 * the FileOperationQueue ensures only one operation runs at a time per driver.
 */
public interface RemoteDriver {

    /**
     * Establishes the connection using the provided credentials.
     * @throws IOException on network or authentication failure
     */
    void connect() throws IOException;

    /** Closes the connection, releasing all resources. Safe to call even if not connected. */
    void disconnect();

    /** Returns true if the connection is currently active. */
    boolean isConnected();

    /**
     * Lists the contents of a remote directory.
     * @param remotePath absolute remote path, e.g. "/home/user/docs"
     * @return ordered list of {@link FileItem} entries; never null
     * @throws IOException on error
     */
    List<FileItem> listFiles(String remotePath) throws IOException;

    /**
     * Opens a readable stream for a remote file.
     * Caller is responsible for closing the stream.
     */
    InputStream openInputStream(String remotePath) throws IOException;

    /**
     * Opens a writable stream for a remote file, creating it if needed.
     * Caller is responsible for closing the stream.
     */
    OutputStream openOutputStream(String remotePath) throws IOException;

    /**
     * Deletes the file or directory at the given remote path.
     * Directories must be empty before deletion (callers handle recursive delete).
     */
    void deleteFile(String remotePath) throws IOException;

    /**
     * Renames or moves a remote file/directory.
     * @param fromPath source remote path
     * @param toPath   destination remote path
     */
    void rename(String fromPath, String toPath) throws IOException;

    /**
     * Creates a directory (and any missing intermediate directories) at the given path.
     */
    void mkdir(String remotePath) throws IOException;
}
