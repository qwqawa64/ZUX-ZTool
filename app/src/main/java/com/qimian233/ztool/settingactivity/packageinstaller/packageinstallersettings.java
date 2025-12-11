package com.qimian233.ztool.settingactivity.packageinstaller;

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

public class packageinstallersettings extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchDisableScanAPK;
    private MaterialSwitch switchOnlyAllow;
    private MaterialSwitch switchSkipWarnPage;
    private MaterialSwitch switchDisableInstallerAD;
    private MaterialSwitch switchEnableRowStyle;
    private MaterialSwitch switchDisableDeletePackage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        //绑定视图
        setContentView(R.layout.activity_packageinstallersettings);

        String appName = getIntent().getStringExtra("app_name");
        appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + getString(R.string.detailed_settings_suffix));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 初始化工具类
        mPrefsUtils = new ModulePreferencesUtils(this);

        initViews();
        loadSettings();
        initRestartButton();
    }

    private void initViews() {
        // 初始化视图

        // 禁用扫描APK功能
        switchDisableScanAPK = findViewById(R.id.switch_disable_ScanAPK);
        switchDisableScanAPK.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("disable_scanAPK",isChecked));

        // 总是允许安装应用所需的权限
        switchOnlyAllow = findViewById(R.id.switch_onlyAllow);
        switchOnlyAllow.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("Always_AllowPermission",isChecked));

        // 跳过警告页面
        switchSkipWarnPage = findViewById(R.id.switch_skipWarnPage);
        switchSkipWarnPage.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("Skip_WarnPage",isChecked));

        // 禁用安装器广告
        switchDisableInstallerAD = findViewById(R.id.switch_disable_installerAD);
        switchDisableInstallerAD.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("disable_installerAD",isChecked));

        // 启用原生安装器样式
        switchEnableRowStyle = findViewById(R.id.switch_enable_rowStyle);
        switchEnableRowStyle.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("packageInstallerStyle_hook",isChecked));

        // 拦截删除安装包行为
        switchDisableDeletePackage = findViewById(R.id.switch_disable_delete);
        switchDisableDeletePackage.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("package_installer_disable_delete",isChecked));

    }

    private void loadSettings() {
        // 加载跳过APK扫描设置
        boolean removeBlacklistEnabled = mPrefsUtils.loadBooleanSetting("disable_scanAPK", false);
        switchDisableScanAPK.setChecked(removeBlacklistEnabled);
        // 加载总是允许安装应用所需的权限设置
        boolean Always_AllowPermission = mPrefsUtils.loadBooleanSetting("Always_AllowPermission", false);
        switchOnlyAllow.setChecked(Always_AllowPermission);
        // 加载跳过警告页面设置
        boolean Skip_WarnPage = mPrefsUtils.loadBooleanSetting("Skip_WarnPage", false);
        switchSkipWarnPage.setChecked(Skip_WarnPage);
        // 加载禁用安装器广告设置
        boolean disable_installerAD = mPrefsUtils.loadBooleanSetting("disable_installerAD", false);
        switchDisableInstallerAD.setChecked(disable_installerAD);
        // 加载启用原生安装器样式设置
        boolean packageInstallerStyle_hook = mPrefsUtils.loadBooleanSetting("packageInstallerStyle_hook", false);
        switchEnableRowStyle.setChecked(packageInstallerStyle_hook);
        // 加载拦截删除安装包行为设置
        boolean package_installer_disable_delete = mPrefsUtils.loadBooleanSetting("package_installer_disable_delete", false);
        switchDisableDeletePackage.setChecked(package_installer_disable_delete);
    }

    private void saveSettings(String moduleName,Boolean newValue) {
        mPrefsUtils.saveBooleanSetting(moduleName, newValue);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initRestartButton() {
        FloatingActionButton fabRestart = findViewById(R.id.fab_restart);
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
            Toast.makeText(this, R.string.restart_fail_simple, Toast.LENGTH_SHORT).show();
        }
    }
}
