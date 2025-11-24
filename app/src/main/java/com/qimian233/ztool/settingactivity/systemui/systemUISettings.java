package com.qimian233.ztool.settingactivity.systemui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import static android.widget.Toast.LENGTH_SHORT;
import static android.widget.Toast.makeText;

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
import com.qimian233.ztool.settingactivity.systemui.ControlCenter.ControlCenterSettingsActivity;
import com.qimian233.ztool.settingactivity.systemui.lockscreen.LockScreenSettingsActivity;
import com.qimian233.ztool.settingactivity.systemui.statusBarSetting.StatusBarSettingsActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class systemUISettings extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private FloatingActionButton fabRestart;
    private MaterialSwitch switchEnableNativeAod;
    private MaterialSwitch switchEnableLenovoAod;
    private MaterialSwitch switchChargingAnimation;
    private MaterialSwitch switchEnableGuestMode;
    private MaterialSwitch switchChargingAnimationFix;
    private View buttonStartAODActivity;

    // Shell执行器
    private EnhancedShellExecutor shellExecutor;

    // 待处理的Shell任务
    private final List<Future<EnhancedShellExecutor.ShellResult>> pendingFutures = new ArrayList<>();

    // 防止重复点击的标志
    private boolean isAodSwitchProcessing = false;
    private boolean isRestartProcessing = false;
    final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_system_uisettings);

        // 初始化Shell执行器
        shellExecutor = EnhancedShellExecutor.getInstance();

        String appName = getIntent().getStringExtra("app_name");
        appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + getString(R.string.SystemUIActionBar));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mPrefsUtils = new ModulePreferencesUtils(this);
        initViews();
        loadSettingsAsync();
        initRestartButton();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 取消所有未完成的Shell任务
        cancelPendingFutures();
        Log.d("SystemUISettings", "Activity销毁，已取消所有Shell任务");
    }

    /**
     * 取消所有待处理的Shell任务
     */
    private void cancelPendingFutures() {
        synchronized (pendingFutures) {
            for (Future<EnhancedShellExecutor.ShellResult> future : pendingFutures) {
                if (!future.isDone()) {
                    future.cancel(true);
                    Log.d("SystemUISettings", "取消Shell任务");
                }
            }
            pendingFutures.clear();
        }
    }

    /**
     * 添加Future到待处理列表
     */
    private void addPendingFuture(Future<EnhancedShellExecutor.ShellResult> future) {
        synchronized (pendingFutures) {
            pendingFutures.add(future);
        }
    }

    /**
     * 从待处理列表中移除Future
     */
    private void removePendingFuture(Future<EnhancedShellExecutor.ShellResult> future) {
        synchronized (pendingFutures) {
            pendingFutures.remove(future);
        }
    }

    private void initViews() {
        // 状态栏设置入口
        LinearLayout cardStatusBar = findViewById(R.id.card_statusbar_settings);
        cardStatusBar.setOnClickListener(v -> {
            Intent intent = new Intent(this, StatusBarSettingsActivity.class);
            intent.putExtra("app_name", getIntent().getStringExtra("app_name"));
            intent.putExtra("app_package", appPackageName);
            startActivity(intent);
        });

        // 锁屏设置入口
        LinearLayout cardLockScreen = findViewById(R.id.card_lockscreen_settings);
        cardLockScreen.setOnClickListener(v -> {
            Intent intent = new Intent(this, LockScreenSettingsActivity.class);
            intent.putExtra("app_name", getIntent().getStringExtra("app_name"));
            intent.putExtra("app_package", appPackageName);
            startActivity(intent);
        });

        // 控制中心设置入口
        LinearLayout card_control_center_settings = findViewById(R.id.card_control_center_settings);
        card_control_center_settings.setOnClickListener(v -> {
            Intent intent = new Intent(this, ControlCenterSettingsActivity.class);
            intent.putExtra("app_name", getIntent().getStringExtra("app_name"));
            intent.putExtra("app_package", appPackageName);
            startActivity(intent);
        });

        // 原生AOD设置
        switchEnableNativeAod = findViewById(R.id.switch_native_aod);
        switchEnableNativeAod.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isAodSwitchProcessing) {
                Log.d("NativeAODSwitch", "AOD开关正在处理中，忽略重复操作");
                return;
            }
            isAodSwitchProcessing = true;
            handler.post(() -> {
                setNativeAodEnabled(isChecked);
                mPrefsUtils.saveBooleanSetting("ForceNativeAOD", isChecked);
                Log.d("NativeAODSwitch", "Switch state saved: " + isChecked);

                boolean lenovoAodEnabled = mPrefsUtils.loadBooleanSetting("ForceLenovoAOD", false);
                if (lenovoAodEnabled) {
                    mPrefsUtils.saveBooleanSetting("ForceLenovoAOD", false);
                    Log.d("LenovoAODSwitch", "Switch state saved: false");
                    switchEnableLenovoAod.setChecked(false);
                    buttonStartAODActivity.setVisibility(View.GONE);
                    makeText(
                        this, R.string.restart_scope_required,
                        LENGTH_SHORT
                    ).show();
                }
                isAodSwitchProcessing = false;
            });
        });

        // 联想AOD，本功能和原生AOD不共存
        switchEnableLenovoAod = findViewById(R.id.switch_lenovo_aod);
        switchEnableLenovoAod.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isAodSwitchProcessing) {
                Log.d("LenovoAODSwitch", "AOD开关正在处理中，忽略重复操作");
                return;
            }
            isAodSwitchProcessing = true;
            mPrefsUtils.saveBooleanSetting("ForceLenovoAOD", isChecked);
            Log.d("LenovoAODSwitch", "Switch state saved: " + isChecked);
            buttonStartAODActivity.setVisibility(isChecked ? View.VISIBLE : View.GONE);

            handler.post(() -> {
                if (isAodEnabled()) {
                    setNativeAodEnabled(false);
                    Log.d("NativeAODSwitch", "Switch state saved: false");
                    switchEnableNativeAod.setChecked(false);
                }
                isAodSwitchProcessing = false;
            });
        });

        buttonStartAODActivity = findViewById(R.id.start_lenovo_aod_activity);
        buttonStartAODActivity.setOnClickListener(v -> {
            EnhancedShellExecutor.ShellResult executeResult = shellExecutor.executeRootCommand("am start -n com.android.systemui/com.android.systemui.aod.setting.AoDSettingActivity", 5);
            Log.d("LenovoAODPicker",executeResult.toString());
        });

        // 充电动画设置
        switchChargingAnimation = findViewById(R.id.switch_chargingAnimation);
        switchChargingAnimation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 保存开关状态到SharedPreferences
            mPrefsUtils.saveBooleanSetting("No_ChargeAnimation", isChecked);
            Log.d("ChargingAnimation", "Switch state saved: " + isChecked);
        });

        // 充电动画修复
        switchChargingAnimationFix = findViewById(R.id.switch_chargingFixAnimation);
        switchChargingAnimationFix.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 保存开关状态到SharedPreferences
            mPrefsUtils.saveBooleanSetting("charge_animation_fix", isChecked);
        });

        // 访客模式设置
        switchEnableGuestMode = findViewById(R.id.switch_GuestFix);
        switchEnableGuestMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 保存开关状态到SharedPreferences
            mPrefsUtils.saveBooleanSetting("guest_mode_controller", isChecked);
            Log.d("GuestModeSwitch", "Switch state saved: " + isChecked);
        });
    }

    /**
     * 异步加载设置
     */
    private void loadSettingsAsync() {
        new Thread(() -> {
            try {
                // 加载原生AOD设置
                boolean aodEnabled = mPrefsUtils.loadBooleanSetting("ForceNativeAOD", false);
                runOnUiThread(() -> switchEnableNativeAod.setChecked(aodEnabled));

                // 加载联想AOD设置
                boolean lenovoAodEnabled = mPrefsUtils.loadBooleanSetting("ForceLenovoAOD", false);
                runOnUiThread(() -> switchEnableLenovoAod.setChecked(lenovoAodEnabled));

                // 加载启动联想AOD设置界面控件的可见性
                buttonStartAODActivity.setVisibility(lenovoAodEnabled ? View.VISIBLE : View.GONE);

                // 加载充电动画开关状态
                boolean chargingAnimationEnabled = mPrefsUtils.loadBooleanSetting("No_ChargeAnimation", false);
                runOnUiThread(() -> switchChargingAnimation.setChecked(chargingAnimationEnabled));

                // 加载充电动画修复开关状态
                boolean chargingAnimationFixEnabled = mPrefsUtils.loadBooleanSetting("charge_animation_fix", false);
                runOnUiThread(() -> switchChargingAnimationFix.setChecked(chargingAnimationFixEnabled));

                // 加载访客模式开关状态
                boolean guestModeEnabled = mPrefsUtils.loadBooleanSetting("guest_mode_controller", false);
                runOnUiThread(() -> switchEnableGuestMode.setChecked(guestModeEnabled));

                Log.d("SystemUISettings", "设置加载完成");
            } catch (Exception e) {
                Log.e("SystemUISettings", "加载设置失败: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 启用Android原生的AOD显示
     */
    private void setNativeAodEnabled(final boolean enabled) {
        new Thread(() -> {
            try {
                String command = "settings put secure doze_always_on " + (enabled ? "1" : "0");
                EnhancedShellExecutor.ShellResult result = shellExecutor.executeRootCommand(command, 5);

                final boolean success = result.isSuccess();
                Log.d("AODSwitch", "AOD设置命令执行结果: " + (success ? "成功" : "失败") +
                        ", 退出码: " + result.exitCode);

                handler.post(() -> {
                    if (!success) {
                        // 恢复开关状态
                        switchEnableNativeAod.setChecked(!enabled);
                        makeText(this,
                                "设置失败: " + result.error, LENGTH_SHORT).show();
                    }
                    isAodSwitchProcessing = false;
                });

            } catch (Exception e) {
                Log.e("AODSwitch", "设置AOD时出错: " + e.getMessage());
                handler.post(() -> {
                    // 恢复开关状态
                    switchEnableNativeAod.setChecked(!enabled);
                    makeText(this,
                            "执行错误: " + e.getMessage(), LENGTH_SHORT).show();
                    isAodSwitchProcessing = false;
                });
            }
        }).start();
    }

    /**
     * 检查AOD是否启用 - 优化版本
     */
    public boolean isAodEnabled() {
        try {
            EnhancedShellExecutor.ShellResult result = shellExecutor.executeRootCommand(
                    "settings get secure doze_always_on", 5);

            if (result.isSuccess() && result.output != null) {
                String output = result.output.trim();
                boolean enabled = output.equals("1");
                Log.d("AODCheck", "原生AOD状态: " + (enabled ? "启用" : "禁用"));
                return enabled;
            }
        } catch (Exception e) {
            Log.e("AODCheck", "检查原生AOD状态失败: " + e.getMessage());
        }
        return false;
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
                .setTitle(getString(R.string.restart_xp_title))
                .setMessage(getString(R.string.restart_xp_message_header) + appPackageName + "，com.zui.wallpapersetting" + getString(R.string.restart_xp_message))
                .setPositiveButton(R.string.restart_yes, (dialog, which) -> forceStopApp())
                .setNegativeButton(R.string.restart_no, null)
                .show();
    }

    /**
     * 强制停止应用 - 优化版本
     */
    private void forceStopApp() {
        if (appPackageName == null || appPackageName.isEmpty()) {
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
                Log.w("ForceStopApp", "方法1失败，尝试方法2");
                String command = "killall " + appPackageName;
                EnhancedShellExecutor.ShellResult result2 = shellExecutor.executeRootCommand(command, 5);

                final boolean success = result2.isSuccess();

                handler.post(() -> {
                    if (success) {
                        makeText(this, R.string.restartSuccess, LENGTH_SHORT).show();
                    } else {
                        makeText(this,
                                R.string.restartFail + result2.error, LENGTH_SHORT).show();
                    }
                    resetRestartButton();
                });

                Log.d("ForceStopApp", "强制停止应用结果: " + (success ? "成功" : "失败"));

            } catch (Exception e) {
                Log.e("ForceStopApp", "强制停止应用时出错: " + e.getMessage());
                handler.post(() -> {
                    makeText(this,
                            R.string.restartFail + e.getMessage(), LENGTH_SHORT).show();
                    resetRestartButton();
                });
            }
        }).start();

        new Thread(() -> {
            try {
                Log.w("ForceStopApp", "方法1失败，尝试方法2");
                String command = "killall " + "com.zui.wallpapersetting";
                EnhancedShellExecutor.ShellResult result2 = shellExecutor.executeRootCommand(command, 5);

                final boolean success = result2.isSuccess();

                Log.d("ForceStopApp", "强制停止应用结果: " + (success ? "成功" : "失败"));

            } catch (Exception e) {
                Log.e("ForceStopApp", "强制停止应用时出错: " + e.getMessage());
                handler.post(() -> {
                    makeText(this,
                            R.string.restartFail + e.getMessage(), LENGTH_SHORT).show();
                    resetRestartButton();
                });
            }
        }).start();
    }

    /**
     * 重置重启按钮状态
     */
    private void resetRestartButton() {
        handler.post(() -> {
            isRestartProcessing = false;
            fabRestart.setEnabled(true);
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("SystemUISettings", "Activity暂停");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("SystemUISettings", "Activity恢复");
    }
}
