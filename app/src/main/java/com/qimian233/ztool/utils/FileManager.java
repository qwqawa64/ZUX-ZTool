package com.qimian233.ztool.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
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
    public static boolean saveConfigWithSAF(Context context, Uri uri, String fileName, String configContent) {
        ContentResolver resolver = context.getContentResolver();
        if (uri != null) {
            try (OutputStream outputStream = resolver.openOutputStream(uri)){
                if (outputStream != null) {
                    outputStream.write(configContent.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    Log.i(TAG, "配置已保存到" + uri + fileName);
                    return true;
                } else {
                    Log.e(TAG, "输出流为空");
                    return false;
                }
            } catch (IOException e) {
                Log.e(TAG, "保存文件失败: " + e.getMessage());
                return false;
            }
        } else {
                Log.e(TAG,"uri为空");
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
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8));
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
            Log.e("SAF", "SAF 读取文件失败: " + e.getMessage());
        }
        return null;
    }


    /**
     * 生成备份文件名
     */
    public static String generateBackupFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        return "ZTool_Config_Backup_" + timestamp + ".json";
    }

}