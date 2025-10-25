package com.qimian233.ztool.settingactivity.ota;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.qimian233.ztool.R;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class OtaSettings extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchDisableAudio;
    private LinearLayout layoutOtaInfo;
    private FloatingActionButton fabRestart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        //绑定视图
        setContentView(R.layout.activity_ota_settings);

        String appName = getIntent().getStringExtra("app_name");
        appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + " - 详细设置");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 初始化工具类
        mPrefsUtils = new ModulePreferencesUtils(this);

        initViews();
        loadSettings();
        initRestartButton();
    }

    private void initViews() {
        // 初始化视图

        // 禁用游戏音频优化设置
        switchDisableAudio = findViewById(R.id.switch_disable_OtaCHeck);
        switchDisableAudio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("disable_OtaCheck",isChecked);
            }
        });

        // OTA信息拉取功能
        layoutOtaInfo = findViewById(R.id.layout_ota_info);
        layoutOtaInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchOtaInfo();
            }
        });
    }

    private void loadSettings() {
        // 加载禁用游戏音频优化设置
        boolean removeBlacklistEnabled = mPrefsUtils.loadBooleanSetting("disable_OtaCheck", false);
        switchDisableAudio.setChecked(removeBlacklistEnabled);
    }

    private void saveSettings(String moduleName,Boolean newValue) {
        // 保存禁用游戏音频优化设置
        mPrefsUtils.saveBooleanSetting(moduleName, newValue);
    }

    private void fetchOtaInfo() {
        // 在后台线程中执行文件读取操作
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String filePath = "/data_mirror/data_ce/null/0/com.lenovo.tbengine/shared_prefs/lenovo_row_ota_package_info.xml";
                    String xmlContent = readFileWithRoot(filePath);
                    Map<String, String> otaInfo = parseOtaInfoXml(xmlContent);

                    // 回到UI线程显示对话框
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showOtaInfoDialog(otaInfo);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(OtaSettings.this, "读取OTA信息失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private String readFileWithRoot(String filePath) throws IOException, InterruptedException {
        // 方法1：使用DataOutputStream方式（推荐）
        Process process = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(process.getOutputStream());
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        // 发送cat命令
        os.writeBytes("cat " + filePath + "\n");
        os.writeBytes("exit\n");
        os.flush();

        StringBuilder content = new StringBuilder();
        StringBuilder errorContent = new StringBuilder();
        String line;

        // 读取正常输出
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }

        // 读取错误输出
        while ((line = errorReader.readLine()) != null) {
            errorContent.append(line).append("\n");
        }

        reader.close();
        errorReader.close();
        os.close();

        int exitCode = process.waitFor();

        Log.i("readFileWithRoot", "Exit code: " + exitCode);
        Log.i("readFileWithRoot", "Content length: " + content.length());
        Log.i("readFileWithRoot", "Error content: " + errorContent.toString());

        if (exitCode != 0 || content.length() == 0) {
            throw new IOException("Root command failed. Exit code: " + exitCode +
                    ", Error: " + errorContent.toString());
        }

        return content.toString();
    }


    private Map<String, String> parseOtaInfoXml(String xmlContent) throws Exception {
        Map<String, String> otaInfo = new HashMap<>();
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(xmlContent));

        int eventType = parser.getEventType();
        String currentKey = null;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tagName = parser.getName();
                if ("string".equals(tagName) || "int".equals(tagName) || "long".equals(tagName) || "boolean".equals(tagName)) {
                    currentKey = parser.getAttributeValue(null, "name");
                    if ("string".equals(tagName)) {
                        // 字符串值在标签内容中
                        eventType = parser.next();
                        if (eventType == XmlPullParser.TEXT) {
                            String value = parser.getText();
                            otaInfo.put(currentKey, value);
                        }
                    } else {
                        // 其他类型值在value属性中
                        String value = parser.getAttributeValue(null, "value");
                        otaInfo.put(currentKey, value);
                    }
                }
            }
            eventType = parser.next();
        }
        return otaInfo;
    }

    private String getChangelogByLocale(Map<String, String> otaInfo) {
        Locale currentLocale = getResources().getConfiguration().locale;
        String language = currentLocale.getLanguage();
        String country = currentLocale.getCountry();

        // 尝试匹配精确的语言地区
        String preciseKey = "HashMap." + language + "_" + country;
        if (otaInfo.containsKey(preciseKey)) {
            return otaInfo.get(preciseKey);
        }

        // 尝试匹配语言
        String languageKey = "HashMap." + language;
        if (otaInfo.containsKey(languageKey)) {
            return otaInfo.get(languageKey);
        }

        // 默认返回英文
        return otaInfo.getOrDefault("HashMap.en", "无可用更新日志");
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private void showOtaInfoDialog(Map<String, String> otaInfo) {
        // 创建自定义布局
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_ota_info, null);

        // 获取视图引用
        TextView textVersionInfo = dialogView.findViewById(R.id.text_version_info);
        TextView textChangelog = dialogView.findViewById(R.id.text_changelog);
        TextView textDownloadInfo = dialogView.findViewById(R.id.text_download_info);

        // 提取OTA信息
        String fromVersion = otaInfo.getOrDefault("mUpdateFromVersion", "未知");
        String toVersion = otaInfo.getOrDefault("updateToVersion", "未知");
        String downloadUrl = otaInfo.getOrDefault("downloadUrl", "无下载链接");
        String sizeStr = otaInfo.getOrDefault("size", "0");
        String md5 = otaInfo.getOrDefault("md5", "未知");
        String changelog = getChangelogByLocale(otaInfo);

        // 格式化文件大小
        long size = Long.parseLong(sizeStr);
        String formattedSize = formatFileSize(size);

        // 设置显示内容
        textVersionInfo.setText(String.format("当前版本: %s\n新版本: %s", fromVersion, toVersion));
        textChangelog.setText(changelog);
        textDownloadInfo.setText(String.format("下载链接: %s\n文件大小: %s\nMD5: %s", downloadUrl, formattedSize, md5));

        // 创建对话框
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("OTA更新信息")
                .setView(dialogView)
                .setPositiveButton("复制下载链接", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        copyToClipboard(downloadUrl);
                        Toast.makeText(OtaSettings.this, "下载链接已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("复制更新日志", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 构建不包含下载链接的更新日志信息
                        String changelogText = String.format(
                                "版本信息:\n当前版本: %s\n新版本: %s\n\n更新日志:\n%s\n\n文件信息:\n文件大小: %s\nMD5: %s",
                                fromVersion, toVersion, changelog, formattedSize, md5
                        );
                        copyToClipboard(changelogText);
                        Toast.makeText(OtaSettings.this, "更新日志已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("关闭", null)
                .show();
    }

    private void initRestartButton() {
        fabRestart = findViewById(R.id.fab_restart);
        fabRestart.setOnClickListener(v -> showRestartConfirmationDialog());
    }

    private void showRestartConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("重启XP模块作用域")
                .setMessage("是否重启此页的XP模块作用域？这将强行停止 " + appPackageName + " 的进程。")
                .setPositiveButton("确定", (dialog, which) -> forceStopApp())
                .setNegativeButton("取消", null)
                .show();
    }

    private void forceStopApp() {
        if (appPackageName == null || appPackageName.isEmpty()) {
            return;
        }

        try {
            Process process = Runtime.getRuntime().exec("su -c am force-stop " + appPackageName);
            process.waitFor(); // 等待命令执行完成
        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(this, "强制停止失败", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("OTA信息", text);
        clipboard.setPrimaryClip(clip);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
