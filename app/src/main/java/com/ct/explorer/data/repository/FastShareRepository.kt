package com.ct.explorer.data.repository

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.ct.explorer.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class FastShareState(
    val isServerRunning: Boolean = false,
    val hostIp: String = "127.0.0.1",
    val port: Int = 7777,
    val sharingFiles: List<FileItem> = emptyList(),
    val transferCount: Int = 0,
    val lastDownloadedFileName: String? = null
) {
    val shareUrl: String get() = "http://$hostIp:$port"
}

class FastShareRepository(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var sharedFilesList = listOf<FileItem>()
    private var clientExecutor: ExecutorService? = null
    private var serverThread: Thread? = null

    fun getLocalIpAddress(): String {
        try {
            val interfaces = java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
            // Prioritize Wi-Fi and Hotspot interfaces: wlan, ap, softap, p2p, eth
            val prioritizedNames = listOf("wlan", "ap", "softap", "p2p", "eth")
            val sortedInterfaces = interfaces.sortedByDescending { iface ->
                val name = iface.name.lowercase()
                prioritizedNames.any { name.contains(it) }
            }

            for (intf in sortedInterfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotEmpty() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return "192.168.43.1" // standard Android hotspot IP fallback
    }

    suspend fun startShareServer(
        files: List<FileItem>,
        port: Int = 7777,
        onFileDownloaded: (String) -> Unit
    ): Result<Pair<String, Int>> = withContext(Dispatchers.IO) {
        if (isRunning) stopShareServer()
        sharedFilesList = files

        try {
            val portsToTry = listOf(port, 7777, 7778, 7779, 8888, 8080, 0)
            var sSocket: ServerSocket? = null
            var actualPort = port
            for (p in portsToTry) {
                try {
                    sSocket = ServerSocket(p)
                    actualPort = sSocket.localPort
                    break
                } catch (e: Exception) {
                    // try next port
                }
            }

            if (sSocket == null) {
                return@withContext Result.failure(Exception("Could not bind to any network port"))
            }

            serverSocket = sSocket
            isRunning = true
            clientExecutor = Executors.newFixedThreadPool(6)
            val ip = getLocalIpAddress()

            serverThread = Thread {
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        client.soTimeout = 15000 // 15s timeout
                        val exec = clientExecutor
                        if (exec != null && !exec.isShutdown) {
                            exec.submit {
                                handleClient(client, onFileDownloaded)
                            }
                        } else {
                            try { client.close() } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }.apply {
                name = "FastShareServerThread"
                isDaemon = true
                start()
            }

            Result.success(Pair("http://$ip:$actualPort", actualPort))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun handleClient(socket: Socket, onFileDownloaded: (String) -> Unit) {
        try {
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = BufferedOutputStream(socket.getOutputStream())

            val requestLine = input.readLine() ?: run {
                socket.close()
                return
            }
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }

            val path = parts[1]

            if (path.startsWith("/download")) {
                val query = path.substringAfter("?", "")
                val fileNameEncoded = query.substringAfter("file=", "").substringBefore("&")
                val fileName = try {
                    URLDecoder.decode(fileNameEncoded, "UTF-8")
                } catch (e: Exception) {
                    fileNameEncoded
                }

                val target = sharedFilesList.find {
                    it.name == fileName || it.name.equals(fileName, ignoreCase = true) || it.name == fileNameEncoded
                }

                if (target != null && target.file.exists()) {
                    onFileDownloaded(target.name)
                    val length = target.file.length()
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/octet-stream\r\n" +
                            "Content-Length: $length\r\n" +
                            "Content-Disposition: attachment; filename=\"${URLEncoder.encode(target.name, "UTF-8")}\"\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray())

                    FileInputStream(target.file).use { fis ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        while (fis.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                        }
                    }
                    output.flush()
                } else {
                    val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 9\r\n\r\nNot Found"
                    output.write(notFound.toByteArray())
                    output.flush()
                }
            } else {
                // Serve HTML Web Interface
                val html = buildHtmlPage(sharedFilesList)
                val bytes = html.toByteArray(Charsets.UTF_8)
                val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Connection: close\r\n\r\n"
                output.write(header.toByteArray())
                output.write(bytes)
                output.flush()
            }

            socket.close()
        } catch (e: Exception) {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }


    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    private fun buildHtmlPage(files: List<FileItem>): String {
        val rows = StringBuilder()
        for (f in files) {
            val encodedName = URLEncoder.encode(f.name, "UTF-8")
            val safeName = escapeHtml(f.name)
            rows.append(
                """
                <div class="file-card">
                    <div class="file-info">
                        <span class="file-name">$safeName</span>
                        <span class="file-size">${f.formattedSize}</span>
                    </div>
                    <a class="dl-btn" href="/download?file=$encodedName" download>Download</a>
                </div>
                """.trimIndent()
            )
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Cent Share — Direct P2P Transfer</title>
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 24px; }
                .container { max-width: 600px; margin: 0 auto; }
                h1 { color: #ff6900; margin-bottom: 8px; font-size: 24px; }
                p { color: #94a3b8; font-size: 14px; margin-top: 0; }
                .file-card { background: #1e293b; padding: 14px 16px; border-radius: 12px; margin-bottom: 12px; display: flex; align-items: center; justify-content: space-between; border: 1px solid #334155; }
                .file-info { display: flex; flex-direction: column; overflow: hidden; margin-right: 12px; }
                .file-name { font-weight: 600; font-size: 15px; word-break: break-all; }
                .file-size { color: #94a3b8; font-size: 12px; margin-top: 4px; }
                .dl-btn { background: #ff6900; color: white; text-decoration: none; padding: 8px 16px; border-radius: 8px; font-size: 13px; font-weight: bold; white-space: nowrap; }
                .dl-btn:hover { background: #e05d00; }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>⚡ Cent Fast Share</h1>
                <p>High-speed offline transfer from Cent File Manager</p>
                <div>
                    ${if (files.isEmpty()) "<p>No files selected for transfer.</p>" else rows.toString()}
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    fun stopShareServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        try {
            serverThread?.interrupt()
        } catch (_: Exception) {}
        serverThread = null
        try {
            clientExecutor?.shutdownNow()
        } catch (_: Exception) {}
        clientExecutor = null
    }
}
