package com.qimian233.ztool.data.zuiui

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZuiComponentScannerTest {

    private lateinit var dir: File
    private var counter = 0

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("zui-scan").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `widget package is detected`() {
        assertTrue(scan(apk("classes.dex" to dex("Lzui/widget/ProgressBar;"))))
    }

    @Test
    fun `dialog controller package is detected`() {
        assertTrue(scan(apk("classes.dex" to dex("Lcom/zui/internal/app/DialogController;"))))
    }

    @Test
    fun `preference package is detected`() {
        assertTrue(scan(apk("classes.dex" to dex("Lzui/preference/Preference;"))))
    }

    @Test
    fun `appcompat package is detected`() {
        assertTrue(scan(apk("classes.dex" to dex("Lzui/appcompat/widget/Toolbar;"))))
    }

    @Test
    fun `plain app is not detected`() {
        assertFalse(scan(apk("classes.dex" to dex("Lcom/example/app/MainActivity;"))))
    }

    @Test
    fun `non component zui package is not detected`() {
        assertFalse(
            scan(
                apk(
                    "classes.dex" to dex(
                        "Lzui/util/ReflectClass;",
                        "Lzui/platform/R;",
                        "Lzui/themes/ThemeConfig;"
                    )
                )
            )
        )
    }

    @Test
    fun `sibling package sharing the prefix is not detected`() {
        assertFalse(scan(apk("classes.dex" to dex("Lzui/widgets/Widget;"))))
    }

    @Test
    fun `same leaf under a different parent is not detected`() {
        assertFalse(scan(apk("classes.dex" to dex("Lcom/zui/widget/Widget;"))))
    }

    @Test
    fun `marker straddling the read window is detected`() {
        val chunk = 64 * 1024
        val filler = ByteArray(chunk - 5) { 'A'.code.toByte() }
        val content = filler + "Lzui/widget/ProgressBar;".toByteArray(Charsets.US_ASCII)
        assertTrue(scan(apk("classes.dex" to content)))
    }

    @Test
    fun `marker in a secondary dex is detected`() {
        assertTrue(
            scan(
                apk(
                    "classes.dex" to dex("Lcom/example/app/MainActivity;"),
                    "classes2.dex" to dex("Lzui/app/MessageDialog;")
                )
            )
        )
    }

    @Test
    fun `marker in a split apk is detected`() {
        val base = apk("classes.dex" to dex("Lcom/example/app/MainActivity;"))
        val split = apk("classes.dex" to dex("Lzui/widget/SeekBar;"))
        assertTrue(ZuiComponentScanner.containsZuiComponents(listOf(base, split)))
    }

    @Test
    fun `missing apk yields no verdict`() {
        assertFalse(ZuiComponentScanner.containsZuiComponents(listOf("does-not-exist.apk")))
    }

    @Test
    fun `non dex entries are ignored`() {
        assertFalse(scan(apk("assets/zui.txt" to "Lzui/widget/ProgressBar;".toByteArray())))
    }

    private fun scan(path: String) = ZuiComponentScanner.containsZuiComponents(listOf(path))

    /** Descriptors are NUL separated so a match can never span a junction. */
    private fun dex(vararg descriptors: String): ByteArray =
        ("dex\n035\u0000" + descriptors.joinToString("\u0000"))
            .toByteArray(Charsets.US_ASCII)

    private fun apk(vararg entries: Pair<String, ByteArray>): String {
        val file = File(dir, "apk-${counter++}.apk")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file.absolutePath
    }
}
