package com.ct.explorer

import com.ct.explorer.data.model.FileCategory
import com.ct.explorer.data.model.FileItem
import com.ct.explorer.utils.HashCalculator
import com.ct.explorer.utils.InAppPackageInstallerHelper
import com.ct.explorer.utils.InstallerStatusBus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FeatureCompletenessTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testValidAndInvalidPackageNameValidation() {
        assertTrue(InAppPackageInstallerHelper.isValidPackageName("com.pkstudio.ctexplorer.app"))
        assertTrue(InAppPackageInstallerHelper.isValidPackageName("org.mozilla.firefox"))
        assertTrue(InAppPackageInstallerHelper.isValidPackageName("com.google.android.apps.photos"))

        assertFalse(InAppPackageInstallerHelper.isValidPackageName(null))
        assertFalse(InAppPackageInstallerHelper.isValidPackageName(""))
        assertFalse(InAppPackageInstallerHelper.isValidPackageName("   "))
        assertFalse(InAppPackageInstallerHelper.isValidPackageName("Unknown"))
        assertFalse(InAppPackageInstallerHelper.isValidPackageName("singleword"))
        assertFalse(InAppPackageInstallerHelper.isValidPackageName("123.numeric.prefix"))
    }

    @Test
    fun testInstallerStatusFailureReasonFormatting() {
        val blockedReason = InstallerStatusBus.formatFailureReason(
            android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED,
            null
        )
        assertTrue(blockedReason.contains("blocked", ignoreCase = true))

        val conflictReason = InstallerStatusBus.formatFailureReason(
            android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT,
            null
        )
        assertTrue(conflictReason.contains("conflicting", ignoreCase = true))

        val storageReason = InstallerStatusBus.formatFailureReason(
            android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE,
            null
        )
        assertTrue(storageReason.contains("storage", ignoreCase = true))
    }

    @Test
    fun testFileItemCategoryResolution() {
        val imgFile = tempFolder.newFile("photo.jpg")
        val imgItem = FileItem(imgFile)
        assertEquals(FileCategory.IMAGE, imgItem.category)

        val vidFile = tempFolder.newFile("movie.mp4")
        val vidItem = FileItem(vidFile)
        assertEquals(FileCategory.VIDEO, vidItem.category)

        val audioFile = tempFolder.newFile("song.mp3")
        val audioItem = FileItem(audioFile)
        assertEquals(FileCategory.AUDIO, audioItem.category)

        val docFile = tempFolder.newFile("report.pdf")
        val docItem = FileItem(docFile)
        assertEquals(FileCategory.DOCUMENT, docItem.category)

        val apkFile = tempFolder.newFile("test.apk")
        val apkItem = FileItem(apkFile)
        assertEquals(FileCategory.APK, apkItem.category)

        val zipFile = tempFolder.newFile("data.zip")
        val zipItem = FileItem(zipFile)
        assertEquals(FileCategory.ARCHIVE, zipItem.category)
    }

    @Test
    fun testFileItemExtensionAndNaming() {
        val testFile = tempFolder.newFile("document_final_v2.docx")
        val item = FileItem(testFile)
        assertEquals("docx", item.extension.lowercase())
        assertEquals("document_final_v2.docx", item.name)
        assertFalse(item.isDirectory)
    }

    @Test
    fun testHashCalculatorConsistency() = runBlocking {
        val sample = tempFolder.newFile("hash_data.bin")
        val data = ByteArray(4096) { it.toByte() }
        sample.writeBytes(data)

        val hashResult1 = HashCalculator.calculateHashes(sample)
        val hashResult2 = HashCalculator.calculateHashes(sample)

        assertTrue(hashResult1.isSuccess)
        assertTrue(hashResult2.isSuccess)
        assertEquals(hashResult1.getOrNull()?.md5, hashResult2.getOrNull()?.md5)
        assertEquals(hashResult1.getOrNull()?.sha256, hashResult2.getOrNull()?.sha256)
    }

    @Test
    fun testVideoPlayerWidgetConstantsAndConfiguration() {
        assertEquals("EXTRA_WIDGET_TARGET", com.ct.explorer.widget.CtVideoWidgetProvider.EXTRA_WIDGET_TARGET)
        assertEquals("VIDEO_PLAYER", com.ct.explorer.widget.CtVideoWidgetProvider.TARGET_VIDEO_PLAYER)
        assertEquals("VIDEO_GALLERY", com.ct.explorer.widget.CtVideoWidgetProvider.TARGET_VIDEO_GALLERY)
    }
}
