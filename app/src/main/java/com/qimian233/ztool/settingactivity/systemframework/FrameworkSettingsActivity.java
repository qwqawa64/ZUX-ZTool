package com.qimian233.ztool.settingactivity.systemframework;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.qimian233.ztool.R;
import com.qimian233.ztool.config.ModuleConfig;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;

public class FrameworkSettingsActivity extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private FloatingActionButton fabRestart;
    private MaterialSwitch switchKeepRotation;
    private MaterialSwitch switchDisableZUIApplist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_framework_settings);

        String appName = getIntent().getStringExtra("app_name");
        appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + " - 系统框架设置");
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
        switchKeepRotation.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                saveSettings("keep_rotation",isChecked);
            }
        });
        // 设置禁用系统应用列表管理点击监听
        switchDisableZUIApplist = findViewById(R.id.switch_disable_zuiapplist);
        switchDisableZUIApplist.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                saveSettings("allow_get_packages",isChecked);
            }
        });
    }

    private void loadSettings() {
        // 加载禁用系统应用列表管理开关状态
        boolean disableZUIApplist = mPrefsUtils.loadBooleanSetting("allow_get_packages",false);
        switchDisableZUIApplist.setChecked(disableZUIApplist);
        // 加载保持屏幕方向设置
        boolean isKeepRotation = mPrefsUtils.loadBooleanSetting("keep_rotation", false);
        switchKeepRotation.setChecked(isKeepRotation);

    }

    private void initRestartButton() {
        fabRestart = findViewById(R.id.fab_restart);
        fabRestart.setOnClickListener(v -> showRestartConfirmationDialog());
    }

    private void showRestartConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("重启系统")
                .setMessage("更改涉及系统框架，需要重启系统以应用更改。")
                .setPositiveButton("确定", (dialog, which) -> restartOS())
                .setNegativeButton("取消", null)
                .show();
    }

    private void restartOS() {
        try {
            // 使用root权限执行重启命令
            Process process = Runtime.getRuntime().exec("su -c reboot");
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "重启失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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