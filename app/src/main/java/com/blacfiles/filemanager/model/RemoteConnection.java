package com.blacfiles.filemanager.model;

/**
 * Persistent connection profile for a remote server.
 * Serialised to/from JSON by AppPreferences.
 */
public class RemoteConnection {

    public enum Protocol {
        SFTP, SCP, FTP, FTPS, WEBDAV, SMB
    }

    private String   id;            // UUID assigned at creation time
    private String   displayName;   // User-friendly label shown in drawer
    private Protocol protocol;
    private String   host;
    private int      port;
    private String   username;
    private String   password;      // Stored as plain text in SharedPreferences
    private String   initialPath;   // Remote path to open on connect, e.g. "/"
    private String   privateKeyPath;// Optional: local path to SSH private key (SFTP/SCP)

    public RemoteConnection() {}

    public RemoteConnection(String id, String displayName, Protocol protocol,
                            String host, int port,
                            String username, String password,
                            String initialPath, String privateKeyPath) {
        this.id             = id;
        this.displayName    = displayName;
        this.protocol       = protocol;
        this.host           = host;
        this.port           = port;
        this.username       = username;
        this.password       = password;
        this.initialPath    = initialPath;
        this.privateKeyPath = privateKeyPath;
    }

    /** Default port for each protocol. */
    public static int defaultPort(Protocol p) {
        switch (p) {
            case SFTP:    return 22;
            case SCP:     return 22;
            case FTP:     return 21;
            case FTPS:    return 990;
            case WEBDAV:  return 80;
            case SMB:     return 445;
            default:      return 0;
        }
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public String   getId()             { return id; }
    public void     setId(String v)     { id = v; }

    public String   getDisplayName()          { return displayName; }
    public void     setDisplayName(String v)  { displayName = v; }

    public Protocol getProtocol()             { return protocol; }
    public void     setProtocol(Protocol v)   { protocol = v; }

    public String   getHost()           { return host; }
    public void     setHost(String v)   { host = v; }

    public int      getPort()           { return port; }
    public void     setPort(int v)      { port = v; }

    public String   getUsername()           { return username; }
    public void     setUsername(String v)   { username = v; }

    public String   getPassword()           { return password; }
    public void     setPassword(String v)   { password = v; }

    public String   getInitialPath()            { return initialPath; }
    public void     setInitialPath(String v)    { initialPath = v; }

    public String   getPrivateKeyPath()             { return privateKeyPath; }
    public void     setPrivateKeyPath(String v)     { privateKeyPath = v; }

    @Override
    public String toString() {
        return "RemoteConnection{" + protocol + "://" + username + "@" + host + ":" + port + initialPath + "}";
    }
}
