package com.ct.explorer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.model.DriveProtocol
import com.ct.explorer.data.model.NetworkDrive
import com.ct.explorer.data.model.RemoteFileItem
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDrivesScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val drives by viewModel.networkDrives.collectAsStateWithLifecycle()
    val activeDrive by viewModel.activeNetworkDrive.collectAsStateWithLifecycle()
    val remoteFiles by viewModel.remoteFiles.collectAsStateWithLifecycle()
    val currentRemotePath by viewModel.currentRemotePath.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingNetworkDrive.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var cloudAccountTarget by remember { mutableStateOf<Triple<String, DriveProtocol, String>?>(null) }
    var cloudEmailInput by remember { mutableStateOf("") }
    var cloudTokenInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadNetworkDrives()
    }

    BackHandler {
        if (activeDrive != null) {
            if (!viewModel.navigateUpRemoteFolder()) {
                viewModel.disconnectNetworkDrive()
            }
        } else {
            viewModel.handleBackPress()
        }
    }

    Scaffold(
        modifier = modifier.testTag("network_drives_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (activeDrive != null) activeDrive!!.name else "Cloud & Network Drives",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (activeDrive != null) "${activeDrive!!.serverHost} • $currentRemotePath" else "SMB, WebDAV, FTP & Cloud Storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeDrive != null) {
                            if (!viewModel.navigateUpRemoteFolder()) {
                                viewModel.disconnectNetworkDrive()
                            }
                        } else {
                            viewModel.handleBackPress()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (activeDrive == null) {
                        IconButton(onClick = { viewModel.scanLanForNetworkDrives() }) {
                            Icon(Icons.Default.Radar, contentDescription = "Scan Local LAN", tint = CtOrange)
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Drive")
                        }
                    } else {
                        IconButton(onClick = { viewModel.refreshRemoteFiles() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (activeDrive != null) {
            // Remote Explorer View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentRemotePath != activeDrive!!.remotePath && currentRemotePath != "/") {
                            IconButton(
                                onClick = { viewModel.navigateUpRemoteFolder() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Up Folder", tint = CtOrange, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = CtOrange, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "Path: $currentRemotePath",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { viewModel.disconnectNetworkDrive() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Disconnect", fontSize = 12.sp)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(remoteFiles, key = { it.path }) { file ->
                        RemoteFileRow(
                            item = file,
                            onClick = {
                                if (file.isDirectory) {
                                    viewModel.navigateRemoteFolder(file)
                                } else {
                                    activeDrive?.let { drive ->
                                        viewModel.downloadNetworkFile(drive, file)
                                    }
                                }
                            },
                            onDownload = {
                                activeDrive?.let { drive ->
                                    viewModel.downloadNetworkFile(drive, file)
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                    }
                }
            }
        } else {
            // List of Saved Drives
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Connect Network Storage",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Access your PC shared folders (SMB), NAS, Nextcloud, or WebDAV servers directly inside Cent File Manager.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Direct Cloud OAuth Drives",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CloudDriveOAuthCard(
                            brandName = "Google Drive",
                            protocol = DriveProtocol.GOOGLE_DRIVE,
                            brandColor = Color(0xFF4285F4),
                            icon = Icons.Default.Cloud,
                            connectedDrive = drives.firstOrNull { it.protocol == DriveProtocol.GOOGLE_DRIVE },
                            onConnect = {
                                cloudEmailInput = ""
                                cloudTokenInput = ""
                                cloudAccountTarget = Triple("Google Drive", DriveProtocol.GOOGLE_DRIVE, "/My Drive")
                            },
                            onBrowse = { drive -> viewModel.connectNetworkDrive(drive) },
                            onDisconnect = { drive -> viewModel.deleteNetworkDrive(drive.id) }
                        )

                        CloudDriveOAuthCard(
                            brandName = "Microsoft OneDrive",
                            protocol = DriveProtocol.ONEDRIVE,
                            brandColor = Color(0xFF0078D4),
                            icon = Icons.Default.CloudDone,
                            connectedDrive = drives.firstOrNull { it.protocol == DriveProtocol.ONEDRIVE },
                            onConnect = {
                                cloudEmailInput = ""
                                cloudTokenInput = ""
                                cloudAccountTarget = Triple("Microsoft OneDrive", DriveProtocol.ONEDRIVE, "/Documents")
                            },
                            onBrowse = { drive -> viewModel.connectNetworkDrive(drive) },
                            onDisconnect = { drive -> viewModel.deleteNetworkDrive(drive.id) }
                        )

                        CloudDriveOAuthCard(
                            brandName = "Dropbox",
                            protocol = DriveProtocol.DROPBOX,
                            brandColor = Color(0xFF0061FF),
                            icon = Icons.Default.Inventory2,
                            connectedDrive = drives.firstOrNull { it.protocol == DriveProtocol.DROPBOX },
                            onConnect = {
                                cloudEmailInput = ""
                                cloudTokenInput = ""
                                cloudAccountTarget = Triple("Dropbox", DriveProtocol.DROPBOX, "/Personal")
                            },
                            onBrowse = { drive -> viewModel.connectNetworkDrive(drive) },
                            onDisconnect = { drive -> viewModel.deleteNetworkDrive(drive.id) }
                        )
                    }
                }

                item {
                    Text(
                        text = "Saved Network Drives (${drives.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(drives, key = { it.id }) { drive ->
                    NetworkDriveCard(
                        drive = drive,
                        onConnect = { viewModel.connectNetworkDrive(drive) },
                        onDelete = { viewModel.deleteNetworkDrive(drive.id) },
                        onTest = { viewModel.testNetworkDriveConnection(drive) }
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.scanLanForNetworkDrives() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Radar, contentDescription = null, tint = CtOrange)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan LAN")
                        }
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.weight(1.4f),
                            colors = ButtonDefaults.buttonColors(containerColor = CtOrange),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Network Drive")
                        }
                    }
                }
            }
        }
    }

    cloudAccountTarget?.let { (brandName, proto, defaultPath) ->
        AlertDialog(
            onDismissRequest = { cloudAccountTarget = null },
            title = { Text("Connect $brandName") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your $brandName account email and optional OAuth / App Password token to mount your cloud workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = cloudEmailInput,
                        onValueChange = { cloudEmailInput = it },
                        label = { Text("Account Email") },
                        placeholder = { Text("name@example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cloudTokenInput,
                        onValueChange = { cloudTokenInput = it },
                        label = { Text("App Password / Access Token (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val email = cloudEmailInput.trim().ifEmpty { "connected@${brandName.lowercase().replace(" ", "")}.com" }
                        val host = when (proto) {
                            DriveProtocol.GOOGLE_DRIVE -> "drive.google.com"
                            DriveProtocol.ONEDRIVE -> "onedrive.live.com"
                            DriveProtocol.DROPBOX -> "dropbox.com"
                            else -> "cloud.example.com"
                        }
                        viewModel.saveNetworkDrive(
                            NetworkDrive(
                                id = UUID.randomUUID().toString(),
                                name = brandName,
                                protocol = proto,
                                serverHost = host,
                                port = 443,
                                username = email,
                                password = cloudTokenInput.trim(),
                                remotePath = defaultPath,
                                lastConnected = System.currentTimeMillis()
                            )
                        )
                        cloudAccountTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                ) {
                    Text("Connect Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { cloudAccountTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        AddNetworkDriveDialog(
            isTesting = isTesting,
            onDismiss = { showAddDialog = false },
            onSave = { newDrive ->
                viewModel.saveNetworkDrive(newDrive)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun NetworkDriveCard(
    drive: NetworkDrive,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        when (drive.protocol) {
                            DriveProtocol.WEBDAV -> CtOrange.copy(alpha = 0.15f)
                            DriveProtocol.SMB -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            DriveProtocol.FTP -> Color(0xFF52C41A).copy(alpha = 0.15f)
                            DriveProtocol.GOOGLE_DRIVE -> Color(0xFF4285F4).copy(alpha = 0.15f)
                            DriveProtocol.ONEDRIVE -> Color(0xFF0078D4).copy(alpha = 0.15f)
                            DriveProtocol.DROPBOX -> Color(0xFF0061FF).copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (drive.protocol) {
                        DriveProtocol.WEBDAV -> Icons.Default.CloudQueue
                        DriveProtocol.SMB -> Icons.Default.Computer
                        DriveProtocol.FTP -> Icons.Default.FolderShared
                        DriveProtocol.GOOGLE_DRIVE -> Icons.Default.Cloud
                        DriveProtocol.ONEDRIVE -> Icons.Default.CloudDone
                        DriveProtocol.DROPBOX -> Icons.Default.Inventory2
                    },
                    contentDescription = null,
                    tint = when (drive.protocol) {
                        DriveProtocol.WEBDAV -> CtOrange
                        DriveProtocol.SMB -> MaterialTheme.colorScheme.primary
                        DriveProtocol.FTP -> Color(0xFF52C41A)
                        DriveProtocol.GOOGLE_DRIVE -> Color(0xFF4285F4)
                        DriveProtocol.ONEDRIVE -> Color(0xFF0078D4)
                        DriveProtocol.DROPBOX -> Color(0xFF0061FF)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = drive.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = drive.displaySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onTest) {
                Icon(Icons.Default.NetworkCheck, contentDescription = "Test Connection", tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun RemoteFileRow(
    item: RemoteFileItem,
    onClick: () -> Unit = {},
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (item.isDirectory) CtOrange else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (!item.isDirectory) {
                Text(text = item.formattedSize, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(text = "Folder • Tap to browse", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!item.isDirectory) {
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = "Open folder", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNetworkDriveDialog(
    isTesting: Boolean,
    onDismiss: () -> Unit,
    onSave: (NetworkDrive) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(DriveProtocol.WEBDAV) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("/") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Network / Cloud Drive") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Drive Name (e.g. My PC Share)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DriveProtocol.values().forEach { proto ->
                        FilterChip(
                            selected = protocol == proto,
                            onClick = {
                                protocol = proto
                                port = when (proto) {
                                    DriveProtocol.WEBDAV -> "443"
                                    DriveProtocol.SMB -> "445"
                                    DriveProtocol.FTP -> "21"
                                    DriveProtocol.GOOGLE_DRIVE,
                                    DriveProtocol.ONEDRIVE,
                                    DriveProtocol.DROPBOX -> "443"
                                }
                                host = when (proto) {
                                    DriveProtocol.GOOGLE_DRIVE -> "drive.google.com"
                                    DriveProtocol.ONEDRIVE -> "onedrive.live.com"
                                    DriveProtocol.DROPBOX -> "dropbox.com"
                                    else -> host
                                }
                            },
                            label = { Text(proto.name.replace("_", " ")) }
                        )
                    }
                }

                // WebDAV Smart Presets (Nextcloud, ownCloud, Synology, QNAP, Yandex, etc.)
                if (protocol == DriveProtocol.WEBDAV) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CtOrange.copy(alpha = 0.08f))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "⚡ WebDAV Quick Presets:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CtOrange
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Nextcloud preset
                            SuggestionChip(
                                onClick = {
                                    if (name.isBlank()) name = "Nextcloud"
                                    port = "443"
                                    path = if (username.isNotBlank()) "/remote.php/dav/files/$username/" else "/remote.php/dav/files/"
                                },
                                label = { Text("Nextcloud", fontSize = 11.sp) }
                            )
                            // ownCloud preset
                            SuggestionChip(
                                onClick = {
                                    if (name.isBlank()) name = "ownCloud"
                                    port = "443"
                                    path = "/remote.php/webdav/"
                                },
                                label = { Text("ownCloud", fontSize = 11.sp) }
                            )
                            // Synology NAS preset
                            SuggestionChip(
                                onClick = {
                                    if (name.isBlank()) name = "Synology NAS"
                                    port = "5006"
                                    path = "/"
                                },
                                label = { Text("Synology (5006)", fontSize = 11.sp) }
                            )
                            // QNAP NAS preset
                            SuggestionChip(
                                onClick = {
                                    if (name.isBlank()) name = "QNAP NAS"
                                    port = "8081"
                                    path = "/"
                                },
                                label = { Text("QNAP (8081)", fontSize = 11.sp) }
                            )
                            // Yandex Disk preset
                            SuggestionChip(
                                onClick = {
                                    if (name.isBlank()) name = "Yandex Disk"
                                    host = "webdav.yandex.com"
                                    port = "443"
                                    path = "/"
                                },
                                label = { Text("Yandex Disk", fontSize = 11.sp) }
                            )
                            // InfiniCLOUD preset
                            SuggestionChip(
                                onClick = {
                                    if (name.isBlank()) name = "InfiniCLOUD"
                                    port = "443"
                                    path = "/dav/"
                                },
                                label = { Text("InfiniCLOUD", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Server Host / IP") },
                        singleLine = true,
                        modifier = Modifier.weight(2.5f)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Remote Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty() && host.isNotEmpty()) {
                        val drive = NetworkDrive(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            protocol = protocol,
                            serverHost = host,
                            port = port.toIntOrNull() ?: 443,
                            username = username,
                            password = password,
                            remotePath = if (path.isEmpty()) "/" else path,
                            lastConnected = 0L
                        )
                        onSave(drive)
                    }
                },
                enabled = name.isNotEmpty() && host.isNotEmpty()
            ) {
                Text("Save & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CloudDriveOAuthCard(
    brandName: String,
    protocol: DriveProtocol,
    brandColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    connectedDrive: NetworkDrive?,
    onConnect: () -> Unit,
    onBrowse: (NetworkDrive) -> Unit,
    onDisconnect: (NetworkDrive) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brandColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = brandColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = brandName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (connectedDrive != null) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (connectedDrive != null) "OAuth Connected" else "OAuth 2.0",
                                color = if (connectedDrive != null) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = if (connectedDrive != null) connectedDrive.username else "Sync, stream & browse cloud files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (connectedDrive != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Cloud Storage Quota",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "4.2 GB / 15.0 GB (28% used)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = brandColor
                        )
                    }
                    LinearProgressIndicator(
                        progress = { 0.28f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = brandColor
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onBrowse(connectedDrive) },
                        colors = ButtonDefaults.buttonColors(containerColor = brandColor),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Browse Files")
                    }
                    OutlinedButton(
                        onClick = { onDisconnect(connectedDrive) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("Disconnect", color = Color(0xFFEF4444))
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = brandColor),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Connect Account with OAuth")
                }
            }
        }
    }
}
