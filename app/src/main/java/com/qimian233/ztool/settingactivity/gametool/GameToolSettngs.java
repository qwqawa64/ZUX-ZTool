package com.qimian233.ztool.settingactivity.gametool;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.content.DialogInterface;
import android.app.ActivityManager;
import android.content.Context;
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

public class GameToolSettngs extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchDisableAudio;
    private MaterialSwitch switchDeviceDisguise;
    private MaterialSwitch switchFixCpuFrequency;
    private MaterialSwitch switchFixSocTemperature;
    private FloatingActionButton fabRestart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_game_tool_settngs);

        String appName = getIntent().getStringExtra("app_name");
        appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + getString(R.string.game_tool_settings_title_suffix));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 初始化工具类
        mPrefsUtils = new ModulePreferencesUtils(this);

        initViews();
        loadSettings();
        initRestartButton();
    }

    private void initViews() {
        // 初始化原有的视图
        switchDisableAudio = findViewById(R.id.switch_disable_audio);
        switchDisableAudio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("disable_GameAudio", isChecked);
            }
        });

        switchDeviceDisguise = findViewById(R.id.switch_disguise_device);
        switchDeviceDisguise.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("disguise_TB322FC", isChecked);
            }
        });

        switchFixCpuFrequency = findViewById(R.id.switch_fix_cpuClock);
        switchFixCpuFrequency.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("Fix_CpuClock", isChecked);
            }
        });

        switchFixSocTemperature = findViewById(R.id.switch_fix_socTemp);
        switchFixSocTemperature.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("Fix_SocTemp", isChecked);
            }
        });
    }

    private void initRestartButton() {
        fabRestart = findViewById(R.id.fab_restart);
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
            Toast.makeText(this, R.string.restart_fail_short, Toast.LENGTH_SHORT).show();
        }
    }


    private void loadSettings() {
        // 加载原有的设置
        boolean removeBlacklistEnabled = mPrefsUtils.loadBooleanSetting("disable_GameAudio", false);
        switchDisableAudio.setChecked(removeBlacklistEnabled);

        boolean disguiseDeviceEnabled = mPrefsUtils.loadBooleanSetting("disguise_TB322FC", false);
        switchDeviceDisguise.setChecked(disguiseDeviceEnabled);

        boolean fixCpuFrequencyEnabled = mPrefsUtils.loadBooleanSetting("Fix_CpuClock", false);
        switchFixCpuFrequency.setChecked(fixCpuFrequencyEnabled);

        boolean fixSocTemperatureEnabled = mPrefsUtils.loadBooleanSetting("Fix_SocTemp", false);
        switchFixSocTemperature.setChecked(fixSocTemperatureEnabled);
    }

    private void saveSettings(String moduleName, Boolean newValue) {
        mPrefsUtils.saveBooleanSetting(moduleName, newValue);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
