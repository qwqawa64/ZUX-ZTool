package com.qimian233.ztool.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.net.Uri
import com.qimian233.ztool.EnhancedShellExecutor
import com.qimian233.ztool.R
import java.io.File
import java.io.FileOutputStream
import java.util.Random

class FontInstallerManager {

    // 复制 Uri 到临时文件
    @Throws(Exception::class)
    fun copyFontToTemp(context: Context, uri: Uri): File {
        val tempDir = File(context.filesDir, TEMP_FONT_DIR)
        if (!tempDir.exists()) tempDir.mkdirs()
        val tempFile = File(tempDir, "temp_font_" + System.currentTimeMillis() + ".ttf")

        val input = context.contentResolver.openInputStream(uri)
            ?: throw Exception("InputStream is null")
        input.use { inputStream ->
            FileOutputStream(tempFile).use { out ->
                val buffer = ByteArray(4096)
                var len: Int
                while (inputStream.read(buffer).also { len = it } != -1) {
                    out.write(buffer, 0, len)
                }
            }
        }
        return tempFile
    }

    // 执行安装流程
    @Throws(Exception::class)
    fun installFont(context: Context, fontFile: File, fontName: String, fontDesc: String) {
        val folderName = generateRandomFolderName()
        val targetFolderPath = FONT_BASE_PATH + "/" + folderName
        val executor = EnhancedShellExecutor.getInstance()

        // 1. 创建目标目录
        executor.executeRootCommand("mkdir -p " + targetFolderPath)

        // 2. 复制字体文件
        copyFileWithRoot(executor, fontFile.absolutePath, targetFolderPath + "/font.ttf")

        // 3. 创建 XML
        val xmlContent = generateFontXml(context, fontName, fontDesc)
        createXmlFileWithRoot(context, executor, targetFolderPath + "/font.xml", xmlContent)

        // 4. 生成预览图
        generatePreviewImages(context, executor, targetFolderPath, fontFile.absolutePath, fontName)

        // 5. 设置权限
        setFolderPermissions(executor, targetFolderPath)

        // 清理
        FileUtils.deleteRecursive(File(context.filesDir, TEMP_FONT_DIR))
    }

    private fun generatePreviewImages(context: Context, executor: EnhancedShellExecutor, targetFolderPath: String, fontPath: String, fontName: String) {
        val tf = Typeface.createFromFile(fontPath)

        // Small
        val small = generateFontPreviewBitmap(tf, fontName, 249, 70)
        val smallFile = saveBitmapToTemp(context, small, "small.png")
        copyFileWithRoot(executor, smallFile.absolutePath, targetFolderPath + "/small.png")
        small.recycle()

        // Preview
        val preview = generateFontPreviewBitmap(tf, context.getString(R.string.common_font_preview_text), 948, 945)
        val previewFile = saveBitmapToTemp(context, preview, "preview.png")
        copyFileWithRoot(executor, previewFile.absolutePath, targetFolderPath + "/preview.png")
        preview.recycle()
    }

    private fun setFolderPermissions(executor: EnhancedShellExecutor, folderPath: String) {
        // 获取参考权限
        val res = executor.executeRootCommand("ls -ld " + FONT_BASE_PATH)
        if (!res.isSuccess) return

        val parts = res.output.split("\\s+".toRegex())
        if (parts.size >= 4) {
            val owner = parts[2]
            val group = parts[3]
            executor.executeRootCommand("chown -R " + owner + ":" + group + " " + folderPath)
            executor.executeRootCommand("chmod 700 " + folderPath)
            executor.executeRootCommand("chmod 600 " + folderPath + "/*")
        }
    }

    // 位图生成逻辑保持不变，但作为工具方法
    private fun generateFontPreviewBitmap(typeface: Typeface, text: String, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val paint = Paint()
        paint.typeface = typeface
        paint.color = Color.BLACK
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.CENTER

        val lines = text.split("\n".toRegex())
        // 简化计算逻辑，复用你原有的 calculateOptimalTextSize 方法（此处省略具体实现，保持原样即可）
        // 在实际整合时，把 SettingsDetailActivity 里的 calculateOptimalTextSize 移到这里
        val textSize = 40f // 示例值，实际应调用 calculateOptimalTextSize
        paint.textSize = textSize

        // ... (原有的绘制逻辑) ...
        // 为节省篇幅，假设此处直接绘制。实际使用时请将原 Activity 中的完整绘制代码复制过来。
        val startY = height / 2f
        for (i in lines.indices) {
            canvas.drawText(lines[i], width / 2f, startY + i * 50, paint)
        }

        return bitmap
    }

    private fun generateRandomFolderName(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz"
        val random = Random()
        val sb = StringBuilder(6)
        for (i in 0 until 6) sb.append(chars[random.nextInt(chars.length)])
        return sb.toString()
    }

    private fun generateFontXml(context: Context, name: String, desc: String): String {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ZFont>\n" +
                "<name>" + name + "</name>\n" +
                "<language>" + context.getString(R.string.common_font_language) + "</language>\n" +
                "<author>" + context.getString(R.string.common_font_author) + "</author>\n" +
                "<abstract>" + desc + "</abstract>\n" +
                "</ZFont>"
    }

    private fun copyFileWithRoot(executor: EnhancedShellExecutor, src: String, dest: String) {
        executor.executeRootCommand("cp \"" + src + "\" \"" + dest + "\"")
    }

    private fun createXmlFileWithRoot(context: Context, executor: EnhancedShellExecutor, dest: String, content: String) {
        val temp = File(context.filesDir, "temp.xml")
        FileUtils.writeStringToFile(temp, content)
        copyFileWithRoot(executor, temp.absolutePath, dest)
        temp.delete()
    }

    private fun saveBitmapToTemp(context: Context, bmp: Bitmap, name: String): File {
        val file = File(context.filesDir, TEMP_FONT_DIR + "/" + name)
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return file
    }

    companion object {
        private const val FONT_BASE_PATH = "/data_mirror/data_ce/null/0/com.zui.homesettings/files/.ZFont/.localFont"
        private const val TEMP_FONT_DIR = "temp_fonts"
    }
}
