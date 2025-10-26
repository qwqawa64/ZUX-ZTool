package com.qimian233.ztool.settingactivity.systemui;

import static android.view.View.VISIBLE;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
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
import com.qimian233.ztool.settingactivity.ota.OtaSettings;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class systemUISettings extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchDisplaySeconds;
    private MaterialSwitch switchCustomClock;
    private FloatingActionButton fabRestart;
    private LinearLayout llCustomClock;
    private static final String PREFS_NAME = "StatusBar_Clock";
    private Button SaveButton;
    private SharedPreferences ZToolPrefs;
    private TextView textPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        //绑定视图
        setContentView(R.layout.activity_system_uisettings);

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
        llCustomClock = findViewById(R.id.ll_customClock);
        ZToolPrefs = getZToolPreferences();
        SaveButton = findViewById(R.id.button_save_clock_format);
        textPreview = findViewById(R.id.textview_clock_preview);

        // 设置状态栏显秒事件
        switchDisplaySeconds = findViewById(R.id.switch_statusBarDisplay_seconds);
        switchDisplaySeconds.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("StatusBarDisplay_Seconds",isChecked);
            }
        });

        // 设置自定义时钟事件
        switchCustomClock = findViewById(R.id.switch_custom_clock);
        switchCustomClock.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("Custom_StatusBarClock",isChecked);
            }
        });

        // 保存自定义时钟格式事件
        SaveButton.setOnClickListener(v -> {
            String clockFormat = ((TextView) findViewById(R.id.edittext_clock_format)).getText().toString();
            ZToolPrefs.edit().putString("Custom_StatusBarClockFormat", clockFormat).apply();
            new MaterialAlertDialogBuilder(this)
                    .setTitle("成功")
                    .setMessage("自定义时钟格式已保存")
                    .setPositiveButton("确定", null)
                    .show();
        });

        EditText editTextClockFormat = findViewById(R.id.edittext_clock_format);
        editTextClockFormat.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateClockPreview(s.toString());
            }
        });
    }

    private void loadSettings() {
        // 加载状态栏显秒设置
        boolean removeBlacklistEnabled = mPrefsUtils.loadBooleanSetting("StatusBarDisplay_Seconds", false);
        switchDisplaySeconds.setChecked(removeBlacklistEnabled);
        // 加载自定义时钟设置
        boolean customClockEnabled = mPrefsUtils.loadBooleanSetting("Custom_StatusBarClock", false);
        switchCustomClock.setChecked(customClockEnabled);
        llCustomClock.setVisibility(customClockEnabled ? VISIBLE : View.GONE);
        if (customClockEnabled) {
            EditText editTextClockFormat = findViewById(R.id.edittext_clock_format);
            String savedFormat = ZToolPrefs.getString("Custom_StatusBarClockFormat", "");
            editTextClockFormat.setText(savedFormat);
            updateClockPreview(savedFormat); // 初始加载时更新预览
        }
    }

    // 更新时钟预览文本
    private void updateClockPreview(String format) {
        if (format == null || format.isEmpty()) {
            textPreview.setText(getString(R.string.preview_default));
            return;
        }
        try {
            // 使用SimpleDateFormat格式化当前时间
            SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
            String currentTime = sdf.format(new Date());
            textPreview.setText(getString(R.string.preview_display, currentTime));
        } catch (IllegalArgumentException e) {
            // 格式无效时显示错误
            textPreview.setText(getString(R.string.preview_invalid));
        }
    }

    private void saveSettings(String moduleName,Boolean newValue) {
        mPrefsUtils.saveBooleanSetting(moduleName, newValue);
        if (moduleName.equals("Custom_StatusBarClock")) {
            llCustomClock.setVisibility(newValue ? VISIBLE : View.GONE);
            if (newValue) {
                EditText editTextClockFormat = findViewById(R.id.edittext_clock_format);
                String savedFormat = ZToolPrefs.getString("Custom_StatusBarClockFormat", "");
                editTextClockFormat.setText(savedFormat);
                updateClockPreview(savedFormat); // 初始加载时更新预览
            }
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
            process.waitFor(); // 等待命令执行完成
        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(this, "强制停止失败", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    public SharedPreferences getZToolPreferences() {
        Context mContext = this;
        try {
            Context moduleContext = mContext.createPackageContext("com.qimian233.ztool", Context.CONTEXT_IGNORE_SECURITY);
            return moduleContext.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        } catch (Exception e) {
            Log.e("ModulePreferences", "Failed to get module preferences, using fallback", e);
            // 降级方案：使用当前Context
            return mContext.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        }
    }
}