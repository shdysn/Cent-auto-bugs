package com.ct.explorer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ct.explorer.data.model.SocialAppGroup
import com.ct.explorer.data.model.SocialFolderEntry
import com.ct.explorer.data.model.SocialFolderType
import com.ct.explorer.ui.theme.MiGreen
import com.ct.explorer.ui.theme.CtOrange
import com.ct.explorer.ui.viewmodel.ExplorerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialHubScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.socialHubState.collectAsStateWithLifecycle()
    var searchVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (state.apps.isEmpty() && !state.isLoading) {
            viewModel.loadSocialHub()
        }
    }

    // Filter apps based on search query and selected media filter
    val filteredApps = remember(state.apps, state.searchQuery, state.selectedFilter) {
        state.apps.mapNotNull { app ->
            val matchesSearch = state.searchQuery.isBlank() ||
                app.appName.contains(state.searchQuery, ignoreCase = true) ||
                app.folders.any { it.name.contains(state.searchQuery, ignoreCase = true) }

            if (!matchesSearch) return@mapNotNull null

            val matchingFolders = when (state.selectedFilter) {
                "Images" -> app.folders.filter { it.categoryHint.equals("Images", ignoreCase = true) || it.name.contains("image", ignoreCase = true) || it.name.contains("photo", ignoreCase = true) }
                "Videos" -> app.folders.filter { it.categoryHint.equals("Videos", ignoreCase = true) || it.name.contains("video", ignoreCase = true) || it.name.contains("movie", ignoreCase = true) }
                "Documents" -> app.folders.filter { it.categoryHint.equals("Documents", ignoreCase = true) || it.name.contains("doc", ignoreCase = true) }
                "Voice/Audio" -> app.folders.filter { it.categoryHint.equals("Audio", ignoreCase = true) || it.categoryHint.equals("Voice Notes", ignoreCase = true) || it.name.contains("voice", ignoreCase = true) || it.name.contains("audio", ignoreCase = true) }
                else -> app.folders
            }

            if (matchingFolders.isNotEmpty() || state.searchQuery.isNotBlank()) {
                app.copy(folders = matchingFolders)
            } else {
                null
            }
        }
    }

    val totalMediaFiles = remember(filteredApps) { filteredApps.sumOf { it.totalFiles } }
    val totalMediaBytes = remember(filteredApps) { filteredApps.sumOf { it.totalSizeBytes } }

    Scaffold(
        modifier = modifier.testTag("social_hub_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Social Media Folders",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${filteredApps.size} platforms • $totalMediaFiles items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.handleBackPress() },
                        modifier = Modifier.testTag("social_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(onClick = { viewModel.loadSocialHub() }, enabled = !state.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input Field
            if (searchVisible) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSocialSearchQuery(it) },
                    placeholder = { Text("Search social apps or folders...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CtOrange) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSocialSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Media Type Quick Filter Chips
            val filterChips = listOf("All", "Images", "Videos", "Documents", "Voice/Audio")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterChips) { filter ->
                    val isSelected = state.selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setSocialFilter(filter) },
                        label = {
                            Text(
                                text = filter,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CtOrange,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) CtOrange else MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = CtOrange,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CtOrange)
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CtOrange.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                tint = CtOrange,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No social media folders found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "WhatsApp, Telegram, Instagram and other app folders will appear here once active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadSocialHub() },
                            colors = ButtonDefaults.buttonColors(containerColor = CtOrange)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Again")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredApps, key = { it.socialType.name }) { appGroup ->
                        SocialAppCard(
                            appGroup = appGroup,
                            onFolderClick = { folder ->
                                viewModel.navigateToSocialFolder(folder)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SocialAppCard(
    appGroup: SocialAppGroup,
    onFolderClick: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val brandColor = Color(appGroup.iconHexColor)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // App Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Branded App Squircle Icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(brandColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getSocialIcon(appGroup.socialType),
                        contentDescription = appGroup.appName,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = appGroup.appName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (appGroup.isAppInstalled) {
                            Surface(
                                color = MiGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(MiGreen)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Installed",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MiGreen
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "${appGroup.folders.size} folder${if (appGroup.folders.size == 1) "" else "s"} • ${appGroup.totalFiles} items • ${appGroup.formattedTotalSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            // Sub-folders list
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                appGroup.folders.forEach { folderEntry ->
                    SocialFolderRow(
                        folderEntry = folderEntry,
                        brandColor = brandColor,
                        onClick = { onFolderClick(folderEntry.folder) }
                    )
                }
            }
        }
    }
}

@Composable
fun SocialFolderRow(
    folderEntry: SocialFolderEntry,
    brandColor: Color,
    onClick: () -> Unit
) {
    val hintIcon = when (folderEntry.categoryHint.lowercase()) {
        "images" -> Icons.Default.Image
        "videos" -> Icons.Default.Movie
        "documents" -> Icons.Default.Description
        "audio" -> Icons.Default.Audiotrack
        "voice notes", "voice" -> Icons.Default.Mic
        "stickers" -> Icons.Default.EmojiEmotions
        "gifs" -> Icons.Default.Gif
        else -> Icons.Default.Folder
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(brandColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = hintIcon,
                    contentDescription = null,
                    tint = brandColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderEntry.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${folderEntry.fileCount} item${if (folderEntry.fileCount == 1) "" else "s"} • ${folderEntry.formattedSize}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open folder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

fun getSocialIcon(socialType: SocialFolderType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (socialType) {
        SocialFolderType.WHATSAPP -> Icons.Default.Chat
        SocialFolderType.TELEGRAM -> Icons.Default.Send
        SocialFolderType.INSTAGRAM -> Icons.Default.PhotoCamera
        SocialFolderType.FACEBOOK -> Icons.Default.ThumbUp
        SocialFolderType.MESSENGER -> Icons.Default.FlashOn
        SocialFolderType.TIKTOK -> Icons.Default.MusicVideo
        SocialFolderType.SNAPCHAT -> Icons.Default.AutoAwesome
        SocialFolderType.TWITTER -> Icons.Default.Tag
        SocialFolderType.YOUTUBE -> Icons.Default.SmartDisplay
        SocialFolderType.REDDIT -> Icons.Default.Forum
        SocialFolderType.DISCORD -> Icons.Default.SportsEsports
        SocialFolderType.PINTEREST -> Icons.Default.PushPin
        SocialFolderType.LINKEDIN -> Icons.Default.Work
        SocialFolderType.WECHAT -> Icons.Default.Forum
        SocialFolderType.SHAREME -> Icons.Default.WifiTethering
    }
}
