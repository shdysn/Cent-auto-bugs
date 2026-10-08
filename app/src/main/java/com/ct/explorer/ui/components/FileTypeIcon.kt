package com.ct.explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.utils.FileIconDescriptor
import com.ct.explorer.utils.FileIconHelper
import java.io.File

/**
 * Reusable Material Design 3 Composable to display appropriate file and folder icons
 * with squircle badge styling, branded tints, and format badges.
 */
@Composable
fun FileTypeIcon(
    item: FileItem,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    showBadge: Boolean = true
) {
    val descriptor = remember(item.path, item.name, item.isDirectory) {
        FileIconHelper.getDescriptor(item)
    }
    FileTypeIcon(
        descriptor = descriptor,
        modifier = modifier,
        size = size,
        iconSize = iconSize,
        showBadge = showBadge
    )
}

/**
 * Display file type icon directly from a File reference.
 */
@Composable
fun FileTypeIcon(
    file: File,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    showBadge: Boolean = true
) {
    val descriptor = remember(file.absolutePath, file.name, file.isDirectory) {
        FileIconHelper.getDescriptor(file)
    }
    FileTypeIcon(
        descriptor = descriptor,
        modifier = modifier,
        size = size,
        iconSize = iconSize,
        showBadge = showBadge
    )
}

/**
 * Display file type icon directly from a FileIconDescriptor.
 */
@Composable
fun FileTypeIcon(
    descriptor: FileIconDescriptor,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    showBadge: Boolean = true
) {
    val cornerRadius = size * 0.3f

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(descriptor.backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = descriptor.icon,
            contentDescription = descriptor.categoryLabel,
            tint = descriptor.tintColor,
            modifier = Modifier.size(iconSize)
        )

        // Type badge in bottom-right corner for specific formats (e.g. "PDF", "XLS", "DOC", "APK")
        if (showBadge && descriptor.badgeText != null && size >= 36.dp) {
            Surface(
                color = descriptor.tintColor,
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 2.dp, end = 2.dp)
            ) {
                Text(
                    text = descriptor.badgeText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 7.sp,
                    lineHeight = 9.sp,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
        }
    }
}
