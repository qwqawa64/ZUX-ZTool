package com.qimian233.ztool.settingactivity.systemui;

import static android.view.View.VISIBLE;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
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
import com.qimian233.ztool.hook.modules.systemui.CustomDateFormatter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;

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

    // 新增：样式相关的视图
    private LinearLayout llTextSize, llLetterSpacing, llTextColor, llTextBold;
    private MaterialSwitch switchTextSize, switchLetterSpacing, switchTextColor, switchTextBold, switchEnableAod;
    private SeekBar seekbarTextSize, seekbarLetterSpacing;
    private TextView textTextSizeValue, textLetterSpacingValue, textTextColorValue;
    private View viewColorPreview;
    private Button buttonPickColor;

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

        // 初始化样式视图
        initStyleViews();

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
                // 显示或隐藏所有样式选项
                updateStyleViewsVisibility(isChecked);
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

        // 添加格式帮助按钮点击事件
        ImageView helpButton = findViewById(R.id.info_img);
        helpButton.setOnClickListener(v -> showFormatHelpDialog());

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

    /**
     * 初始化样式相关的视图
     */
    private void initStyleViews() {
        // 初始化布局
        llTextSize = findViewById(R.id.ll_text_size);
        llLetterSpacing = findViewById(R.id.ll_letter_spacing);
        llTextColor = findViewById(R.id.ll_text_color);
        llTextBold = findViewById(R.id.ll_text_bold);

        // 初始化开关
        switchTextSize = findViewById(R.id.switch_text_size);
        switchLetterSpacing = findViewById(R.id.switch_letter_spacing);
        switchTextColor = findViewById(R.id.switch_text_color);
        switchTextBold = findViewById(R.id.switch_text_bold);
        switchEnableAod = findViewById(R.id.switch_aod);

        // 初始化进度条和数值显示
        seekbarTextSize = findViewById(R.id.seekbar_text_size);
        seekbarLetterSpacing = findViewById(R.id.seekbar_letter_spacing);
        textTextSizeValue = findViewById(R.id.textview_text_size_value);
        textLetterSpacingValue = findViewById(R.id.textview_letter_spacing_value);
        textTextColorValue = findViewById(R.id.textview_text_color_value);

        // 初始化颜色选择器
        viewColorPreview = findViewById(R.id.view_color_preview);
        buttonPickColor = findViewById(R.id.button_pick_color);

        // 设置息屏显示开关的监听器
        switchEnableAod.setOnCheckedChangeListener((buttonView, isChecked) -> {
            new Thread(() -> {
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "settings put secure doze_always_on " + (isChecked ? "1" : "0")});
                    int exitCode = process.waitFor();
                    Log.d("AODSwitch", "Command executed with exit code: " + exitCode);

                    // 在主线程中更新UI
                    runOnUiThread(() -> {
                        if (exitCode !=0) {
                            //Toast.makeText(systemUISettings.this, "AOD设置已更新", Toast.LENGTH_SHORT).show();
                            switchEnableAod.setChecked(!isChecked);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        Toast.makeText(systemUISettings.this, "执行错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        // 恢复开关状态
                        switchEnableAod.setChecked(!isChecked);
                    });
                }
            }).start();
        });

        // 设置进度条监听器
        seekbarTextSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float textSize = 10 + (progress * 0.5f);
                    textTextSizeValue.setText(textSize + "sp");
                    saveTextSize(textSize);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekbarLetterSpacing.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float letterSpacing = progress * 0.1f; // 0.0-2.0范围
                    textLetterSpacingValue.setText(String.format("%.1f", letterSpacing));
                    saveLetterSpacing(letterSpacing);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 设置开关监听器
        switchTextSize.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveStyleEnabled("Custom_StatusBarClockTextSizeEnabled", isChecked);
            seekbarTextSize.setEnabled(isChecked);
        });

        switchLetterSpacing.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveStyleEnabled("Custom_StatusBarClockLetterSpacingEnabled", isChecked);
            seekbarLetterSpacing.setEnabled(isChecked);
        });

        switchTextColor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveStyleEnabled("Custom_StatusBarClockTextColorEnabled", isChecked);
            buttonPickColor.setEnabled(isChecked);
        });

        switchTextBold.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveStyleEnabled("Custom_StatusBarClockTextBold", isChecked);
        });

        // 设置颜色选择器点击事件
        buttonPickColor.setOnClickListener(v -> showColorPickerDialog());

    }

    /**
     * 显示颜色选择对话框
     */
    private void showColorPickerDialog() {
        // 预定义一些常用颜色
        int[] colors = {
                Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE,
                Color.YELLOW, Color.CYAN, Color.MAGENTA, 0xFF2196F3, 0xFF4CAF50,
                0xFFFF9800, 0xFF9C27B0, 0xFF607D8B, 0xFFFF5722, 0xFF795548
        };

        String[] colorNames = {
                "白色", "黑色", "红色", "绿色", "蓝色",
                "黄色", "青色", "洋红", "蓝色", "绿色",
                "橙色", "紫色", "灰色", "深橙", "棕色"
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle("选择字体颜色")
                .setItems(colorNames, (dialog, which) -> {
                    int selectedColor = colors[which];
                    saveTextColor(selectedColor);
                    updateColorPreview(selectedColor);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 更新颜色预览
     */
    private void updateColorPreview(int color) {
        viewColorPreview.setBackgroundColor(color);
        textTextColorValue.setText(String.format("#%08X", color));
    }

    /**
     * 更新样式视图的显示状态
     */
    private void updateStyleViewsVisibility(boolean show) {
        int visibility = show ? VISIBLE : View.GONE;
        llTextSize.setVisibility(visibility);
        llLetterSpacing.setVisibility(visibility);
        llTextColor.setVisibility(visibility);
        llTextBold.setVisibility(visibility);
    }

    /**
     * 显示详细的格式帮助对话框
     */
    private void showFormatHelpDialog() {
        String detailedHelp = "时间格式基于ISO 8601进行拓展，自定义时钟格式说明：\n\n" +
                "📅 ISO 8601标准日期格式：\n" +
                "  yyyy - 年份(2024)\n" +
                "  yy   - 年份后两位(24)\n" +
                "  MM   - 月份(12)\n" +
                "  MMM  - 月份缩写(12月)\n" +
                "  MMMM - 月份全称(十二月)\n" +
                "  dd   - 日期(25)\n" +
                "  HH   - 24小时制(14)\n" +
                "  hh   - 12小时制(02)\n" +
                "  mm   - 分钟(30)\n" +
                "  ss   - 秒(45)\n" +
                "  SSS  - 毫秒(123)\n\n" +

                "📅 星期格式：\n" +
                "  E    - 星期缩写(周一)\n" +
                "  EE   - 星期缩写(周一)\n" +
                "  EEE  - 星期缩写(周一)\n" +
                "  EEEE - 星期全称(星期一)\n" +
                "  u    - 数字星期(1-7,1=周一)\n" +
                "  W    - 自定义星期格式(周一)\n\n" +

                "🌙 农历相关：\n" +
                "  N - 农历日期(腊月廿三)\n" +
                "  J - 节气(仅当天显示，如立春)\n" +
                "  A - 生肖(龙)\n\n" +

                "⏰ 时间相关：\n" +
                "  T - 时辰(子时)\n" +
                "  a - 上午/下午标记(AM/PM)\n" +
                "  k - 24小时制(1-24)\n" +
                "  K - 12小时制(0-11)\n\n" +

                "✨ 其他特殊格式：\n" +
                "  C - 星座(水瓶座)\n" +
                "  D - 一年中的第几天(1-365)\n" +
                "  F - 一个月中的第几个星期\n" +
                "  w - 一年中的第几周(1-53)\n" +
                "  W - 一个月中的第几周(1-5)\n" +
                "  z - 时区名称(GMT+08:00)\n" +
                "  Z - 时区偏移量(+0800)\n\n" +

                "🎯 使用示例：\n" +
                "  \"yyyy-MM-dd HH:mm:ss\" → \"2024-12-25 14:30:45\"\n" +
                "  \"MM月dd日 EEEE\" → \"12月25日 星期三\"\n" +
                "  \"HH:mm T\" → \"14:30 午时\"\n" +
                "  \"yyyy年MM月dd日 N A\" → \"2024年12月25日 腊月廿三 龙\"\n" +
                "  \"yyyy/MM/dd E C\" → \"2024/12/25 周三 摩羯座\"\n\n" +

                "💡 注意事项：\n" +
                "  • 农历和节气基于中国农历算法\n" +
                "  • 时辰按2小时一个时段划分\n" +
                "  • 星座基于公历日期计算\n" +
                "  • 生肖基于农历年份确定\n" +
                "  • 自定义格式符(N,J,T,C,A,W)区分大小写";


        new MaterialAlertDialogBuilder(this)
                .setTitle("时钟格式帮助")
                .setMessage(detailedHelp)
                .setPositiveButton("确定", null)
                .setNeutralButton("复制示例", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("时钟格式示例", "HH:mm N");
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "示例已复制到剪贴板", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    public boolean isAodEnabled() {
        try {
            Process process = Runtime.getRuntime().exec("su -c \"settings get secure doze_always_on\"");
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


    private void loadSettings() {
        // 加载状态栏显秒设置
        boolean removeBlacklistEnabled = mPrefsUtils.loadBooleanSetting("StatusBarDisplay_Seconds", false);
        switchDisplaySeconds.setChecked(removeBlacklistEnabled);

        // 加载自定义时钟设置
        boolean customClockEnabled = mPrefsUtils.loadBooleanSetting("Custom_StatusBarClock", false);
        switchCustomClock.setChecked(customClockEnabled);
        llCustomClock.setVisibility(customClockEnabled ? VISIBLE : View.GONE);

        //加载AOD显示设置
        boolean aodEnabled = isAodEnabled();
        switchEnableAod.setChecked(aodEnabled);

        if (customClockEnabled) {
            EditText editTextClockFormat = findViewById(R.id.edittext_clock_format);
            String savedFormat = ZToolPrefs.getString("Custom_StatusBarClockFormat", "");
            editTextClockFormat.setText(savedFormat);
            updateClockPreview(savedFormat); // 初始加载时更新预览

            // 加载样式设置
            loadStyleSettings();
            updateStyleViewsVisibility(true);
        }
    }

    /**
     * 加载样式设置
     */
    private void loadStyleSettings() {
        // 读取字体大小（使用浮点数）
        float textSize = ZToolPrefs.getFloat("Custom_StatusBarClockTextSize", 16.0f);
        boolean textSizeEnabled = ZToolPrefs.getBoolean("Custom_StatusBarClockTextSizeEnabled", false);

        // 将实际值转换为 SeekBar 进度
        int progress = (int) ((textSize - 10) / 0.5f);
        seekbarTextSize.setProgress(progress);
        switchTextSize.setChecked(textSizeEnabled);

        // 显示格式化后的文本大小（保留1位小数）
        textTextSizeValue.setText(String.format("%.1fsp", textSize));
        seekbarTextSize.setEnabled(textSizeEnabled);


        // 字间距
        float letterSpacing = ZToolPrefs.getFloat("Custom_StatusBarClockLetterSpacing", 0.1f);
        boolean letterSpacingEnabled = ZToolPrefs.getBoolean("Custom_StatusBarClockLetterSpacingEnabled", false);
        seekbarLetterSpacing.setProgress((int)(letterSpacing * 10));
        switchLetterSpacing.setChecked(letterSpacingEnabled);
        textLetterSpacingValue.setText(String.format("%.1f", letterSpacing));
        seekbarLetterSpacing.setEnabled(letterSpacingEnabled);

        // 字体颜色
        int textColor = ZToolPrefs.getInt("Custom_StatusBarClockTextColor", 0xFFFFFFFF);
        boolean textColorEnabled = ZToolPrefs.getBoolean("Custom_StatusBarClockTextColorEnabled", false);
        switchTextColor.setChecked(textColorEnabled);
        updateColorPreview(textColor);
        buttonPickColor.setEnabled(textColorEnabled);

        // 粗体
        boolean textBold = ZToolPrefs.getBoolean("Custom_StatusBarClockTextBold", true);
        boolean textBoldEnabled = ZToolPrefs.getBoolean("Custom_StatusBarClockTextBold", false);
        switchTextBold.setChecked(textBoldEnabled);
    }

    // 更新时钟预览文本 - 使用新的CustomDateFormatter
    private void updateClockPreview(String format) {
        if (format == null || format.isEmpty()) {
            textPreview.setText(getString(R.string.preview_default));
            return;
        }
        try {
            // 使用新的CustomDateFormatter格式化当前时间
            String currentTime = CustomDateFormatter.format(format, new Date());
            textPreview.setText(getString(R.string.preview_display, currentTime));
        } catch (Exception e) {
            // 格式无效时显示错误
            textPreview.setText(getString(R.string.preview_invalid) + "\n错误: " + e.getMessage());
            Log.e("CustomDatePreview", "Error formatting date: " + format, e);
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
                loadStyleSettings();
            }
            updateStyleViewsVisibility(newValue);
        }
    }

    /**
     * 保存字体大小设置
     */
    private void saveTextSize(float textSize) {
        ZToolPrefs.edit().putFloat("Custom_StatusBarClockTextSize", textSize).apply();
    }

    /**
     * 保存字间距设置
     */
    private void saveLetterSpacing(float letterSpacing) {
        ZToolPrefs.edit().putFloat("Custom_StatusBarClockLetterSpacing", letterSpacing).apply();
    }

    /**
     * 保存字体颜色设置
     */
    private void saveTextColor(int color) {
        ZToolPrefs.edit().putInt("Custom_StatusBarClockTextColor", color).apply();
    }

    /**
     * 保存样式启用状态
     */
    private void saveStyleEnabled(String key, boolean enabled) {
        ZToolPrefs.edit().putBoolean(key, enabled).apply();
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
