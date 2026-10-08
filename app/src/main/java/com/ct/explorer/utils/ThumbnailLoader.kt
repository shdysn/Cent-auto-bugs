package com.ct.explorer.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.ct.explorer.data.model.FileCategory
import com.ct.explorer.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * High-performance, memory-cached thumbnail loader for local media files.
 * Generates and caches real thumbnails for:
 * 1. Images (JPG, PNG, WEBP, GIF, BMP, HEIC) with EXIF orientation correction
 * 2. Videos (MP4, MKV, WEBM, MOV, etc.) - first frame extraction
 * 3. APK Packages - extracts actual app launcher icon
 */
object ThumbnailLoader {

    // Allocate 1/4th of available app memory for the bitmap thumbnail cache
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 4).coerceAtLeast(1024 * 16) // minimum 16MB

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    /**
     * Get cached bitmap synchronously if present.
     */
    fun getFromCache(path: String): Bitmap? {
        return memoryCache.get(path)
    }

    /**
     * Load thumbnail asynchronously on IO dispatcher with memory caching.
     */
    suspend fun loadThumbnail(
        context: Context,
        file: File,
        category: FileCategory,
        targetWidth: Int = 360,
        targetHeight: Int = 360
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead() || file.isDirectory) {
            return@withContext null
        }

        val cacheKey = "${file.absolutePath}_${file.lastModified()}_${targetWidth}x$targetHeight"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val bitmap: Bitmap? = try {
            when (category) {
                FileCategory.IMAGE -> decodeImageThumbnail(file, targetWidth, targetHeight)
                FileCategory.VIDEO -> decodeVideoThumbnail(file, targetWidth, targetHeight)
                FileCategory.APK -> decodeApkIcon(context, file, targetWidth, targetHeight)
                else -> null
            }
        } catch (e: Throwable) {
            null
        }

        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        }
        bitmap
    }

    private fun decodeImageThumbnail(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888 // High quality 32-bit color for clear thumbnails
        options.inDither = true

        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        // Check EXIF orientation
        return try {
            val exif = android.media.ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
            val rotationDegrees = when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } else {
                decoded
            }
        } catch (e: Exception) {
            decoded
        }
    }

    private fun decodeVideoThumbnail(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, reqWidth, reqHeight)
                    ?: retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, reqWidth, reqHeight)
            } else {
                val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                frame?.let { src ->
                    if (src.width > reqWidth || src.height > reqHeight) {
                        val scale = Math.min(reqWidth.toFloat() / src.width, reqHeight.toFloat() / src.height)
                        val w = (src.width * scale).toInt().coerceAtLeast(1)
                        val h = (src.height * scale).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(src, w, h, true)
                    } else {
                        src
                    }
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun decodeApkIcon(context: Context, file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
            info.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = file.absolutePath
                appInfo.publicSourceDir = file.absolutePath
                val iconDrawable = appInfo.loadIcon(pm)
                drawableToBitmap(iconDrawable, reqWidth, reqHeight)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val bitmap = Bitmap.createBitmap(
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Composable helper to remember and load a thumbnail for a file.
     */
    @Composable
    fun rememberThumbnailState(
        file: File,
        category: FileCategory,
        targetWidth: Int = 360,
        targetHeight: Int = 360
    ): State<Bitmap?> {
        val context = LocalContext.current
        val cacheKey = "${file.absolutePath}_${file.lastModified()}_${targetWidth}x$targetHeight"
        val initial = remember(cacheKey) { memoryCache.get(cacheKey) }

        return produceState<Bitmap?>(initialValue = initial, key1 = cacheKey) {
            if (value == null && (category == FileCategory.IMAGE || category == FileCategory.VIDEO || category == FileCategory.APK)) {
                value = loadThumbnail(context, file, category, targetWidth, targetHeight)
            }
        }
    }
}
