package com.blacfiles.filemanager.network;

import com.blacfiles.filemanager.model.FileItem;
import com.blacfiles.filemanager.model.RemoteConnection;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * FTP / FTPS driver using Apache Commons Net 3.6.
 *
 * Pass {@link RemoteConnection.Protocol#FTPS} to enable implicit TLS.
 * Passive mode is used by default to work across NAT/firewall environments
 * which are common on BB10 devices connecting over Wi-Fi.
 */
public class FtpDriver implements RemoteDriver {

    private static final int CONNECT_TIMEOUT_MS = 15_000;

    private final RemoteConnection config;
    private final SessionCache     cache;
    private final boolean          useTls;

    private FTPClient client;

    public FtpDriver(RemoteConnection config) {
        this.config = config;
        this.cache  = new SessionCache();
        this.useTls = config.getProtocol() == RemoteConnection.Protocol.FTPS;
    }

    // ── RemoteDriver ─────────────────────────────────────────────────────────

    @Override
    public synchronized void connect() throws IOException {
        client = useTls ? new FTPSClient(true) : new FTPClient();
        client.setConnectTimeout(CONNECT_TIMEOUT_MS);
        client.connect(config.getHost(), config.getPort());

        int reply = client.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            client.disconnect();
            throw new IOException("FTP server refused connection: " + reply);
        }

        if (!client.login(config.getUsername(), config.getPassword())) {
            throw new IOException("FTP login failed");
        }

        client.setFileType(FTP.BINARY_FILE_TYPE);
        client.enterLocalPassiveMode();
    }

    @Override
    public synchronized void disconnect() {
        if (client != null && client.isConnected()) {
            try {
                client.logout();
                client.disconnect();
            } catch (IOException ignored) {}
        }
        client = null;
        cache.clear();
    }

    @Override
    public synchronized boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public synchronized List<FileItem> listFiles(String remotePath) throws IOException {
        String cacheKey = config.getHost() + ":" + config.getPort() + ":" + remotePath;
        List<FileItem> cached = cache.get(cacheKey);
        if (cached != null) return cached;

        ensureConnected();
        FTPFile[] files = client.listFiles(remotePath);
        if (files == null) {
            throw new IOException("FTP list failed for: " + remotePath);
        }

        List<FileItem> result = new ArrayList<FileItem>();
        for (FTPFile f : files) {
            if (f == null) continue;
            String name = f.getName();
            if (".".equals(name) || "..".equals(name)) continue;
            boolean isDir = f.isDirectory();
            String fullPath = remotePath.endsWith("/")
                    ? remotePath + name
                    : remotePath + "/" + name;
            long mtime = f.getTimestamp() != null
                    ? f.getTimestamp().getTimeInMillis() : 0L;

            FileItem.Protocol proto = useTls ? FileItem.Protocol.FTPS : FileItem.Protocol.FTP;
            result.add(new FileItem(name, fullPath, isDir ? 0L : f.getSize(),
                    mtime, isDir, name.startsWith("."), proto));
        }
        cache.put(cacheKey, result);
        return result;
    }

    @Override
    public synchronized InputStream openInputStream(String remotePath) throws IOException {
        ensureConnected();
        InputStream in = client.retrieveFileStream(remotePath);
        if (in == null) {
            throw new IOException("FTP retrieve failed: " + client.getReplyString());
        }
        return in;
    }

    @Override
    public synchronized OutputStream openOutputStream(String remotePath) throws IOException {
        ensureConnected();
        OutputStream out = client.storeFileStream(remotePath);
        if (out == null) {
            throw new IOException("FTP store failed: " + client.getReplyString());
        }
        cache.invalidate(parentOf(remotePath));
        return out;
    }

    @Override
    public synchronized void deleteFile(String remotePath) throws IOException {
        ensureConnected();
        // Try file first; fall back to directory removal
        if (!client.deleteFile(remotePath)) {
            if (!client.removeDirectory(remotePath)) {
                throw new IOException("FTP delete failed: " + client.getReplyString());
            }
        }
        cache.invalidate(parentOf(remotePath));
    }

    @Override
    public synchronized void rename(String fromPath, String toPath) throws IOException {
        ensureConnected();
        if (!client.rename(fromPath, toPath)) {
            throw new IOException("FTP rename failed: " + client.getReplyString());
        }
        cache.invalidate(parentOf(fromPath));
    }

    @Override
    public synchronized void mkdir(String remotePath) throws IOException {
        ensureConnected();
        if (!client.makeDirectory(remotePath)) {
            throw new IOException("FTP mkdir failed: " + client.getReplyString());
        }
        cache.invalidate(parentOf(remotePath));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensureConnected() throws IOException {
        if (!isConnected()) connect();
    }

    private static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return (slash <= 0) ? "/" : path.substring(0, slash);
    }
}
