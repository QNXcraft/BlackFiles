package com.blacfiles.filemanager.network;

import com.blacfiles.filemanager.model.FileItem;
import com.blacfiles.filemanager.model.RemoteConnection;

import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * SMB / CIFS driver using jcifs-ng 2.1.6.
 *
 * jcifs-ng 2.1.6 targets Java 7 and does not require SMB2 support from the
 * server, falling back gracefully to SMB1 for maximum BB10 compatibility.
 *
 * URL format:  smb://host/share/path
 */
public class SmbDriver implements RemoteDriver {

    private final RemoteConnection config;
    private final SessionCache     cache;

    private CIFSContext cifsContext;
    private boolean     connected;

    public SmbDriver(RemoteConnection config) {
        this.config = config;
        this.cache  = new SessionCache();
    }

    // ── RemoteDriver ─────────────────────────────────────────────────────────

    @Override
    public synchronized void connect() throws IOException {
        try {
            Properties props = new Properties();
            props.setProperty("jcifs.smb.client.dfs.disabled", "true");
            props.setProperty("jcifs.smb.client.responseTimeout", "15000");
            PropertyConfiguration pc = new PropertyConfiguration(props);
            NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(
                    "",                      // domain
                    config.getUsername(),
                    config.getPassword()
            );
            cifsContext = new BaseContext(pc).withCredentials(auth);
            // Verify by listing the initial path
            SmbFile root = new SmbFile(toSmbUrl(config.getInitialPath()), cifsContext);
            root.list();
            connected = true;
        } catch (Exception e) {
            throw new IOException("SMB connect failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void disconnect() {
        cifsContext = null;
        connected   = false;
        cache.clear();
    }

    @Override
    public synchronized boolean isConnected() {
        return connected && cifsContext != null;
    }

    @Override
    public synchronized List<FileItem> listFiles(String remotePath) throws IOException {
        String cacheKey = config.getHost() + ":" + config.getPort() + ":" + remotePath;
        List<FileItem> cached = cache.get(cacheKey);
        if (cached != null) return cached;

        ensureConnected();
        try {
            SmbFile dir = new SmbFile(toSmbUrl(remotePath), cifsContext);
            SmbFile[] files = dir.listFiles();
            List<FileItem> result = new ArrayList<FileItem>();
            if (files != null) {
                for (SmbFile f : files) {
                    String name   = f.getName().replaceAll("/$", "");
                    boolean isDir = f.isDirectory();
                    long    size  = isDir ? 0L : f.length();
                    long    mtime = f.lastModified();
                    String  path  = remotePath.endsWith("/")
                            ? remotePath + name
                            : remotePath + "/" + name;
                    result.add(new FileItem(name, path, size, mtime,
                            isDir, name.startsWith("."), FileItem.Protocol.SMB));
                }
            }
            cache.put(cacheKey, result);
            return result;
        } catch (Exception e) {
            throw new IOException("SMB list failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized InputStream openInputStream(String remotePath) throws IOException {
        ensureConnected();
        try {
            SmbFile file = new SmbFile(toSmbUrl(remotePath), cifsContext);
            return file.getInputStream();
        } catch (Exception e) {
            throw new IOException("SMB read failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized OutputStream openOutputStream(String remotePath) throws IOException {
        ensureConnected();
        try {
            SmbFile file = new SmbFile(toSmbUrl(remotePath), cifsContext);
            cache.invalidate(parentOf(remotePath));
            return file.getOutputStream();
        } catch (Exception e) {
            throw new IOException("SMB write failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void deleteFile(String remotePath) throws IOException {
        ensureConnected();
        try {
            SmbFile file = new SmbFile(toSmbUrl(remotePath), cifsContext);
            file.delete();
            cache.invalidate(parentOf(remotePath));
        } catch (Exception e) {
            throw new IOException("SMB delete failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void rename(String fromPath, String toPath) throws IOException {
        ensureConnected();
        try {
            SmbFile src  = new SmbFile(toSmbUrl(fromPath), cifsContext);
            SmbFile dest = new SmbFile(toSmbUrl(toPath),   cifsContext);
            src.renameTo(dest);
            cache.invalidate(parentOf(fromPath));
        } catch (Exception e) {
            throw new IOException("SMB rename failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void mkdir(String remotePath) throws IOException {
        ensureConnected();
        try {
            SmbFile dir = new SmbFile(toSmbUrl(remotePath) + "/", cifsContext);
            dir.mkdirs();
            cache.invalidate(parentOf(remotePath));
        } catch (Exception e) {
            throw new IOException("SMB mkdir failed: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensureConnected() throws IOException {
        if (!isConnected()) connect();
    }

    private String toSmbUrl(String path) {
        if (path == null || path.length() == 0) path = "/";
        if (!path.startsWith("/")) path = "/" + path;
        return "smb://" + config.getHost() + path;
    }

    private static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return (slash <= 0) ? "/" : path.substring(0, slash);
    }
}
