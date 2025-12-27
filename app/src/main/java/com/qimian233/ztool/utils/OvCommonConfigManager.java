package com.qimian233.ztool.utils;

import android.content.Context;
import android.util.Log;
import android.util.Xml;

import com.qimian233.ztool.EnhancedShellExecutor;
import com.qimian233.ztool.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OvCommonConfigManager {
    private static final String SYSTEM_FILE_PATH = "/data/system/zui/ov_common_persist_user_0.xml";
    private static final String TEMP_FILE_NAME = "ov_config_temp.xml";

    // 对应 <user_persist> 的配置模型
    public static class AppConfig {
        public Boolean overrideSplitSupport;     // overrideSplitSupport
        public Boolean overrideFreeformSupport;  // overrideFreeformSupport
        public Integer overrideFreeformDragMode; // overrideFreeformDragMode (1=Free, 0=Fixed)

        // 判断是否所有配置都为空（如果是，则需要删除该条目）
        public boolean isEmpty() {
            return overrideSplitSupport == null && overrideFreeformSupport == null && overrideFreeformDragMode == null;
        }
    }

    // 加载配置：System -> Cache -> Map
    public Map<String, AppConfig> loadConfig(Context context) {
        Map<String, AppConfig> configMap = new HashMap<>();
        EnhancedShellExecutor executor = EnhancedShellExecutor.getInstance();
        File tempFile = new File(context.getCacheDir(), TEMP_FILE_NAME);

        // 1. 尝试将系统文件复制到缓存
        // 如果文件不存在，直接返回空 Map
        EnhancedShellExecutor.ShellResult checkRes = executor.executeRootCommand("ls " + SYSTEM_FILE_PATH);
        if (!checkRes.isSuccess()) {
            return configMap; // 文件不存在，返回空
        }

        executor.executeRootCommand("cp " + SYSTEM_FILE_PATH + " " + tempFile.getAbsolutePath());
        executor.executeRootCommand("chmod 644 " + tempFile.getAbsolutePath()); // 确保 App 可读

        // 2. 解析 XML
        if (tempFile.exists()) {
            try (FileInputStream fis = new FileInputStream(tempFile)) {
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser parser = factory.newPullParser();
                parser.setInput(fis, "UTF-8");

                int eventType = parser.getEventType();
                String currentPackage = null;
                AppConfig currentConfig = null;

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    String tagName = parser.getName();
                    switch (eventType) {
                        case XmlPullParser.START_TAG:
                            if ("config".equals(tagName)) {
                                currentPackage = parser.getAttributeValue(null, "packageName");
                                currentConfig = new AppConfig();
                            } else if ("user_persist".equals(tagName) && currentConfig != null) {
                                String split = parser.getAttributeValue(null, "overrideSplitSupport");
                                String freeform = parser.getAttributeValue(null, "overrideFreeformSupport");
                                String dragMode = parser.getAttributeValue(null, "overrideFreeformDragMode");

                                if (split != null) currentConfig.overrideSplitSupport = "1".equals(split);
                                if (freeform != null) currentConfig.overrideFreeformSupport = "1".equals(freeform);
                                if (dragMode != null) currentConfig.overrideFreeformDragMode = Integer.parseInt(dragMode);
                            }
                            break;

                        case XmlPullParser.END_TAG:
                            if ("config".equals(tagName) && currentPackage != null && currentConfig != null) {
                                if (!currentConfig.isEmpty()) {
                                    configMap.put(currentPackage, currentConfig);
                                }
                                currentPackage = null;
                                currentConfig = null;
                            }
                            break;
                    }
                    eventType = parser.next();
                }
            } catch (Exception e) {
                e.printStackTrace();
                // 解析失败视为文件损坏或空，返回部分或空数据
            }
            tempFile.delete();
        }
        return configMap;
    }

    // 保存配置：Map -> XML -> Cache -> System
    public String saveConfig(Context context, Map<String, AppConfig> configMap) {
        File tempFile = new File(context.getCacheDir(), TEMP_FILE_NAME);
        EnhancedShellExecutor executor = EnhancedShellExecutor.getInstance();

        try {
            // 1. 构建 XML 字符串
            XmlSerializer serializer = Xml.newSerializer();
            StringWriter writer = new StringWriter();
            serializer.setOutput(writer);
            serializer.startDocument("UTF-8", true);
            serializer.text("\n");
            serializer.startTag(null, "configs");

            for (Map.Entry<String, AppConfig> entry : configMap.entrySet()) {
                AppConfig cfg = entry.getValue();
                if (cfg == null || cfg.isEmpty()) continue; // 跳过空配置

                serializer.text("\n  ");
                serializer.startTag(null, "config");
                serializer.attribute(null, "packageName", entry.getKey());

                serializer.text("\n    ");
                serializer.startTag(null, "user_persist");

                if (cfg.overrideSplitSupport != null) {
                    serializer.attribute(null, "overrideSplitSupport", cfg.overrideSplitSupport ? "1" : "0");
                }
                if (cfg.overrideFreeformSupport != null) {
                    serializer.attribute(null, "overrideFreeformSupport", cfg.overrideFreeformSupport ? "1" : "0");
                }
                if (cfg.overrideFreeformDragMode != null) {
                    serializer.attribute(null, "overrideFreeformDragMode", String.valueOf(cfg.overrideFreeformDragMode));
                }

                serializer.endTag(null, "user_persist");
                serializer.text("\n  ");
                serializer.endTag(null, "config");
            }

            serializer.text("\n");
            serializer.endTag(null, "configs");
            serializer.endDocument();

            // 2. 写入临时文件
            FileUtils.writeStringToFile(tempFile, writer.toString());

            // 3. 移动回系统目录并设置权限
            // 注意：/data/system/zui/ 可能需要 mkdir，虽然通常它是存在的
            String cmd = "mkdir -p /data/system/zui/ && " +
                    "cp " + tempFile.getAbsolutePath() + " " + SYSTEM_FILE_PATH + " && " +
                    "chown 1000:1000 " + SYSTEM_FILE_PATH + " && " + // 关键：system 用户组
                    "chmod 660 " + SYSTEM_FILE_PATH; // 关键：读写权限

            EnhancedShellExecutor.ShellResult result = executor.executeRootCommand(cmd);

            // 清理
            tempFile.delete();

            if (result.isSuccess()) {
                return "success";
            } else {
                return context.getString(R.string.error_shell_command_failed, result.error);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    // --- 业务逻辑辅助方法 ---

    // 模式定义
    public static final int MODE_SPLIT_SCREEN = 1;
    public static final int MODE_FREEFORM_FREE = 2;  // 自由小窗 (DragMode=1)
    public static final int MODE_FREEFORM_FIXED = 3; // 固定比例小窗 (DragMode=0)

    // 获取当前开启了某项功能的包名列表
    public List<String> getPackagesForMode(Map<String, AppConfig> map, int mode) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, AppConfig> entry : map.entrySet()) {
            AppConfig cfg = entry.getValue();
            switch (mode) {
                case MODE_SPLIT_SCREEN:
                    if (Boolean.TRUE.equals(cfg.overrideSplitSupport)) list.add(entry.getKey());
                    break;
                case MODE_FREEFORM_FREE:
                    if (Boolean.TRUE.equals(cfg.overrideFreeformSupport) &&
                            cfg.overrideFreeformDragMode != null && cfg.overrideFreeformDragMode == 1) {
                        list.add(entry.getKey());
                    }
                    break;
                case MODE_FREEFORM_FIXED:
                    if (Boolean.TRUE.equals(cfg.overrideFreeformSupport) &&
                            cfg.overrideFreeformDragMode != null && cfg.overrideFreeformDragMode == 0) {
                        list.add(entry.getKey());
                    }
                    break;
            }
        }
        return list;
    }

    // 更新配置逻辑：根据用户选择的列表，更新 Map
    public void updateConfigForMode(Map<String, AppConfig> map, List<String> selectedPackages, int mode) {
        // 1. 遍历现有的 Map，清理掉该模式下不再选中的包
        // 注意：为了避免并发修改异常，先收集要修改的 Key
        for (Map.Entry<String, AppConfig> entry : map.entrySet()) {
            String pkg = entry.getKey();
            AppConfig cfg = entry.getValue();

            // 如果该包不在新选中的列表中，且当前配置了该模式，则移除该配置
            if (!selectedPackages.contains(pkg)) {
                removeModeFromConfig(cfg, mode);
            }
        }

        // 2. 遍历选中的包，添加/更新配置
        for (String pkg : selectedPackages) {
            AppConfig cfg = map.get(pkg);
            if (cfg == null) {
                cfg = new AppConfig();
                map.put(pkg, cfg);
            }
            addModeToConfig(cfg, mode);
        }
    }

    private void removeModeFromConfig(AppConfig cfg, int mode) {
        switch (mode) {
            case MODE_SPLIT_SCREEN:
                cfg.overrideSplitSupport = null;
                break;
            case MODE_FREEFORM_FREE:
                // 如果当前是自由模式，才移除。防止误伤固定模式
                if (Boolean.TRUE.equals(cfg.overrideFreeformSupport) &&
                        cfg.overrideFreeformDragMode != null && cfg.overrideFreeformDragMode == 1) {
                    cfg.overrideFreeformSupport = null;
                    cfg.overrideFreeformDragMode = null;
                }
                break;
            case MODE_FREEFORM_FIXED:
                // 如果当前是固定模式，才移除
                if (Boolean.TRUE.equals(cfg.overrideFreeformSupport) &&
                        cfg.overrideFreeformDragMode != null && cfg.overrideFreeformDragMode == 0) {
                    cfg.overrideFreeformSupport = null;
                    cfg.overrideFreeformDragMode = null;
                }
                break;
        }
    }

    private void addModeToConfig(AppConfig cfg, int mode) {
        switch (mode) {
            case MODE_SPLIT_SCREEN:
                cfg.overrideSplitSupport = true;
                break;
            case MODE_FREEFORM_FREE:
                cfg.overrideFreeformSupport = true;
                cfg.overrideFreeformDragMode = 1; // 1 = Free
                break;
            case MODE_FREEFORM_FIXED:
                cfg.overrideFreeformSupport = true;
                cfg.overrideFreeformDragMode = 0; // 0 = Fixed
                break;
        }
    }
}
