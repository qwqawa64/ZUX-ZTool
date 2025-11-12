package com.qimian233.ztool.settingactivity.setting;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.qimian233.ztool.LoadingDialog;
import com.qimian233.ztool.R;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;
import com.qimian233.ztool.settingactivity.setting.floatingwindow.FloatingWindow;
import com.qimian233.ztool.settingactivity.setting.magicwindowsearch.searchPage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class SettingsDetailActivity extends AppCompatActivity {

    private MaterialSwitch switchRemoveBlacklist;
    private MaterialSwitch switchRemoveHdAppFilter;
    private MaterialSwitch ModuleSwitch;
    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private FloatingWindow floatingWindow;
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private LoadingDialog loadingDialog;
    private FloatingActionButton fabRestart;
    private MaterialSwitch switchFloatMandatory;
    private MaterialSwitch switchSplitScreenMandatory;
    private MaterialSwitch switchAllowDisableDolby;
    private  MaterialSwitch switchAllowNativePermissionController;

    private static final int REQUEST_CODE_PICK_FONT = 1002;
    private static final String FONT_BASE_PATH = "/data_mirror/data_ce/null/0/com.zui.homesettings/files/.ZFont/.localFont/";
    private static final String TEMP_FONT_DIR = "temp_fonts";
    private File currentSelectedFontFile;
    private LoadingDialog fontImportDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings_detail);

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
        switchRemoveBlacklist = findViewById(R.id.switch_remove_blacklist);

        // 设置一视界移除黑名单监听器
        switchRemoveBlacklist.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("remove_blacklist",isChecked);
            }
        });

        // 设置Magisk模块开关监听器
        ModuleSwitch = findViewById(R.id.switch_MagiskModule);
        ModuleSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    ModuleSwitch.setChecked(true);
                    new MaterialAlertDialogBuilder(SettingsDetailActivity.this)
                            .setTitle("警告")
                            .setMessage("APP内暂不支持卸载模块，请在Magisk/KSU模块管理中卸载模块后再重启APP")
                            .setNegativeButton("知道了", null)
                            .show();
                } else {
                    // 如果模块已经安装，直接返回
                    if (isMagiskModuleEnabled()) {
                        ModuleSwitch.setChecked(true);
                        return;
                    }
                    loadingDialog = new LoadingDialog(SettingsDetailActivity.this);
                    loadingDialog.show("正在安装模块...");
                    String result = copyEmbeddingModule(SettingsDetailActivity.this);
                    if ("success".equals(result)) {
                        loadingDialog.dismiss();
                        new MaterialAlertDialogBuilder(SettingsDetailActivity.this)
                                .setTitle("提示")
                                .setMessage("安装成功，重启系统生效")
                                .setNegativeButton("知道了", null)
                                .show();
                    } else {
                        new MaterialAlertDialogBuilder(SettingsDetailActivity.this)
                                .setTitle("错误")
                                .setMessage("安装失败：" + result)
                                .setNegativeButton("知道了", null)
                                .show();
                    }
                }
            }
        });

        //强制浮动窗口适配开关
        switchFloatMandatory = findViewById(R.id.switch_Float_app_Mandatory);
        switchFloatMandatory.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String command;
                            if (isChecked) {
                                command = "settings put global force_resizable_activities 1";
                            } else {
                                command = "settings put global force_resizable_activities 0";
                            }

                            Process process = Runtime.getRuntime().exec("su -c " + command);
                            process.waitFor();

                            // 检查执行结果
                            int exitValue = process.exitValue();
                            if (exitValue != 0) {
                                Log.e("SwitchCommand", "命令执行失败，退出码: " + exitValue);
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("SwitchCommand", "执行命令时发生错误: " + e.getMessage());
                        }
                    }
                }).start();
            }
        });

        //强制分屏适配开关
        switchSplitScreenMandatory = findViewById(R.id.switch_Split_screen_Mandatory);
        switchSplitScreenMandatory.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("Split_Screen_mandatory",isChecked);
            }
        });

        // 设置悬浮窗按钮点击监听
        ImageButton floatingButton = findViewById(R.id.button_floating_window);
        floatingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFloatingWindow();
            }
        });

        // 设置横屏适配按钮点击监听
        View customLandscapeLayout = findViewById(R.id.custom_landscape_layout);
        if (customLandscapeLayout != null) {
            customLandscapeLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startFloatingWindow();
                }
            });
        }

        // 设置横屏适配结果查看按钮点击监听
        View customLandscapeResult = findViewById(R.id.custom_landscapeResult_layout);
        if (customLandscapeResult != null) {
            customLandscapeResult.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 读取并解析配置文件
                    List<ConfigFileInfo> validConfigs = loadAndValidateConfigFiles();

                    // 显示配置选择对话框
                    showConfigSelectionDialog(validConfigs);
                }
            });
        }

        // 设置适配策略按钮点击监听
        View adapter_yishijie = findViewById(R.id.view_adapted_strategies_layout);
        if (adapter_yishijie != null) {
            adapter_yishijie.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(SettingsDetailActivity.this, searchPage.class);
                    startActivity(intent);
                }
            });
        }

        // 设置字体导入点击监听
        View importFontLayout = findViewById(R.id.import_font_layout);
        if (importFontLayout != null) {
            importFontLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startFontImportProcess();
                }
            });
        }

        // 设置杜比音效按钮点击监听
        switchAllowDisableDolby = findViewById(R.id.switch_AllowDolbyDisable);
        switchAllowDisableDolby.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                saveSettings("allow_display_dolby",isChecked);
            }
        });

        switchAllowNativePermissionController = findViewById(R.id.switch_AllowNativePermissionController);
        switchAllowNativePermissionController.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                saveSettings("PermissionControllerHook",isChecked);
            }
        });
    }

    // 启动悬浮窗
    private void startFloatingWindow() {
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission();
                return;
            }
        }

        // 检查使用情况统计权限
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "请授予使用情况统计权限", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
            return;
        }

        showFloatingWindow();
    }

    // 请求悬浮窗权限（Android 6.0+）
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    startFloatingWindow();
                } else {
                    Toast.makeText(this, "悬浮窗权限未授予", Toast.LENGTH_SHORT).show();
                }
            }
        }

        // 处理选择字体文件结果
        if (requestCode == REQUEST_CODE_PICK_FONT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                // 将选中的字体文件复制到临时目录
                copyFontToTemp(uri);
            }
        }
    }

    // 检查是否拥有PACKAGE_USAGE_STATS权限
    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    // 显示悬浮窗 - 使用FloatingWindow类
    private void showFloatingWindow() {
        if (floatingWindow != null) {
            // 如果已经显示，则隐藏
            hideFloatingWindow();
            return;
        }

        // 创建FloatingWindow实例
        floatingWindow = new FloatingWindow(this);
        Toast.makeText(this, "配置向导已启动", Toast.LENGTH_SHORT).show();
    }

    // 隐藏悬浮窗 - 使用FloatingWindow类
    private void hideFloatingWindow() {
        if (floatingWindow != null) {
            floatingWindow.hide();
            floatingWindow = null;
            Toast.makeText(this, "配置向导已关闭", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSettings() {
        // 加载一视界移除黑名单设置
        boolean removeBlacklistEnabled = mPrefsUtils.loadBooleanSetting("remove_blacklist", false);
        switchRemoveBlacklist.setChecked(removeBlacklistEnabled);
        // 加载Magisk模块开关设置
        ModuleSwitch.setChecked(isMagiskModuleEnabled());
        // 加载强制小窗选项
        switchFloatMandatory.setChecked(isForceResizableActivitiesEnabled());
        // 加载强制分屏适配开关设置
        boolean SplitScreenMandatory = mPrefsUtils.loadBooleanSetting("Split_Screen_mandatory", false);
        switchSplitScreenMandatory.setChecked(SplitScreenMandatory);
        // 加载允许显示杜比音效设置
        boolean allowDisplayDolby = mPrefsUtils.loadBooleanSetting("allow_display_dolby", false);
        switchAllowDisableDolby.setChecked(allowDisplayDolby);
        // 加载原生权限控制器设置
        boolean useNativePMController = mPrefsUtils.loadBooleanSetting("PermissionControllerHook", false);
        switchAllowNativePermissionController.setChecked(useNativePMController);
    }

    /**
     * 检查是否开启了强制可调整大小的活动
     *
     * @return 如果开启了强制可调整大小的活动，则返回true；否则返回false
     * @throws Exception 如果执行过程中发生异常，则抛出该异常
     */
    public boolean isForceResizableActivitiesEnabled() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "settings get global force_resizable_activities"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();

            if (line != null) {
                line = line.trim();
                return "1".equals(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("SettingsCheck", "检查设置时发生错误: " + e.getMessage());
        }
        return false; // 默认返回false，表示未开启
    }


    private void saveSettings(String moduleName,Boolean newValue) {
        // 保存一视界移除黑名单设置
        mPrefsUtils.saveBooleanSetting(moduleName, newValue);
    }

    private Boolean isMagiskModuleEnabled() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "ls /data/adb/modules"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("zuxos_embedding")) {
                    return true;
                }
            }
            // 等待进程结束
            process.waitFor();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public String copyEmbeddingModule(Context context) {
        String sourceAssetsPath = "embedding/zuxos_embedding"; // assets中的源路径
        String tempDirPath = context.getFilesDir().getAbsolutePath() + "/zuxos_embedding_temp"; // 临时目录路径
        String targetDirPath = "/data/adb/modules/zuxos_embedding"; // 目标路径
        File tempDir = new File(tempDirPath);
        try {
            // 删除已存在的临时目录（清理旧数据）
            deleteRecursive(tempDir);
            if (!tempDir.mkdirs()) {
                return "Error: Failed to create temp directory";
            }
            // 步骤1: 将assets中的文件复制到临时目录
            if (!copyAssetsToDirectory(context, sourceAssetsPath, tempDir)) {
                return "Error: Failed to copy assets to temp directory";
            }
            // 步骤2: 使用su权限复制临时目录到目标位置
            String[] commands = {
                    "su", "-c",
                    "mkdir -p " + targetDirPath + " && " +
                            "cp -r " + tempDirPath + "/* " + targetDirPath + "/ && " +
                            "chmod -R 755 " + targetDirPath  // 可选：设置权限确保可执行文件正常工作
            };
            Process process = Runtime.getRuntime().exec(commands);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // 读取错误流获取详细信息
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder error = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    error.append(line).append("; ");
                }
                errorReader.close();
                return "Error: Shell command failed - " + error.toString();
            }
            return "success";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        } finally {
            // 清理临时目录
            deleteRecursive(tempDir);
        }
    }
    // 递归复制assets目录到目标目录
    private boolean copyAssetsToDirectory(Context context, String assetsPath, File targetDir) {
        AssetManager assetManager = context.getAssets();
        try {
            String[] files = assetManager.list(assetsPath);
            if (files == null || files.length == 0) {
                // 可能是文件而非目录，直接复制
                InputStream in = assetManager.open(assetsPath);
                File outFile = new File(targetDir, new File(assetsPath).getName());
                FileOutputStream out = new FileOutputStream(outFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
                out.flush();
                out.close();
                in.close();
                return true;
            } else {
                // 处理目录
                loadingDialog.updateMessage("正在释放文件...");
                for (String file : files) {
                    String fullAssetsPath = assetsPath.isEmpty() ? file : assetsPath + "/" + file;
                    File targetFile = new File(targetDir, file);
                    if (assetManager.list(fullAssetsPath).length > 0) {
                        // 子目录：递归复制
                        if (!targetFile.mkdirs()) {
                            return false;
                        }
                        if (!copyAssetsToDirectory(context, fullAssetsPath, targetFile)) {
                            return false;
                        }
                    } else {
                        // 文件：直接复制
                        InputStream in = assetManager.open(fullAssetsPath);
                        FileOutputStream out = new FileOutputStream(targetFile);
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = in.read(buffer)) > 0) {
                            out.write(buffer, 0, length);
                        }
                        out.flush();
                        out.close();
                        in.close();
                    }
                }
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    // 递归删除目录或文件
    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            loadingDialog.updateMessage("正在清理临时文件...");
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 确保在Activity销毁时关闭悬浮窗
        hideFloatingWindow();
    }

    private static class ConfigFileInfo {
        File file;
        String timestamp;
        String packageName;
        String appName;
        String configContent;
    }
    /**
     * 读取并验证配置文件
     */
    private List<ConfigFileInfo> loadAndValidateConfigFiles() {
        List<ConfigFileInfo> validConfigs = new ArrayList<>();

        try {
            // 获取配置目录
            File configDir = new File(getFilesDir(), "data/custom_EmbeddingConfig");

            if (!configDir.exists() || !configDir.isDirectory()) {
                return validConfigs;
            }

            // 遍历目录下所有文件
            File[] files = configDir.listFiles();
            if (files == null) {
                return validConfigs;
            }

            for (File file : files) {
                if (file.isFile()) {
                    ConfigFileInfo configInfo = parseConfigFile(file);
                    if (configInfo != null) {
                        validConfigs.add(configInfo);
                    } else {
                        // 解析失败，删除无效文件
                        file.delete();
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return validConfigs;
    }
    /**
     * 解析单个配置文件
     */
    private ConfigFileInfo parseConfigFile(File file) {
        try {
            ConfigFileInfo configInfo = new ConfigFileInfo();
            configInfo.file = file;

            // 解析文件名：时间戳_包名
            String fileName = file.getName();
            String[] nameParts = fileName.split("_", 2);
            if (nameParts.length != 2) {
                return null; // 文件名格式不正确
            }

            String timestampStr = nameParts[0];
            String packageName = nameParts[1];

            // 验证时间戳格式
            try {
                long timestamp = Long.parseLong(timestampStr);
                configInfo.timestamp = formatTimestamp(timestamp);
                configInfo.packageName = packageName;
                configInfo.appName = getAppNameFromPackage(packageName);
            } catch (NumberFormatException e) {
                return null; // 时间戳格式错误
            }

            // 读取文件内容并Base64解码
            String base64Content = readFileContent(file);
            if (base64Content == null) {
                return null; // 读取文件失败
            }

            // Base64解码
            byte[] decodedBytes = Base64.decode(base64Content, Base64.DEFAULT);
            String originalContent = new String(decodedBytes, "UTF-8");
            configInfo.configContent = originalContent;

            // 验证JSON格式
            if (!isValidJson(originalContent)) {
                return null; // JSON格式无效
            }

            return configInfo;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * 格式化时间戳
     */
    private String formatTimestamp(long timestamp) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd-HH:mm:ss", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            return String.valueOf(timestamp);
        }
    }
    /**
     * 根据包名获取应用名称
     */
    private String getAppNameFromPackage(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            // 应用未安装，返回包名
            return packageName;
        } catch (Exception e) {
            e.printStackTrace();
            return packageName;
        }
    }
    /**
     * 读取文件内容
     */
    private String readFileContent(File file) {
        try {
            FileInputStream inputStream = new FileInputStream(file);
            byte[] buffer = new byte[(int) file.length()];
            inputStream.read(buffer);
            inputStream.close();
            return new String(buffer, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    /**
     * 验证JSON格式
     */
    private boolean isValidJson(String content) {
        try {
            new JSONObject(content); // 尝试解析为JSONObject
            return true;
        } catch (JSONException e1) {
            try {
                new JSONArray(content); // 尝试解析为JSONArray
                return true;
            } catch (JSONException e2) {
                return false;
            }
        }
    }
    /**
     * 显示配置选择对话框
     */
    private void showConfigSelectionDialog(List<ConfigFileInfo> configs) {
        if (configs.isEmpty()) {
            Toast.makeText(this, "请" +
                    "使用配置向导生成第一个配置文件吧", Toast.LENGTH_SHORT).show();
            return;
        }
        // 准备对话框数据
        String[] displayItems = new String[configs.size()];
        for (int i = 0; i < configs.size(); i++) {
            ConfigFileInfo config = configs.get(i);
            displayItems[i] = config.timestamp + " " + config.appName + " 配置";
        }
        boolean[] checkedItems = new boolean[configs.size()];
        Arrays.fill(checkedItems, false);
        // 从SharedPreferences读取已刷入的策略
        Set<String> flashedConfigs = loadStringSetSetting("flashed_configs", new HashSet<>());
        int flashedCount = flashedConfigs.size();
        // 创建自定义布局的对话框
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);

        // 设置自定义布局
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_config_selection, null);
        builder.setView(dialogView);
        // 设置标题
        TextView titleText = dialogView.findViewById(R.id.dialog_title);
        titleText.setText("选择配置文件");

        // 显示已刷入策略数量
        TextView flashedCountText = dialogView.findViewById(R.id.flashed_count_text);
        if (flashedCount > 0) {
            flashedCountText.setText("已刷入 " + flashedCount + " 个自定义策略");
            flashedCountText.setVisibility(View.VISIBLE);
        } else {
            flashedCountText.setVisibility(View.GONE);
        }
        // 获取ListView
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        // 设置列表项
        ListView listView = dialogView.findViewById(R.id.config_list_view);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_multiple_choice, displayItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                CheckedTextView checkedTextView = (CheckedTextView) view;

                // 检查这个配置是否已经刷入
                ConfigFileInfo config = configs.get(position);
                String configKey = config.timestamp + "_" + config.packageName;
                boolean isFlashed = flashedConfigs.contains(configKey);

                if (isFlashed) {
                    // 已刷入的配置显示为灰色且不可选择
                    checkedTextView.setTextColor(Color.GRAY);
                    checkedTextView.setEnabled(false);
                    checkedTextView.setChecked(true);
                    checkedItems[position] = false;
                } else {
                    checkedTextView.setTextColor(Color.BLACK);
                    checkedTextView.setEnabled(true);
                    checkedTextView.setChecked(checkedItems[position]);
                }

                return view;
            }
        };

        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        // 设置列表项点击监听
        listView.setOnItemClickListener((parent, view, position, id) -> {
            ConfigFileInfo config = configs.get(position);
            String configKey = config.timestamp + "_" + config.packageName;

            // 如果配置已刷入，则不允许选择
            if (flashedConfigs.contains(configKey)) {
                ((CheckedTextView) view).setChecked(false);
                Toast.makeText(this, "已刷入的配置不可选择", Toast.LENGTH_SHORT).show();
            } else {
                checkedItems[position] = !checkedItems[position];
                ((CheckedTextView) view).setChecked(checkedItems[position]);
            }
        });
        // 设置按钮
        Button deleteButton = dialogView.findViewById(R.id.delete_button);
        Button flashButton = dialogView.findViewById(R.id.flash_button);
        Button cancelButton = dialogView.findViewById(R.id.cancel_button);
        Button restoreButton = dialogView.findViewById(R.id.restore_button);

        // 修改删除按钮点击事件
        deleteButton.setOnClickListener(v -> {
            // 获取选中的配置
            List<ConfigFileInfo> selectedConfigs = new ArrayList<>();
            for (int i = 0; i < checkedItems.length; i++) {
                if (checkedItems[i]) {
                    selectedConfigs.add(configs.get(i));
                }
            }

            if (selectedConfigs.isEmpty()) {
                Toast.makeText(this, "请选择要删除的配置文件", Toast.LENGTH_SHORT).show();
                return;
            }

            // 执行删除操作
            performConfigDelete(selectedConfigs, flashedConfigs, dialog, configs);
        });

        flashButton.setOnClickListener(v -> {
            List<ConfigFileInfo> selectedConfigs = new ArrayList<>();
            for (int i = 0; i < checkedItems.length; i++) {
                if (checkedItems[i]) {
                    selectedConfigs.add(configs.get(i));
                }
            }
            if (selectedConfigs.isEmpty()) {
                Toast.makeText(this, "请选择至少一个配置文件", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            flashSelectedConfigs(selectedConfigs);
        });
        cancelButton.setOnClickListener(v -> {
            dialog.dismiss();
        });
        // 设置还原按钮可见性
        if (!flashedConfigs.isEmpty()) {
            restoreButton.setVisibility(View.VISIBLE);
            restoreButton.setOnClickListener(v -> {
                dialog.dismiss();
                restoreOriginalModule();
            });
        } else {
            restoreButton.setVisibility(View.GONE);
        }
        // 显示对话框
        dialog.show();
    }

    /**
     * 执行配置删除操作
     */
    private void performConfigDelete(List<ConfigFileInfo> selectedConfigs, Set<String> flashedConfigs,
                                     androidx.appcompat.app.AlertDialog dialog, List<ConfigFileInfo> originalConfigs) {
        int deletedCount = 0;
        int skippedCount = 0;

        for (ConfigFileInfo config : selectedConfigs) {
            String configKey = config.timestamp + "_" + config.packageName;

            // 检查是否为已刷入的配置
            if (flashedConfigs.contains(configKey)) {
                skippedCount++;
                continue; // 跳过已刷入的配置
            }

            // 删除本地文件
            if (config.file.exists() && config.file.delete()) {
                deletedCount++;
            }
        }

        // 显示删除结果
        StringBuilder resultMessage = new StringBuilder();
        if (deletedCount > 0) {
            resultMessage.append("成功删除 ").append(deletedCount).append(" 个配置文件");
        }
        if (skippedCount > 0) {
            if (resultMessage.length() > 0) {
                resultMessage.append("\n");
            }
            resultMessage.append("自动跳过 ").append(skippedCount).append(" 个已刷入的配置");
        }

        if (resultMessage.length() > 0) {
            Toast.makeText(this, resultMessage.toString(), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "没有文件被删除", Toast.LENGTH_SHORT).show();
        }

        // 关闭当前对话框并重新加载配置列表
        dialog.dismiss();
        List<ConfigFileInfo> newConfigs = loadAndValidateConfigFiles();
        if (!newConfigs.isEmpty()) {
            showConfigSelectionDialog(newConfigs);
        } else {
            Toast.makeText(this, "所有配置文件已删除", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * 刷入选中的配置
     */
    private void flashSelectedConfigs(List<ConfigFileInfo> selectedConfigs) {
        // 检查模块是否已安装
        if (!isMagiskModuleEnabled()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("提示")
                    .setMessage("请先安装拓展模块")
                    .setNegativeButton("知道了", null)
                    .show();
            return;
        }

        // 检查是否有重复包名的配置
        Set<String> packageNames = new HashSet<>();
        for (ConfigFileInfo config : selectedConfigs) {
            if (packageNames.contains(config.packageName)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("警告")
                        .setMessage("一个应用只允许一次刷入一次配置，请检查选择的应用：" + config.appName)
                        .setNegativeButton("知道了", null)
                        .show();
                return;
            }
            packageNames.add(config.packageName);
        }

        // 显示警告提示
        new MaterialAlertDialogBuilder(this)
                .setTitle("警告")
                .setMessage("刷入的策略如果与原策略冲突将会覆盖处理")
                .setPositiveButton("继续", (dialog, which) -> {
                    // 开始刷入配置
                    performConfigFlash(selectedConfigs);
                })
                .setNegativeButton("取消", null)
                .show();
    }
    /**
     * 执行配置刷入操作
     */
    private void performConfigFlash(List<ConfigFileInfo> selectedConfigs) {
        loadingDialog = new LoadingDialog(this);
        loadingDialog.show("正在刷入配置...");

        new Thread(() -> {
            try {
                // 步骤1: 提取原模块JSON文件到临时目录
                loadingDialog.updateMessage("正在提取原配置文件...");

                // 使用更可靠的临时目录路径
                String tempDirPath = getCacheDir().getAbsolutePath() + "/module_temp";
                File tempDir = new File(tempDirPath);

                // 创建临时目录
                deleteRecursive(tempDir);
                if (!tempDir.mkdirs()) {
                    throw new IOException("无法创建临时目录: " + tempDirPath);
                }

                // 首先检查模块文件是否存在
                String[] checkCommands = {
                        "su", "-c",
                        "ls /data/adb/modules/zuxos_embedding/"
                };
                Process checkProcess = Runtime.getRuntime().exec(checkCommands);
                int checkExitCode = checkProcess.waitFor();
                if (checkExitCode != 0) {
                    throw new IOException("模块目录不存在或无法访问");
                }

                // 复制原JSON文件到临时目录
                String[] copyCommands = {
                        "su", "-c",
                        "cat /data/adb/modules/zuxos_embedding/embedding_config.json > " + tempDirPath + "/embedding_config.json"
                };
                Process copyProcess = Runtime.getRuntime().exec(copyCommands);
                int copyExitCode = copyProcess.waitFor();
                if (copyExitCode != 0) {
                    throw new IOException("复制原配置文件失败，请检查模块是否完整安装");
                }

                // 更改文件权限和所有者：使用chmod和chown确保应用可读写
                loadingDialog.updateMessage("正在设置文件权限...");
                int uid = android.os.Process.myUid(); // 获取应用用户ID
                String[] permissionCommands = {
                        "su", "-c",
                        "chmod 644 " + tempDirPath + "/embedding_config.json" + " && " +
                                "chown " + uid + "." + uid + " " + tempDirPath + "/embedding_config.json"
                };
                Process permissionProcess = Runtime.getRuntime().exec(permissionCommands);
                int permissionExitCode = permissionProcess.waitFor();
                if (permissionExitCode != 0) {
                    throw new IOException("设置文件权限和所有者失败");
                }

                // 步骤2: 读取并解析原JSON文件
                loadingDialog.updateMessage("正在解析原配置...");
                File tempJsonFile = new File(tempDirPath, "embedding_config.json");
                if (!tempJsonFile.exists()) {
                    throw new IOException("临时配置文件不存在: " + tempJsonFile.getAbsolutePath());
                }

                String originalJsonContent = readFileContent(tempJsonFile);
                if (originalJsonContent == null || originalJsonContent.isEmpty()) {
                    throw new IOException("读取原配置文件失败");
                }

                JSONObject originalJson = new JSONObject(originalJsonContent);
                JSONArray originalPackages = originalJson.getJSONArray("packages");

                // 步骤3: 处理选中的配置
                loadingDialog.updateMessage("正在处理新配置...");
                for (ConfigFileInfo config : selectedConfigs) {
                    // 解析用户配置
                    JSONObject userConfig = new JSONObject(config.configContent);
                    String packageName = userConfig.getString("name");

                    // 从原配置中删除相同包名的配置
                    JSONArray newPackages = new JSONArray();
                    for (int i = 0; i < originalPackages.length(); i++) {
                        JSONObject pkg = originalPackages.getJSONObject(i);
                        if (!pkg.getString("name").equals(packageName)) {
                            newPackages.put(pkg);
                        }
                    }

                    // 添加新的配置
                    newPackages.put(userConfig);
                    originalPackages = newPackages;
                }

                // 更新packages数组
                originalJson.put("packages", originalPackages);

                // 步骤4: 写回临时文件（现在文件所有者是应用用户，可以正常写入）
                loadingDialog.updateMessage("正在生成新配置文件...");
                String newJsonContent = originalJson.toString(2);
                FileOutputStream fos = new FileOutputStream(tempJsonFile); // 不再抛出权限错误
                fos.write(newJsonContent.getBytes("UTF-8"));
                fos.close();

                // 步骤5: 覆盖模块目录下的JSON文件（需要root权限）
                loadingDialog.updateMessage("正在更新模块配置...");
                String[] overwriteCommands = {
                        "su", "-c",
                        "cp " + tempJsonFile.getAbsolutePath() + " /data/adb/modules/zuxos_embedding/embedding_config.json && " +
                                "chmod 644 /data/adb/modules/zuxos_embedding/embedding_config.json"
                };
                Process overwriteProcess = Runtime.getRuntime().exec(overwriteCommands);
                int overwriteExitCode = overwriteProcess.waitFor();
                if (overwriteExitCode != 0) {
                    throw new IOException("更新模块配置失败，请检查root权限");
                }

                // 步骤6: 保存刷入的策略到SharedPreferences
                Set<String> existingFlashedConfigs = loadStringSetSetting("flashed_configs", new HashSet<>());
                Set<String> newFlashedConfigs = new HashSet<>(existingFlashedConfigs); // 复制已有的配置

                for (ConfigFileInfo config : selectedConfigs) {
                    String configKey = config.timestamp + "_" + config.packageName;
                    newFlashedConfigs.add(configKey);
                }

                saveStringSetSetting("flashed_configs", newFlashedConfigs);

                // 清理临时文件
                deleteRecursive(tempDir);

                // 成功提示
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    new MaterialAlertDialogBuilder(SettingsDetailActivity.this)
                            .setTitle("成功")
                            .setMessage("配置刷入成功，重启系统生效")
                            .setNegativeButton("知道了", null)
                            .show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    new MaterialAlertDialogBuilder(SettingsDetailActivity.this)
                            .setTitle("错误")
                            .setMessage("刷入配置失败：" + e.getMessage())
                            .setNegativeButton("知道了", null)
                            .show();
                });
            }
        }).start();
    }



    /**
     * 还原初始模块
     */
    private void restoreOriginalModule() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("确认还原")
                .setMessage("此操作将删除所有自定义配置并重新安装原始模块，是否继续？")
                .setPositiveButton("确认", (dialog, which) -> {
                    performModuleRestore();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 执行模块还原操作
     */
    private void performModuleRestore() {
        loadingDialog = new LoadingDialog(this);
        loadingDialog.show("正在还原模块...");

        new Thread(() -> {
            try {
                // 步骤1: 删除模块目录
                loadingDialog.updateMessage("正在删除模块...");
                String[] deleteCommands = {
                        "su", "-c",
                        "rm -rf /data/adb/modules/zuxos_embedding"
                };
                Process deleteProcess = Runtime.getRuntime().exec(deleteCommands);
                int deleteExitCode = deleteProcess.waitFor();
                if (deleteExitCode != 0) {
                    throw new IOException("删除模块失败，请检查root权限");
                }

                // 步骤2: 重新安装模块
                loadingDialog.updateMessage("正在重新安装模块...");
                String result = copyEmbeddingModule(SettingsDetailActivity.this);
                if (!"success".equals(result)) {
                    throw new IOException("重新安装模块失败: " + result);
                }

                // 步骤3: 清除SharedPreferences中的刷入记录
                saveStringSetSetting("flashed_configs", new HashSet<>());

                // 成功提示
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    new MaterialAlertDialogBuilder(SettingsDetailActivity.this)
                            .setTitle("成功")
                            .setMessage("模块还原成功，重启系统生效")
                            .setNegativeButton("知道了", null)
                            .show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    new MaterialAlertDialogBuilder(SettingsDetailActivity.this)
                            .setTitle("错误")
                            .setMessage("模块还原失败：" + e.getMessage())
                            .setNegativeButton("知道了", null)
                            .show();
                });
            }
        }).start();
    }

    /**
     * 从SharedPreferences加载字符串集合设置
     * @param key 设置的键名
     * @param defaultSet 默认值
     * @return 字符串集合
     */
    private Set<String> loadStringSetSetting(String key, Set<String> defaultSet) {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences("module_settings", Context.MODE_PRIVATE);
            Set<String> result = sharedPreferences.getStringSet(key, null);

            if (result == null) {
                return defaultSet;
            }

            // 返回新的HashSet以避免并发修改异常
            return new HashSet<>(result);
        } catch (Exception e) {
            e.printStackTrace();
            return defaultSet;
        }
    }

    /**
     * 保存字符串集合设置到SharedPreferences
     * @param key 设置的键名
     * @param set 要保存的字符串集合
     */
    private void saveStringSetSetting(String key, Set<String> set) {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences("module_settings", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();

            // 使用新的HashSet来避免Android SharedPreferences的bug
            editor.putStringSet(key, new HashSet<>(set));
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从SharedPreferences中移除设置
     * @param key 要移除的设置的键名
     */
    private void removeSetting(String key) {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences("module_settings", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(key);
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始字体导入流程
     */
    private void startFontImportProcess() {
        // 拉起文件管理器选择TTF文件
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // 创建文件类型过滤器，只显示TTF文件
        String[] mimeTypes = {"font/ttf", "application/x-font-ttf", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        try {
            startActivityForResult(Intent.createChooser(intent, "选择TTF字体文件"), REQUEST_CODE_PICK_FONT);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "未找到文件管理器", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 将字体文件复制到临时目录
     */
    private void copyFontToTemp(Uri uri) {
        fontImportDialog = new LoadingDialog(this);
        fontImportDialog.show("正在准备字体文件...");

        new Thread(() -> {
            try {
                // 创建临时目录
                File tempDir = new File(getFilesDir(), TEMP_FONT_DIR);
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }

                // 获取原始文件名
                String originalFileName = getFileName(uri);
                if (originalFileName == null) {
                    originalFileName = "unknown_font.ttf";
                }

                // 创建临时文件
                File tempFile = new File(tempDir, "temp_font_" + System.currentTimeMillis() + ".ttf");

                // 复制文件
                try (InputStream inputStream = getContentResolver().openInputStream(uri);
                     FileOutputStream outputStream = new FileOutputStream(tempFile)) {

                    if (inputStream != null) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }

                        currentSelectedFontFile = tempFile;

                        // 在主线程显示输入对话框
                        String finalOriginalFileName = originalFileName;
                        runOnUiThread(() -> showFontInputDialog(finalOriginalFileName));
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    fontImportDialog.dismiss();
                    Toast.makeText(SettingsDetailActivity.this, "文件复制失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 显示字体信息输入对话框
     */
    private void showFontInputDialog(String originalFileName) {
        fontImportDialog.dismiss();

        // 创建自定义布局
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_font_input, null);
        EditText etFontName = dialogView.findViewById(R.id.et_font_name);
        EditText etFontDescription = dialogView.findViewById(R.id.et_font_description);

        // 设置默认描述
        String defaultDescription = "自定义导入的字体" + originalFileName;
        etFontDescription.setText(defaultDescription);

        new MaterialAlertDialogBuilder(this)
                .setTitle("输入字体信息")
                .setView(dialogView)
                .setPositiveButton("确认", (dialog, which) -> {
                    String fontName = etFontName.getText().toString().trim();
                    String fontDescription = etFontDescription.getText().toString().trim();

                    if (fontName.isEmpty()) {
                        Toast.makeText(this, "字体名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (fontDescription.isEmpty()) {
                        Toast.makeText(this, "字体描述不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 开始导入流程
                    startFontImport(fontName, fontDescription, originalFileName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 开始字体导入
     */
    private void startFontImport(String fontName, String fontDescription, String originalFileName) {
        fontImportDialog = new LoadingDialog(this);
        fontImportDialog.show("正在导入字体...");

        new Thread(() -> {
            try {
                // 步骤1: 生成随机文件夹名
                fontImportDialog.updateMessage("正在创建字体目录...");
                String randomFolderName = generateRandomFolderName();
                String targetFolderPath = FONT_BASE_PATH + randomFolderName;

                // 创建目标文件夹
                createFolderWithRoot(targetFolderPath);

                // 步骤2: 复制字体文件到系统目录
                fontImportDialog.updateMessage("正在复制字体文件...");
                String targetFontPath = targetFolderPath + "/font.ttf";
                copyFileWithRoot(currentSelectedFontFile.getAbsolutePath(), targetFontPath);

                // 步骤3: 创建字体元数据
                fontImportDialog.updateMessage("正在创建字体元数据...");
                String xmlContent = generateFontXml(fontName, fontDescription);
                String xmlFilePath = targetFolderPath + "/font.xml";
                createXmlFileWithRoot(xmlFilePath, xmlContent);

                // 步骤4: 生成预览图片（使用临时文件）
                fontImportDialog.updateMessage("正在生成预览图片...");
                generatePreviewImages(targetFolderPath, currentSelectedFontFile.getAbsolutePath(), fontName);

                // 步骤5: 设置正确的文件夹权限和所有者
                fontImportDialog.updateMessage("正在设置文件夹权限...");
                setFolderPermissions(targetFolderPath);

                // 清理临时文件
                cleanupTempFiles();

                // 导入成功
                runOnUiThread(() -> {
                    fontImportDialog.dismiss();
                    new MaterialAlertDialogBuilder(SettingsDetailActivity.this)
                            .setTitle("导入成功")
                            .setMessage("字体已成功导入到系统字体库\n前往 设置-个性定制-字体-本地 即可查看")
                            .setPositiveButton("确定", null)
                            .show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    fontImportDialog.dismiss();
                    new MaterialAlertDialogBuilder(SettingsDetailActivity.this)
                            .setTitle("导入失败")
                            .setMessage("字体导入失败: " + e.getMessage())
                            .setPositiveButton("确定", null)
                            .show();
                });
            }
        }).start();
    }

    /**
     * 设置文件夹权限和所有者
     */
    private void setFolderPermissions(String folderPath) throws Exception {
        // 获取参考文件夹的所有者和组
        String[] ownerGroup = getReferenceFolderOwnerAndGroup();
        String owner = ownerGroup[0];
        String group = ownerGroup[1];

        // 设置文件夹所有者
        String chownCommand = "chown -R " + owner + ":" + group + " " + folderPath;
        executeRootCommand(chownCommand);

        // 设置文件夹权限 (drwx------，对应700)
        String chmodCommand = "chmod 700 " + folderPath;
        executeRootCommand(chmodCommand);

        // 设置文件夹内文件的权限 (rw-------，对应600)
        String chmodFilesCommand = "chmod 600 " + folderPath + "/*";
        executeRootCommand(chmodFilesCommand);
    }


    /**
     * 生成随机文件夹名（6个字母）
     */
    private String generateRandomFolderName() {
        String chars = "abcdefghijklmnopqrstuvwxyz";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 生成字体XML内容
     */
    private String generateFontXml(String fontName, String fontDescription) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ZFont>\n" +
                "<name>" + fontName + "</name>\n" +
                "<language>简体</language>\n" +
                "<author>Import By ZTool</author>\n" +
                "<abstract>" + fontDescription + "</abstract>\n" +
                "</ZFont>";
    }

    /**
     * 生成字体预览Bitmap（支持多行文本排版，自适应文本大小，透明背景）
     */
    private Bitmap generateFontPreviewBitmap(Typeface typeface, String text, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 设置透明背景
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        Paint paint = new Paint();
        paint.setTypeface(typeface);
        paint.setColor(Color.BLACK);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);

        // 按换行符分割文本
        String[] lines = text.split("\n");

        // 动态计算文本大小
        float textSize = calculateOptimalTextSize(paint, lines, width, height);
        paint.setTextSize(textSize);

        // 获取字体度量
        Paint.FontMetrics fm = paint.getFontMetrics();
        float lineHeight = fm.descent - fm.ascent;
        float lineSpacing = lineHeight * 0.2f; // 行间距为字高的20%
        float totalLineHeight = lineHeight + lineSpacing;

        // 计算起始Y坐标（垂直居中）
        float totalTextHeight = lines.length * totalLineHeight - lineSpacing;
        float startY = (height - totalTextHeight) / 2f - fm.ascent;

        // 绘制每一行文本
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim(); // 去除行首尾空格

            // 处理缩进：如果行首有空格，进行特殊处理
            float x = width / 2f;
            if (line.startsWith("   ")) { // 三个空格表示缩进
                // 对于有缩进的行，调整对齐方式为左对齐并添加缩进
                paint.setTextAlign(Paint.Align.LEFT);
                x = width * 0.1f; // 左侧缩进10%
            } else if (line.equals("---")) {
                // 分隔线特殊处理
                paint.setTextAlign(Paint.Align.CENTER);
                x = width / 2f;
            } else {
                paint.setTextAlign(Paint.Align.CENTER);
                x = width / 2f;
            }

            // 绘制当前行
            canvas.drawText(line, x, startY + i * totalLineHeight, paint);

            // 重置对齐方式
            paint.setTextAlign(Paint.Align.CENTER);
        }

        return bitmap;
    }

    /**
     * 计算多行文本的最优字体大小
     */
    private float calculateOptimalTextSize(Paint paint, String[] lines, int maxWidth, int maxHeight) {
        float minTextSize = 8f;
        float maxTextSize = 200f;
        float optimalTextSize = minTextSize;

        // 获取字体度量
        Paint.FontMetrics fm = paint.getFontMetrics();

        // 二分查找最优字体大小
        while (minTextSize <= maxTextSize) {
            float midTextSize = (minTextSize + maxTextSize) / 2;
            paint.setTextSize(midTextSize);

            // 计算行高
            float lineHeight = fm.descent - fm.ascent;
            float lineSpacing = lineHeight * 0.2f;
            float totalLineHeight = lineHeight + lineSpacing;

            // 检查所有行是否都能在宽度内显示
            boolean fitsWidth = true;
            float maxLineWidth = 0;

            for (String line : lines) {
                String trimmedLine = line.trim();
                float lineWidth = paint.measureText(trimmedLine);
                maxLineWidth = Math.max(maxLineWidth, lineWidth);

                // 对于有缩进的行，需要额外考虑缩进空间
                if (trimmedLine.startsWith("   ")) {
                    float availableWidth = maxWidth * 0.9f; // 缩进后可用宽度为90%
                    if (lineWidth > availableWidth) {
                        fitsWidth = false;
                        break;
                    }
                } else {
                    if (lineWidth > maxWidth * 0.95f) { // 普通行可用宽度为95%
                        fitsWidth = false;
                        break;
                    }
                }
            }

            // 检查总高度是否合适
            float totalTextHeight = lines.length * totalLineHeight - lineSpacing;
            boolean fitsHeight = totalTextHeight <= maxHeight * 0.9f;

            if (fitsWidth && fitsHeight) {
                optimalTextSize = midTextSize;
                minTextSize = midTextSize + 1;
            } else {
                maxTextSize = midTextSize - 1;
            }
        }

        return optimalTextSize;
    }

    /**
     * 生成预览图片（更新后的方法，包含正确的预览文本）
     */
    private void generatePreviewImages(String targetFolderPath, String fontPath, String fontName) throws Exception {
        // 创建临时Typeface
        Typeface typeface = Typeface.createFromFile(fontPath);

        // 生成缩略图 (249x57)
        fontImportDialog.updateMessage("正在生成缩略图...");
        Bitmap smallBitmap = generateFontPreviewBitmap(typeface, fontName, 249, 70);
        String smallTempPath = saveBitmapToTemp(smallBitmap, "small_temp.png");
        copyFileWithRoot(smallTempPath, targetFolderPath + "/small.png");

        // 生成详细预览图 (948x945) - 使用正确排版的文本
        String previewString = "有言\n\n" +
                "   与恶龙缠斗过久，自身亦成为恶龙；\n" +
                "   凝视深渊过久，深渊将回以凝视。\n\n" +
                "           ---" +
                "弗里德里希·威廉·尼采";

        fontImportDialog.updateMessage("正在生成详细预览图...");
        Bitmap previewBitmap = generateFontPreviewBitmap(typeface, previewString, 948, 945);
        String previewTempPath = saveBitmapToTemp(previewBitmap, "preview_temp.png");
        copyFileWithRoot(previewTempPath, targetFolderPath + "/preview.png");

        // 回收Bitmap
        if (!smallBitmap.isRecycled()) {
            smallBitmap.recycle();
        }
        if (!previewBitmap.isRecycled()) {
            previewBitmap.recycle();
        }
    }



    /**
     * 保存Bitmap到临时文件
     */
    private String saveBitmapToTemp(Bitmap bitmap, String fileName) throws Exception {
        File tempDir = new File(getFilesDir(), TEMP_FONT_DIR);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        File tempFile = new File(tempDir, fileName);
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return tempFile.getAbsolutePath();
    }
    /**
     * 使用root权限创建文件夹
     */
    private void createFolderWithRoot(String path) throws Exception {
        String command = "mkdir -p " + path;
        executeRootCommand(command);
    }

    /**
     * 使用root权限复制文件
     */
    private void copyFileWithRoot(String sourcePath, String targetPath) throws Exception {
        String command = "cp \"" + sourcePath + "\" \"" + targetPath + "\"";
        executeRootCommand(command);
    }

    /**
     * 使用root权限创建XML文件
     */
    private void createXmlFileWithRoot(String filePath, String content) throws Exception {
        // 先写入临时文件
        File tempFile = new File(getFilesDir(), "temp_xml.xml");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(content.getBytes("UTF-8"));
        }

        // 使用root权限复制到目标位置
        copyFileWithRoot(tempFile.getAbsolutePath(), filePath);

        // 删除临时文件
        tempFile.delete();
    }

    /**
     * 执行root命令
     */
    private void executeRootCommand(String command) throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("Root命令执行失败，退出码: " + exitCode);
        }
    }

    /**
     * 获取文件名
     */
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    /**
     * 清理临时文件
     */
    private void cleanupTempFiles() {
        try {
            File tempDir = new File(getFilesDir(), TEMP_FONT_DIR);
            if (tempDir.exists()) {
                deleteRecursive(tempDir);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取参考文件夹的所有者和组信息
     */
    private String[] getReferenceFolderOwnerAndGroup() throws Exception {
        String referenceFolder = FONT_BASE_PATH + "Aa-BPBDY";
        String command = "ls -ld " + referenceFolder;

        Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        process.waitFor();

        if (line != null) {
            // 解析输出格式: drwx------ 2 u0_a96 u0_a96 3452 2025-11-06 16:29 /path/to/folder
            String[] parts = line.split("\\s+");
            if (parts.length >= 4) {
                String owner = parts[2];
                String group = parts[3];
                return new String[]{owner, group};
            }
        }

        throw new Exception("无法获取参考文件夹的权限信息");
    }



    private void initRestartButton() {
        fabRestart = findViewById(R.id.fab_restart);
        fabRestart.setOnClickListener(v -> showRestartConfirmationDialog());
    }

    private void showRestartConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("重启XP模块作用域")
                // 尽量不破坏原有的Intent，临时使用硬编码，后续需要重构这部分逻辑
                .setMessage("是否重启此页的XP模块作用域？这将强行停止 " + appPackageName + ", com.android.permissioncontroller" + ", com.zui.safecenter" + " 的进程。")
                .setPositiveButton("确定", (dialog, which) -> forceStopApp())
                .setNegativeButton("取消", null)
                .show();
    }

    private void forceStopApp() {
        if (appPackageName == null || appPackageName.isEmpty()) {
            return;
        }

        try {
            // 尽量不破坏原有的Intent，临时使用硬编码，后续需要重构这部分逻辑
            Process process = Runtime.getRuntime().exec("su -c am force-stop " + appPackageName);
            process.waitFor(); // 等待命令执行完成
            for (String mystring : new String[]{"com.android.permissioncontroller", "com.zui.safecenter"}) {
                process = Runtime.getRuntime().exec("su -c am force-stop " + mystring);
                process.waitFor(); // 等待命令执行完成
            }

        } catch (Exception e) {
            Toast.makeText(this, "重启失败", Toast.LENGTH_SHORT).show();
        }
    }
}
