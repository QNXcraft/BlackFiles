package com.blacfiles.filemanager.network;

import com.blacfiles.filemanager.model.FileItem;
import com.blacfiles.filemanager.model.RemoteConnection;
import com.thegrizzlylabs.sardineandroid.DavResource;
import com.thegrizzlylabs.sardineandroid.Sardine;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WebDAV protocol driver using sardine-android 0.8.
 *
 * sardine-android uses HttpURLConnection internally (no OkHttp dependency),
 * making it compatible with API 18.
 *
 * URLs are constructed as: http[s]://host:port/path
 */
public class WebDavDriver implements RemoteDriver {

    private final RemoteConnection config;
    private final SessionCache     cache;
    private final String           baseUrl;

    private Sardine sardine;
    private boolean connected;

    public WebDavDriver(RemoteConnection config) {
        this.config  = config;
        this.cache   = new SessionCache();
        // Determine scheme from port: 443 or 8443 → https, otherwise http
        String scheme = (config.getPort() == 443 || config.getPort() == 8443) ? "https" : "http";
        this.baseUrl  = scheme + "://" + config.getHost() + ":" + config.getPort();
    }

    // ── RemoteDriver ─────────────────────────────────────────────────────────

    @Override
    public synchronized void connect() throws IOException {
        sardine = new OkHttpSardine();
        sardine.setCredentials(config.getUsername(), config.getPassword());
        // Verify connectivity with a PROPFIND on the initial path
        String url = toUrl(config.getInitialPath());
        sardine.list(url);
        connected = true;
    }

    @Override
    public synchronized void disconnect() {
        sardine    = null;
        connected  = false;
        cache.clear();
    }

    @Override
    public synchronized boolean isConnected() {
        return connected && sardine != null;
    }

    @Override
    public synchronized List<FileItem> listFiles(String remotePath) throws IOException {
        String cacheKey = config.getHost() + ":" + config.getPort() + ":" + remotePath;
        List<FileItem> cached = cache.get(cacheKey);
        if (cached != null) return cached;

        ensureConnected();
        String url = toUrl(remotePath);
        List<DavResource> resources = sardine.list(url);

        List<FileItem> result = new ArrayList<FileItem>();
        boolean first = true;
        for (DavResource res : resources) {
            if (first) { first = false; continue; } // Skip the collection itself

            String name    = res.getName();
            boolean isDir  = res.isDirectory();
            long    size   = res.getContentLength() != null ? res.getContentLength() : 0L;
            long    mtime  = res.getModified() != null ? res.getModified().getTime() : 0L;
            String fullPath = remotePath.endsWith("/")
                    ? remotePath + name
                    : remotePath + "/" + name;

            result.add(new FileItem(name, fullPath, isDir ? 0L : size,
                    mtime, isDir, name.startsWith("."), FileItem.Protocol.WEBDAV));
        }
        cache.put(cacheKey, result);
        return result;
    }

    @Override
    public synchronized InputStream openInputStream(String remotePath) throws IOException {
        ensureConnected();
        return sardine.get(toUrl(remotePath));
    }

    @Override
    public synchronized OutputStream openOutputStream(String remotePath) throws IOException {
        ensureConnected();
        // sardine-android's put() accepts an InputStream; we buffer via a pipe
        final java.io.PipedInputStream  pin  = new java.io.PipedInputStream(65536);
        final java.io.PipedOutputStream pout = new java.io.PipedOutputStream(pin);
        final String url = toUrl(remotePath);

        // Sardine.put() blocks reading from pin; run on a separate thread
        Thread putThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sardine.put(url, pin, null);
                } catch (IOException ignored) {}
            }
        });
        putThread.setDaemon(true);
        putThread.start();

        cache.invalidate(parentOf(remotePath));
        return pout;
    }

    @Override
    public synchronized void deleteFile(String remotePath) throws IOException {
        ensureConnected();
        sardine.delete(toUrl(remotePath));
        cache.invalidate(parentOf(remotePath));
    }

    @Override
    public synchronized void rename(String fromPath, String toPath) throws IOException {
        ensureConnected();
        sardine.move(toUrl(fromPath), toUrl(toPath));
        cache.invalidate(parentOf(fromPath));
    }

    @Override
    public synchronized void mkdir(String remotePath) throws IOException {
        ensureConnected();
        sardine.createDirectory(toUrl(remotePath));
        cache.invalidate(parentOf(remotePath));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensureConnected() throws IOException {
        if (!isConnected()) connect();
    }

    private String toUrl(String path) {
        if (path == null || path.length() == 0) path = "/";
        if (!path.startsWith("/")) path = "/" + path;
        return baseUrl + path;
    }

    private static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return (slash <= 0) ? "/" : path.substring(0, slash);
    }
}
