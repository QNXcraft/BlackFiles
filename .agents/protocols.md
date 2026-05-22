# .agents/protocols.md
## Remote Protocol Implementation Details

### Connection Timeouts
All drivers use a 15 000 ms (15 s) connect timeout.
Set `session.setTimeout(15_000)` for JSch and `client.setConnectTimeout(15_000)` for Commons Net.

---

### SFTP / SCP (JSch 0.1.55)
- Always set `StrictHostKeyChecking = no` (acceptable for a device-local app).
- Use `ChannelSftp.ls(path)` for directory listing — returns `Vector<LsEntry>`.
- `SftpATTRS.getMTime()` returns **seconds** since epoch — multiply by 1000 for millis.
- Private key support: call `jsch.addIdentity(path)` before `session.connect()`.
- BB10 quirk: some older BB10 firmware rejects AES-256-CBC; if handshake fails, add:
  `props.put("cipher.s2c", "aes128-cbc,3des-cbc");`

### FTP / FTPS (Apache Commons Net 3.6)
- Always call `enterLocalPassiveMode()` after login — BB10 Wi-Fi NAT requires it.
- Always set `FTP.BINARY_FILE_TYPE` after login.
- FTPS: use `new FTPSClient(true)` for implicit TLS (port 990).
- BB10 quirk: some FTPS servers require explicit fallback — use `new FTPSClient("TLS")` for explicit mode (port 21 + `AUTH TLS`).
- After `storeFileStream()` or `retrieveFileStream()`, always call `client.completePendingCommand()` after closing the stream.

### WebDAV (sardine-android 0.8)
- `sardine-android` uses `HttpURLConnection` — no OkHttp dependency, safe on API 18.
- `Sardine.list(url)` → `List<DavResource>` — first entry is the collection itself (skip it).
- `DavResource.getContentLength()` may return `null` for directories.
- HTTPS: provide `https://` URL; `HttpURLConnection` on API 18 supports TLS 1.0/1.1.

### SMB (jcifs-ng 2.1.6)
- Set `jcifs.smb.client.dfs.disabled=true` to avoid DFS lookup failures.
- Set `jcifs.smb.client.responseTimeout=15000`.
- `SmbFile.list()` returns names only; construct full `SmbFile` objects for metadata.
- `SmbFile.getName()` for directories includes a trailing `/` — strip it with `replaceAll("/$", "")`.
- SMB1 fallback is automatic in jcifs-ng 2.1.x; no special config needed.
- BB10 quirk: NTLM v2 may fail on some BB10 firmware builds. If auth fails, try setting:
  `jcifs.smb.client.useExtendedSecurity=false`

---

### SessionCache
- Key format: `"host:port:path"`.
- Cache is invalidated on any mutating operation (delete, rename, mkdir, upload).
- Max 64 entries (LRU eviction).
- Cache is cleared on `disconnect()`.
