package com.qimian233.ztool.settingactivity.systemui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;
import com.qimian233.ztool.settingactivity.systemui.ControlCenter.ControlCenterSettingsActivity;
import com.qimian233.ztool.settingactivity.systemui.lockscreen.LockScreenSettingsActivity;
import com.qimian233.ztool.settingactivity.systemui.statusBarSetting.StatusBarSettingsActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class systemUISettings extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private FloatingActionButton fabRestart;
    private MaterialSwitch switchEnableAod;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_system_uisettings);

        String appName = getIntent().getStringExtra("app_name");
        appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + " - 系统界面设置");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mPrefsUtils = new ModulePreferencesUtils(this);
        initViews();
        loadSettings();
        initRestartButton();
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

        // AOD设置
        switchEnableAod = findViewById(R.id.switch_aod);
        switchEnableAod.setOnCheckedChangeListener((buttonView, isChecked) -> {
            new Thread(() -> {
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "settings put secure doze_always_on " + (isChecked ? "1" : "0")});
                    int exitCode = process.waitFor();
                    Log.d("AODSwitch", "Command executed with exit code: " + exitCode);

                    runOnUiThread(() -> {
                        if (exitCode != 0) {
                            switchEnableAod.setChecked(!isChecked);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        Toast.makeText(systemUISettings.this, "执行错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        switchEnableAod.setChecked(!isChecked);
                    });
                }
            }).start();
        });
    }

    private void loadSettings() {
        boolean aodEnabled = isAodEnabled();
        switchEnableAod.setChecked(aodEnabled);
    }

    public boolean isAodEnabled() {
        try {
            Process process = Runtime.getRuntime().exec("su -c settings get secure doze_always_on");
            process.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();
            reader.close();
            return output != null && output.trim().equals("1");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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
        } catch (Exception e) {
            Toast.makeText(this, "重启失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
