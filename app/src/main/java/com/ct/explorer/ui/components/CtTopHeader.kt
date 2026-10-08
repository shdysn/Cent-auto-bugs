package com.ct.explorer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.ct.explorer.ui.theme.CtOrange

enum class CtTab {
    RECENT,
    STORAGE
}

typealias MiTab = CtTab

@Composable
fun MiTopHeader(
    selectedTab: CtTab,
    onTabSelected: (CtTab) -> Unit,
    onSearchClick: () -> Unit,
    onCleanerClick: () -> Unit,
    onFtpClick: () -> Unit,
    onVaultClick: () -> Unit = {},
    onDuplicatesClick: () -> Unit = {},
    onAnalyzerClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    onNetworkDrivesClick: () -> Unit = {},
    onFastShareClick: () -> Unit = {},
    onDualPaneToggle: () -> Unit = {},
    isDualPaneActive: Boolean = false,
    onAmoledToggle: () -> Unit = {},
    isAmoled: Boolean = false,
    onToolsClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    modifier: Modifier = Modifier
) = CtTopHeader(
    selectedTab, onTabSelected, onSearchClick, onCleanerClick, onFtpClick,
    onVaultClick, onDuplicatesClick, onAnalyzerClick, onTrashClick,
    onNetworkDrivesClick, onFastShareClick, onDualPaneToggle, isDualPaneActive,
    onAmoledToggle, isAmoled, onToolsClick, onFavoritesClick, modifier
)

@Composable
fun CtTopHeader(
    selectedTab: CtTab,
    onTabSelected: (CtTab) -> Unit,
    onSearchClick: () -> Unit,
    onCleanerClick: () -> Unit,
    onFtpClick: () -> Unit,
    onVaultClick: () -> Unit = {},
    onDuplicatesClick: () -> Unit = {},
    onAnalyzerClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    onNetworkDrivesClick: () -> Unit = {},
    onFastShareClick: () -> Unit = {},
    onDualPaneToggle: () -> Unit = {},
    isDualPaneActive: Boolean = false,
    onAmoledToggle: () -> Unit = {},
    isAmoled: Boolean = false,
    onToolsClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab Selector (Recent vs Storage)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CtTabPill(
                    text = "Recent",
                    isSelected = selectedTab == CtTab.RECENT,
                    onClick = { onTabSelected(CtTab.RECENT) },
                    modifier = Modifier.testTag("tab_recent")
                )
                CtTabPill(
                    text = "Storage",
                    isSelected = selectedTab == CtTab.STORAGE,
                    onClick = { onTabSelected(CtTab.STORAGE) },
                    modifier = Modifier.testTag("tab_storage")
                )
            }

            // Action icons: Dual-Pane, Search, More (Clean, uncluttered top bar)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDualPaneToggle,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerticalSplit,
                        contentDescription = "Dual Pane Split Screen",
                        tint = if (isDualPaneActive) CtOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("header_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("header_more_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More tools",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        containerColor = Color.White
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tools & Utilities") },
                            leadingIcon = { Icon(Icons.Default.Widgets, contentDescription = null, tint = Color(0xFF8B5CF6)) },
                            onClick = {
                                showMoreMenu = false
                                onToolsClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Favourites") },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B)) },
                            onClick = {
                                showMoreMenu = false
                                onFavoritesClick()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Deep Cleaner") },
                            leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = CtOrange) },
                            onClick = {
                                showMoreMenu = false
                                onCleanerClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Cloud & Network Drives") },
                            leadingIcon = { Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFF0EA5E9)) },
                            onClick = {
                                showMoreMenu = false
                                onNetworkDrivesClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Cent Fast Share (Wi-Fi P2P)") },
                            leadingIcon = { Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color(0xFF10B981)) },
                            onClick = {
                                showMoreMenu = false
                                onFastShareClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Transfer to PC (FTP)") },
                            leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF6366F1)) },
                            onClick = {
                                showMoreMenu = false
                                onFtpClick()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Private Vault") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = CtOrange) },
                            onClick = {
                                showMoreMenu = false
                                onVaultClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate Finder") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF10B981)) },
                            onClick = {
                                showMoreMenu = false
                                onDuplicatesClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Storage Analyzer") },
                            leadingIcon = { Icon(Icons.Default.PieChart, contentDescription = null, tint = Color(0xFF3B82F6)) },
                            onClick = {
                                showMoreMenu = false
                                onAnalyzerClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Recycle Bin") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444)) },
                            onClick = {
                                showMoreMenu = false
                                onTrashClick()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Theme: Clean White") },
                            leadingIcon = { Icon(Icons.Default.LightMode, contentDescription = null, tint = CtOrange) },
                            onClick = {
                                showMoreMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CtTabPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        label = "pillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) CtOrange else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp
            ),
            color = textColor
        )
    }
}
