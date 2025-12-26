package com.qimian233.ztool.settingactivity.setting;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.qimian233.ztool.EnhancedShellExecutor;
import com.qimian233.ztool.LoadingDialog;
import com.qimian233.ztool.R;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;
import com.qimian233.ztool.settingactivity.setting.floatingwindow.FloatingWindow;
import com.qimian233.ztool.settingactivity.setting.magicwindowsearch.searchPage;
import com.qimian233.ztool.utils.EmbeddingConfigManager;
import com.qimian233.ztool.utils.FontInstallerManager;
import com.qimian233.ztool.utils.MagiskModuleManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class SettingsDetailActivity extends AppCompatActivity {

    // UI 组件
    private MaterialSwitch switchRemoveBlacklist;
    private MaterialSwitch ModuleSwitch;
    private MaterialSwitch switchFloatMandatory;
    private MaterialSwitch switchSplitScreenMandatory;
    private MaterialSwitch switchAllowDisableDolby;
    private MaterialSwitch switchAllowNativePermissionController;
    private LoadingDialog loadingDialog;

    // 业务逻辑管理器
    private ModulePreferencesUtils mPrefsUtils;
    private MagiskModuleManager magiskManager;
    private EmbeddingConfigManager configManager;
    private FontInstallerManager fontManager;

    // 状态变量
    private String appPackageName;
    private FloatingWindow floatingWindow;
    private File currentSelectedFontFile;

    // 请求码
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_CODE_PICK_FONT = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings_detail);

        // 1. 初始化管理器
        initManagers();

        // 2. 初始化 Toolbar 和 Intent 数据
        initToolbarData();

        // 3. 初始化视图和监听器
        initViews();

        // 4. 加载当前设置状态
        loadSettings();

        // 5. 初始化重启按钮
        initRestartButton();
    }

    private void initManagers() {
        mPrefsUtils = new ModulePreferencesUtils(this);
        magiskManager = new MagiskModuleManager();
        configManager = new EmbeddingConfigManager();
        fontManager = new FontInstallerManager();
    }

    private void initToolbarData() {
        String appName = getIntent().getStringExtra("app_name");
        appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + getString(R.string.settings_detail_title_suffix));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initViews() {
        // --- 简单配置开关 ---
        switchRemoveBlacklist = findViewById(R.id.switch_remove_blacklist);
        switchRemoveBlacklist.setOnCheckedChangeListener((v, c) -> mPrefsUtils.saveBooleanSetting("remove_blacklist", c));

        switchSplitScreenMandatory = findViewById(R.id.switch_Split_screen_Mandatory);
        switchSplitScreenMandatory.setOnCheckedChangeListener((v, c) -> mPrefsUtils.saveBooleanSetting("Split_Screen_mandatory", c));

        switchAllowDisableDolby = findViewById(R.id.switch_AllowDolbyDisable);
        switchAllowDisableDolby.setOnCheckedChangeListener((v, c) -> mPrefsUtils.saveBooleanSetting("allow_display_dolby", c));

        switchAllowNativePermissionController = findViewById(R.id.switch_AllowNativePermissionController);
        switchAllowNativePermissionController.setOnCheckedChangeListener((v, c) -> mPrefsUtils.saveBooleanSetting("PermissionControllerHook", c));

        // --- Magisk 模块开关 ---
        ModuleSwitch = findViewById(R.id.switch_MagiskModule);
        ModuleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> handleModuleSwitch(isChecked));

        // --- 强制小窗模式开关 (Root命令) ---
        switchFloatMandatory = findViewById(R.id.switch_Float_app_Mandatory);
        switchFloatMandatory.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 使用线程池执行命令，防止阻塞 UI
            EnhancedShellExecutor.getInstance().executeCommand(
                    "su -c settings put global force_resizable_activities " + (isChecked ? "1" : "0")
            );
        });

        // --- 悬浮窗按钮 ---
        ImageButton floatingButton = findViewById(R.id.button_floating_window);
        floatingButton.setOnClickListener(v -> startFloatingWindow());

        // --- 横屏/小窗 适配功能 ---
        View customLandscapeLayout = findViewById(R.id.custom_landscape_layout);
        if (customLandscapeLayout != null) {
            customLandscapeLayout.setOnClickListener(v -> startFloatingWindow());
        }

        // --- 查看适配结果 (导入配置) ---
        View customLandscapeResult = findViewById(R.id.custom_landscapeResult_layout);
        if (customLandscapeResult != null) {
            customLandscapeResult.setOnClickListener(v -> {
                // 读取配置是一个IO操作，但在点击时读取通常可以接受，或者放入线程
                List<EmbeddingConfigManager.ConfigFileInfo> validConfigs = configManager.loadAndValidateConfigFiles(this);
                showConfigSelectionDialog(validConfigs);
            });
        }

        // --- 适配策略搜索 ---
        View adapter_yishijie = findViewById(R.id.view_adapted_strategies_layout);
        if (adapter_yishijie != null) {
            adapter_yishijie.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsDetailActivity.this, searchPage.class);
                startActivity(intent);
            });
        }

        // --- 字体导入 ---
        View importFontLayout = findViewById(R.id.import_font_layout);
        if (importFontLayout != null) {
            importFontLayout.setOnClickListener(v -> startFontImportProcess());
        }
    }

    private void loadSettings() {
        // 加载 SharedPreferences 配置
        switchRemoveBlacklist.setChecked(mPrefsUtils.loadBooleanSetting("remove_blacklist", false));
        switchSplitScreenMandatory.setChecked(mPrefsUtils.loadBooleanSetting("Split_Screen_mandatory", false));
        switchAllowDisableDolby.setChecked(mPrefsUtils.loadBooleanSetting("allow_display_dolby", false));
        switchAllowNativePermissionController.setChecked(mPrefsUtils.loadBooleanSetting("PermissionControllerHook", false));

        // 异步检查 Root 状态和模块状态
        new Thread(() -> {
            boolean isModuleEnabled = magiskManager.isModuleEnabled();
            boolean isForceResize = isForceResizableActivitiesEnabled();

            runOnUiThread(() -> {
                ModuleSwitch.setChecked(isModuleEnabled);
                switchFloatMandatory.setChecked(isForceResize);
            });
        }).start();
    }

    private void initRestartButton() {
        FloatingActionButton fabRestart = findViewById(R.id.fab_restart);
        fabRestart.setOnClickListener(v -> showRestartConfirmationDialog());
    }

    // ============================================================================================
    // Magisk 模块处理逻辑
    // ============================================================================================

    private void handleModuleSwitch(boolean isChecked) {
        // 防止重复触发：如果已经在对应状态，则不做处理
        if (isChecked && magiskManager.isModuleEnabled()) {
            ModuleSwitch.setChecked(true);
            return;
        }

        loadingDialog = new LoadingDialog(this);
        loadingDialog.show(getString(isChecked ? R.string.installing_module : R.string.removing_module));

        new Thread(() -> {
            String result;
            if (isChecked) {
                result = magiskManager.installModule(this);
            } else {
                result = magiskManager.removeModule(this);
            }

            runOnUiThread(() -> {
                loadingDialog.dismiss();
                if ("success".equals(result)) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.tip_title)
                            .setMessage(isChecked ? R.string.install_success_message : R.string.remove_success_message)
                            .setNegativeButton(R.string.got_it_button, null)
                            .show();
                } else {
                    // 失败回滚开关状态
                    ModuleSwitch.setChecked(!isChecked);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.error_title)
                            .setMessage(getString(isChecked ? R.string.install_failed_message : R.string.remove_failed_message, result))
                            .setNegativeButton(R.string.got_it_button, null)
                            .show();
                }
            });
        }).start();
    }

    // ============================================================================================
    // 悬浮窗逻辑
    // ============================================================================================

    private void startFloatingWindow() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, R.string.request_usage_stats_permission, Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            return;
        }
        showFloatingWindow();
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void showFloatingWindow() {
        if (floatingWindow != null) {
            hideFloatingWindow();
            return;
        }
        floatingWindow = new FloatingWindow(this);
        Toast.makeText(this, R.string.floating_window_started, Toast.LENGTH_SHORT).show();
    }

    private void hideFloatingWindow() {
        if (floatingWindow != null) {
            floatingWindow.hide();
            floatingWindow = null;
            Toast.makeText(this, R.string.floating_window_closed, Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================================================
    // 一视界配置 (JSON) 刷入逻辑
    // ============================================================================================

    private void showConfigSelectionDialog(List<EmbeddingConfigManager.ConfigFileInfo> configs) {
        if (configs.isEmpty()) {
            Toast.makeText(this, R.string.no_config_files_prompt, Toast.LENGTH_SHORT).show();
            return;
        }

        // 准备数据
        String[] displayItems = new String[configs.size()];
        for (int i = 0; i < configs.size(); i++) {
            EmbeddingConfigManager.ConfigFileInfo config = configs.get(i);
            displayItems[i] = config.timestamp + " " + config.appName + getString(R.string.config_suffix);
        }

        boolean[] checkedItems = new boolean[configs.size()];
        Arrays.fill(checkedItems, false);

        Set<String> flashedConfigs = loadStringSetSetting("flashed_configs", new HashSet<>());

        // 构建 Dialog UI
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_config_selection, null);
        builder.setView(dialogView);

        TextView titleText = dialogView.findViewById(R.id.dialog_title);
        titleText.setText(R.string.select_config_files);

        TextView flashedCountText = dialogView.findViewById(R.id.flashed_count_text);
        int flashedCount = flashedConfigs.size();
        if (flashedCount > 0) {
            flashedCountText.setText(getString(R.string.flashed_configs_count, flashedCount));
            flashedCountText.setVisibility(View.VISIBLE);
        } else {
            flashedCountText.setVisibility(View.GONE);
        }

        AlertDialog dialog = builder.create();

        // 配置 ListView
        ListView listView = dialogView.findViewById(R.id.config_list_view);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_multiple_choice, displayItems) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                CheckedTextView checkedTextView = (CheckedTextView) view;
                EmbeddingConfigManager.ConfigFileInfo config = configs.get(position);
                String configKey = config.timestamp + "_" + config.packageName;

                if (flashedConfigs.contains(configKey)) {
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
        listView.setOnItemClickListener((parent, view, position, id) -> {
            EmbeddingConfigManager.ConfigFileInfo config = configs.get(position);
            String configKey = config.timestamp + "_" + config.packageName;
            if (flashedConfigs.contains(configKey)) {
                ((CheckedTextView) view).setChecked(false);
                Toast.makeText(this, R.string.config_already_flashed, Toast.LENGTH_SHORT).show();
            } else {
                checkedItems[position] = !checkedItems[position];
                ((CheckedTextView) view).setChecked(checkedItems[position]);
            }
        });

        // 按钮事件
        Button deleteButton = dialogView.findViewById(R.id.delete_button);
        Button flashButton = dialogView.findViewById(R.id.flash_button);
        Button cancelButton = dialogView.findViewById(R.id.cancel_button);
        Button restoreButton = dialogView.findViewById(R.id.restore_button);

        deleteButton.setOnClickListener(v -> {
            List<EmbeddingConfigManager.ConfigFileInfo> toDelete = new ArrayList<>();
            for (int i = 0; i < checkedItems.length; i++) {
                if (checkedItems[i]) toDelete.add(configs.get(i));
            }
            if (!toDelete.isEmpty()) {
                performConfigDelete(toDelete, flashedConfigs, dialog);
            }
        });

        flashButton.setOnClickListener(v -> {
            List<EmbeddingConfigManager.ConfigFileInfo> toFlash = new ArrayList<>();
            for (int i = 0; i < checkedItems.length; i++) {
                if (checkedItems[i]) toFlash.add(configs.get(i));
            }
            if (!toFlash.isEmpty()) {
                dialog.dismiss();
                flashSelectedConfigs(toFlash);
            }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        if (!flashedConfigs.isEmpty()) {
            restoreButton.setVisibility(View.VISIBLE);
            restoreButton.setOnClickListener(v -> {
                dialog.dismiss();
                restoreOriginalModule();
            });
        } else {
            restoreButton.setVisibility(View.GONE);
        }

        dialog.show();
    }

    private void performConfigDelete(List<EmbeddingConfigManager.ConfigFileInfo> toDelete, Set<String> flashed, AlertDialog dialog) {
        int count = 0;
        for (EmbeddingConfigManager.ConfigFileInfo c : toDelete) {
            if (flashed.contains(c.timestamp + "_" + c.packageName)) continue;
            if (c.file.delete()) count++;
        }
        Toast.makeText(this, getString(R.string.delete_success, count), Toast.LENGTH_SHORT).show();
        dialog.dismiss();
        showConfigSelectionDialog(configManager.loadAndValidateConfigFiles(this));
    }

    private void flashSelectedConfigs(List<EmbeddingConfigManager.ConfigFileInfo> selectedConfigs) {
        if (!magiskManager.isModuleEnabled()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.tip_title).setMessage(R.string.install_module_first).show();
            return;
        }

        loadingDialog = new LoadingDialog(this);
        loadingDialog.show(getString(R.string.flashing_config));

        new Thread(() -> {
            try {
                // 调用 Manager 执行核心刷入逻辑
                configManager.flashConfigs(this, selectedConfigs);

                // 更新 SharedPrefs
                Set<String> flashed = loadStringSetSetting("flashed_configs", new HashSet<>());
                for (EmbeddingConfigManager.ConfigFileInfo c : selectedConfigs) {
                    flashed.add(c.timestamp + "_" + c.packageName);
                }
                saveStringSetSetting("flashed_configs", flashed);

                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    new MaterialAlertDialogBuilder(this).setTitle(R.string.success_title).setMessage(R.string.flash_success_message).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    new MaterialAlertDialogBuilder(this).setTitle(R.string.error_title).setMessage(getString(R.string.flash_failed_message, e.getMessage())).show();
                });
            }
        }).start();
    }

    private void restoreOriginalModule() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_restore_title)
                .setMessage(R.string.confirm_restore_message)
                .setPositiveButton(R.string.confirm_button, (dialog, which) -> {
                    loadingDialog = new LoadingDialog(this);
                    loadingDialog.show(getString(R.string.restoring_module));
                    new Thread(() -> {
                        magiskManager.removeModule(this); // 先删
                        String res = magiskManager.installModule(this); // 再装（恢复初始）
                        saveStringSetSetting("flashed_configs", new HashSet<>()); // 清空记录
                        runOnUiThread(() -> {
                            loadingDialog.dismiss();
                            if ("success".equals(res)) {
                                new MaterialAlertDialogBuilder(this).setTitle(R.string.success_title).setMessage(R.string.restore_success_message).show();
                            } else {
                                new MaterialAlertDialogBuilder(this).setTitle(R.string.error_title).setMessage(res).show();
                            }
                        });
                    }).start();
                })
                .setNegativeButton(R.string.restart_no, null)
                .show();
    }

    // ============================================================================================
    // 字体导入逻辑
    // ============================================================================================

    private void startFontImportProcess() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"font/ttf", "application/x-font-ttf", "application/octet-stream"});
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_ttf_file)), REQUEST_CODE_PICK_FONT);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_file_manager_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleFontSelection(Uri uri) {
        loadingDialog = new LoadingDialog(this);
        loadingDialog.show(getString(R.string.preparing_font_file));
        new Thread(() -> {
            try {
                // 复制到缓存
                currentSelectedFontFile = fontManager.copyFontToTemp(this, uri);
                String fileName = getFileName(uri);
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    showFontInputDialog(fileName != null ? fileName : getString(R.string.unknown_font_filename));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(this, "File Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showFontInputDialog(String originalFileName) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_font_input, null);
        EditText etFontName = dialogView.findViewById(R.id.et_font_name);
        EditText etFontDescription = dialogView.findViewById(R.id.et_font_description);
        etFontDescription.setText(getString(R.string.default_font_description, originalFileName));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.input_font_info_title)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm_button, (dialog, which) -> {
                    String name = etFontName.getText().toString().trim();
                    String desc = etFontDescription.getText().toString().trim();
                    if (!name.isEmpty() && !desc.isEmpty()) {
                        startFontImport(name, desc);
                    }
                })
                .show();
    }

    private void startFontImport(String fontName, String fontDescription) {
        loadingDialog = new LoadingDialog(this);
        loadingDialog.show(getString(R.string.importing_font));
        new Thread(() -> {
            try {
                fontManager.installFont(this, currentSelectedFontFile, fontName, fontDescription);
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    new MaterialAlertDialogBuilder(this).setTitle(R.string.import_success_title)
                            .setMessage(R.string.import_success_message).setPositiveButton(R.string.restart_yes, null).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    new MaterialAlertDialogBuilder(this).setTitle(R.string.import_failed_title)
                            .setMessage(e.getMessage()).show();
                });
            }
        }).start();
    }

    // ============================================================================================
    // 杂项 Helper 方法
    // ============================================================================================

    private void showRestartConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restart_xp_title)
                .setMessage(getString(R.string.restart_xp_message_header) + appPackageName + getString(R.string.restart_xp_message))
                .setPositiveButton(R.string.restart_yes, (dialog, which) -> forceStopApp())
                .setNegativeButton(R.string.restart_no, null)
                .show();
    }

    private void forceStopApp() {
        if (appPackageName == null) return;
        // 使用 EnhancedShellExecutor 批量执行强行停止
        new Thread(() -> {
            EnhancedShellExecutor executor = EnhancedShellExecutor.getInstance();
            executor.executeRootCommand("am force-stop " + appPackageName);
            executor.executeRootCommand("am force-stop com.android.permissioncontroller");
            executor.executeRootCommand("am force-stop com.zui.safecenter");
        }).start();
    }

    private boolean isForceResizableActivitiesEnabled() {
        EnhancedShellExecutor.ShellResult result = EnhancedShellExecutor.getInstance()
                .executeRootCommand("settings get global force_resizable_activities", 2);
        return result.isSuccess() && "1".equals(result.output);
    }

    private Set<String> loadStringSetSetting(String key, Set<String> defaultSet) {
        SharedPreferences sp = getSharedPreferences("module_settings", Context.MODE_PRIVATE);
        Set<String> result = sp.getStringSet(key, null);
        return result == null ? defaultSet : new HashSet<>(result);
    }

    private void saveStringSetSetting(String key, Set<String> set) {
        SharedPreferences sp = getSharedPreferences("module_settings", Context.MODE_PRIVATE);
        sp.edit().putStringSet(key, new HashSet<>(set)).apply();
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (Objects.equals(uri.getScheme(), "content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) result = cursor.getString(index);
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) result = result.substring(cut + 1);
            }
        }
        return result;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) startFloatingWindow();
            else Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_SHORT).show();
        } else if (requestCode == REQUEST_CODE_PICK_FONT && resultCode == RESULT_OK && data != null) {
            handleFontSelection(data.getData());
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideFloatingWindow();
    }
}
