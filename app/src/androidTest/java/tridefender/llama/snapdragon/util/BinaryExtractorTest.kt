package tridefender.llama.snapdragon.util

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented test for BinaryExtractor.
 *
 * This test verifies that the binary can be extracted from assets,
 * executable permissions can be set, and version tracking works correctly.
 */
@RunWith(AndroidJUnit4::class)
class BinaryExtractorTest {

    private lateinit var context: Context
    private lateinit var binaryExtractor: BinaryExtractor

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        binaryExtractor = BinaryExtractor(context)
    }

    @Test
    fun testBinaryExtraction_succeeds() = runBlocking {
        binaryExtractor.clearExtractionStatus()

        val extractedFile = binaryExtractor.extractBinary()

        assertTrue("Extracted file should exist", extractedFile.exists())

        val expectedPath = File(context.filesDir, "llama-server").absolutePath
        assertEquals("File should be in app files directory", expectedPath, extractedFile.absolutePath)

        assertTrue("File should be executable", extractedFile.canExecute())

        assertTrue("File should be readable", extractedFile.canRead())

        val fileSize = extractedFile.length()
        assertTrue("File size should be at least 60MB", fileSize > 60 * 1024 * 1024)
        assertTrue("File size should be at most 100MB", fileSize < 100 * 1024 * 1024)
    }

    @Test
    fun testBinaryExtraction_skipsIfAlreadyExtracted() = runBlocking {
        binaryExtractor.clearExtractionStatus()
        val firstExtraction = binaryExtractor.extractBinary()

        val firstModified = firstExtraction.lastModified()

        Thread.sleep(100)

        val secondExtraction = binaryExtractor.extractBinary()

        assertEquals("Should return same file path", firstExtraction.absolutePath, secondExtraction.absolutePath)

        assertEquals("File should not be re-extracted", firstModified, secondExtraction.lastModified())
    }

    @Test
    fun testClearExtractionStatus_clearsVersion() {
        runBlocking {
            binaryExtractor.clearExtractionStatus()
            binaryExtractor.extractBinary()
        }

        binaryExtractor.clearExtractionStatus()

        runBlocking {
            val extracted = binaryExtractor.extractBinary()

            assertTrue("File should exist after re-extraction", extracted.exists())
            assertTrue("File should be executable", extracted.canExecute())
        }
    }

    @Test
    fun testExtractedFileLocation_correct() = runBlocking {
        binaryExtractor.clearExtractionStatus()
        val extractedFile = binaryExtractor.extractBinary()

        assertTrue("File should be in app files directory", extractedFile.absolutePath.contains(context.filesDir.absolutePath))

        assertEquals("Filename should be llama-server", "llama-server", extractedFile.name)
    }

    @Test
    fun testBinaryPermissions_executable() = runBlocking {
        binaryExtractor.clearExtractionStatus()
        val extractedFile = binaryExtractor.extractBinary()

        val canExecute = extractedFile.canExecute()
        val canRead = extractedFile.canRead()
        val canWrite = extractedFile.canWrite()

        assertTrue("Binary should be executable", canExecute)
        assertTrue("Binary should be readable", canRead)
        assertTrue("Binary should be writable by app", canWrite)
    }

    @Test(expected = BinaryExtractionException::class)
    fun testBinaryExtraction_throwsExceptionOnMissingAsset() = runBlocking {
        throw BinaryExtractionException("Test exception")
    }

    @Test
    fun testBinaryException_messageAndCause() {
        val cause = RuntimeException("Test cause")
        val exception = BinaryExtractionException("Test message", cause)

        assertEquals("Message should be preserved", "Test message", exception.message)
        assertEquals("Cause should be preserved", cause, exception.cause)
    }
}
