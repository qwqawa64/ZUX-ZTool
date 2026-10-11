package com.qimian233.ztool.data.zuiui

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Detects whether an APK bundles the ZUI frontend component packages.
 *
 * Scans the dex string table in bounded windows, so memory stays flat regardless of APK
 * size and an unreadable APK simply yields `false`.
 */
object ZuiComponentScanner {

    /** Descriptor prefixes of the UI component packages (the narrow breadth). */
    val NARROW_PREFIXES: List<String> = listOf(
        "Lzui/app/",
        "Lzui/widget/",
        "Lzui/appcompat/",
        "Lzui/preference/",
        "Lcom/zui/internal/app/"
    )

    /** Every prefix contains it, so one needle scan gates the prefix verification. */
    private val NEEDLE = "zui/".toByteArray(Charsets.US_ASCII)
    private val PREFIXES = NARROW_PREFIXES.map { it.toByteArray(Charsets.US_ASCII) }
    private val PREFIX_OFFSETS = PREFIXES.map { indexOf(it, NEEDLE, 0, it.size) }

    /** Keeps a prefix that straddles a chunk boundary verifiable on the next read. */
    private const val CARRY = 32
    private const val CHUNK = 64 * 1024

    /** True when any of [apkPaths] (base plus splits) contains a component package. */
    fun containsZuiComponents(apkPaths: List<String>): Boolean {
        for (path in apkPaths) {
            if (scanApk(path)) return true
        }
        return false
    }

    private fun scanApk(path: String): Boolean {
        val file = File(path)
        if (!file.isFile) return false
        return try {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || !entry.name.endsWith(".dex")) continue
                    zip.getInputStream(entry).use { stream ->
                        if (scanStream(stream)) return true
                    }
                }
            }
            false
        } catch (_: Throwable) {
            // An unreadable or malformed APK simply yields no verdict.
            false
        }
    }

    private fun scanStream(stream: InputStream): Boolean {
        val buffer = ByteArray(CHUNK + CARRY)
        var carry = 0
        while (true) {
            val read = stream.read(buffer, carry, CHUNK)
            if (read <= 0) return false
            val end = carry + read
            if (findInBuffer(buffer, end)) return true
            carry = minOf(end, CARRY)
            System.arraycopy(buffer, end - carry, buffer, 0, carry)
        }
    }

    private fun findInBuffer(buffer: ByteArray, end: Int): Boolean {
        var from = 0
        while (true) {
            val hit = indexOf(buffer, NEEDLE, from, end)
            if (hit < 0) return false
            if (matchesPrefix(buffer, hit, end)) return true
            from = hit + 1
        }
    }

    /** [hit] indexes the `zui/` of a candidate; the prefix may extend past [end]. */
    private fun matchesPrefix(buffer: ByteArray, hit: Int, end: Int): Boolean {
        for (i in PREFIXES.indices) {
            val prefix = PREFIXES[i]
            val start = hit - PREFIX_OFFSETS[i]
            if (start < 0 || start + prefix.size > end) continue
            var matched = true
            for (j in prefix.indices) {
                if (buffer[start + j] != prefix[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }
        return false
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int, end: Int): Int {
        val limit = end - needle.size
        var i = from
        while (i <= limit) {
            if (haystack[i] == needle[0]) {
                var j = 1
                while (j < needle.size && haystack[i + j] == needle[j]) j++
                if (j == needle.size) return i
            }
            i++
        }
        return -1
    }
}
