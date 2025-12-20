package com.qimian233.ztool.settingactivity.systemframework;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.qimian233.ztool.R;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;

public class FrameworkSettingsActivity extends AppCompatActivity {

    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchKeepRotation, switchDisableZUIApplist, switchDisableFlagSecure;
    // private MaterialSwitch switchDisableForcedLockscreenPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_framework_settings);

        String appName = getIntent().getStringExtra("app_name");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + getString(R.string.framework_settings_title_suffix));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mPrefsUtils = new ModulePreferencesUtils(this);
        initViews();
        loadSettings();
        initRestartButton();
    }

    private void initViews() {
        // 设置保持屏幕方向按钮点击监听
        switchKeepRotation = findViewById(R.id.switch_keep_rotation);
        switchKeepRotation.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("keep_rotation",isChecked));
        // 设置禁用系统应用列表管理点击监听
        switchDisableZUIApplist = findViewById(R.id.switch_disable_zuiapplist);
        switchDisableZUIApplist.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("allow_get_packages",isChecked));
        // 设置禁用每24H验证一次锁屏密码
        // switchDisableForcedLockscreenPassword = findViewById(R.id.switch_disable_lockscreen_password_per24h);
        // switchDisableForcedLockscreenPassword.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("NoMorePasswordPer24H", isChecked));
        // 移除安全窗口标识
        switchDisableFlagSecure = findViewById(R.id.switch_DisableFlagSecure);
        switchDisableFlagSecure.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("disable_flag_secure",isChecked));
    }

    private void loadSettings() {
        // 加载禁用系统应用列表管理开关状态
        boolean disableZUIApplist = mPrefsUtils.loadBooleanSetting("allow_get_packages",false);
        switchDisableZUIApplist.setChecked(disableZUIApplist);
        // 加载保持屏幕方向设置
        boolean isKeepRotation = mPrefsUtils.loadBooleanSetting("keep_rotation", false);
        switchKeepRotation.setChecked(isKeepRotation);
        // 加载禁止24H验证一次锁屏密码的设置
        // boolean isNotRequireLockscreenPasswordPer24H = mPrefsUtils.loadBooleanSetting("NoMorePasswordPer24H", false);
        // switchDisableForcedLockscreenPassword.setChecked(isNotRequireLockscreenPasswordPer24H);
        // 加载移除安全窗口标识
        boolean isDisableFlagSecure = mPrefsUtils.loadBooleanSetting("disable_flag_secure", false);
        switchDisableFlagSecure.setChecked(isDisableFlagSecure);
    }

    private void initRestartButton() {
        FloatingActionButton fabRestart = findViewById(R.id.fab_restart);
        fabRestart.setOnClickListener(v -> showRestartConfirmationDialog());
    }

    private void showRestartConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restart_system_title)
                .setMessage(R.string.restart_system_message)
                .setPositiveButton(R.string.restart_yes, (dialog, which) -> restartOS())
                .setNegativeButton(R.string.restart_no, null)
                .show();
    }

    private void restartOS() {
        try {
            // 使用root权限执行重启命令
            Process process = Runtime.getRuntime().exec("su -c reboot");
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.restart_fail_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void saveSettings(String moduleName, Boolean newValue) {
        mPrefsUtils.saveBooleanSetting(moduleName, newValue);
    }
}
