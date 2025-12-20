package com.qimian233.ztool.settingactivity.safecenter;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.qimian233.ztool.R;
import com.qimian233.ztool.EnhancedShellExecutor;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class SafeCenterSettingsActivity extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private FloatingActionButton fabRestart;
    private MaterialSwitch switchAllowAutoRun, switchDisableSafeScan, switchBypassDocumentsUI;

    // Shell执行器
    private EnhancedShellExecutor shellExecutor;

    // 待处理的Shell任务
    private final List<Future<EnhancedShellExecutor.ShellResult>> pendingFutures = new ArrayList<>();

    // 防止重复点击的标志
    private boolean isRestartProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_safecenter_settings);

        // 初始化Shell执行器
        shellExecutor = EnhancedShellExecutor.getInstance();

        String appName = getIntent().getStringExtra("app_name");
        appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + getString(R.string.safe_center_settings_title_suffix));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mPrefsUtils = new ModulePreferencesUtils(this);
        initViews();
        loadSettings();
        initRestartButton();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 取消所有未完成的Shell任务
        cancelPendingFutures();
        Log.d("SafeCenterSettings", "Activity销毁，已取消所有Shell任务");
    }

    /**
     * 取消所有待处理的Shell任务
     */
    private void cancelPendingFutures() {
        synchronized (pendingFutures) {
            for (Future<EnhancedShellExecutor.ShellResult> future : pendingFutures) {
                if (!future.isDone()) {
                    future.cancel(true);
                    Log.d("SafeCenterSettings", "取消Shell任务");
                }
            }
            pendingFutures.clear();
        }
    }

    private void initViews() {
        // 默认允许应用自启开关
        switchAllowAutoRun = findViewById(R.id.switch_allow_autorun);
        switchAllowAutoRun.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("default_enable_autorun", isChecked));

        // 禁用联想安全中心自动扫描开关
        switchDisableSafeScan = findViewById(R.id.switch_Disable_SafeScanBlock);
        switchDisableSafeScan.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("block_safecenter_scan", isChecked));

        // 允许用户选择根目录开关
        switchBypassDocumentsUI = findViewById(R.id.switch_BypassDocumentsUI);
        switchBypassDocumentsUI.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("documents_ui_bypass", isChecked));
    }

    private void loadSettings() {
        // 加载默认允许应用自启状态
        boolean isAutoRunAllowedbyDefault = mPrefsUtils.loadBooleanSetting("default_enable_autorun",false);
        switchAllowAutoRun.setChecked(isAutoRunAllowedbyDefault);

        // 加载禁用联想安全中心自动扫描状态
        boolean isSafeScanBlocked = mPrefsUtils.loadBooleanSetting("block_safecenter_scan",false);
        switchDisableSafeScan.setChecked(isSafeScanBlocked);

        // 加载允许用户选择根目录状态
        boolean isDocumentsUIBypassed = mPrefsUtils.loadBooleanSetting("documents_ui_bypass",false);
        switchBypassDocumentsUI.setChecked(isDocumentsUIBypassed);
    }

    private void initRestartButton() {
        fabRestart = findViewById(R.id.fab_restart);
        fabRestart.setOnClickListener(v -> {
            if (isRestartProcessing) {
                Log.d("RestartButton", "重启操作正在进行中，忽略重复点击");
                return;
            }
            showRestartConfirmationDialog();
        });
    }

    private void showRestartConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restart_xp_title)
                .setMessage(getString(R.string.restart_xp_message_header) + appPackageName + ", com.android.documentsui"+ getString(R.string.restart_xp_message))
                .setPositiveButton(R.string.restart_yes, (dialog, which) -> forceStopApp())
                .setNegativeButton(R.string.restart_no, null)
                .show();
    }

    /**
     * 强制停止应用 - 优化版本
     */
    private void forceStopApp() {
        if (appPackageName == null || appPackageName.isEmpty()) {
            Toast.makeText(this, R.string.empty_package_name_message, Toast.LENGTH_SHORT).show();
            return;
        }

        if (isRestartProcessing) {
            Log.d("ForceStopApp", "重启操作正在进行中");
            return;
        }

        isRestartProcessing = true;
        fabRestart.setEnabled(false);

        new Thread(() -> {
            try {
                // 方法1: 使用am force-stop命令（推荐）
                String command = "am force-stop " + appPackageName;
                EnhancedShellExecutor.ShellResult result = shellExecutor.executeRootCommand(command, 5);

                String command2 = "am force-stop com.android.documentsui";
                EnhancedShellExecutor.ShellResult result_1 = shellExecutor.executeRootCommand(command2, 5);

                final boolean success = result.isSuccess() && result_1.isSuccess();

                // 如果方法1失败，尝试方法2: 使用killall
                if (!success) {
                    Log.w("ForceStopApp", "方法1失败，尝试方法2");
                    command = "killall " + appPackageName;
                    EnhancedShellExecutor.ShellResult result2 = shellExecutor.executeRootCommand(command, 5);

                    final boolean success2 = result2.isSuccess();

                    runOnUiThread(() -> {
                        if (success2) {
                            Toast.makeText(SafeCenterSettingsActivity.this, R.string.app_process_restarted_message, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(SafeCenterSettingsActivity.this,
                                    getString(R.string.restart_fail_prefix) + result2.error, Toast.LENGTH_SHORT).show();
                        }
                        resetRestartButton();
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(SafeCenterSettingsActivity.this, R.string.app_process_restarted_message, Toast.LENGTH_SHORT).show();
                        resetRestartButton();
                    });
                }

                Log.d("ForceStopApp", "强制停止应用结果: " + (success ? "成功" : "失败"));

            } catch (Exception e) {
                Log.e("ForceStopApp", "强制停止应用时出错: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(SafeCenterSettingsActivity.this,
                            getString(R.string.restart_fail_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show();
                    resetRestartButton();
                });
            }
        }).start();
    }

    /**
     * 重置重启按钮状态
     */
    private void resetRestartButton() {
        isRestartProcessing = false;
        fabRestart.setEnabled(true);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void saveSettings(String moduleName, Boolean newValue) {
        mPrefsUtils.saveBooleanSetting(moduleName, newValue);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("SafeCenterSettings", "Activity暂停");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("SafeCenterSettings", "Activity恢复");
    }
}
