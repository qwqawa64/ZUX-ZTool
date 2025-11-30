package com.qimian233.ztool.settingactivity.gametool;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.content.DialogInterface;
import android.app.ActivityManager;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
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
import com.qimian233.ztool.EnhancedShellExecutor;
import com.qimian233.ztool.MainActivity;
import com.qimian233.ztool.R;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;
import com.qimian233.ztool.utils.AppChooserDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameToolSettngs extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchDisableAudio;
    private MaterialSwitch switchDeviceDisguise;
    private MaterialSwitch switchFixCpuFrequency;
    private MaterialSwitch switchFixSocTemperature;
    private FloatingActionButton fabRestart;
    private Spinner mistakeTouchSpinner;
    private LinearLayout mistakeTouchConfig;
    private TextView mistakeTouchWhiteListCount;
    private List<String> TARGET_GAME_PACKAGES;


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

        // 初始化防误触配置布局和文本视图
        mistakeTouchConfig = findViewById(R.id.layout_whitelist_config);
        mistakeTouchConfig.setOnClickListener(v -> {
            SelectGameAPP();
        });
        // 读取白名单
        String WhiteList = mPrefsUtils.loadStringSetting("MistakeTouchWhiteListGame", "");
        TARGET_GAME_PACKAGES = Arrays.asList(WhiteList.split(","));
        // 初始化白名单数量文本视图
        mistakeTouchWhiteListCount = findViewById(R.id.text_whitelist_summary);
        mistakeTouchWhiteListCount.setText(getString(R.string.whitelist_count, TARGET_GAME_PACKAGES.size()));
        // 初始化防误触开关
        List<String> mistakeTouch = new ArrayList<>();
        mistakeTouch.add(getString(R.string.SelectDefault));
        mistakeTouch.add(getString(R.string.SelectAllGames));
        mistakeTouch.add(getString(R.string.SelectWhiteList));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, mistakeTouch);
        mistakeTouchSpinner = findViewById(R.id.spinner_auto_prevent_touch);
        mistakeTouchSpinner.setAdapter(adapter);
        // 设置选项选择监听器
        mistakeTouchSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 或者使用 position 来判断
                switch (position) {
                    case 0: // 默认
                        SelectDefault();
                        break;
                    case 1: // 全部游戏
                        SelectAllGames();
                        break;
                    case 2: // 白名单内
                        SelectWhiteList();
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 当没有选项被选中时的处理
                Log.d("Spinner", "没有选项被选中");
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

        // 加载防误触设置
        boolean autoMistakeTouch = mPrefsUtils.loadBooleanSetting("auto_mistake_touch", false);
        boolean mistakeTouchWhiteList = mPrefsUtils.loadBooleanSetting("MistakeTouchWhiteList", false);
        if (autoMistakeTouch && !mistakeTouchWhiteList) {
            mistakeTouchSpinner.setSelection(1); // 选择全部游戏
        } else if (!autoMistakeTouch) {
            mistakeTouchSpinner.setSelection(0); // 选择默认
        } else {
            mistakeTouchSpinner.setSelection(2); // 选择白名单内
        }
    }

    private void SelectDefault() {
        mistakeTouchConfig.setVisibility(View.GONE);
        saveSettings("auto_mistake_touch", false);
        saveSettings("MistakeTouchWhiteList", false);
    }

    private void SelectAllGames() {
        mistakeTouchConfig.setVisibility(View.GONE);
        saveSettings("auto_mistake_touch", true);
        saveSettings("MistakeTouchWhiteList", false);
    }

    private void SelectWhiteList() {
        saveSettings("auto_mistake_touch", true);
        saveSettings("MistakeTouchWhiteList", true);
        mistakeTouchConfig.setVisibility(View.VISIBLE);
    }

    private void SelectGameAPP() {
        AppChooserDialog.show(this, getPackageNames(), TARGET_GAME_PACKAGES, getString(R.string.SelectGame), new AppChooserDialog.AppSelectionCallback() {
            @Override
            public void onSelected(List<AppChooserDialog.AppInfo> selectedApps) {
                StringBuilder selectedPackageNames = new StringBuilder();
                // 处理用户选择的应用
                TARGET_GAME_PACKAGES = new ArrayList<>();
                for (AppChooserDialog.AppInfo app : selectedApps) {
                    Log.d("AppChooser", "Selected: " + app.getAppName() + " (" + app.getPackageName() + ")");
                    selectedPackageNames.append(app.getPackageName()).append(",");
                    TARGET_GAME_PACKAGES.add(app.getPackageName());
                }
                saveConfig("MistakeTouchWhiteListGame", selectedPackageNames.toString());
                // 刷新白名单数量文本视图
                RefreshMistakeTouchWhiteList();
            }

            @Override
            public void onCancel() {
            }
        });
    }

    public static List<String> getPackageNames() {
        List<String> packages = new ArrayList<>();

        EnhancedShellExecutor.ShellResult result = EnhancedShellExecutor.getInstance()
                .executeRootCommand("ls /data/system_ce/0/managed_apps/");

        if (result.isSuccess()) {
            String[] names = result.output.trim().split("\\s+"); // 按空白字符分割
            packages.addAll(Arrays.asList(names));
        }

        return packages;
    }
    private void RefreshMistakeTouchWhiteList() {
        mistakeTouchWhiteListCount.setText(getString(R.string.whitelist_count, TARGET_GAME_PACKAGES.size()));
    }

    private void saveSettings(String moduleName, Boolean newValue) {
        mPrefsUtils.saveBooleanSetting(moduleName, newValue);
    }
    private void saveConfig(String configName, String newValue) {
        mPrefsUtils.saveStringSetting(configName, newValue);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
