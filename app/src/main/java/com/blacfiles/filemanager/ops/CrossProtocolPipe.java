package com.blacfiles.filemanager.ops;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Pipes bytes from any {@link InputStream} to any {@link OutputStream}.
 *
 * Used for cross-protocol transfers (e.g. SFTP → local, FTP → SMB) without
 * materialising the full file on disk as an intermediate step.
 * Both streams are closed by this method after the transfer completes.
 */
public final class CrossProtocolPipe {

    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB

    private CrossProtocolPipe() {}

    public interface ByteListener {
        /** Called periodically with the number of bytes transferred in the last chunk. */
        void onBytes(long bytes);
    }

    /**
     * Transfers all bytes from {@code in} to {@code out}, flushing and closing
     * both streams when done (or on error).
     *
     * @param in       source stream — will be closed
     * @param out      destination stream — will be closed
     * @param listener progress listener, may be null
     * @throws IOException if any read/write fails
     */
    public static void pipe(InputStream in, OutputStream out, ByteListener listener)
            throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        try {
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
                if (listener != null) {
                    listener.onBytes(read);
                }
            }
            out.flush();
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try { c.close(); } catch (IOException ignored) {}
        }
    }
}
