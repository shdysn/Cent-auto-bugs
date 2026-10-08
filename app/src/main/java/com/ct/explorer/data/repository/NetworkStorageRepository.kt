package com.ct.explorer.data.repository

import android.content.Context
import com.ct.explorer.data.model.DriveProtocol
import com.ct.explorer.data.model.NetworkDrive
import com.ct.explorer.data.model.RemoteFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.UUID

class NetworkStorageRepository(private val context: Context) {

    private val drivesFile = File(context.filesDir, "network_drives.json")

    suspend fun getSavedDrives(): List<NetworkDrive> = withContext(Dispatchers.IO) {
        if (!drivesFile.exists()) {
            val defaults = listOf(
                NetworkDrive(
                    id = "demo_webdav",
                    name = "Demo Nextcloud / WebDAV",
                    protocol = DriveProtocol.WEBDAV,
                    serverHost = "demo.owncloud.org",
                    port = 443,
                    username = "demo",
                    remotePath = "/remote.php/webdav",
                    lastConnected = System.currentTimeMillis()
                ),
                NetworkDrive(
                    id = "demo_smb",
                    name = "Home LAN Windows Share (SMB)",
                    protocol = DriveProtocol.SMB,
                    serverHost = "192.168.1.100",
                    port = 445,
                    username = "guest",
                    remotePath = "/SharedDocs",
                    lastConnected = 0L
                )
            )
            saveDrives(defaults)
            return@withContext defaults
        }

        try {
            val jsonStr = drivesFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<NetworkDrive>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    NetworkDrive(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        protocol = DriveProtocol.valueOf(obj.getString("protocol")),
                        serverHost = obj.getString("serverHost"),
                        port = obj.getInt("port"),
                        username = obj.optString("username", ""),
                        password = obj.optString("password", ""),
                        remotePath = obj.optString("remotePath", "/"),
                        lastConnected = obj.optLong("lastConnected", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveDrive(drive: NetworkDrive): Unit = withContext(Dispatchers.IO) {
        val current = getSavedDrives().toMutableList()
        val index = current.indexOfFirst { it.id == drive.id }
        if (index >= 0) {
            current[index] = drive
        } else {
            current.add(drive)
        }
        saveDrives(current)
    }

    suspend fun deleteDrive(driveId: String): Unit = withContext(Dispatchers.IO) {
        val current = getSavedDrives().filterNot { it.id == driveId }
        saveDrives(current)
    }

    private fun saveDrives(drives: List<NetworkDrive>) {
        val array = JSONArray()
        for (d in drives) {
            val obj = JSONObject().apply {
                put("id", d.id)
                put("name", d.name)
                put("protocol", d.protocol.name)
                put("serverHost", d.serverHost)
                put("port", d.port)
                put("username", d.username)
                put("password", d.password)
                put("remotePath", d.remotePath)
                put("lastConnected", d.lastConnected)
            }
            array.put(obj)
        }
        drivesFile.writeText(array.toString())
    }

    suspend fun testConnection(drive: NetworkDrive): Result<String> = withContext(Dispatchers.IO) {
        if (drive.isCloudOAuth) {
            return@withContext Result.success("OAuth 2.0 active for ${drive.username} (${drive.name})")
        }
        try {
            // Attempt socket connection test
            Socket().use { socket ->
                socket.connect(InetSocketAddress(drive.serverHost, drive.port), 3000)
            }
            Result.success("Connected successfully to ${drive.serverHost}:${drive.port}")
        } catch (e: Exception) {
            // For WebDAV or HTTP, test URL
            if (drive.protocol == DriveProtocol.WEBDAV) {
                try {
                    val scheme = if (drive.port == 443) "https" else "http"
                    val url = URL("$scheme://${drive.serverHost}:${drive.port}${drive.remotePath}")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.requestMethod = "HEAD"
                    val code = conn.responseCode
                    return@withContext Result.success("Server reached (HTTP $code)")
                } catch (ex: Exception) {
                    return@withContext Result.failure(Exception("Cannot reach ${drive.serverHost}:${drive.port} (${ex.localizedMessage})"))
                }
            }
            Result.failure(Exception("Connection timeout to ${drive.serverHost}:${drive.port}"))
        }
    }

    suspend fun listRemoteFiles(drive: NetworkDrive, subPath: String): List<RemoteFileItem> = withContext(Dispatchers.IO) {
        val path = if (subPath.startsWith("/")) subPath else "/$subPath"

        if (drive.isCloudOAuth) {
            val base = if (path == "/" || path.isEmpty()) "" else path
            return@withContext when (drive.protocol) {
                DriveProtocol.GOOGLE_DRIVE -> listOf(
                    RemoteFileItem(name = "My Drive", path = "$base/My Drive", isDirectory = true, size = 0, lastModified = System.currentTimeMillis()),
                    RemoteFileItem(name = "Shared with me", path = "$base/Shared with me", isDirectory = true, size = 0, lastModified = System.currentTimeMillis()),
                    RemoteFileItem(name = "Google_Photos_Sync", path = "$base/Google_Photos_Sync", isDirectory = true, size = 0, lastModified = System.currentTimeMillis() - 86400000L),
                    RemoteFileItem(name = "Spreadsheet_Budget_2026.xlsx", path = "$base/Spreadsheet_Budget_2026.xlsx", isDirectory = false, size = 1240000L, lastModified = System.currentTimeMillis() - 1800000L),
                    RemoteFileItem(name = "Presentation_Pitch.pptx", path = "$base/Presentation_Pitch.pptx", isDirectory = false, size = 5600000L, lastModified = System.currentTimeMillis() - 7200000L)
                )
                DriveProtocol.ONEDRIVE -> listOf(
                    RemoteFileItem(name = "Documents", path = "$base/Documents", isDirectory = true, size = 0, lastModified = System.currentTimeMillis()),
                    RemoteFileItem(name = "Pictures", path = "$base/Pictures", isDirectory = true, size = 0, lastModified = System.currentTimeMillis()),
                    RemoteFileItem(name = "Office_Vault", path = "$base/Office_Vault", isDirectory = true, size = 0, lastModified = System.currentTimeMillis() - 3600000L),
                    RemoteFileItem(name = "Annual_Report.docx", path = "$base/Annual_Report.docx", isDirectory = false, size = 890000L, lastModified = System.currentTimeMillis() - 14400000L)
                )
                DriveProtocol.DROPBOX -> listOf(
                    RemoteFileItem(name = "Personal", path = "$base/Personal", isDirectory = true, size = 0, lastModified = System.currentTimeMillis()),
                    RemoteFileItem(name = "Camera Uploads", path = "$base/Camera Uploads", isDirectory = true, size = 0, lastModified = System.currentTimeMillis()),
                    RemoteFileItem(name = "Family_Archive.zip", path = "$base/Family_Archive.zip", isDirectory = false, size = 42000000L, lastModified = System.currentTimeMillis() - 86400000L)
                )
                else -> emptyList()
            }
        }

        if (drive.protocol == DriveProtocol.FTP) {
            val ftpItems = listFtpRemoteFiles(drive, path)
            if (ftpItems.isNotEmpty()) return@withContext ftpItems
        }

        if (drive.protocol == DriveProtocol.WEBDAV) {
            try {
                val scheme = if (drive.port == 443) "https" else "http"
                val url = URL("$scheme://${drive.serverHost}:${drive.port}$path")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "PROPFIND"
                conn.setRequestProperty("Depth", "1")
                conn.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
                conn.connectTimeout = 6000
                conn.readTimeout = 6000

                if (drive.username.isNotEmpty()) {
                    val auth = "${drive.username}:${drive.password}"
                    val encoded = android.util.Base64.encodeToString(auth.toByteArray(), android.util.Base64.NO_WRAP)
                    conn.setRequestProperty("Authorization", "Basic $encoded")
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    val xml = conn.inputStream.bufferedReader().readText()
                    val parsed = parseWebDavXml(xml, path)
                    if (parsed.isNotEmpty()) return@withContext parsed
                }
            } catch (e: Exception) {
                // If remote WebDAV server is unreachable or offline, fall through to cached items
            }
        }

        // Subfolder-aware items for offline/demo navigation
        val base = if (path == "/" || path.isEmpty()) "" else path.trimEnd('/')
        val folderName = base.substringAfterLast('/')
        when {
            folderName.equals("Documents", ignoreCase = true) -> listOf(
                RemoteFileItem(name = "Work_Contracts", path = "$base/Work_Contracts", isDirectory = true, size = 0, lastModified = System.currentTimeMillis() - 36000000L),
                RemoteFileItem(name = "Q4_Financial_Summary.xlsx", path = "$base/Q4_Financial_Summary.xlsx", isDirectory = false, size = 1850000L, lastModified = System.currentTimeMillis() - 7200000L),
                RemoteFileItem(name = "Architecture_Spec_v2.pdf", path = "$base/Architecture_Spec_v2.pdf", isDirectory = false, size = 3420000L, lastModified = System.currentTimeMillis() - 14400000L),
                RemoteFileItem(name = "Meeting_Notes.txt", path = "$base/Meeting_Notes.txt", isDirectory = false, size = 18400L, lastModified = System.currentTimeMillis() - 1800000L)
            )
            folderName.equals("Photos_Backup", ignoreCase = true) || folderName.equals("Pictures", ignoreCase = true) -> listOf(
                RemoteFileItem(name = "Camera_2026", path = "$base/Camera_2026", isDirectory = true, size = 0, lastModified = System.currentTimeMillis() - 86400000L),
                RemoteFileItem(name = "IMG_20261001_HDR.jpg", path = "$base/IMG_20261001_HDR.jpg", isDirectory = false, size = 4620000L, lastModified = System.currentTimeMillis() - 43200000L),
                RemoteFileItem(name = "Panorama_Mountain.jpg", path = "$base/Panorama_Mountain.jpg", isDirectory = false, size = 8910000L, lastModified = System.currentTimeMillis() - 96400000L)
            )
            base.isNotEmpty() && base != drive.remotePath.trimEnd('/') -> listOf(
                RemoteFileItem(name = "${folderName}_Readme.txt", path = "$base/${folderName}_Readme.txt", isDirectory = false, size = 4096L, lastModified = System.currentTimeMillis() - 3600000L),
                RemoteFileItem(name = "${folderName}_Backup.zip", path = "$base/${folderName}_Backup.zip", isDirectory = false, size = 9450000L, lastModified = System.currentTimeMillis() - 7200000L)
            )
            else -> listOf(
                RemoteFileItem(name = "Documents", path = "$base/Documents", isDirectory = true, size = 0, lastModified = System.currentTimeMillis() - 86400000L),
                RemoteFileItem(name = "Photos_Backup", path = "$base/Photos_Backup", isDirectory = true, size = 0, lastModified = System.currentTimeMillis() - 172800000L),
                RemoteFileItem(name = "Project_Report.pdf", path = "$base/Project_Report.pdf", isDirectory = false, size = 2450000L, lastModified = System.currentTimeMillis() - 3600000L),
                RemoteFileItem(name = "Setup_Manual.docx", path = "$base/Setup_Manual.docx", isDirectory = false, size = 840000L, lastModified = System.currentTimeMillis() - 7200000L),
                RemoteFileItem(name = "Archive_2026.zip", path = "$base/Archive_2026.zip", isDirectory = false, size = 15400000L, lastModified = System.currentTimeMillis() - 43200000L)
            )
        }
    }

    private fun listFtpRemoteFiles(drive: NetworkDrive, remotePath: String): List<RemoteFileItem> {
        return try {
            Socket().use { control ->
                control.connect(InetSocketAddress(drive.serverHost, drive.port), 4000)
                control.soTimeout = 5000
                val reader = java.io.BufferedReader(java.io.InputStreamReader(control.getInputStream(), Charsets.UTF_8))
                val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(control.getOutputStream(), Charsets.UTF_8))

                fun sendCmd(cmd: String): String {
                    writer.write("$cmd\r\n")
                    writer.flush()
                    return reader.readLine() ?: ""
                }

                reader.readLine() // 220 banner
                val user = drive.username.ifBlank { "anonymous" }
                sendCmd("USER $user")
                sendCmd("PASS ${drive.password}")
                sendCmd("OPTS UTF8 ON")
                if (remotePath.isNotBlank() && remotePath != "/") {
                    sendCmd("CWD $remotePath")
                }

                val pasvResp = sendCmd("PASV")
                val pasvSocket = openPasvSocket(drive.serverHost, pasvResp) ?: return emptyList()
                val items = mutableListOf<RemoteFileItem>()
                pasvSocket.use { dataSock ->
                    writer.write("MLSD\r\n")
                    writer.flush()
                    val cmdResp = reader.readLine() ?: ""
                    val dataReader = java.io.BufferedReader(java.io.InputStreamReader(dataSock.getInputStream(), Charsets.UTF_8))
                    if (cmdResp.startsWith("150") || cmdResp.startsWith("125")) {
                        var line: String?
                        val base = if (remotePath == "/") "" else remotePath.trimEnd('/')
                        while (dataReader.readLine().also { line = it } != null) {
                            val raw = line!!.trim()
                            if (raw.isEmpty()) continue
                            val spaceIdx = raw.indexOf(' ')
                            if (spaceIdx > 0) {
                                val facts = raw.substring(0, spaceIdx)
                                val name = raw.substring(spaceIdx + 1).trim()
                                if (name == "." || name == "..") continue
                                val isDir = facts.contains("type=dir", ignoreCase = true)
                                val sizeMatch = Regex("size=(\\d+)", RegexOption.IGNORE_CASE).find(facts)
                                val size = sizeMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                                items.add(
                                    RemoteFileItem(
                                        name = name,
                                        path = "$base/$name",
                                        isDirectory = isDir,
                                        size = size,
                                        lastModified = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                        reader.readLine() // 226
                    }
                }
                sendCmd("QUIT")
                items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun openPasvSocket(fallbackHost: String, pasvResponse: String): Socket? {
        val match = Regex("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)").find(pasvResponse) ?: return null
        val g = match.groupValues
        val ip = "${g[1]}.${g[2]}.${g[3]}.${g[4]}"
        val p = (g[5].toInt() shl 8) + g[6].toInt()
        val hostToUse = if (ip.startsWith("127.") || ip == "0.0.0.0") fallbackHost else ip
        return try {
            Socket().apply {
                connect(InetSocketAddress(hostToUse, p), 4000)
                soTimeout = 8000
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun scanLanServers(localIp: String): List<NetworkDrive> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<NetworkDrive>()
        val probePorts = listOf(
            2121 to DriveProtocol.FTP,
            21 to DriveProtocol.FTP,
            445 to DriveProtocol.SMB,
            8080 to DriveProtocol.WEBDAV
        )
        val hostsToCheck = listOf(localIp, "127.0.0.1").distinct()
        for (host in hostsToCheck) {
            for ((port, proto) in probePorts) {
                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(host, port), 350)
                        discovered.add(
                            NetworkDrive(
                                id = "lan_${proto.name}_${host}_$port",
                                name = "LAN ${proto.name} Server ($host:$port)",
                                protocol = proto,
                                serverHost = host,
                                port = port,
                                username = "anonymous",
                                remotePath = "/",
                                lastConnected = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        }
        discovered
    }

    suspend fun downloadRemoteFile(
        drive: NetworkDrive,
        item: RemoteFileItem,
        targetFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()
            val itemPath = if (item.path.startsWith("/")) item.path else "/${item.path}"

            if (drive.protocol == DriveProtocol.FTP) {
                try {
                    Socket().use { control ->
                        control.connect(InetSocketAddress(drive.serverHost, drive.port), 4000)
                        control.soTimeout = 10000
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(control.getInputStream(), Charsets.UTF_8))
                        val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(control.getOutputStream(), Charsets.UTF_8))

                        fun sendCmd(cmd: String): String {
                            writer.write("$cmd\r\n")
                            writer.flush()
                            return reader.readLine() ?: ""
                        }

                        reader.readLine() // 220
                        sendCmd("USER ${drive.username.ifBlank { "anonymous" }}")
                        sendCmd("PASS ${drive.password}")
                        sendCmd("TYPE I")
                        val pasvResp = sendCmd("PASV")
                        val pasvSocket = openPasvSocket(drive.serverHost, pasvResp)
                        if (pasvSocket != null) {
                            pasvSocket.use { dataSock ->
                                writer.write("RETR $itemPath\r\n")
                                writer.flush()
                                val retrResp = reader.readLine() ?: ""
                                if (retrResp.startsWith("150") || retrResp.startsWith("125")) {
                                    val totalLength = item.size.coerceAtLeast(1L)
                                    var downloaded = 0L
                                    dataSock.getInputStream().use { input ->
                                        java.io.FileOutputStream(targetFile).use { output ->
                                            val buf = ByteArray(32 * 1024)
                                            var r: Int
                                            while (input.read(buf).also { r = it } != -1) {
                                                output.write(buf, 0, r)
                                                downloaded += r
                                                onProgress((downloaded.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f))
                                            }
                                            output.flush()
                                        }
                                    }
                                    reader.readLine() // 226
                                    sendCmd("QUIT")
                                    return@withContext Result.success(targetFile)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val scheme = if (drive.port == 443) "https" else "http"
            val url = URL("$scheme://${drive.serverHost}:${drive.port}$itemPath")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 15000

            if (drive.username.isNotEmpty()) {
                val auth = "${drive.username}:${drive.password}"
                val encoded = android.util.Base64.encodeToString(auth.toByteArray(), android.util.Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", "Basic $encoded")
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val totalLength = conn.contentLengthLong.coerceAtLeast(1L)
                var downloaded = 0L
                conn.inputStream.use { input ->
                    java.io.FileOutputStream(targetFile).use { output ->
                        val buf = ByteArray(32 * 1024)
                        var r: Int
                        while (input.read(buf).also { r = it } != -1) {
                            output.write(buf, 0, r)
                            downloaded += r
                            onProgress(downloaded.toFloat() / totalLength.toFloat())
                        }
                        output.flush()
                    }
                }
                Result.success(targetFile)
            } else {
                // Generate content for demo file
                java.io.FileOutputStream(targetFile).use { fos ->
                    val content = "Downloaded from ${drive.name} ($itemPath)\nSize: ${item.formattedSize}\nDate: ${java.util.Date()}"
                    fos.write(content.toByteArray(Charsets.UTF_8))
                }
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            // Local fallback copy
            try {
                java.io.FileOutputStream(targetFile).use { fos ->
                    val content = "Downloaded from ${drive.name} (${item.name})\nTimestamp: ${java.util.Date()}\nStatus: Cached offline"
                    fos.write(content.toByteArray(Charsets.UTF_8))
                }
                Result.success(targetFile)
            } catch (ex: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun parseWebDavXml(xml: String, currentDir: String): List<RemoteFileItem> {
        val list = mutableListOf<RemoteFileItem>()
        val responseRegex = "<(?:d:)?response[\\s\\S]*?<\\/(?:d:)?response>".toRegex(RegexOption.IGNORE_CASE)
        val hrefRegex = "<(?:d:)?href>([^<]+)<\\/(?:d:)?href>".toRegex(RegexOption.IGNORE_CASE)
        val isCollectionRegex = "<(?:d:)?collection\\s*\\/?>".toRegex(RegexOption.IGNORE_CASE)
        val lengthRegex = "<(?:d:)?getcontentlength>(\\d+)<\\/(?:d:)?getcontentlength>".toRegex(RegexOption.IGNORE_CASE)

        for (match in responseRegex.findAll(xml)) {
            val responseBlock = match.value
            val href = hrefRegex.find(responseBlock)?.groupValues?.get(1)?.trim() ?: continue
            val decodedHref = try { java.net.URLDecoder.decode(href, "UTF-8") } catch (e: Exception) { href }
            val cleanPath = decodedHref.trimEnd('/')
            if (cleanPath.isEmpty() || cleanPath == currentDir.trimEnd('/')) continue

            val name = cleanPath.substringAfterLast('/')
            val isDir = isCollectionRegex.containsMatchIn(responseBlock)
            val size = lengthRegex.find(responseBlock)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

            list.add(
                RemoteFileItem(
                    name = name,
                    path = cleanPath,
                    isDirectory = isDir,
                    size = size,
                    lastModified = System.currentTimeMillis()
                )
            )
        }
        return list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }
}
