package com.ct.explorer.utils.webshare

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Environment
import android.text.format.Formatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class WebShareState(
    val isRunning: Boolean = false,
    val ipAddress: String = "",
    val port: Int = 8080,
    val clientCount: Int = 0,
    val lastActivity: String = "Server stopped",
    val sessionToken: String = ""
) {
    val serverUrl: String get() = if (ipAddress.isNotEmpty()) {
        if (sessionToken.isNotEmpty()) "http://$ipAddress:$port?token=$sessionToken" else "http://$ipAddress:$port"
    } else ""
}

/**
 * Lightweight, high-speed embedded HTTP Server for wireless browser-based file management.
 * Any device on the same Wi-Fi (PC, Mac, iPhone, iPad, Linux, Smart TV) can open the server URL
 * in Chrome/Safari/Firefox to browse, download, and upload files without installing any app.
 */
class WebShareServer(private val context: Context, private val port: Int = 8080) {

    private var activePort: Int = port
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val threadPool = Executors.newCachedThreadPool()
    private val rootDir = Environment.getExternalStorageDirectory()
    private var sessionToken: String = ""

    var onStateChanged: ((WebShareState) -> Unit)? = null
    private var connectedClients = 0

    fun isRunning(): Boolean = isRunning.get()

    private fun generateSessionToken(): String {
        val chars = "abcdefghjkmnpqrstuvwxyz23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        val rnd = java.security.SecureRandom()
        return (1..8).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }

