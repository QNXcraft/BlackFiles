package com.blacfiles.filemanager.network;

import com.blacfiles.filemanager.model.FileItem;
import com.blacfiles.filemanager.model.RemoteConnection;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * SCP protocol driver backed by JSch.
 *
 * SCP does not have a native directory-listing command; this driver delegates
 * listing to a lightweight SSH "ls -la" exec channel and parses the output.
 * Download/upload use the standard SCP wire protocol.
 */
public class ScpDriver implements RemoteDriver {

    private static final int CONNECT_TIMEOUT_MS = 15_000;

    private final RemoteConnection config;
    private final SessionCache     cache;

    private Session session;

    public ScpDriver(RemoteConnection config) {
        this.config = config;
        this.cache  = new SessionCache();
    }

    // ── RemoteDriver ─────────────────────────────────────────────────────────

    @Override
    public synchronized void connect() throws IOException {
        try {
            JSch jsch = new JSch();
            if (config.getPrivateKeyPath() != null && config.getPrivateKeyPath().length() > 0) {
                jsch.addIdentity(config.getPrivateKeyPath());
            }
            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            if (config.getPassword() != null && config.getPassword().length() > 0) {
                session.setPassword(config.getPassword());
            }
            Properties props = new Properties();
            props.put("StrictHostKeyChecking", "no");
            session.setConfig(props);
            session.setTimeout(CONNECT_TIMEOUT_MS);
            session.connect(CONNECT_TIMEOUT_MS);
        } catch (JSchException e) {
            throw new IOException("SCP connect failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void disconnect() {
        if (session != null) {
            try { session.disconnect(); } catch (Exception ignored) {}
            session = null;
        }
        cache.clear();
    }

    @Override
    public synchronized boolean isConnected() {
        return session != null && session.isConnected();
    }

    /**
     * Lists a remote directory by executing "ls -la --time-style=+%s <path>" over SSH.
     * Parses GNU ls-style output lines.
     */
    @Override
    public synchronized List<FileItem> listFiles(String remotePath) throws IOException {
        String cacheKey = config.getHost() + ":" + config.getPort() + ":" + remotePath;
        List<FileItem> cached = cache.get(cacheKey);
        if (cached != null) return cached;

        ensureConnected();
        String cmd = "ls -la --time-style=+%s " + escapePath(remotePath);
        String output = execCommand(cmd);

        List<FileItem> result = new ArrayList<FileItem>();
        String[] lines = output.split("\n");
        for (String line : lines) {
            FileItem item = parseLsLine(line, remotePath);
            if (item != null) result.add(item);
        }
        cache.put(cacheKey, result);
        return result;
    }

    @Override
    public synchronized InputStream openInputStream(String remotePath) throws IOException {
        ensureConnected();
        try {
            // SCP from remote: "scp -f <remotePath>"
            String cmd = "scp -f " + escapePath(remotePath);
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(cmd);
            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();
            channel.connect(CONNECT_TIMEOUT_MS);

            // SCP protocol handshake
            sendByte(out, (byte) 0);
            int c = in.read();
            if (c != 'C') {
                throw new IOException("SCP: unexpected response byte: " + c);
            }
            // Read "Cperm size filename\n"
            String header = readLine(in);
            String[] parts = header.split(" ", 3);
            // parts[1] = size (ignored — we stream)
            sendByte(out, (byte) 0);
            return in;
        } catch (JSchException e) {
            throw new IOException("SCP open failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized OutputStream openOutputStream(String remotePath) throws IOException {
        ensureConnected();
        try {
            String dir  = parentOf(remotePath);
            String name = nameOf(remotePath);
            String cmd  = "scp -t " + escapePath(dir);
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(cmd);
            OutputStream out = channel.getOutputStream();
            InputStream  in  = channel.getInputStream();
            channel.connect(CONNECT_TIMEOUT_MS);
            checkAck(in);

            // Caller writes content; we wrap with an OutputStream that sends
            // the SCP header before the first byte and the final ACK after close.
            return new ScpOutputStream(out, in, name);
        } catch (JSchException e) {
            throw new IOException("SCP put failed: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void deleteFile(String remotePath) throws IOException {
        ensureConnected();
        execCommand("rm -rf " + escapePath(remotePath));
        cache.invalidate(parentOf(remotePath));
    }

    @Override
    public synchronized void rename(String fromPath, String toPath) throws IOException {
        ensureConnected();
        execCommand("mv " + escapePath(fromPath) + " " + escapePath(toPath));
        cache.invalidate(parentOf(fromPath));
    }

    @Override
    public synchronized void mkdir(String remotePath) throws IOException {
        ensureConnected();
        execCommand("mkdir -p " + escapePath(remotePath));
        cache.invalidate(parentOf(remotePath));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensureConnected() throws IOException {
        if (!isConnected()) connect();
    }

    private String execCommand(String cmd) throws IOException {
        try {
            ChannelExec ch = (ChannelExec) session.openChannel("exec");
            ch.setCommand(cmd);
            InputStream in = ch.getInputStream();
            ch.connect(CONNECT_TIMEOUT_MS);
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) != -1) {
                sb.append(new String(buf, 0, read, "UTF-8"));
            }
            ch.disconnect();
            return sb.toString();
        } catch (JSchException e) {
            throw new IOException("SSH exec failed: " + e.getMessage(), e);
        }
    }

    /** Parses a single GNU ls -la line into a FileItem, returns null for header/invalid lines. */
    private FileItem parseLsLine(String line, String parentPath) {
        if (line == null || line.length() < 10) return null;
        // GNU ls -la --time-style=+%s format:
        // permissions links owner group size epoch name
        // e.g.: -rw-r--r-- 1 user group 1234 1700000000 file.txt
        String[] parts = line.trim().split("\\s+", 7);
        if (parts.length < 7) return null;
        String perms = parts[0];
        if (perms.startsWith("total")) return null;
        boolean isDir  = perms.charAt(0) == 'd';
        long    size   = 0L;
        long    mtime  = 0L;
        try {
            size  = Long.parseLong(parts[4]);
            mtime = Long.parseLong(parts[5]) * 1000L;
        } catch (NumberFormatException ignored) { return null; }

        String name = parts[6].trim();
        if (".".equals(name) || "..".equals(name)) return null;

        String fullPath = parentPath.endsWith("/")
                ? parentPath + name
                : parentPath + "/" + name;

        return new FileItem(name, fullPath, isDir ? 0L : size, mtime,
                isDir, name.startsWith("."), FileItem.Protocol.SCP);
    }

    private static String escapePath(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }

    private static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return (slash <= 0) ? "/" : path.substring(0, slash);
    }

    private static String nameOf(String path) {
        int slash = path.lastIndexOf('/');
        return (slash < 0) ? path : path.substring(slash + 1);
    }

    private static void sendByte(OutputStream out, byte b) throws IOException {
        out.write(b);
        out.flush();
    }

    private static int checkAck(InputStream in) throws IOException {
        int b = in.read();
        if (b == 1 || b == 2) {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = in.read()) != '\n') sb.append((char) c);
            throw new IOException("SCP ACK error: " + sb.toString());
        }
        return b;
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != '\n' && c != -1) sb.append((char) c);
        return sb.toString();
    }

    // ── ScpOutputStream ───────────────────────────────────────────────────────

    /**
     * Wraps the SCP upload channel OutputStream.
     * Sends the SCP header on first write and the final null-byte ACK on close.
     */
    private static class ScpOutputStream extends OutputStream {
        private final OutputStream out;
        private final InputStream  ackIn;
        private final String       name;
        private final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();

        ScpOutputStream(OutputStream out, InputStream ackIn, String name) {
            this.out   = out;
            this.ackIn = ackIn;
            this.name  = name;
        }

        @Override
        public void write(int b) throws IOException {
            buffer.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            buffer.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            byte[] data   = buffer.toByteArray();
            String header = "C0644 " + data.length + " " + name + "\n";
            out.write(header.getBytes("UTF-8"));
            out.flush();
            checkAck(ackIn);
            out.write(data);
            out.write(0);
            out.flush();
            checkAck(ackIn);
            out.close();
        }
    }
}
