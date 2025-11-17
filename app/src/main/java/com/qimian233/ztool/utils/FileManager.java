package com.qimian233.ztool.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Android API 33+ 文件管理工具类
 * 支持 SAF (Storage Access Framework) 和 MediaStore 方式读写文件
 */
public class FileManager {
    private static final String TAG = "FileManager";

    /**
     * 使用 SAF 创建文件并保存配置
     */
    public static boolean saveConfigWithSAF(Context context, String content, String fileName) {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);

            // 需要调用方在 Activity 中处理结果
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "SAF 创建文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 使用 SAF 打开并读取文件
     */
    public static String readConfigWithSAF(Context context, Uri uri) {
        try {
            ContentResolver resolver = context.getContentResolver();
            InputStream inputStream = resolver.openInputStream(uri);
            if (inputStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                reader.close();
                inputStream.close();
                return stringBuilder.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "SAF 读取文件失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 使用 MediaStore 保存配置到 Downloads 目录
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static boolean saveConfigToDownloads(Context context, String content, String fileName) {
        try {
            ContentResolver resolver = context.getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            contentValues.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
            if (uri != null) {
                try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                    if (outputStream != null) {
                        outputStream.write(content.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                        Log.i(TAG, "配置已保存到 Downloads: " + fileName);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "保存到 Downloads 失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 使用 MediaStore 从 Downloads 目录读取配置
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static String readConfigFromDownloads(Context context, String fileName) {
        try {
            ContentResolver resolver = context.getContentResolver();
            String selection = MediaStore.Downloads.DISPLAY_NAME + "=?";
            String[] selectionArgs = new String[]{fileName};

            Uri uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            try (InputStream inputStream = resolver.openInputStream(uri)) {
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    reader.close();
                    return stringBuilder.toString();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "从 Downloads 读取失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 将配置内容写入指定的 URI
     * @param context 上下文
     * @param uri 目标文件的 URI
     * @param content 要写入的内容
     * @return 写入是否成功
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static boolean writeConfigToUri(Context context, Uri uri, String content) {
        try {
            ContentResolver resolver = context.getContentResolver();
            try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                if (outputStream != null) {
                    outputStream.write(content.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    Log.i(TAG, "配置已成功写入到: " + uri);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "写入文件失败: " + e.getMessage());
        }
        return false;
    }


    /**
     * 生成备份文件名
     */
    public static String generateBackupFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        return "ZTool_Config_Backup_" + timestamp + ".json";
    }

    /**
     * 检查是否支持 SAF
     */
    public static boolean supportsSAF() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    /**
     * 检查是否支持 MediaStore
     */
    public static boolean supportsMediaStore() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }
}