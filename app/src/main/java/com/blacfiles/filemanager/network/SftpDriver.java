package com.blacfiles.filemanager.network;

import com.blacfiles.filemanager.model.FileItem;
import com.blacfiles.filemanager.model.RemoteConnection;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

/**
 * SFTP protocol driver backed by JSch 0.1.55.
 *
 * A single JSch Session and ChannelSftp are reused for the lifetime of the
 * connection; re-opened automatically if the channel is closed unexpectedly.
 */
public class SftpDriver implements RemoteDriver {

    private static final int CONNECT_TIMEOUT_MS = 15_000;

    private final RemoteConnection config;
    private final SessionCache     cache;

    private Session     session;
    private ChannelSftp channel;

    public SftpDriver(RemoteConnection config) {
        this.config = config;
        this.cache  = new SessionCache();
    }

    // ── RemoteDriver ─────────────────────────────────────────────────────────

    @Override
    public synchronized void connect() throws IOException {
        try {
            JSch jsch = new JSch();

            // Optional: load private key
            if (config.getPrivateKeyPath() != null && config.getPrivateKeyPath().length() > 0) {
                jsch.addIdentity(config.getPrivateKeyPath());
            }

            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());

            if (config.getPassword() != null && config.getPassword().length() > 0) {
                session.setPassword(config.getPassword());
            }

            // Disable strict host-key checking (acceptable for a local-device app)
            Properties props = new Properties();
            props.put("StrictHostKeyChecking", "no");
            session.setConfig(props);
            session.setTimeout(CONNECT_TIMEOUT_MS);
            session.connect(CONNECT_TIMEOUT_MS);

            Channel ch = session.openChannel("sftp");
            ch.connect(CONNECT_TIMEOUT_MS);
            channel = (ChannelSftp) ch;
        } catch (JSchException e) {
            throw new IOException("SFTP connect failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void disconnect() {
        if (channel != null) {
            try { channel.disconnect(); } catch (Exception ignored) {}
            channel = null;
        }
        if (session != null) {
            try { session.disconnect(); } catch (Exception ignored) {}
            session = null;
        }
        cache.clear();
    }

    @Override
    public synchronized boolean isConnected() {
        return session != null && session.isConnected()
                && channel != null && channel.isConnected();
    }

    @Override
    public synchronized List<FileItem> listFiles(String remotePath) throws IOException {
        String cacheKey = cacheKey(remotePath);
        List<FileItem> cached = cache.get(cacheKey);
        if (cached != null) return cached;

        ensureConnected();
        try {
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = channel.ls(remotePath);
            List<FileItem> result = new ArrayList<FileItem>();
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (".".equals(name) || "..".equals(name)) continue;
                SftpATTRS attrs = entry.getAttrs();
                String fullPath = remotePath.endsWith("/")
                        ? remotePath + name
                        : remotePath + "/" + name;
                result.add(new FileItem(
                        name,
                        fullPath,
                        attrs.isDir() ? 0L : attrs.getSize(),
                        (long) attrs.getMTime() * 1000L,
                        attrs.isDir(),
                        name.startsWith("."),
                        FileItem.Protocol.SFTP
                ));
            }
            cache.put(cacheKey, result);
            return result;
        } catch (SftpException e) {
            throw new IOException("SFTP ls failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized InputStream openInputStream(String remotePath) throws IOException {
        ensureConnected();
        try {
            return channel.get(remotePath);
        } catch (SftpException e) {
            throw new IOException("SFTP get failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized OutputStream openOutputStream(String remotePath) throws IOException {
        ensureConnected();
        try {
            return channel.put(remotePath);
        } catch (SftpException e) {
            throw new IOException("SFTP put failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void deleteFile(String remotePath) throws IOException {
        ensureConnected();
        try {
            SftpATTRS attrs = channel.stat(remotePath);
            if (attrs.isDir()) {
                channel.rmdir(remotePath);
            } else {
                channel.rm(remotePath);
            }
            cache.invalidate(parentOf(remotePath));
        } catch (SftpException e) {
            throw new IOException("SFTP delete failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void rename(String fromPath, String toPath) throws IOException {
        ensureConnected();
        try {
            channel.rename(fromPath, toPath);
            cache.invalidate(parentOf(fromPath));
        } catch (SftpException e) {
            throw new IOException("SFTP rename failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void mkdir(String remotePath) throws IOException {
        ensureConnected();
        try {
            channel.mkdir(remotePath);
            cache.invalidate(parentOf(remotePath));
        } catch (SftpException e) {
            throw new IOException("SFTP mkdir failed: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            connect();
        }
    }

    private String cacheKey(String path) {
        return config.getHost() + ":" + config.getPort() + ":" + path;
    }

    private static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        if (slash <= 0) return "/";
        return path.substring(0, slash);
    }
}
