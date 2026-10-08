package com.ct.explorer.ui.screens

import android.app.Activity
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import com.ct.explorer.utils.BiometricHelper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.model.FileCategory
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import com.ct.explorer.features.vault.VaultViewModel
import com.ct.explorer.utils.FileOpener

@Composable
fun VaultScreen(viewModel: ExplorerViewModel) {
    VaultScreen(vaultViewModel = viewModel.vaultViewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(vaultViewModel: VaultViewModel) {
    val isPinSet by vaultViewModel.isVaultPinSet.collectAsStateWithLifecycle()
    val isUnlocked by vaultViewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val vaultFiles by vaultViewModel.vaultFiles.collectAsStateWithLifecycle()
    val isLoading by vaultViewModel.isVaultLoading.collectAsStateWithLifecycle()

    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isConfirmingPin by remember { mutableStateOf(false) }
    var firstPinAttempt by remember { mutableStateOf("") }
    var securityAnswerInput by remember { mutableStateOf("") }
    var showSecurityQuestionSetup by remember { mutableStateOf(false) }
    var showRestoreConfirmTarget by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteConfirmTarget by remember { mutableStateOf<FileItem?>(null) }
    var showVaultSettings by remember { mutableStateOf(false) }
    var showForgotPinDialog by remember { mutableStateOf(false) }
    var recoveryAnswer by remember { mutableStateOf("") }
    var recoveryNewPin by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val isBiometricEnabled by vaultViewModel.isBiometricVaultEnabled.collectAsStateWithLifecycle()
    val isBiometricAvailable = remember { BiometricHelper.isBiometricAvailable(context) }

    var activeCancellationSignal by remember { mutableStateOf<CancellationSignal?>(null) }
    var isExiting by remember { mutableStateOf(false) }
    var hasAutoPromptedOnEntry by rememberSaveable { mutableStateOf(false) }

    fun launchBiometricPrompt() {
        if (isExiting) return
        activeCancellationSignal?.cancel()
        val activity = context as? Activity ?: return
        activeCancellationSignal = BiometricHelper.authenticate(
            activity = activity,
            onSuccess = {
                activeCancellationSignal = null
                vaultViewModel.unlockVaultWithBiometrics()
            },
            onError = { msg ->
                activeCancellationSignal = null
                pinError = msg
            },
            onNegativeButton = {
                activeCancellationSignal = null
                // Return to PIN
            }
        )
    }

    // Auto-prompt ONLY once when opening the vault screen if it is locked
    LaunchedEffect(Unit) {
        if (!hasAutoPromptedOnEntry && isPinSet && !isUnlocked && isBiometricEnabled && isBiometricAvailable) {
            hasAutoPromptedOnEntry = true
            launchBiometricPrompt()
        }
    }

    fun handleExit() {
        isExiting = true
        activeCancellationSignal?.cancel()
        activeCancellationSignal = null
        vaultViewModel.lockVault()
        vaultViewModel.handleBackPress()
    }

    DisposableEffect(Unit) {
        onDispose {
            activeCancellationSignal?.cancel()
            activeCancellationSignal = null
            vaultViewModel.lockVault()
        }
    }

    BackHandler(enabled = true) {
        handleExit()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = CtOrange
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Private Vault",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        handleExit()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isUnlocked) {
                        IconButton(onClick = { showVaultSettings = true }) {
                            Icon(Icons.Default.Security, contentDescription = "Security Settings")
                        }
                        IconButton(onClick = { vaultViewModel.lockVault() }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Vault")
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
            if (!isUnlocked) {
                // PIN Entry or Setup Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(CtOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = CtOrange,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = if (!isPinSet) {
                            if (showSecurityQuestionSetup) "Recovery Question"
                            else if (isConfirmingPin) "Confirm 4-Digit PIN"
                            else "Set a 4-Digit Vault PIN"
                        } else "Enter Vault PIN",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (!isPinSet) {
                            if (showSecurityQuestionSetup) "What is your favorite city? (For PIN recovery)"
                            else "Protect your sensitive photos & files from others."
                        } else "Access your locked files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (showSecurityQuestionSetup) {
                        OutlinedTextField(
                            value = securityAnswerInput,
                            onValueChange = { securityAnswerInput = it },
                            label = { Text("Security Answer (e.g. Lahore / Paris)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (securityAnswerInput.isNotBlank()) {
                                    vaultViewModel.setupVaultPin(firstPinAttempt, securityAnswerInput)
                                    showSecurityQuestionSetup = false
                                    pinInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CtOrange),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Finish Setup & Enter Vault")
                        }
                    } else {
                        // PIN Dots Indicator
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            repeat(4) { idx ->
                                val filled = idx < pinInput.length
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (filled) CtOrange else MaterialTheme.colorScheme.outlineVariant
                                        )
                                )
                            }
                        }

                        if (pinError != null) {
                            Text(
                                text = pinError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Custom Keypad
                        KeypadGrid(
                            onDigit = { digit ->
                                if (pinInput.length < 4) {
                                    val newPin = pinInput + digit
                                    pinInput = newPin
                                    pinError = null

                                    if (newPin.length == 4) {
                                        if (!isPinSet) {
                                            if (!isConfirmingPin) {
                                                firstPinAttempt = newPin
                                                isConfirmingPin = true
                                                pinInput = ""
                                            } else {
                                                if (newPin == firstPinAttempt) {
                                                    showSecurityQuestionSetup = true
                                                } else {
                                                    pinError = "PINs do not match. Try again."
                                                    pinInput = ""
                                                    isConfirmingPin = false
                                                }
                                            }
                                        } else {
                                            val valid = vaultViewModel.unlockVault(newPin)
                                            if (!valid) {
                                                pinError = "Incorrect PIN. Try again."
                                                pinInput = ""
                                            }
                                        }
                                    }
                                }
                            },
                            onBackspace = {
                                if (pinInput.isNotEmpty()) {
                                    pinInput = pinInput.dropLast(1)
                                    pinError = null
                                }
                            }
                        )

                        if (isPinSet && isBiometricAvailable) {
                            Spacer(modifier = Modifier.height(16.dp))
                            FilledTonalButton(
                                onClick = { launchBiometricPrompt() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = CtOrange)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unlock with Fingerprint")
                            }
                        }

                        if (isPinSet) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    recoveryAnswer = ""
                                    recoveryNewPin = ""
                                    recoveryError = null
                                    showForgotPinDialog = true
                                }
                            ) {
                                Icon(Icons.Default.HelpOutline, contentDescription = null, tint = CtOrange, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Forgot PIN? Recover via Security Question", color = CtOrange, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else {
                // Unlocked Vault View
                Column(modifier = Modifier.fillMaxSize()) {
                    // Vault Stats Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Safe & Hidden Storage",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${vaultFiles.size} hidden items • Not visible to other apps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CtOrange)
                        }
                    } else if (vaultFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Vault is empty",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Use 'Hide in Vault' from any file menu in Storage tab to protect it here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(vaultFiles, key = { it.path }) { item ->
                                VaultItemRow(
                                    item = item,
                                    onClick = {
                                        vaultViewModel.openVaultFilePreview(item) { decryptedItem ->
                                            FileOpener.openWithChooser(context, decryptedItem)
                                        }
                                    },
                                    onRestore = { showRestoreConfirmTarget = item },
                                    onDelete = { showDeleteConfirmTarget = item }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialogs
    showRestoreConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { showRestoreConfirmTarget = null },
            title = { Text("Restore File") },
            text = { Text("Move \"${target.name}\" back to standard storage (Downloads/Restored)?") },
            confirmButton = {
                Button(
                    onClick = {
                        vaultViewModel.restoreFileFromVault(target)
                        showRestoreConfirmTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showDeleteConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmTarget = null },
            title = { Text("Delete Permanently") },
            text = { Text("Are you sure you want to permanently delete \"${target.name}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        vaultViewModel.deleteFileFromVault(target)
                        showDeleteConfirmTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showVaultSettings) {
        AlertDialog(
            onDismissRequest = { showVaultSettings = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CtOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vault Security")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fingerprint Unlock", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (isBiometricAvailable) "Use biometric sensor to unlock vault" else "Biometrics not available on device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { vaultViewModel.toggleBiometricVault(it) },
                            enabled = isBiometricAvailable
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVaultSettings = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showForgotPinDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPinDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LockReset, contentDescription = null, tint = CtOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Vault PIN")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Question: What is your favorite city?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = recoveryAnswer,
                        onValueChange = {
                            recoveryAnswer = it
                            recoveryError = null
                        },
                        label = { Text("Security Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = recoveryNewPin,
                        onValueChange = { input ->
                            val digitsOnly = input.filter { it.isDigit() }.take(4)
                            recoveryNewPin = digitsOnly
                            recoveryError = null
                        },
                        label = { Text("New 4-Digit PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (recoveryError != null) {
                        Text(
                            text = recoveryError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (recoveryNewPin.length != 4) {
                            recoveryError = "Please enter a valid 4-digit PIN."
                        } else if (vaultViewModel.resetVaultPinWithAnswer(recoveryAnswer, recoveryNewPin)) {
                            showForgotPinDialog = false
                            pinInput = ""
                        } else {
                            recoveryError = "Incorrect security answer. Please try again."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                ) {
                    Text("Reset & Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun KeypadGrid(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "DEL")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(280.dp)
    ) {
        for (row in keys) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (key in row) {
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.size(64.dp))
                    } else if (key == "DEL") {
                        IconButton(
                            onClick = onBackspace,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Backspace",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable { onDigit(key) },
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = key,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VaultItemRow(
    item: FileItem,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.ct.explorer.utils.FileIconHelper.FileIconBadge(
                item = item,
                size = 44.dp,
                iconSize = 24.dp,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.friendlyTypeLabel} • ${item.formattedSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRestore) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Restore",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
