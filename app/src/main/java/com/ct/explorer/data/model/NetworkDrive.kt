package com.ct.explorer.data.model

enum class DriveProtocol {
    WEBDAV,
    SMB,
    FTP,
    GOOGLE_DRIVE,
    ONEDRIVE,
    DROPBOX
}

data class NetworkDrive(
    val id: String,
    val name: String,
    val protocol: DriveProtocol,
    val serverHost: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val remotePath: String = "/",
    val lastConnected: Long = 0L,
    val totalQuotaBytes: Long = 15L * 1024 * 1024 * 1024,
    val usedQuotaBytes: Long = 4L * 1024 * 1024 * 1024
) {
    val isCloudOAuth: Boolean
        get() = protocol == DriveProtocol.GOOGLE_DRIVE || protocol == DriveProtocol.ONEDRIVE || protocol == DriveProtocol.DROPBOX

    val displaySubtitle: String
        get() = if (isCloudOAuth) {
            "${protocol.name.replace("_", " ")} • $username"
        } else {
            "${protocol.name} • $serverHost:$port$remotePath"
        }
}

data class RemoteFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
) {
    val formattedSize: String get() = FileItem.formatBytes(size)
}
