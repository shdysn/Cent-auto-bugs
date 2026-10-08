package com.ct.explorer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.repository.ZipEntryItem
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import com.ct.explorer.utils.ArchiveHelper
import com.ct.explorer.utils.ArchiveIntegrityResult
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipViewerScreen(viewModel: ExplorerViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val zipState by viewModel.zipViewerState.collectAsStateWithLifecycle()
    val isExtracting by viewModel.isZipExtracting.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    val selectedEntries = remember { mutableStateListOf<String>() }
    var showExtractDestinationDialog by remember { mutableStateOf(false) }
    var isTestingIntegrity by remember { mutableStateOf(false) }
    var integrityResult by remember { mutableStateOf<ArchiveIntegrityResult?>(null) }

    var singleExtractTarget by remember { mutableStateOf<ZipEntryItem?>(null) }
    var singleExtractDestination by remember { mutableStateOf("") }
    var passwordPromptTarget by remember { mutableStateOf<ZipEntryItem?>(null) }
    var passwordPromptInput by remember { mutableStateOf("") }
    var archivePassword by remember { mutableStateOf(viewModel.cachedArchivePassword ?: "") }

    val archive = zipState.archiveInfo
    val archiveType = remember(archive?.sourceFile) {
        archive?.sourceFile?.let { ArchiveHelper.detectArchiveType(it) }
    }
    val isEncrypted = remember(archive?.sourceFile) {
        archive?.sourceFile?.let { ArchiveHelper.isArchiveEncrypted(it) } ?: false
    }

    val filteredEntries = remember(archive?.entries, searchQuery) {
        val list = archive?.entries ?: emptyList()
        if (searchQuery.isBlank()) list
        else list.filter { it.name.contains(searchQuery, ignoreCase = true) || it.fullPath.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = archive?.archiveName ?: "Archive Studio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isEncrypted) {
                                Surface(
                                    color = Color(0xFFEF4444).copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444))
                                ) {
                                    Text(
                                        text = "AES-256",
                                        color = Color(0xFFEF4444),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        if (archive != null) {
                            Text(
                                text = "${archiveType?.label ?: "Archive"} • ${archive.totalEntries} items • ${archive.formattedArchiveSize}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.handleBackPress() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (archive?.sourceFile != null && !zipState.isLoading) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isTestingIntegrity = true
                                    val res = ArchiveHelper.testArchiveIntegrity(archive.sourceFile)
                                    isTestingIntegrity = false
                                    res.onSuccess { integrityResult = it }
                                    res.onFailure { viewModel.showMessage("Integrity test error: ${it.localizedMessage}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = "Test Archive Integrity", tint = Color(0xFF10B981))
                        }

                        FilledTonalButton(
                            onClick = { showExtractDestinationDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CtOrange,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedEntries.isNotEmpty()) "Extract (${selectedEntries.size})" else "Extract All")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (zipState.isLoading || isExtracting || isTestingIntegrity) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CtOrange)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                isTestingIntegrity -> "Verifying CRC32 & stream integrity..."
                                isExtracting -> "Extracting files..."
                                else -> "Reading archive contents..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (archive == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Failed to load Archive",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = zipState.errorMessage ?: "The archive may be corrupted or unreadable.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Archive Overview Header Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(CtOrange.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isEncrypted) Icons.Default.EnhancedEncryption else Icons.Default.FolderZip,
                                    contentDescription = null,
                                    tint = CtOrange,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isEncrypted) "AES-256 Encrypted Archive" else "${archiveType?.label ?: "Archive"} Compression",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "${archive.formattedArchiveSize}  ➔  ${archive.formattedUncompressedSize}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (archive.overallSavingsPercentage > 0) {
                                Surface(
                                    color = Color(0xFF10B981),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "${archive.overallSavingsPercentage}% Saved",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Search inside archive
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search files in archive...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CtOrange) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    // Selection count & toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredEntries.size} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedEntries.isNotEmpty()) {
                            TextButton(onClick = { selectedEntries.clear() }) {
                                Text("Clear Selection (${selectedEntries.size})", color = CtOrange)
                            }
                        } else {
                            TextButton(onClick = {
                                selectedEntries.clear()
                                selectedEntries.addAll(filteredEntries.map { it.fullPath })
                            }) {
                                Text("Select All", color = CtOrange)
                            }
                        }
                    }

                    // Archive entries list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredEntries, key = { it.fullPath }) { entry ->
                            val isSelected = selectedEntries.contains(entry.fullPath)
                            ZipEntryRow(
                                entry = entry,
                                isSelected = isSelected,
                                onItemClick = {
                                    if (entry.isDirectory) {
                                        if (isSelected) selectedEntries.remove(entry.fullPath)
                                        else selectedEntries.add(entry.fullPath)
                                    } else if (archive.sourceFile != null) {
                                        viewModel.openZipEntry(
                                            archiveFile = archive.sourceFile,
                                            entry = entry,
                                            password = archivePassword.takeIf { it.isNotBlank() },
                                            onRequiresPassword = {
                                                passwordPromptTarget = entry
                                            }
                                        )
                                    }
                                },
                                onToggleSelect = {
                                    if (isSelected) selectedEntries.remove(entry.fullPath)
                                    else selectedEntries.add(entry.fullPath)
                                },
                                onOpen = {
                                    if (archive.sourceFile != null) {
                                        viewModel.openZipEntry(
                                            archiveFile = archive.sourceFile,
                                            entry = entry,
                                            password = archivePassword.takeIf { it.isNotBlank() },
                                            onRequiresPassword = {
                                                passwordPromptTarget = entry
                                            }
                                        )
                                    }
                                },
                                onOpenWith = {
                                    if (archive.sourceFile != null) {
                                        viewModel.openZipEntryWithChooser(
                                            archiveFile = archive.sourceFile,
                                            entry = entry,
                                            context = context,
                                            password = archivePassword.takeIf { it.isNotBlank() },
                                            onRequiresPassword = {
                                                passwordPromptTarget = entry
                                            }
                                        )
                                    }
                                },
                                onExtractSingle = {
                                    val src = archive?.sourceFile
                                    val parent = src?.parentFile
                                    val defaultDest = if (parent != null && parent.canWrite()) parent.absolutePath
                                    else android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath
                                    singleExtractDestination = defaultDest
                                    singleExtractTarget = entry
                                },
                                onCopyPath = {
                                    clipboardManager.setText(AnnotatedString(entry.fullPath))
                                    viewModel.showMessage("Copied path: ${entry.fullPath}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExtractDestinationDialog && archive?.sourceFile != null) {
        val defaultTarget = remember {
            val src = archive.sourceFile
            val parent = src.parentFile
            val baseName = archive.archiveName
                .removeSuffix(".tar.gz")
                .removeSuffix(".tgz")
                .substringBeforeLast(".")
            if (parent != null && parent.canWrite()) {
                File(parent, baseName)
            } else {
                File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Extracted/$baseName")
            }
        }

        var destinationPath by remember { mutableStateOf(defaultTarget.absolutePath) }
        var extractPassword by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showExtractDestinationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Unarchive, contentDescription = null, tint = CtOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Extract Archive")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (selectedEntries.isNotEmpty()) "Extract ${selectedEntries.size} selected items to:" else "Extract all ${archive.totalEntries} items to:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = destinationPath,
                        onValueChange = { destinationPath = it },
                        label = { Text("Destination Directory") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isEncrypted) {
                        OutlinedTextField(
                            value = extractPassword,
                            onValueChange = { extractPassword = it },
                            label = { Text("Archive Password (Required)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CtOrange) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetDir = File(destinationPath)
                        val toExtract = if (selectedEntries.isNotEmpty()) selectedEntries.toSet() else null
                        val pass = extractPassword.takeIf { it.isNotBlank() }
                        viewModel.extractZipArchive(archive.sourceFile, targetDir, toExtract, pass)
                        showExtractDestinationDialog = false
                    },
                    enabled = !isEncrypted || extractPassword.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                ) {
                    Text("Extract Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtractDestinationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    integrityResult?.let { res ->
        AlertDialog(
            onDismissRequest = { integrityResult = null },
            icon = {
                Icon(
                    imageVector = if (res.isHealthy) Icons.Default.VerifiedUser else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (res.isHealthy) Color(0xFF10B981) else Color(0xFFEF4444),
                    modifier = Modifier.size(44.dp)
                )
            },
            title = {
                Text(if (res.isHealthy) "Archive Integrity Verified" else "Integrity Issues Found")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Files Checked: ${res.totalChecked}")
                    Text("Passed CRC/Stream Check: ${res.passedCount}")
                    Text("Encryption: ${if (res.isEncrypted) "AES-256 Protected" else "Standard Unencrypted"}")
                    if (res.failedEntries.isNotEmpty()) {
                        Text(
                            text = "Corrupted entries: ${res.failedEntries.take(5).joinToString(", ")}",
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { integrityResult = null }) {
                    Text("OK")
                }
            }
        )
    }

    // Single file extraction dialog
    singleExtractTarget?.let { entry ->
        if (archive?.sourceFile != null) {
            AlertDialog(
                onDismissRequest = { singleExtractTarget = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Unarchive, contentDescription = null, tint = CtOrange)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extract File")
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Extract \"${entry.name}\" to destination folder:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = singleExtractDestination,
                            onValueChange = { singleExtractDestination = it },
                            label = { Text("Destination Directory") },
                            singleLine = false,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isEncrypted && archivePassword.isBlank()) {
                            OutlinedTextField(
                                value = archivePassword,
                                onValueChange = { archivePassword = it },
                                label = { Text("Password (Encrypted Archive)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CtOrange) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetDir = File(singleExtractDestination)
                            viewModel.extractSingleZipEntry(
                                archiveFile = archive.sourceFile,
                                entry = entry,
                                targetDir = targetDir,
                                password = archivePassword.takeIf { it.isNotBlank() }
                            )
                            singleExtractTarget = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                    ) {
                        Text("Extract File")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { singleExtractTarget = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // Password Prompt Dialog when opening an encrypted file
    passwordPromptTarget?.let { entry ->
        if (archive?.sourceFile != null) {
            AlertDialog(
                onDismissRequest = { passwordPromptTarget = null },
                icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CtOrange, modifier = Modifier.size(36.dp)) },
                title = { Text("Password Required") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "\"${entry.name}\" is protected by AES-256 encryption. Enter the password to open it:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = passwordPromptInput,
                            onValueChange = { passwordPromptInput = it },
                            label = { Text("Archive Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val pass = passwordPromptInput.trim()
                            if (pass.isNotEmpty()) {
                                archivePassword = pass
                                viewModel.cachedArchivePassword = pass
                                passwordPromptTarget = null
                                viewModel.openZipEntry(
                                    archiveFile = archive.sourceFile,
                                    entry = entry,
                                    password = pass
                                )
                            }
                        },
                        enabled = passwordPromptInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                    ) {
                        Text("Unlock & Open")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { passwordPromptTarget = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZipEntryRow(
    entry: ZipEntryItem,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onOpenWith: () -> Unit,
    onExtractSingle: () -> Unit,
    onCopyPath: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onToggleSelect
            ),
        color = if (isSelected) CtOrange.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = if (isSelected) BorderStroke(1.dp, CtOrange.copy(alpha = 0.4f)) else null,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(checkedColor = CtOrange)
            )

            val descriptor = remember(entry.name, entry.isDirectory, entry.extension) {
                if (entry.isDirectory) {
                    com.ct.explorer.utils.FileIconHelper.getFolderDescriptor(entry.name)
                } else {
                    com.ct.explorer.utils.FileIconHelper.getFileDescriptor(entry.name, entry.extension)
                }
            }
            com.ct.explorer.utils.FileIconHelper.FileIconBadge(
                descriptor = descriptor,
                size = 38.dp,
                iconSize = 20.dp,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (entry.extension.isNotEmpty() && !entry.isDirectory) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = entry.extension.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = if (entry.isDirectory) "Folder" else "${entry.formattedSize}  (${entry.ratioPercentage}% saved)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!entry.isDirectory) {
                // Quick Open / View Icon Button
                IconButton(
                    onClick = onOpen,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "View file",
                        tint = CtOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 3-Dots Action Menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = CtOrange, modifier = Modifier.size(18.dp))
                            },
                            text = { Text("Open / Preview") },
                            onClick = {
                                showMenu = false
                                onOpen()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            text = { Text("Open with...") },
                            onClick = {
                                showMenu = false
                                onOpenWith()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(Icons.Default.Unarchive, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                            },
                            text = { Text("Extract this file") },
                            onClick = {
                                showMenu = false
                                onExtractSingle()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            text = { Text("Copy entry path") },
                            onClick = {
                                showMenu = false
                                onCopyPath()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            text = { Text(if (isSelected) "Deselect" else "Select") },
                            onClick = {
                                showMenu = false
                                onToggleSelect()
                            }
                        )
                    }
                }
            }
        }
    }
}
