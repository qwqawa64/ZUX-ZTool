package com.qimian233.ztool

import com.qimian233.ztool.utils.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

/**
 * Covers the zip packaging used by the log export: the log directory keeps its
 * subdirectory structure and the generated system brief is appended at the archive root
 * without disturbing the directory content.
 */
class FileUtilsZipTest {

    @Test
    fun directoryContentAndExtraEntriesLandInOneZip() {
        val root = createTempDir()
        try {
            val sourceDir = File(root, "Log")
            File(sourceDir, "app").mkdirs()
            File(sourceDir, "lsposed").mkdirs()
            File(sourceDir, "app/example.log").writeText("app log", StandardCharsets.UTF_8)
            File(sourceDir, "lsposed/lspd.log").writeText("lsposed log", StandardCharsets.UTF_8)

            val outputZip = File(root, "logs.zip")
            val brief = "ZTool system brief"
            val success = FileUtils.createZipFromDirectory(
                sourceDir,
                outputZip,
                mapOf("system_brief.txt" to brief.toByteArray(StandardCharsets.UTF_8))
            )

            assertTrue("zip creation should succeed", success)
            ZipFile(outputZip).use { zip ->
                assertNotNull(zip.getEntry("app/example.log"))
                assertNotNull(zip.getEntry("lsposed/lspd.log"))
                val briefEntry = zip.getEntry("system_brief.txt")
                assertNotNull("system brief must be at the archive root", briefEntry)
                val content = zip.getInputStream(briefEntry).bufferedReader(StandardCharsets.UTF_8).readText()
                assertEquals(brief, content)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingSourceDirectoryFailsEvenWithExtraEntries() {
        val root = createTempDir()
        try {
            val outputZip = File(root, "logs.zip")
            val success = FileUtils.createZipFromDirectory(
                File(root, "does-not-exist"),
                outputZip,
                mapOf("system_brief.txt" to "brief".toByteArray(StandardCharsets.UTF_8))
            )

            assertTrue("a zip is only produced from an existing directory", !success)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createTempDir(): File =
        File.createTempFile("ztool-zip-test", "").let { file ->
            file.delete()
            file.mkdirs()
            file
        }
}
