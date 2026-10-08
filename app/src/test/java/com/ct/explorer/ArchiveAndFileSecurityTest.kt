package com.ct.explorer

import com.ct.explorer.data.model.FileItem
import com.ct.explorer.utils.ArchiveHelper
import com.ct.explorer.utils.HashCalculator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveAndFileSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFileItemByteFormatting() {
        assertEquals("0 B", FileItem.formatBytes(0L))
        assertEquals("1.0 KB", FileItem.formatBytes(1024L))
        assertEquals("1.0 MB", FileItem.formatBytes(1024L * 1024L))
        assertEquals("1.50 GB", FileItem.formatBytes((1.5 * 1024L * 1024L * 1024L).toLong()))
    }

    @Test
    fun testHashCalculatorProducesAccurateChecksums() = runBlocking {
        val testFile = tempFolder.newFile("sample_hash_test.txt")
        testFile.writeText("CentFileManagerTestString123")

        val result = HashCalculator.calculateHashes(testFile)
        assertTrue(result.isSuccess)
        val hashes = result.getOrNull()
        assertNotNull(hashes)
        assertEquals(28L, hashes?.fileSize)
        assertFalse(hashes?.md5.isNullOrBlank())
        assertFalse(hashes?.sha1.isNullOrBlank())
        assertFalse(hashes?.sha256.isNullOrBlank())
    }

    @Test
    fun testZipSlipPathTraversalIsBlocked() = runBlocking {
        val traversalZip = tempFolder.newFile("traversal_test.zip")
        ZipOutputStream(FileOutputStream(traversalZip)).use { zos ->
            // Try to write outside the extraction destination
            zos.putNextEntry(ZipEntry("../traversal_sample.txt"))
            zos.write("traversal test content".toByteArray())
            zos.closeEntry()
        }

        val extractDest = tempFolder.newFolder("safe_extract_dir")
        val extractResult = ArchiveHelper.extractArchive(
            file = traversalZip,
            destDir = extractDest
        ) { _, _ -> }

        // Must fail with a SecurityException detecting Zip Slip
        assertTrue(extractResult.isFailure)
        val exception = extractResult.exceptionOrNull()
        assertTrue(exception is SecurityException)
        assertTrue(exception?.message?.contains("Zip Slip") == true)
    }

    @Test
    fun testNormalArchiveExtractsSuccessfully() = runBlocking {
        val normalZip = tempFolder.newFile("normal.zip")
        ZipOutputStream(FileOutputStream(normalZip)).use { zos ->
            zos.putNextEntry(ZipEntry("documents/hello.txt"))
            zos.write("hello world from cent file manager".toByteArray())
            zos.closeEntry()
        }

        val extractDest = tempFolder.newFolder("extracted_normal")
        val extractResult = ArchiveHelper.extractArchive(
            file = normalZip,
            destDir = extractDest
        ) { _, _ -> }

        assertTrue(extractResult.isSuccess)
        val extractedFile = File(extractDest, "documents/hello.txt")
        assertTrue(extractedFile.exists())
        assertEquals("hello world from cent file manager", extractedFile.readText())
    }

    @Test
    fun testLargeFileTruncationGuardThreshold() {
        val maxSafeChars = 150_000
        val normalText = "A".repeat(10_000)
        val oversizedText = "B".repeat(160_000)

        assertFalse(normalText.length > maxSafeChars)
        assertTrue(oversizedText.length > maxSafeChars)
        assertEquals(150_000, oversizedText.take(maxSafeChars).length)
    }

    @Test
    fun testChecksumCalculationForEmptyFile() = runBlocking {
        val emptyFile = tempFolder.newFile("empty_file.txt")
        val result = HashCalculator.calculateHashes(emptyFile)
        assertTrue(result.isSuccess)
        val hashes = result.getOrNull()
        assertNotNull(hashes)
        assertEquals(0L, hashes?.fileSize)
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", hashes?.md5?.lowercase())
    }
}
