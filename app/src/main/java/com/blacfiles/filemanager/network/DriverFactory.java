package com.blacfiles.filemanager.network;

import com.blacfiles.filemanager.model.RemoteConnection;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry that maps connection IDs → active {@link RemoteDriver} instances.
 *
 * Drivers are created on first use and cached for the session.
 * Call {@link #releaseDriver(String)} when a connection should be closed.
 */
public final class DriverFactory {

    private DriverFactory() {}

    /** Active drivers keyed by connection ID. */
    private static final Map<String, RemoteDriver> activeDrivers =
            new HashMap<String, RemoteDriver>();

    /**
     * Returns (or creates) a driver for the given connection.
     * The driver is NOT yet connected — callers must call {@link RemoteDriver#connect()}.
     */
    public static synchronized RemoteDriver getDriver(RemoteConnection conn) {
        RemoteDriver existing = activeDrivers.get(conn.getId());
        if (existing != null) return existing;

        RemoteDriver driver = createDriver(conn);
        activeDrivers.put(conn.getId(), driver);
        return driver;
    }

    /**
     * Looks up an already-created driver by connection ID.
     * Returns null if no driver exists for that ID.
     */
    public static synchronized RemoteDriver getActiveDriver(String connectionId) {
        return activeDrivers.get(connectionId);
    }

    /**
     * Looks up an active driver by protocol name (e.g. "SFTP").
     * Used by FileOperationQueue when it only knows the source FileItem's protocol.
     * Returns the first matching driver, or null.
     */
    public static synchronized RemoteDriver getActiveDriverByProtocol(String protocolName) {
        for (RemoteDriver drv : activeDrivers.values()) {
            // Match by class name convention: SftpDriver, FtpDriver, etc.
            String className = drv.getClass().getSimpleName().toUpperCase();
            if (className.startsWith(protocolName.toUpperCase())) {
                return drv;
            }
        }
        return null;
    }

    /** Disconnects and removes a driver from the registry. */
    public static synchronized void releaseDriver(String connectionId) {
        RemoteDriver drv = activeDrivers.remove(connectionId);
        if (drv != null) drv.disconnect();
    }

    /** Disconnects all active drivers. */
    public static synchronized void releaseAll() {
        for (RemoteDriver drv : activeDrivers.values()) {
            drv.disconnect();
        }
        activeDrivers.clear();
    }

    // ── Private factory ───────────────────────────────────────────────────────

    private static RemoteDriver createDriver(RemoteConnection conn) {
        switch (conn.getProtocol()) {
            case SFTP:    return new SftpDriver(conn);
            case SCP:     return new ScpDriver(conn);
            case FTP:
            case FTPS:    return new FtpDriver(conn);
            case WEBDAV:  return new WebDavDriver(conn);
            case SMB:     return new SmbDriver(conn);
            default:
                throw new IllegalArgumentException("Unsupported protocol: " + conn.getProtocol());
        }
    }
}
