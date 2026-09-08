package com.qimian233.ztool.utils

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Objects
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileUtils {

    /**
     * 将多个文件打包为zip
     * @param files 要打包的文件数组
     * @param outputZip 输出的zip文件
     * @return 是否成功
     */
    fun createZipFromFiles(files: Array<File>?, outputZip: File?): Boolean {
        if (files == null || files.isEmpty() || outputZip == null) return false

        try {
            FileOutputStream(outputZip).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    val buffer = ByteArray(1024)

                    for (file in files) {
                        if (!file.exists() || file.isDirectory) continue

                        FileInputStream(file).use { fis ->
                            val zipEntry = ZipEntry(file.name)
                            zos.putNextEntry(zipEntry)

                            var length: Int
                            while (fis.read(buffer).also { length = it } > 0) {
                                zos.write(buffer, 0, length)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 将目录打包为zip，保留子目录结构
     * @param sourceDir 要打包的源目录
     * @param outputZip 输出的zip文件
     * @return 是否成功
     */
    fun createZipFromDirectory(sourceDir: File?, outputZip: File?): Boolean {
        if (sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory || outputZip == null) return false

        try {
            FileOutputStream(outputZip).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    val buffer = ByteArray(1024)
                    val basePath = sourceDir.absolutePath

                    addFilesToZip(sourceDir, basePath, zos, buffer)
                }
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun addFilesToZip(dir: File, basePath: String, zos: ZipOutputStream, buffer: ByteArray) {
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                addFilesToZip(file, basePath, zos, buffer)
            } else {
                // Normalize path separators for zip entries
                val relativePath = file.absolutePath.substring(basePath.length + 1).replace(File.separatorChar, '/')

                FileInputStream(file).use { fis ->
                    val zipEntry = ZipEntry(relativePath)
                    zos.putNextEntry(zipEntry)

                    var length: Int
                    while (fis.read(buffer).also { length = it } > 0) {
                        zos.write(buffer, 0, length)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    // 递归删除目录
    fun deleteRecursive(fileOrDirectory: File?) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return

        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        fileOrDirectory.delete()
    }

    // 读取文件内容为字符串
    fun readFileContent(file: File): String? {
        return try {
            FileInputStream(file).use { inputStream ->
                val buffer = ByteArray(file.length().toInt())
                val read = inputStream.read(buffer)
                if (read == -1) null else String(buffer, StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
    }

    // 写入字符串到文件
    @Throws(IOException::class)
    fun writeStringToFile(file: File, content: String) {
        FileOutputStream(file).use { fos ->
            fos.write(content.toByteArray(StandardCharsets.UTF_8))
        }
    }

    // 复制 Assets 到普通目录
    fun copyAssetsToDirectory(context: Context, assetsPath: String, targetDir: File): Boolean {
        val assetManager = context.assets
        return try {
            val files = assetManager.list(assetsPath)
            if (files == null || files.isEmpty()) {
                // 文件
                assetManager.open(assetsPath).use { input ->
                    FileOutputStream(File(targetDir, File(assetsPath).name)).use { out ->
                        val buffer = ByteArray(1024)
                        var length: Int
                        while (input.read(buffer).also { length = it } > 0) {
                            out.write(buffer, 0, length)
                        }
                    }
                }
            } else {
                // 目录
                if (!targetDir.exists() && !targetDir.mkdirs()) return true
                for (file in files) {
                    val fullAssetsPath = if (assetsPath.isEmpty()) file else assetsPath + "/" + file
                    val targetFile = File(targetDir, file)
                    if (Objects.requireNonNull(assetManager.list(fullAssetsPath)).isNotEmpty()) {
                        if (!targetFile.mkdirs()) return true
                        if (copyAssetsToDirectory(context, fullAssetsPath, targetFile)) return true
                    } else {
                        assetManager.open(fullAssetsPath).use { input ->
                            FileOutputStream(targetFile).use { out ->
                                val buffer = ByteArray(1024)
                                var length: Int
                                while (input.read(buffer).also { length = it } > 0) {
                                    out.write(buffer, 0, length)
                                }
                            }
                        }
                    }
                }
            }
            false // Success
        } catch (e: IOException) {
            true // Failure
        }
    }
}
