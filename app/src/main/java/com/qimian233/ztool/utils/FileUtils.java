package com.qimian233.ztool.utils;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileUtils {
    
    /**
     * 将多个文件打包为zip
     * @param files 要打包的文件数组
     * @param outputZip 输出的zip文件
     * @return 是否成功
     */
    public static boolean createZipFromFiles(File[] files, File outputZip) {
        if (files == null || files.length == 0 || outputZip == null) return false;
        
        try (FileOutputStream fos = new FileOutputStream(outputZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            byte[] buffer = new byte[1024];
            
            for (File file : files) {
                if (!file.exists() || file.isDirectory()) continue;
                
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(file.getName());
                    zos.putNextEntry(zipEntry);
                    
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                    zos.closeEntry();
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将目录打包为zip，保留子目录结构
     * @param sourceDir 要打包的源目录
     * @param outputZip 输出的zip文件
     * @return 是否成功
     */
    public static boolean createZipFromDirectory(File sourceDir, File outputZip) {
        if (sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory() || outputZip == null) return false;

        try (FileOutputStream fos = new FileOutputStream(outputZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            byte[] buffer = new byte[1024];
            String basePath = sourceDir.getAbsolutePath();

            addFilesToZip(sourceDir, basePath, zos, buffer);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void addFilesToZip(File dir, String basePath, ZipOutputStream zos, byte[] buffer) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                addFilesToZip(file, basePath, zos, buffer);
            } else {
                String relativePath = file.getAbsolutePath().substring(basePath.length() + 1);
                // Normalize path separators for zip entries
                relativePath = relativePath.replace(File.separatorChar, '/');

                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(relativePath);
                    zos.putNextEntry(zipEntry);

                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    // 递归删除目录
    public static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) return;

        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    // 读取文件内容为字符串
    public static String readFileContent(File file) {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = inputStream.read(buffer);
            if (read == -1) return null;
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // 写入字符串到文件
    public static void writeStringToFile(File file, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    // 复制 Assets 到普通目录
    public static boolean copyAssetsToDirectory(Context context, String assetsPath, File targetDir) {
        AssetManager assetManager = context.getAssets();
        try {
            String[] files = assetManager.list(assetsPath);
            if (files == null || files.length == 0) {
                // 文件
                try (InputStream in = assetManager.open(assetsPath);
                     FileOutputStream out = new FileOutputStream(new File(targetDir, new File(assetsPath).getName()))) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }
                }
            } else {
                // 目录
                if (!targetDir.exists() && !targetDir.mkdirs()) return true;
                for (String file : files) {
                    String fullAssetsPath = assetsPath.isEmpty() ? file : assetsPath + "/" + file;
                    File targetFile = new File(targetDir, file);
                    if (Objects.requireNonNull(assetManager.list(fullAssetsPath)).length > 0) {
                        if (!targetFile.mkdirs()) return true;
                        if (copyAssetsToDirectory(context, fullAssetsPath, targetFile)) return true;
                    } else {
                        try (InputStream in = assetManager.open(fullAssetsPath);
                             FileOutputStream out = new FileOutputStream(targetFile)) {
                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = in.read(buffer)) > 0) {
                                out.write(buffer, 0, length);
                            }
                        }
                    }
                }
            }
            return false; // Success
        } catch (IOException e) {
            return true; // Failure
        }
    }
}
