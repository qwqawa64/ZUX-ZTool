package com.qimian233.ztool.settingactivity.packageinstaller;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
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
import com.qimian233.ztool.R;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class packageinstallersettings extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchDisableScanAPK;
    private MaterialSwitch switchOnlyAllow;
    private MaterialSwitch switchSkipWarnPage;
    private MaterialSwitch switchDisableInstallerAD;
    private LinearLayout layoutOtaInfo;
    private FloatingActionButton fabRestart;

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
            getSupportActionBar().setTitle(appName + " - 详细设置");
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
        switchDisableScanAPK.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("disable_scanAPK",isChecked);
            }
        });

        // 总是允许安装应用所需的权限
        switchOnlyAllow = findViewById(R.id.switch_onlyAllow);
        switchOnlyAllow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("Always_AllowPermission",isChecked);
            }
        });

        // 跳过警告页面
        switchSkipWarnPage = findViewById(R.id.switch_skipWarnPage);
        switchSkipWarnPage.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("Skip_WarnPage",isChecked);
            }
        });

        // 禁用安装器广告
        switchDisableInstallerAD = findViewById(R.id.switch_disable_installerAD);
        switchDisableInstallerAD.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("disable_installerAD",isChecked);
            }
        });

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
}
