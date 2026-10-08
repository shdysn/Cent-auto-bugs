package com.ct.explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.ui.theme.CtOrange

data class ToolItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color,
    val onClick: () -> Unit
)

@Composable
fun MiToolsBottomSheet(
    onDismiss: () -> Unit,
    onVaultClick: () -> Unit,
    onFastShareClick: () -> Unit,
    onNetworkDrivesClick: () -> Unit,
    onTrashClick: () -> Unit,
    onAnalyzerClick: () -> Unit,
    onDuplicatesClick: () -> Unit,
    onCleanerClick: () -> Unit,
    onAppManagerClick: () -> Unit,
    onAppInstallerClick: () -> Unit = {},
    onFtpClick: () -> Unit,
    onDualPaneToggle: () -> Unit,
    isDualPaneActive: Boolean = false,
    onSocialClick: () -> Unit = {},
    onPinWidgetClick: () -> Unit = {},
    onWebShareClick: () -> Unit = {},
    onFileShredderClick: () -> Unit = {},
    onSmartCollectionsClick: () -> Unit = {},
    onTimeMachineClick: () -> Unit = {},
    onDiagnosticsClick: () -> Unit = {},
    onAboutClick: () -> Unit = {}
) = CtToolsBottomSheet(
    onDismiss = onDismiss,
    onVaultClick = onVaultClick,
    onFastShareClick = onFastShareClick,
    onNetworkDrivesClick = onNetworkDrivesClick,
    onTrashClick = onTrashClick,
    onAnalyzerClick = onAnalyzerClick,
    onDuplicatesClick = onDuplicatesClick,
    onCleanerClick = onCleanerClick,
    onAppManagerClick = onAppManagerClick,
    onAppInstallerClick = onAppInstallerClick,
    onFtpClick = onFtpClick,
    onDualPaneToggle = onDualPaneToggle,
    isDualPaneActive = isDualPaneActive,
    onSocialClick = onSocialClick,
    onPinWidgetClick = onPinWidgetClick,
    onWebShareClick = onWebShareClick,
    onFileShredderClick = onFileShredderClick,
    onSmartCollectionsClick = onSmartCollectionsClick,
    onTimeMachineClick = onTimeMachineClick,
    onDiagnosticsClick = onDiagnosticsClick,
    onAboutClick = onAboutClick
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CtToolsBottomSheet(
    onDismiss: () -> Unit,
    onVaultClick: () -> Unit,
    onFastShareClick: () -> Unit,
    onNetworkDrivesClick: () -> Unit,
    onTrashClick: () -> Unit,
    onAnalyzerClick: () -> Unit,
    onDuplicatesClick: () -> Unit,
    onCleanerClick: () -> Unit,
    onAppManagerClick: () -> Unit,
    onAppInstallerClick: () -> Unit = {},
    onFtpClick: () -> Unit,
    onDualPaneToggle: () -> Unit,
    isDualPaneActive: Boolean = false,
    onSocialClick: () -> Unit = {},
    onPinWidgetClick: () -> Unit = {},
    onWebShareClick: () -> Unit = {},
    onFileShredderClick: () -> Unit = {},
    onSmartCollectionsClick: () -> Unit = {},
    onTimeMachineClick: () -> Unit = {},
    onDiagnosticsClick: () -> Unit = {},
    onAboutClick: () -> Unit = {}
) {
    val tools = listOf(
        ToolItem(
            id = "diagnostics",
            title = "Health & Auto-Repair",
            subtitle = "Self-Healing & Audit",
            icon = Icons.Default.HealthAndSafety,
            iconColor = Color(0xFF10B981),
            onClick = { onDismiss(); onDiagnosticsClick() }
        ),
        ToolItem(
            id = "home_widget",
            title = "Home Screen Widget",
            subtitle = "Add live storage widget",
            icon = Icons.Default.Widgets,
            iconColor = Color(0xFF0EA5E9),
            onClick = { onDismiss(); onPinWidgetClick() }
        ),
        ToolItem(
            id = "app_installer",
            title = "APKs",
            subtitle = "Install APK files",
            icon = Icons.Default.InstallMobile,
            iconColor = Color(0xFF059669),
            onClick = { onDismiss(); onAppInstallerClick() }
        ),
        ToolItem(
            id = "social",
            title = "Social Folders",
            subtitle = "WhatsApp, Telegram, etc.",
            icon = Icons.Default.Chat,
            iconColor = Color(0xFF25D366),
            onClick = { onDismiss(); onSocialClick() }
        ),
        ToolItem(
            id = "vault",
            title = "Private Vault",
            subtitle = "Fingerprint & PIN safe",
            icon = Icons.Default.Lock,
            iconColor = CtOrange,
            onClick = { onDismiss(); onVaultClick() }
        ),
        ToolItem(
            id = "fast_share",
            title = "Cent Fast Share",
            subtitle = "Offline Wi-Fi transfer",
            icon = Icons.Default.WifiTethering,
            iconColor = Color(0xFF10B981),
            onClick = { onDismiss(); onFastShareClick() }
        ),
        ToolItem(
            id = "web_share",
            title = "PC Web Portal",
            subtitle = "Send & receive via browser",
            icon = Icons.Default.Language,
            iconColor = Color(0xFF2563EB),
            onClick = { onDismiss(); onWebShareClick() }
        ),
        ToolItem(
            id = "cloud",
            title = "Cloud & Network Drives",
            subtitle = "Google Drive, OneDrive, SMB",
            icon = Icons.Default.CloudQueue,
            iconColor = Color(0xFF0EA5E9),
            onClick = { onDismiss(); onNetworkDrivesClick() }
        ),
        ToolItem(
            id = "smart_collections",
            title = "Smart Collections",
            subtitle = "Auto-curated virtual hubs",
            icon = Icons.Default.AutoAwesomeMosaic,
            iconColor = Color(0xFF8B5CF6),
            onClick = { onDismiss(); onSmartCollectionsClick() }
        ),
        ToolItem(
            id = "time_machine",
            title = "Time Machine",
            subtitle = "On This Day & timeline",
            icon = Icons.Default.History,
            iconColor = Color(0xFFF59E0B),
            onClick = { onDismiss(); onTimeMachineClick() }
        ),
        ToolItem(
            id = "file_shredder",
            title = "File Shredder",
            subtitle = "DoD 3-pass secure wipe",
            icon = Icons.Default.EnhancedEncryption,
            iconColor = Color(0xFFEF4444),
            onClick = { onDismiss(); onFileShredderClick() }
        ),
        ToolItem(
            id = "trash",
            title = "Recycle Bin",
            subtitle = "Trash & 30d auto-purge",
            icon = Icons.Default.DeleteOutline,
            iconColor = Color(0xFFEF4444),
            onClick = { onDismiss(); onTrashClick() }
        ),
        ToolItem(
            id = "analyzer",
            title = "Storage Analyzer",
            subtitle = "Visual space breakdown",
            icon = Icons.Default.PieChart,
            iconColor = Color(0xFF3B82F6),
            onClick = { onDismiss(); onAnalyzerClick() }
        ),
        ToolItem(
            id = "duplicates",
            title = "Duplicate Finder",
            subtitle = "Free up redundant space",
            icon = Icons.Default.ContentCopy,
            iconColor = Color(0xFF14B8A6),
            onClick = { onDismiss(); onDuplicatesClick() }
        ),
        ToolItem(
            id = "cleaner",
            title = "Deep Cleaner",
            subtitle = "Clear cache & junk files",
            icon = Icons.Default.CleaningServices,
            iconColor = Color(0xFFF59E0B),
            onClick = { onDismiss(); onCleanerClick() }
        ),
        ToolItem(
            id = "apps",
            title = "APK Cloner & Hub",
            subtitle = "1-tap backup & downgrade",
            icon = Icons.Default.Android,
            iconColor = Color(0xFF8B5CF6),
            onClick = { onDismiss(); onAppManagerClick() }
        ),
        ToolItem(
            id = "ftp",
            title = "Transfer to PC",
            subtitle = "Wireless FTP server",
            icon = Icons.Default.Wifi,
            iconColor = Color(0xFF6366F1),
            onClick = { onDismiss(); onFtpClick() }
        ),
        ToolItem(
            id = "dual_pane",
            title = if (isDualPaneActive) "Single Pane" else "Dual-Pane View",
            subtitle = "Side-by-side browser",
            icon = Icons.Default.VerticalSplit,
            iconColor = Color(0xFFEC4899),
            onClick = { onDismiss(); onDualPaneToggle() }
        ),
        ToolItem(
            id = "about_privacy",
            title = "About & Privacy",
            subtitle = "Policy & disclosures",
            icon = Icons.Default.Shield,
            iconColor = Color(0xFF10B981),
            onClick = { onDismiss(); onAboutClick() }
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Tools & Utilities",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "All power tools in one place",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(tools, key = { it.id }) { tool ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = tool.onClick),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(tool.iconColor.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = null,
                                    tint = tool.iconColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = tool.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1
                                )
                                Text(
                                    text = tool.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
