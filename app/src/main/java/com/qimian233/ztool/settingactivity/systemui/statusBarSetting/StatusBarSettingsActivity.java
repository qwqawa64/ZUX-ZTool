package com.qimian233.ztool.settingactivity.systemui.statusBarSetting;

import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.qimian233.ztool.R;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;
import com.qimian233.ztool.hook.modules.systemui.CustomDateFormatter;

import java.util.Date;

/** @noinspection deprecation*/
public class StatusBarSettingsActivity extends AppCompatActivity {

    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchDisplaySeconds;
    private MaterialSwitch switchCustomClock;
    private LinearLayout llCustomClock;
    private static final String PREFS_NAME = "StatusBar_Clock";
    private SharedPreferences ZToolPrefs;
    private TextView textPreview;

    // 样式相关的视图
    private LinearLayout llTextSize, llLetterSpacing, llTextColor, llTextBold;
    private MaterialSwitch switchTextSize, switchLetterSpacing, switchTextColor, switchTextBold, switchNativeNotificationIcon, switch_NetworkSpeedSize, switch_NetworkSpeedSizeDoubleLayer, switchBatteryExternal;
    private SeekBar seekbarTextSize, seekbarLetterSpacing;
    private TextView textTextSizeValue, textLetterSpacingValue, textTextColorValue;
    private View viewColorPreview;
    private Button buttonPickColor;
    private Spinner spinnerNotifyNumSize;
    private SharedPreferences StatusBar_notifyNumSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_status_bar_settings);

        String appName = getIntent().getStringExtra("app_name");
        String appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + getString(R.string.status_bar_settings_title_suffix));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mPrefsUtils = new ModulePreferencesUtils(this);
        initViews();
        loadSettings();
    }

    private void initViews() {
        llCustomClock = findViewById(R.id.ll_customClock);
        ZToolPrefs = getZToolPreferences();
        Button saveButton = findViewById(R.id.button_save_clock_format);
        textPreview = findViewById(R.id.textview_clock_preview);
        spinnerNotifyNumSize = findViewById(R.id.spinner_notifyNumSize);

        initStyleViews();

        // 状态栏显秒事件
        switchDisplaySeconds = findViewById(R.id.switch_statusBarDisplay_seconds);
        switchDisplaySeconds.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("StatusBarDisplay_Seconds", isChecked);
            }
        });

        // 自定义时钟事件
        switchCustomClock = findViewById(R.id.switch_custom_clock);
        switchCustomClock.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("Custom_StatusBarClock", isChecked);
                updateStyleViewsVisibility(isChecked);
            }
        });

        // 保存自定义时钟格式事件
        saveButton.setOnClickListener(v -> {
            String clockFormat = ((TextView) findViewById(R.id.edittext_clock_format)).getText().toString();
            ZToolPrefs.edit().putString("Custom_StatusBarClockFormat", clockFormat).apply();
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.save_success_title)
                    .setMessage(R.string.clock_format_saved_message)
                    .setPositiveButton(R.string.restart_yes, null)
                    .show();
        });

        // 设置原生通知图标开关事件
        switchNativeNotificationIcon = findViewById(R.id.switch_NativeNotificationIcon);
        switchNativeNotificationIcon.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 保存开关状态
            saveSettings("NativeNotificationIcon",isChecked);
            //Log.d("NativeNotificationIcon", "Switch state saved: " + isChecked);
        });

        // 设置状态栏网速大小优化事件
        switch_NetworkSpeedSize = findViewById(R.id.switch_NetworkSpeedSize);
        switch_NetworkSpeedSize.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSettings("systemui_network_speed_size",isChecked);
        });

        // 设置状态栏网速大小优化事件 双层布局
        switch_NetworkSpeedSizeDoubleLayer = findViewById(R.id.switch_NetworkSpeedSizeDoubleLayer);
        switch_NetworkSpeedSizeDoubleLayer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSettings("systemui_network_speed_doublelayer",isChecked);
        });

        // 设置电池百分比电池外
        switchBatteryExternal = findViewById(R.id.switch_BatteryExternal);
        switchBatteryExternal.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSettings("systemui_battery_percentage",isChecked);
        });


        StatusBar_notifyNumSize = getNotifyNumSizeShared();
        String[] itemsArray = getResources().getStringArray(R.array.notify_num_size_options);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, itemsArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerNotifyNumSize.setAdapter(adapter);

        int selectedIndex = StatusBar_notifyNumSize.getInt("notify_num_size", 4);
        if (selectedIndex == 100) {
            selectedIndex = 15;
        }
        spinnerNotifyNumSize.setSelection(selectedIndex - 1);

        // 格式帮助按钮
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

    private void initStyleViews() {
        llTextSize = findViewById(R.id.ll_text_size);
        llLetterSpacing = findViewById(R.id.ll_letter_spacing);
        llTextColor = findViewById(R.id.ll_text_color);
        llTextBold = findViewById(R.id.ll_text_bold);

        switchTextSize = findViewById(R.id.switch_text_size);
        switchLetterSpacing = findViewById(R.id.switch_letter_spacing);
        switchTextColor = findViewById(R.id.switch_text_color);
        switchTextBold = findViewById(R.id.switch_text_bold);

        seekbarTextSize = findViewById(R.id.seekbar_text_size);
        seekbarLetterSpacing = findViewById(R.id.seekbar_letter_spacing);
        textTextSizeValue = findViewById(R.id.textview_text_size_value);
        textLetterSpacingValue = findViewById(R.id.textview_letter_spacing_value);
        textTextColorValue = findViewById(R.id.textview_text_color_value);

        viewColorPreview = findViewById(R.id.view_color_preview);
        buttonPickColor = findViewById(R.id.button_pick_color);

        // 进度条监听器
        seekbarTextSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float textSize = 10 + (progress * 0.5f);
                    textTextSizeValue.setText(textSize + getString(R.string.sp_unit));
                    saveTextSize(textSize);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekbarLetterSpacing.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float letterSpacing = progress * 0.1f;
                    textLetterSpacingValue.setText(String.format("%.1f", letterSpacing));
                    saveLetterSpacing(letterSpacing);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 开关监听器
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

        // 颜色选择器
        buttonPickColor.setOnClickListener(v -> showColorPickerDialog());

        // 通知大小Spinner
        spinnerNotifyNumSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedItem = parent.getItemAtPosition(position).toString();
                if (selectedItem.equals(getString(R.string.notify_num_default))) {
                    saveSettings("notification_icon_limit", false);
                    return;
                }
                saveSettings("notification_icon_limit", true);
                if (selectedItem.equals(getString(R.string.notify_num_unlimited))) {
                    StatusBar_notifyNumSize.edit().putInt("notify_num_size", 100).apply();
                } else {
                    try {
                        int num = Integer.parseInt(selectedItem);
                        StatusBar_notifyNumSize.edit().putInt("notify_num_size", num).apply();
                    } catch (NumberFormatException e) {
                        Log.e("Spinner", "Invalid number format", e);
                        Toast.makeText(StatusBarSettingsActivity.this, R.string.save_failed_message, Toast.LENGTH_SHORT).show();
                    }
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                StatusBar_notifyNumSize.edit().putInt("notify_num_size",
                        StatusBar_notifyNumSize.getInt("notify_num_size", 4)).apply();
            }
        });
    }

    private void showColorPickerDialog() {
        int[] colors = {
                Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE,
                Color.YELLOW, Color.CYAN, Color.MAGENTA, 0xFF2196F3, 0xFF4CAF50,
                0xFFFF9800, 0xFF9C27B0, 0xFF607D8B, 0xFFFF5722, 0xFF795548
        };

        String[] colorNames = getResources().getStringArray(R.array.color_names);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.select_font_color_title)
                .setItems(colorNames, (dialog, which) -> {
                    int selectedColor = colors[which];
                    saveTextColor(selectedColor);
                    updateColorPreview(selectedColor);
                })
                .setNegativeButton(R.string.restart_no, null)
                .show();
    }

    private void updateColorPreview(int color) {
        viewColorPreview.setBackgroundColor(color);
        textTextColorValue.setText(String.format("#%08X", color));
    }

    private void updateStyleViewsVisibility(boolean show) {
        int visibility = show ? VISIBLE : View.GONE;
        llTextSize.setVisibility(visibility);
        llLetterSpacing.setVisibility(visibility);
        llTextColor.setVisibility(visibility);
        llTextBold.setVisibility(visibility);
    }

    private void showFormatHelpDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clock_format_help_title)
                .setMessage(getString(R.string.clock_format_help_content))
                .setPositiveButton(R.string.restart_yes, null)
                .setNeutralButton(R.string.copy_example_button, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText(getString(R.string.clock_format_example), getString(R.string.clock_format_sample));
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, R.string.example_copied_message, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void loadSettings() {
        boolean removeBlacklistEnabled = mPrefsUtils.loadBooleanSetting("StatusBarDisplay_Seconds", false);
        switchDisplaySeconds.setChecked(removeBlacklistEnabled);

        boolean customClockEnabled = mPrefsUtils.loadBooleanSetting("Custom_StatusBarClock", false);
        switchCustomClock.setChecked(customClockEnabled);
        llCustomClock.setVisibility(customClockEnabled ? VISIBLE : View.GONE);

        boolean isNativeIconEnabled = mPrefsUtils.loadBooleanSetting("NativeNotificationIcon", false);
        switchNativeNotificationIcon.setChecked(isNativeIconEnabled);

        boolean isNetworkSpeedSize = mPrefsUtils.loadBooleanSetting("systemui_network_speed_size", false);
        switch_NetworkSpeedSize.setChecked(isNetworkSpeedSize);

        boolean isNetworkSpeedDoubleLayer = mPrefsUtils.loadBooleanSetting("systemui_network_speed_doublelayer", false);
        switch_NetworkSpeedSizeDoubleLayer.setChecked(isNetworkSpeedDoubleLayer);

        boolean isBatteryPercentageEnabled = mPrefsUtils.loadBooleanSetting("systemui_battery_percentage", false);
        switchBatteryExternal.setChecked(isBatteryPercentageEnabled);

        if (customClockEnabled) {
            EditText editTextClockFormat = findViewById(R.id.edittext_clock_format);
            String savedFormat = ZToolPrefs.getString("Custom_StatusBarClockFormat", "");
            editTextClockFormat.setText(savedFormat);
            updateClockPreview(savedFormat);
            loadStyleSettings();
            updateStyleViewsVisibility(true);
        }
    }

    private void loadStyleSettings() {
        float textSize = ZToolPrefs.getFloat("Custom_StatusBarClockTextSize", 16.0f);
        boolean textSizeEnabled = ZToolPrefs.getBoolean("Custom_StatusBarClockTextSizeEnabled", false);

        int progress = (int) ((textSize - 10) / 0.5f);
        seekbarTextSize.setProgress(progress);
        switchTextSize.setChecked(textSizeEnabled);
        textTextSizeValue.setText(String.format("%.1f%s", textSize, getString(R.string.sp_unit)));
        seekbarTextSize.setEnabled(textSizeEnabled);

        float letterSpacing = ZToolPrefs.getFloat("Custom_StatusBarClockLetterSpacing", 0.1f);
        boolean letterSpacingEnabled = ZToolPrefs.getBoolean("Custom_StatusBarClockLetterSpacingEnabled", false);
        seekbarLetterSpacing.setProgress((int)(letterSpacing * 10));
        switchLetterSpacing.setChecked(letterSpacingEnabled);
        textLetterSpacingValue.setText(String.format("%.1f", letterSpacing));
        seekbarLetterSpacing.setEnabled(letterSpacingEnabled);

        int textColor = ZToolPrefs.getInt("Custom_StatusBarClockTextColor", 0xFFFFFFFF);
        boolean textColorEnabled = ZToolPrefs.getBoolean("Custom_StatusBarClockTextColorEnabled", false);
        switchTextColor.setChecked(textColorEnabled);
        updateColorPreview(textColor);
        buttonPickColor.setEnabled(textColorEnabled);

        boolean textBold = ZToolPrefs.getBoolean("Custom_StatusBarClockTextBold", true);
        boolean textBoldEnabled = ZToolPrefs.getBoolean("Custom_StatusBarClockTextBold", false);
        switchTextBold.setChecked(textBoldEnabled);
    }

    private void updateClockPreview(String format) {
        if (format == null || format.isEmpty()) {
            textPreview.setText(getString(R.string.preview_default));
            return;
        }
        try {
            String currentTime = CustomDateFormatter.format(format, new Date());
            textPreview.setText(getString(R.string.preview_display, currentTime));
        } catch (Exception e) {
            textPreview.setText(getString(R.string.preview_invalid) + "\n" + getString(R.string.error_prefix) + e.getMessage());
            Log.e("CustomDatePreview", "Error formatting date: " + format, e);
        }
    }

    private void saveSettings(String moduleName, Boolean newValue) {
        mPrefsUtils.saveBooleanSetting(moduleName, newValue);
        if (moduleName.equals("Custom_StatusBarClock")) {
            llCustomClock.setVisibility(newValue ? VISIBLE : View.GONE);
            if (newValue) {
                EditText editTextClockFormat = findViewById(R.id.edittext_clock_format);
                String savedFormat = ZToolPrefs.getString("Custom_StatusBarClockFormat", "");
                editTextClockFormat.setText(savedFormat);
                updateClockPreview(savedFormat);
                loadStyleSettings();
            }
            updateStyleViewsVisibility(newValue);
        }
    }

    private void saveTextSize(float textSize) {
        ZToolPrefs.edit().putFloat("Custom_StatusBarClockTextSize", textSize).apply();
    }

    private void saveLetterSpacing(float letterSpacing) {
        ZToolPrefs.edit().putFloat("Custom_StatusBarClockLetterSpacing", letterSpacing).apply();
    }

    private void saveTextColor(int color) {
        ZToolPrefs.edit().putInt("Custom_StatusBarClockTextColor", color).apply();
    }

    private void saveStyleEnabled(String key, boolean enabled) {
        ZToolPrefs.edit().putBoolean(key, enabled).apply();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @SuppressLint("WorldReadableFiles")
    public SharedPreferences getZToolPreferences() {
        Context mContext = this;
        try {
            Context moduleContext = mContext.createPackageContext("com.qimian233.ztool", Context.CONTEXT_IGNORE_SECURITY);
            //noinspection deprecation
            return moduleContext.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        } catch (Exception e) {
            Log.e("ModulePreferences", "Failed to get module preferences, using fallback", e);
            return mContext.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        }
    }

    @SuppressLint("WorldReadableFiles")
    public SharedPreferences getNotifyNumSizeShared() {
        Context mContext = this;
        try {
            Context moduleContext = mContext.createPackageContext("com.qimian233.ztool", Context.CONTEXT_IGNORE_SECURITY);
            return moduleContext.getSharedPreferences("StatusBar_notifyNumSize", Context.MODE_WORLD_READABLE);
        } catch (Exception e) {
            Log.e("ModulePreferences", "Failed to get module preferences, using fallback", e);
            return mContext.getSharedPreferences("StatusBar_notifyNumSize", Context.MODE_WORLD_READABLE);
        }
    }
}
