package com.qimian233.ztool.settingactivity.ota;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.qimian233.ztool.EnhancedShellExecutor;
import com.qimian233.ztool.LoadingDialog;
import com.qimian233.ztool.R;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;
import com.qimian233.ztool.utils.GetPCFlashFirmware;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class OtaSettings extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchDisableOTACheck;
    private LoadingDialog loadingDialog;

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
            getSupportActionBar().setTitle(appName + getString(R.string.ota_settings_title_suffix));
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

        // 启用本地安装选项
        switchDisableOTACheck = findViewById(R.id.switch_disable_OtaCHeck);
        switchDisableOTACheck.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("disable_OtaCheck", isChecked));

        // OTA信息拉取功能
        LinearLayout layoutOtaInfo = findViewById(R.id.layout_ota_info);
        layoutOtaInfo.setOnClickListener(v -> fetchOtaInfo());

        // 获取9008刷机包功能
        LinearLayout layoutGet9008Firmware = findViewById(R.id.layout_fetch_pc_flash_firmware);
        layoutGet9008Firmware.setOnClickListener(v -> {
            // 创建并显示dialog
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pcflash_fetch, null);

            // 首先获取本机SN并设置hint
            TextInputLayout textInputLayout = dialogView.findViewById(R.id.text_input_layout);
            if (textInputLayout != null) {
                String machineSN = getMachineSNByProps();
                if (machineSN != null && !machineSN.isEmpty()) {
                    textInputLayout.setHint(getString(R.string.SN_current_machine_hint, machineSN));
                } else {
                    textInputLayout.setHint(R.string.SN_default_hint);
                }
            }

            builder.setView(dialogView)
                   .setTitle(R.string.PCFlashFirmwareFetch_title)
                   .setPositiveButton(R.string.confirm, (dialog, which) -> {
                       TextInputEditText etMachineSN = dialogView.findViewById(R.id.et_machine_sn);
                       String inputSN = Objects.requireNonNull(etMachineSN.getText()).toString().trim();
                       if (!inputSN.isEmpty()) {
                           // 使用输入的SN
                           getPCFlashFirmwareLink(inputSN);
                       } else {
                           // 使用本机SN
                           getPCFlashFirmwareLink(getMachineSNByProps());
                       }
                   })
                   .setNegativeButton(R.string.cancel, null)
                   .show();
        });
    }

    private void loadSettings() {
        // 加载开启本地安装选项
        boolean removeBlacklistEnabled = mPrefsUtils.loadBooleanSetting("disable_OtaCheck", false);
        switchDisableOTACheck.setChecked(removeBlacklistEnabled);
    }

    private void saveSettings(String moduleName, Boolean newValue) {
        // 保存开启本地安装选项
        mPrefsUtils.saveBooleanSetting(moduleName, newValue);
    }

    private void fetchOtaInfo() {
        // 在后台线程中执行文件读取操作
        new Thread(() -> {
            try {
                String filePath = "/data_mirror/data_ce/null/0/com.lenovo.tbengine/shared_prefs/lenovo_row_ota_package_info.xml";
                String xmlContent = readFileWithRoot(filePath);
                Map<String, String> otaInfo = parseOtaInfoXml(xmlContent);

                // 回到UI线程显示对话框
                runOnUiThread(() -> showOtaInfoDialog(otaInfo));
            } catch (Exception e) {
                // e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(OtaSettings.this, getString(R.string.ota_info_fetch_failed) + e.getMessage(), Toast.LENGTH_LONG).show());
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
        Log.i("readFileWithRoot", "Error content: " + errorContent);

        if (exitCode != 0 || content.length() == 0) {
            throw new IOException("Root command failed. Exit code: " + exitCode +
                    ", Error: " + errorContent);
        }

        return content.toString();
    }


    private Map<String, String> parseOtaInfoXml(String xmlContent) throws Exception {
        Map<String, String> otaInfo = new HashMap<>();
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(xmlContent));

        int eventType = parser.getEventType();
        String currentKey;

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
        return otaInfo.getOrDefault("HashMap.en", getString(R.string.no_changelog_available));
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
        String fromVersion = otaInfo.getOrDefault("mUpdateFromVersion", getString(R.string.unknown));
        String toVersion = otaInfo.getOrDefault("updateToVersion", getString(R.string.unknown));
        String downloadUrl = otaInfo.getOrDefault("downloadUrl", getString(R.string.no_download_link));
        String sizeStr = otaInfo.getOrDefault("size", "0");
        String md5 = otaInfo.getOrDefault("md5", getString(R.string.unknown));
        String changelog = getChangelogByLocale(otaInfo);

        // 格式化文件大小
        assert sizeStr != null;
        long size = Long.parseLong(sizeStr);
        String formattedSize = formatFileSize(size);

        // 设置显示内容
        textVersionInfo.setText(String.format(getString(R.string.version_info_format), fromVersion, toVersion));
        textChangelog.setText(changelog);
        textDownloadInfo.setText(String.format(getString(R.string.download_info_format), downloadUrl, formattedSize, md5));

        // 创建对话框
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.ota_update_info_title)
                .setView(dialogView)
                .setPositiveButton(R.string.copy_download_link, (dialog, which) -> {
                    copyToClipboard(downloadUrl);
                    Toast.makeText(OtaSettings.this, R.string.download_link_copied, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.copy_changelog, (dialog, which) -> {
                    // 构建不包含下载链接的更新日志信息
                    String changelogText = String.format(
                            getString(R.string.changelog_full_format),
                            fromVersion, toVersion, changelog, formattedSize, md5
                    );
                    copyToClipboard(changelogText);
                    Toast.makeText(OtaSettings.this, R.string.changelog_copied, Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.close, null)
                .show();
    }

    // 从三个Property中读取SN，优先使用GSN
    private String getMachineSNByProps(){
        EnhancedShellExecutor shellExecutor = EnhancedShellExecutor.getInstance();
        EnhancedShellExecutor.ShellResult shellResult = shellExecutor.executeRootCommand("getprop ro.odm.lenovo.gsn",5);
        if (shellResult.isSuccess() && !shellResult.output.isEmpty()) return shellResult.output;
        shellResult = shellExecutor.executeRootCommand("getprop ro.serialno",5);
        if (shellResult.isSuccess() && !shellResult.output.isEmpty()) return shellResult.output;
        shellResult = shellExecutor.executeRootCommand("getprop ro.boot.serialno",5);
        if (shellResult.isSuccess() && !shellResult.output.isEmpty()) return shellResult.output;
        return null;
    }

    // 根据提供的SN码从联想服务器拉取9008救砖包
    // 使用异步方式获取固件信息
    private void getPCFlashFirmwareLink(String machineSN) {
        // 创建并显示加载对话框
        loadingDialog = new LoadingDialog(this);
        loadingDialog.show(getString(R.string.fetching_firmware_info));

        GetPCFlashFirmware utils = new GetPCFlashFirmware();
        utils.queryFirmwareAsync(machineSN, this::handleFirmwareInfoResult);
    }

    private void showFirmwareInfoDialog(String[] firmwareInfo){
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);

        if (firmwareInfo == null || firmwareInfo.length < 6) {
            builder.setTitle(R.string.PCFlashFirmwareFetch_error)
                    .setMessage(R.string.PCFlashFirmwareFetch_failed_message)
                    .setNegativeButton(R.string.confirm, null)
                    .show();
            return;
        }

        // 使用字符串资源构建消息，完全替换硬编码
        String message = getString(R.string.firmware_download_link) + firmwareInfo[0] + "\n"
                + getString(R.string.firmware_extract_password) + firmwareInfo[1] + "\n"
                + getString(R.string.firmware_platform_and_method) + firmwareInfo[2]
                + getString(R.string.firmware_platform_suffix) + firmwareInfo[3] + "\n"
                + getString(R.string.firmware_first_upload_time) + formatTimestamp(Long.parseLong(firmwareInfo[4])) + "\n"
                + getString(R.string.firmware_last_update_time) + formatTimestamp(Long.parseLong(firmwareInfo[5]));

        builder.setTitle(R.string.PCFlashFirmwareFetch_result)
                .setMessage(message)
                .setPositiveButton(R.string.copy_download_link, (dialog, which) -> {
                    copyToClipboard(firmwareInfo[0]);
                    Toast.makeText(OtaSettings.this, R.string.download_link_copied, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.copy_password, (dialog, which) -> {
                    copyToClipboard(firmwareInfo[1]);
                    Toast.makeText(OtaSettings.this, R.string.password_copied, Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.close, null)
                .show();
    }
    // 处理固件信息结果的回调方法
    private void handleFirmwareInfoResult(String[] firmwareInfo) {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                if (firmwareInfo != null && firmwareInfo.length >= 6) {
                    loadingDialog.updateMessage(getString(R.string.firmware_fetch_success));
                    // 延迟一小段时间再显示结果，让用户看到成功消息
                    new Handler().postDelayed(() -> showFirmwareInfoDialog(firmwareInfo), 200);
                    loadingDialog.dismiss();
                } else {
                    loadingDialog.updateMessage(getString(R.string.firmware_fetch_failed));
                    // 延迟后显示错误对话框
                    new Handler().postDelayed(() -> showFirmwareInfoDialog(firmwareInfo), 100);
                    loadingDialog.dismiss();
                }
            } else {
                // 如果加载框已经关闭，直接显示结果
                showFirmwareInfoDialog(firmwareInfo);
            }
        });
    }

    private String formatTimestamp(long timestamp) {
        try {
            // 默认假设是秒级时间戳，转换为毫秒级
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd-HH:mm:ss", Locale.getDefault());
            return sdf.format(new Date(timestamp * 1000L));
        } catch (Exception e) {
            return String.valueOf(timestamp);
        }
    }

    private void initRestartButton() {
        FloatingActionButton fabRestart = findViewById(R.id.fab_restart);
        fabRestart.setOnClickListener(v -> showRestartConfirmationDialog());
    }

    private void showRestartConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restart_xp_title)
                .setMessage(getString(R.string.restart_xp_message_header) + appPackageName + getString(R.string.restart_xp_message))
                .setPositiveButton(R.string.restart_yes, (dialog, which) -> forceStopApp())
                .setNegativeButton(R.string.restart_no, null)
                .show();
    }

    private void forceStopApp() {
        if (appPackageName == null || appPackageName.isEmpty()) {
            return;
        }

        try {
            Process process = Runtime.getRuntime().exec("su -c killall " + appPackageName);
            process.waitFor();
        } catch (Exception e) {
            Toast.makeText(this, R.string.restart_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(R.string.ota_info_clipboard_label), text);
        clipboard.setPrimaryClip(clip);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
