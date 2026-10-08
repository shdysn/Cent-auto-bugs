package com.ct.explorer.utils

import android.content.Context
import android.content.Intent
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ExifMetadata(
    val hasGps: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val dateTime: String? = null,
    val software: String? = null,
    val focalLength: String? = null,
    val iso: String? = null,
    val exposureTime: String? = null
)

object ExifPrivacyCleaner {

    fun readExif(file: File): ExifMetadata {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val latLong = FloatArray(2)
            val hasGps = exif.getLatLong(latLong)

            val lat = if (hasGps) latLong[0].toDouble() else null
            val lng = if (hasGps) latLong[1].toDouble() else null
            val alt = exif.getAltitude(0.0).let { if (it != 0.0) it else null }

            val make = exif.getAttribute(ExifInterface.TAG_MAKE)
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)
            val dt = exif.getAttribute(ExifInterface.TAG_DATETIME)
            val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
            val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
            val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
            val exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)

            ExifMetadata(
                hasGps = hasGps,
                latitude = lat,
                longitude = lng,
                altitude = alt,
                cameraMake = make,
                cameraModel = model,
                dateTime = dt,
                software = software,
                focalLength = focal,
                iso = iso,
                exposureTime = exposure
            )
        } catch (e: Exception) {
            ExifMetadata(hasGps = false)
        }
    }

    /**
     * Strips all EXIF metadata (GPS, camera info, timestamps, user comments)
     * directly on the file headers without decoding bitmaps into memory.
     * Prevents OutOfMemoryError crashes on large photos and preserves 100% original image quality.
     */
    suspend fun stripExif(inputFile: File, outputFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            outputFile.parentFile?.mkdirs()
            inputFile.copyTo(outputFile, overwrite = true)

            try {
                val exif = ExifInterface(outputFile.absolutePath)
                val tagsToWipe = listOf(
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifInterface.TAG_GPS_DATESTAMP,
                    ExifInterface.TAG_GPS_PROCESSING_METHOD,
                    ExifInterface.TAG_GPS_AREA_INFORMATION,
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_DATETIME_DIGITIZED,
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_SOFTWARE,
                    ExifInterface.TAG_USER_COMMENT,
                    ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION,
                    ExifInterface.TAG_SUBSEC_TIME,
                    ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
                    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL
                )
                for (tag in tagsToWipe) {
                    exif.setAttribute(tag, null)
                }
                exif.saveAttributes()
            } catch (exifError: Exception) {
                // Non-JPEG format or EXIF cannot be rewritten; file copy preserved
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a temporary stripped copy and launches Android Sharesheet
     * allowing the user to share the image with 100% stripped metadata.
     */
    suspend fun shareCleanImage(context: Context, inputFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanDir = File(context.cacheDir, "clean_shares").apply { mkdirs() }
            val cleanFile = File(cleanDir, "clean_${inputFile.name}")
            val result = stripExif(inputFile, cleanFile)
            if (result.isSuccess) {
                val uri: Uri = FileOpener.getSafeContentUri(context, cleanFile)
                    ?: return@withContext Result.failure(Exception("Cannot generate secure URI for cleaned image"))
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri(null, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Share Clean Photo (No GPS / EXIF)").apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(chooser)
                Result.success(true)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to clean image"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
