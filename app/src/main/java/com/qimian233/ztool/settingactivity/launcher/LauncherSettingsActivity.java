package com.qimian233.ztool.settingactivity.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.qimian233.ztool.R;
import com.qimian233.ztool.config.ModuleConfig;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LauncherSettingsActivity extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private FloatingActionButton fabRestart;
    private MaterialSwitch switchDisableForceStop, switchMoreBigDock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_launcher_settings);

        String appName = getIntent().getStringExtra("app_name");
        appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + " - 启动器设置");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mPrefsUtils = new ModulePreferencesUtils(this);
        initViews();
        loadSettings();
        initRestartButton();
    }

    private void initViews() {
        // 禁止强制停止功能开关
        switchDisableForceStop = findViewById(R.id.switch_disable_force_stop);
        switchDisableForceStop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("disable_force_stop", isChecked);
            }
        });
        // 更多大的Dock栏功能开关
        switchMoreBigDock = findViewById(R.id.switch_dock_moreBig);
        switchMoreBigDock.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("zui_launcher_hotseat", isChecked);
            }
        });
    }

    private void loadSettings() {
        // 加载禁止强制停止开关状态
        boolean disableForceStopEnabled = mPrefsUtils.loadBooleanSetting("disable_force_stop",false);
        switchDisableForceStop.setChecked(disableForceStopEnabled);
        // 加载更多大的Dock栏开关状态
        boolean moreBigDockEnabled = mPrefsUtils.loadBooleanSetting("zui_launcher_hotseat",false);
        switchMoreBigDock.setChecked(moreBigDockEnabled);
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
            Process process = Runtime.getRuntime().exec("su -c killall " + appPackageName);
            process.waitFor();
            Toast.makeText(this, "已重启应用进程", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "重启失败", Toast.LENGTH_SHORT).show();
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