    fun getLocalIpAddress(): String {
        try {
            // First check Wi-Fi Manager
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIp = wifiManager?.connectionInfo?.ipAddress
            if (wifiIp != null && wifiIp != 0) {
                @Suppress("DEPRECATION")
                return Formatter.formatIpAddress(wifiIp)
            }

            // Fallback to active non-loopback network interface
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.isSiteLocalAddress && addr.hostAddress != null) {
                        val ip = addr.hostAddress!!
                        if (!ip.contains(":")) { // IPv4
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return "127.0.0.1"
    }

    fun start(): Boolean {
        if (isRunning.get()) return true

        return try {
            var ss: ServerSocket? = null
            var boundPort = port
            for (p in port..(port + 5)) {
                try {
                    ss = ServerSocket(p)
                    boundPort = p
                    break
                } catch (_: java.net.BindException) {
                    continue
                }
            }
            if (ss == null) {
                ss = ServerSocket(port)
            }
            serverSocket = ss
            activePort = boundPort
            isRunning.set(true)
            connectedClients = 0
            sessionToken = generateSessionToken()

            notifyState("Server running on port $activePort")

            threadPool.execute {
                while (isRunning.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        client.soTimeout = 15000 // 15s socket timeout
                        connectedClients++
                        notifyState("Device connected from ${client.inetAddress.hostAddress}")

                        threadPool.execute {
                            try {
                                handleClient(client)
                            } catch (e: Exception) {
                                // Client connection terminated
                            } finally {
                                try {
                                    client.close()
                                } catch (e: Exception) {}
                                connectedClients = (connectedClients - 1).coerceAtLeast(0)
                            }
                        }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            }
            true
        } catch (e: Exception) {
            isRunning.set(false)
            notifyState("Failed to start server: ${e.message}")
            false
        }
    }

    fun stop() {
        if (!isRunning.get()) return
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        connectedClients = 0
        sessionToken = ""
        notifyState("Server stopped")
    }

    private fun notifyState(activity: String) {
        val state = WebShareState(
            isRunning = isRunning.get(),
            ipAddress = getLocalIpAddress(),
            port = activePort,
            clientCount = connectedClients,
            lastActivity = activity,
            sessionToken = sessionToken
        )
        onStateChanged?.invoke(state)
    }

    private fun handleClient(socket: Socket) {
        val rawInput = socket.getInputStream()
        val output = BufferedOutputStream(socket.getOutputStream())

        // Read HTTP request line and headers safely without buffering the binary body
        val headerBytes = ByteArrayOutputStream()
        var consecutiveNewlines = 0
        var b: Int
        while (rawInput.read().also { b = it } != -1) {
            headerBytes.write(b)
            if (b == '\n'.code) {
                consecutiveNewlines++
                val bytes = headerBytes.toByteArray()
                val len = bytes.size
                if (len >= 4 && bytes[len - 4] == '\r'.code.toByte() && bytes[len - 3] == '\n'.code.toByte() &&
                    bytes[len - 2] == '\r'.code.toByte() && bytes[len - 1] == '\n'.code.toByte()) {
                    break
                }
                if (len >= 2 && bytes[len - 2] == '\n'.code.toByte() && bytes[len - 1] == '\n'.code.toByte()) {
                    break
                }
            }
            if (headerBytes.size() > 64 * 1024) break // Header size limit guard
        }

        val headerText = headerBytes.toString("UTF-8")
        val lines = headerText.lines()
        if (lines.isEmpty()) return

        val requestLine = lines[0].trim()
        val parts = requestLine.split(" ")
        if (parts.size < 2) return

        val method = parts[0]
        val rawUri = parts[1]

        val urlPath = if (rawUri.contains("?")) rawUri.substringBefore("?") else rawUri
        val query = if (rawUri.contains("?")) rawUri.substringAfter("?") else ""

        val queryParams = parseQuery(query)

        // Parse headers from the already-read header text
        var contentLength = 0L
        var contentType = ""
        val headersMap = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val lower = line.lowercase()
            val colonIdx = line.indexOf(":")
            if (colonIdx > 0) {
                headersMap[line.substring(0, colonIdx).trim().lowercase()] = line.substring(colonIdx + 1).trim()
            }
            if (lower.startsWith("content-length:")) {
                contentLength = line.substringAfter(":").trim().toLongOrNull() ?: 0L
            } else if (lower.startsWith("content-type:")) {
                contentType = line.substringAfter(":").trim()
            }
        }

        // Verify session authorization
        val isAuth = isAuthorized(queryParams, headersMap)

        if (!isAuth) {
            if (method == "GET" && urlPath == "/") {
                serveAuthPage(output)
            } else {
                sendResponse(output, 401, "application/json", "{\"status\":\"error\",\"message\":\"Unauthorized: Invalid or missing session token.\"}".toByteArray())
            }
            return
        }

        when {
            method == "GET" && urlPath == "/" -> {
                serveHtmlDashboard(output)
            }
            method == "GET" && urlPath == "/api/browse" -> {
                val targetPath = queryParams["path"]?.let { URLDecoder.decode(it, "UTF-8") } ?: rootDir.absolutePath
                serveDirectoryJson(targetPath, output)
            }
            method == "GET" && urlPath == "/api/download" -> {
                val targetPath = queryParams["path"]?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
                serveFileDownload(targetPath, output)
            }
            method == "POST" && urlPath == "/api/upload" -> {
                val rawDir = queryParams["dir"]?.let { URLDecoder.decode(it, "UTF-8") }
                val uploadDir = if (rawDir.isNullOrBlank()) rootDir.absolutePath else rawDir
                val fileName = queryParams["filename"]?.let { URLDecoder.decode(it, "UTF-8") } ?: "uploaded_${System.currentTimeMillis()}"
                handleFileUpload(rawInput, uploadDir, fileName, contentLength, output)
            }
            else -> {
                sendResponse(output, 404, "text/plain", "404 Not Found".toByteArray())
            }
        }
    }

    private fun isAuthorized(queryParams: Map<String, String>, headers: Map<String, String>): Boolean {
        if (sessionToken.isEmpty()) return true
        val paramToken = queryParams["token"]
        if (paramToken == sessionToken) return true

        val cookie = headers["cookie"] ?: ""
        if (cookie.contains("session_token=$sessionToken")) return true

        val auth = headers["authorization"] ?: headers["x-session-token"] ?: ""
        if (auth.removePrefix("Bearer ").trim() == sessionToken) return true

        return false
    }

    private fun serveAuthPage(out: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Cent Web Share — Authentication</title>
                <style>
                    body { font-family: system-ui, -apple-system, sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; padding: 16px; }
                    .card { background: #1e293b; padding: 32px; border-radius: 20px; max-width: 400px; width: 100%; border: 1px solid #334155; text-align: center; }
                    h2 { color: #FF6700; margin-top: 0; }
                    p { color: #94a3b8; font-size: 14px; margin-bottom: 24px; }
                    input { width: 100%; padding: 14px; border-radius: 10px; border: 1px solid #475569; background: #0f172a; color: white; font-size: 16px; text-align: center; letter-spacing: 2px; box-sizing: border-box; }
                    button { width: 100%; padding: 14px; border-radius: 10px; border: none; background: #FF6700; color: white; font-weight: bold; font-size: 16px; margin-top: 16px; cursor: pointer; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>🔒 Cent Web Share</h2>
                    <p>Enter the 8-character access token displayed on your Android device to unlock file access.</p>
                    <form method="GET" action="/">
                        <input type="text" name="token" placeholder="Security Token" required autocomplete="off" />
                        <button type="submit">Unlock Files</button>
                    </form>
                </div>
            </body>
            </html>
        """.trimIndent()
        sendResponse(out, 401, "text/html; charset=UTF-8", html.toByteArray(Charsets.UTF_8))
    }

    private fun parseQuery(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (query.isEmpty()) return map
        query.split("&").forEach { pair ->
            val idx = pair.indexOf("=")
            if (idx > 0) {
                map[pair.substring(0, idx)] = pair.substring(idx + 1)
            }
        }
        return map
    }

    private fun serveHtmlDashboard(out: OutputStream) {
        val ip = getLocalIpAddress()
        val totalBytes = rootDir.totalSpace
        val freeBytes = rootDir.freeSpace
        val usedBytes = totalBytes - freeBytes
        val usedPct = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Cent File Manager - Wireless Web Share</title>
                <style>
                    :root {
                        --primary: #FF6700;
                        --primary-bg: rgba(255, 103, 0, 0.1);
                        --bg: #121212;
                        --surface: #1E1E1E;
                        --surface-card: #282828;
                        --text: #F3F4F6;
                        --text-muted: #9CA3AF;
                        --border: #333333;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }
                    body { background: var(--bg); color: var(--text); padding: 20px; }
                    .header { display: flex; align-items: center; justify-content: space-between; padding-bottom: 20px; border-bottom: 1px solid var(--border); margin-bottom: 20px; }
                    .brand { display: flex; align-items: center; gap: 12px; }
                    .badge { background: var(--primary); color: white; padding: 4px 10px; border-radius: 8px; font-weight: bold; font-size: 14px; }
                    .stats { background: var(--surface); padding: 16px; border-radius: 14px; display: flex; align-items: center; gap: 20px; margin-bottom: 20px; }
                    .progress-bar { flex: 1; height: 8px; background: #333; border-radius: 4px; overflow: hidden; }
                    .progress-fill { height: 100%; width: ${usedPct}%; background: var(--primary); border-radius: 4px; }
                    .toolbar { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; justify-content: space-between; flex-wrap: wrap; }
                    .breadcrumbs { display: flex; gap: 6px; align-items: center; font-size: 14px; background: var(--surface); padding: 8px 14px; border-radius: 8px; overflow-x: auto; }
                    .breadcrumb-item { color: var(--primary); cursor: pointer; text-decoration: none; }
                    .breadcrumb-item:hover { text-decoration: underline; }
                    .upload-box { background: var(--surface); border: 2px dashed var(--primary); border-radius: 12px; padding: 14px; text-align: center; cursor: pointer; transition: 0.2s; }
                    .upload-box:hover { background: var(--primary-bg); }
                    .file-list { background: var(--surface); border-radius: 14px; overflow: hidden; }
                    .file-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid var(--border); transition: 0.15s; }
                    .file-row:hover { background: var(--surface-card); }
                    .file-info { display: flex; align-items: center; gap: 12px; cursor: pointer; flex: 1; }
                    .file-icon { font-size: 22px; width: 32px; text-align: center; }
                    .file-name { font-weight: 500; word-break: break-all; }
                    .file-meta { font-size: 12px; color: var(--text-muted); }
                    .btn-download { background: var(--primary); color: white; padding: 6px 14px; border-radius: 8px; text-decoration: none; font-size: 13px; font-weight: 600; border: none; cursor: pointer; }
                    .btn-download:hover { opacity: 0.9; }
                </style>
            </head>
            <body>
                <div class="header">
                    <div class="brand">
                        <span class="badge">Cent Web Share</span>
                        <h2>Wireless File Drop</h2>
                    </div>
                    <span style="font-size: 13px; color: var(--text-muted);">$ip:$port</span>
                </div>

                <div class="stats">
                    <span style="font-weight: bold; font-size: 14px;">Internal Storage</span>
                    <div class="progress-bar"><div class="progress-fill"></div></div>
                    <span style="font-size: 13px; color: var(--text-muted);">${usedPct}% used</span>
                </div>

                <div class="toolbar">
                    <div class="breadcrumbs" id="breadcrumbBar">
                        <span>Root: /</span>
                    </div>
                    <div class="upload-box" onclick="document.getElementById('fileInput').click()">
                        📤 <b>Upload File to Phone</b> (Click or Drag & Drop)
                        <input type="file" id="fileInput" style="display:none" onchange="uploadSelectedFile(this.files[0])">
                    </div>
                </div>

                <div class="file-list" id="fileListContainer">
                    <div style="padding: 30px; text-align: center; color: var(--text-muted);">Loading files...</div>
                </div>

                <script>
                    let currentPath = '';

                    function loadDirectory(path) {
                        fetch('/api/browse?path=' + encodeURIComponent(path))
                            .then(res => res.json())
                            .then(data => {
                                currentPath = data.currentPath;
                                renderBreadcrumbs(data.currentPath, data.breadcrumbs);
                                renderFiles(data.items);
                            })
                            .catch(err => {
                                document.getElementById('fileListContainer').innerHTML = '<div style="padding: 20px; color: #ef4444;">Error loading files: ' + err + '</div>';
                            });
                    }

                    function renderBreadcrumbs(path, crumbs) {
                        let html = '';
                        crumbs.forEach((c, idx) => {
                            if (idx > 0) html += ' <span>/</span> ';
                            html += '<span class="breadcrumb-item" onclick="loadDirectory(\'' + escapeJs(c.path) + '\')">' + escapeHtml(c.name) + '</span>';
                        });
                        document.getElementById('breadcrumbBar').innerHTML = html;
                    }

                    function renderFiles(items) {
                        if (!items || items.length === 0) {
                            document.getElementById('fileListContainer').innerHTML = '<div style="padding: 30px; text-align: center; color: var(--text-muted);">This folder is empty</div>';
                            return;
                        }

                        let html = '';
                        items.forEach(item => {
                            let icon = item.isDir ? '📁' : getFileIcon(item.name);
                            let clickAction = item.isDir 
                                ? 'onclick="loadDirectory(\'' + escapeJs(item.path) + '\')"'
                                : 'onclick="window.location.href=\'/api/download?path=' + encodeURIComponent(item.path) + '\'"';

                            html += '<div class="file-row">';
                            html += '  <div class="file-info" ' + clickAction + '>';
                            html += '    <span class="file-icon">' + icon + '</span>';
                            html += '    <div>';
                            html += '      <div class="file-name">' + escapeHtml(item.name) + '</div>';
                            html += '      <div class="file-meta">' + item.size + ' • ' + item.date + '</div>';
                            html += '    </div>';
                            html += '  </div>';
                            if (!item.isDir) {
                                html += '  <a class="btn-download" href="/api/download?path=' + encodeURIComponent(item.path) + '" download>Download</a>';
                            }
                            html += '</div>';
                        });
                        document.getElementById('fileListContainer').innerHTML = html;
                    }

                    function getFileIcon(name) {
                        let ext = name.split('.').pop().toLowerCase();
                        if (['jpg','jpeg','png','webp','gif'].includes(ext)) return '🖼️';
                        if (['mp4','mkv','webm','mov'].includes(ext)) return '🎬';
                        if (['mp3','wav','m4a','flac'].includes(ext)) return '🎵';
                        if (['pdf'].includes(ext)) return '📕';
                        if (['doc','docx'].includes(ext)) return '📄';
                        if (['xls','xlsx'].includes(ext)) return '📊';
                        if (['zip','rar','7z','tar','gz'].includes(ext)) return '📦';
                        if (['apk'].includes(ext)) return '🤖';
                        return '📄';
                    }

                    function uploadSelectedFile(file) {
                        if (!file) return;
                        let uploadUrl = '/api/upload?dir=' + encodeURIComponent(currentPath) + '&filename=' + encodeURIComponent(file.name);
                        let banner = document.getElementById('breadcrumbBar');
                        banner.innerHTML = '<span>⏳ Uploading ' + escapeHtml(file.name) + ' (' + (file.size / 1024).toFixed(1) + ' KB)...</span>';

                        fetch(uploadUrl, {
                            method: 'POST',
                            body: file
                        })
                        .then(res => res.text())
                        .then(() => {
                            loadDirectory(currentPath);
                        })
                        .catch(err => {
                            alert('Upload failed: ' + err);
                            loadDirectory(currentPath);
                        });
                    }

                    function escapeHtml(str) {
                        return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
                    }

                    function escapeJs(str) {
                        return str.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
                    }

                    // Initial load
                    loadDirectory('');
                </script>
            </body>
            </html>
        """.trimIndent()

        sendResponse(out, 200, "text/html; charset=UTF-8", html.toByteArray(Charsets.UTF_8), cookie = "session_token=$sessionToken; Path=/; SameSite=Lax")
    }

    private fun isWithinRoot(file: File, root: File): Boolean {
        val fPath = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
        val rPath = try { root.canonicalPath } catch (_: Exception) { root.absolutePath }
        return fPath == rPath || fPath.startsWith(rPath + File.separator)
    }

    private fun serveDirectoryJson(pathStr: String, out: OutputStream) {
        val rootCanonical = rootDir.canonicalFile
        val target = if (pathStr.isNotBlank()) File(pathStr).canonicalFile else rootCanonical
        val safeTarget = if (isWithinRoot(target, rootCanonical) && target.exists() && target.isDirectory) target else rootCanonical

        val items = safeTarget.listFiles()?.map { f ->
            val sizeStr = if (f.isDirectory) {
                "${f.list()?.size ?: 0} items"
            } else {
                formatBytes(f.length())
            }
            val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(f.lastModified())

            """{"name":${quoteJson(f.name)},"path":${quoteJson(f.absolutePath)},"isDir":${f.isDirectory},"size":${quoteJson(sizeStr)},"date":${quoteJson(dateStr)}}"""
        }?.sortedByDescending { it.contains("\"isDir\":true") } ?: emptyList()

        // Build breadcrumbs
        val crumbs = mutableListOf<String>()
        var curr: File? = safeTarget
        while (curr != null) {
            val name = if (curr.absolutePath == rootDir.absolutePath) "Storage" else curr.name
            crumbs.add(0, """{"name":${quoteJson(name)},"path":${quoteJson(curr.absolutePath)}}""")
            if (curr.absolutePath == rootDir.absolutePath || curr.parentFile == null) break
            curr = curr.parentFile
        }

        val json = """{"currentPath":${quoteJson(safeTarget.absolutePath)},"breadcrumbs":[${crumbs.joinToString(",")}],"items":[${items.joinToString(",")}]}"""
        sendResponse(out, 200, "application/json", json.toByteArray(Charsets.UTF_8))
    }

    private fun serveFileDownload(pathStr: String, out: OutputStream) {
        val rootCanonical = rootDir.canonicalFile
        val file = File(pathStr).canonicalFile
        if (!isWithinRoot(file, rootCanonical) || !file.exists() || !file.isFile || !file.canRead()) {
            sendResponse(out, 404, "text/plain", "File Not Found".toByteArray())
            return
        }

        val mimeType = getMimeType(file.name)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $mimeType\r\n" +
                "Content-Length: ${file.length()}\r\n" +
                "Content-Disposition: attachment; filename=\"${file.name}\"\r\n" +
                "Connection: close\r\n\r\n"

        out.write(header.toByteArray(Charsets.UTF_8))
        FileInputStream(file).use { fis ->
            val buf = ByteArray(64 * 1024)
            var n: Int
            while (fis.read(buf).also { n = it } != -1) {
                out.write(buf, 0, n)
            }
        }
        out.flush()
    }

    private fun handleFileUpload(
        socketInput: InputStream,
        targetDir: String,
        fileName: String,
        contentLength: Long,
        out: OutputStream
    ) {
        val rootCanonical = rootDir.canonicalFile
        val dir = if (targetDir.isBlank()) rootCanonical else File(targetDir).canonicalFile
        if (!isWithinRoot(dir, rootCanonical)) {
            sendResponse(out, 403, "text/plain", "Upload Forbidden outside shared storage".toByteArray())
            return
        }
        if (!dir.exists()) dir.mkdirs()

        val sanitized = File(fileName).name
            .replace("/", "")
            .replace("\\", "")
            .replace("\u0000", "")
            .trim()
        val safeFileName = if (sanitized.isEmpty() || sanitized == "." || sanitized == "..") {
            "uploaded_${System.currentTimeMillis()}"
        } else {
            sanitized
        }

        var destFile = File(dir, safeFileName).canonicalFile
        if (destFile.exists()) {
            val nameWithoutExt = safeFileName.substringBeforeLast(".")
            val ext = if (safeFileName.contains(".")) ".${safeFileName.substringAfterLast(".")}" else ""
            var counter = 1
            while (destFile.exists()) {
                destFile = File(dir, "$nameWithoutExt ($counter)$ext").canonicalFile
                counter++
            }
        }

        if (!isWithinRoot(destFile, rootCanonical)) {
            sendResponse(out, 403, "text/plain", "Invalid upload path".toByteArray())
            return
        }

        val tempFile = File(dir, ".tmp_upload_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}")
        var written = 0L
        var success = false

        try {
            FileOutputStream(tempFile).use { fos ->
                val buffer = ByteArray(32 * 1024)
                var bytesToRead = contentLength
                while (bytesToRead > 0) {
                    val toReadNow = Math.min(buffer.size.toLong(), bytesToRead).toInt()
                    val read = socketInput.read(buffer, 0, toReadNow)
                    if (read == -1) break
                    fos.write(buffer, 0, read)
                    written += read
                    bytesToRead -= read
                }
                fos.flush()
            }

            if (contentLength > 0 && written < contentLength) {
                tempFile.delete()
                sendResponse(out, 400, "text/plain", "Upload aborted or incomplete transfer".toByteArray())
                return
            }

            if (tempFile.renameTo(destFile)) {
                success = true
            } else {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
                success = true
            }
        } catch (e: Exception) {
            tempFile.delete()
            sendResponse(out, 500, "text/plain", "Upload failed: ${e.message}".toByteArray())
            return
        }

        if (success) {
            val response = """{"status":"success","savedAs":${quoteJson(destFile.name)},"bytes":$written}"""
            sendResponse(out, 200, "application/json", response.toByteArray(Charsets.UTF_8))
        } else {
            sendResponse(out, 500, "text/plain", "Failed to finalize uploaded file".toByteArray())
        }
    }

    private fun sendResponse(out: OutputStream, code: Int, contentType: String, data: ByteArray, cookie: String? = null) {
        val statusText = when (code) {
            200 -> "OK"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Error"
        }
        val cookieHeader = if (cookie != null) "Set-Cookie: $cookie\r\n" else ""
        val header = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${data.size}\r\n" +
                cookieHeader +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(data)
        out.flush()
    }

    private fun quoteJson(str: String): String {
        return "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\""
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 4)
        return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun getMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "txt" -> "text/plain"
            "html" -> "text/html"
            else -> "application/octet-stream"
        }
    }
}
