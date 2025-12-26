package com.qimian233.ztool.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Base64;

import com.qimian233.ztool.EnhancedShellExecutor;
import com.qimian233.ztool.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EmbeddingConfigManager {
    private static final String MODULE_CONFIG_FILE = "/data/adb/modules/zuxos_embedding/embedding_config.json";

    public static class ConfigFileInfo {
        public File file;
        public String timestamp;
        public String packageName;
        public String appName;
        public String configContent;
    }

    // 加载配置文件列表
    public List<ConfigFileInfo> loadAndValidateConfigFiles(Context context) {
        List<ConfigFileInfo> validConfigs = new ArrayList<>();
        File configDir = new File(context.getFilesDir(), "data/custom_EmbeddingConfig");

        if (!configDir.exists() || !configDir.isDirectory()) return validConfigs;

        File[] files = configDir.listFiles();
        if (files == null) return validConfigs;

        for (File file : files) {
            if (file.isFile()) {
                ConfigFileInfo info = parseConfigFile(context, file);
                if (info != null) {
                    validConfigs.add(info);
                } else {
                    file.delete();
                }
            }
        }
        return validConfigs;
    }

    private ConfigFileInfo parseConfigFile(Context context, File file) {
        try {
            ConfigFileInfo info = new ConfigFileInfo();
            info.file = file;
            String[] parts = file.getName().split("_", 2);
            if (parts.length != 2) return null;

            long ts = Long.parseLong(parts[0]);
            info.timestamp = new SimpleDateFormat("yyyy.MM.dd-HH:mm:ss", Locale.getDefault()).format(new Date(ts * 1000L));
            info.packageName = parts[1];
            info.appName = getAppName(context, info.packageName);

            String base64 = FileUtils.readFileContent(file);
            if (base64 == null) return null;

            info.configContent = new String(Base64.decode(base64, Base64.DEFAULT), StandardCharsets.UTF_8);

            // 简单验证 JSON
            new JSONObject(info.configContent);
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    private String getAppName(Context context, String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    // 核心功能：刷入配置
    public void flashConfigs(Context context, List<ConfigFileInfo> configs) throws Exception {
        EnhancedShellExecutor executor = EnhancedShellExecutor.getInstance();
        File cacheDir = context.getCacheDir();
        File tempDir = new File(cacheDir, "module_temp");
        File tempJsonFile = new File(tempDir, "embedding_config.json");

        // 1. 准备环境
        FileUtils.deleteRecursive(tempDir);
        if (!tempDir.mkdirs()) throw new Exception(context.getString(R.string.error_create_temp_dir));

        // 2. 复制原配置到临时目录 (Root -> App Cache)
        String cpCmd = "cat " + MODULE_CONFIG_FILE + " > " + tempJsonFile.getAbsolutePath();
        EnhancedShellExecutor.ShellResult cpRes = executor.executeRootCommand(cpCmd);
        if (!cpRes.isSuccess()) throw new Exception(context.getString(R.string.error_copy_original_config));

        // 3. 修改权限以便 App 读取
        int uid = android.os.Process.myUid();
        executor.executeRootCommand("chown " + uid + "." + uid + " " + tempJsonFile.getAbsolutePath());
        executor.executeRootCommand("chmod 644 " + tempJsonFile.getAbsolutePath());

        // 4. 解析 JSON 并合并
        String originalContent = FileUtils.readFileContent(tempJsonFile);
        if (originalContent == null) throw new Exception(context.getString(R.string.error_read_original_config));

        JSONObject rootJson = new JSONObject(originalContent);
        JSONArray packages = rootJson.getJSONArray("packages");

        for (ConfigFileInfo config : configs) {
            JSONObject newConfig = new JSONObject(config.configContent);
            String pkgName = newConfig.getString("name");

            JSONArray mergedPackages = new JSONArray();
            // 过滤掉旧的同名配置
            for (int i = 0; i < packages.length(); i++) {
                JSONObject p = packages.getJSONObject(i);
                if (!p.getString("name").equals(pkgName)) {
                    mergedPackages.put(p);
                }
            }
            // 添加新配置
            mergedPackages.put(newConfig);
            packages = mergedPackages;
        }
        rootJson.put("packages", packages);

        // 5. 写回临时文件
        FileUtils.writeStringToFile(tempJsonFile, rootJson.toString(2));

        // 6. 覆盖回系统目录 (Root)
        String restoreCmd = "cp " + tempJsonFile.getAbsolutePath() + " " + MODULE_CONFIG_FILE + " && " +
                "chmod 644 " + MODULE_CONFIG_FILE;
        EnhancedShellExecutor.ShellResult restoreRes = executor.executeRootCommand(restoreCmd);

        FileUtils.deleteRecursive(tempDir); // 清理

        if (!restoreRes.isSuccess()) {
            throw new Exception(context.getString(R.string.error_update_module_config));
        }
    }
}
