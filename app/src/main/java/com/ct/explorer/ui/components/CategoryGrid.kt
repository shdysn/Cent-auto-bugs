package com.ct.explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.data.model.FileCategory
import com.ct.explorer.ui.theme.*

data class MiCategory(
    val title: String,
    val icon: ImageVector,
    val iconColor: Color,
    val bgColor: Color,
    val category: FileCategory? = null,
    val isTools: Boolean = false,
    val isSocial: Boolean = false,
    val isVault: Boolean = false,
    val isFavorites: Boolean = false,
    val isCleaner: Boolean = false
)

private val categoryRow1 = listOf(
    MiCategory("Images", Icons.Default.Image, Color.White, MiBlue, FileCategory.IMAGE),
    MiCategory("Videos", Icons.Default.Movie, Color.White, MiPurple, FileCategory.VIDEO),
    MiCategory("Music", Icons.Default.Audiotrack, Color.White, MiRed, FileCategory.AUDIO),
    MiCategory("Docs", Icons.Default.Description, Color.White, MiYellow, FileCategory.DOCUMENT)
)

private val categoryRow2 = listOf(
    MiCategory("APKs", Icons.Default.InstallMobile, Color.White, MiGreen, FileCategory.APK),
    MiCategory("Downloads", Icons.Default.Download, Color.White, MiCyan, null),
    MiCategory("Archives", Icons.Default.FolderZip, Color.White, MiAmber, FileCategory.ARCHIVE),
    MiCategory("Vault", Icons.Default.Lock, Color.White, Color(0xFF10B981), null, isVault = true)
)

private val categoryRow3 = listOf(
    MiCategory("Favourites", Icons.Default.Star, Color.White, Color(0xFFF59E0B), null, isFavorites = true),
    MiCategory("Social", Icons.Default.Chat, Color.White, Color(0xFF25D366), null, isSocial = true),
    MiCategory("Cleaner", Icons.Default.CleaningServices, Color.White, Color(0xFF0EA5E9), null, isCleaner = true),
    MiCategory("Utilities", Icons.Default.Widgets, Color.White, Color(0xFF8B5CF6), null, isTools = true)
)

/**
 * Modern 4x3 Category Grid.
 * Includes media categories, Vault, Favourites, Cleaner, and Utilities.
 */
@Composable
fun CategoryGrid(
    onCategoryClick: (FileCategory, String) -> Unit,
    onToolsClick: () -> Unit,
    onSocialClick: () -> Unit,
    onVaultClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onCleanerClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rows = listOf(categoryRow1, categoryRow2, categoryRow3)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mi_category_grid"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                row.forEach { cat ->
                    CategoryTile(
                        category = cat,
                        onClick = {
                            handleCategoryClick(
                                cat = cat,
                                onToolsClick = onToolsClick,
                                onSocialClick = onSocialClick,
                                onVaultClick = onVaultClick,
                                onFavoritesClick = onFavoritesClick,
                                onCleanerClick = onCleanerClick,
                                onCategoryClick = onCategoryClick
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun handleCategoryClick(
    cat: MiCategory,
    onToolsClick: () -> Unit,
    onSocialClick: () -> Unit,
    onVaultClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onCleanerClick: () -> Unit,
    onCategoryClick: (FileCategory, String) -> Unit
) {
    when {
        cat.isTools -> onToolsClick()
        cat.isSocial -> onSocialClick()
        cat.isVault -> onVaultClick()
        cat.isFavorites -> onFavoritesClick()
        cat.isCleaner -> onCleanerClick()
        cat.category != null -> onCategoryClick(cat.category, cat.title)
        cat.title == "Downloads" -> onCategoryClick(FileCategory.UNKNOWN, "Downloads")
    }
}

@Composable
private fun CategoryTile(
    category: MiCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("category_tile_${category.title}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Compact 42dp Squircle Icon Badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(category.bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = category.title,
                tint = category.iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = category.title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
