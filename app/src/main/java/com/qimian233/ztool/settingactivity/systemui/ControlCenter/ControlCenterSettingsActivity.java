package com.qimian233.ztool.settingactivity.systemui.ControlCenter;

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
import com.google.android.material.materialswitch.MaterialSwitch;
import com.qimian233.ztool.R;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;
import com.qimian233.ztool.hook.modules.systemui.CustomDateFormatter;

import java.util.Date;

public class ControlCenterSettingsActivity extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchCustomDate;
    private LinearLayout llCustomDate;
    private static final String PREFS_NAME = "ControlCenter_Date";
    private Button SaveButton;
    private SharedPreferences ZToolPrefs;
    private TextView textPreview;

    // 样式相关的视图
    private LinearLayout llTextSize, llLetterSpacing, llTextColor, llTextBold;
    private MaterialSwitch switchTextSize, switchLetterSpacing, switchTextColor, switchTextBold;
    private SeekBar seekbarTextSize, seekbarLetterSpacing;
    private TextView textTextSizeValue, textLetterSpacingValue, textTextColorValue;
    private View viewColorPreview;
    private Button buttonPickColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_control_center_settings);

        String appName = getIntent().getStringExtra("app_name");
        appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + getString(R.string.control_center_settings_title_suffix));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mPrefsUtils = new ModulePreferencesUtils(this);
        initViews();
        loadSettings();
    }

    private void initViews() {
        llCustomDate = findViewById(R.id.ll_customDate);
        ZToolPrefs = getZToolPreferences();
        SaveButton = findViewById(R.id.button_save_date_format);
        textPreview = findViewById(R.id.textview_date_preview);

        initStyleViews();

        // 自定义时间事件
        switchCustomDate = findViewById(R.id.switch_custom_date);
        switchCustomDate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("Custom_ControlCenterDate", isChecked);
                updateStyleViewsVisibility(isChecked);
            }
        });

        // 保存自定义时间格式事件
        SaveButton.setOnClickListener(v -> {
            String dateFormat = ((TextView) findViewById(R.id.edittext_date_format)).getText().toString();
            Log.d("DateFormat", "保存的格式：" + dateFormat);
            ZToolPrefs.edit().putString("Custom_ControlCenterDateFormat", dateFormat).apply();
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.save_success_title)
                    .setMessage(R.string.date_format_saved_message)
                    .setPositiveButton(R.string.restart_yes, null)
                    .show();
        });

        // 格式帮助按钮
        ImageView helpButton = findViewById(R.id.info_img);
        helpButton.setOnClickListener(v -> showFormatHelpDialog());

        EditText editTextDateFormat = findViewById(R.id.edittext_date_format);
        editTextDateFormat.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateDatePreview(s.toString());
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
            saveStyleEnabled("Custom_ControlCenterDateTextSizeEnabled", isChecked);
            seekbarTextSize.setEnabled(isChecked);
        });

        switchLetterSpacing.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveStyleEnabled("Custom_ControlCenterDateLetterSpacingEnabled", isChecked);
            seekbarLetterSpacing.setEnabled(isChecked);
        });

        switchTextColor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveStyleEnabled("Custom_ControlCenterDateTextColorEnabled", isChecked);
            buttonPickColor.setEnabled(isChecked);
        });

        switchTextBold.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveStyleEnabled("Custom_ControlCenterDateTextBold", isChecked);
        });

        // 颜色选择器
        buttonPickColor.setOnClickListener(v -> showColorPickerDialog());
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
                .setTitle(R.string.date_format_help_title)
                .setMessage(getString(R.string.clock_format_help_content))
                .setPositiveButton(R.string.restart_yes, null)
                .setNeutralButton(R.string.copy_example_button, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText(getString(R.string.date_format_example), getString(R.string.date_format_sample));
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, R.string.example_copied_message, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void loadSettings() {
        boolean customDateEnabled = mPrefsUtils.loadBooleanSetting("Custom_ControlCenterDate", false);
        switchCustomDate.setChecked(customDateEnabled);
        llCustomDate.setVisibility(customDateEnabled ? VISIBLE : View.GONE);

        if (customDateEnabled) {
            EditText editTextDateFormat = findViewById(R.id.edittext_date_format);
            String savedFormat = ZToolPrefs.getString("Custom_ControlCenterDateFormat", getString(R.string.default_date_format));
            editTextDateFormat.setText(savedFormat);
            updateDatePreview(savedFormat);
            loadStyleSettings();
            updateStyleViewsVisibility(true);
        }
    }

    private void loadStyleSettings() {
        float textSize = ZToolPrefs.getFloat("Custom_ControlCenterDateTextSize", 16.0f);
        boolean textSizeEnabled = ZToolPrefs.getBoolean("Custom_ControlCenterDateTextSizeEnabled", false);

        int progress = (int) ((textSize - 10) / 0.5f);
        seekbarTextSize.setProgress(progress);
        switchTextSize.setChecked(textSizeEnabled);
        textTextSizeValue.setText(String.format("%.1f%s", textSize, getString(R.string.sp_unit)));
        seekbarTextSize.setEnabled(textSizeEnabled);

        float letterSpacing = ZToolPrefs.getFloat("Custom_ControlCenterDateLetterSpacing", 0.1f);
        boolean letterSpacingEnabled = ZToolPrefs.getBoolean("Custom_ControlCenterDateLetterSpacingEnabled", false);
        seekbarLetterSpacing.setProgress((int)(letterSpacing * 10));
        switchLetterSpacing.setChecked(letterSpacingEnabled);
        textLetterSpacingValue.setText(String.format("%.1f", letterSpacing));
        seekbarLetterSpacing.setEnabled(letterSpacingEnabled);

        int textColor = ZToolPrefs.getInt("Custom_ControlCenterDateTextColor", 0xFFFFFFFF);
        boolean textColorEnabled = ZToolPrefs.getBoolean("Custom_ControlCenterDateTextColorEnabled", false);
        switchTextColor.setChecked(textColorEnabled);
        updateColorPreview(textColor);
        buttonPickColor.setEnabled(textColorEnabled);

        boolean textBold = ZToolPrefs.getBoolean("Custom_ControlCenterDateTextBold", true);
        boolean textBoldEnabled = ZToolPrefs.getBoolean("Custom_ControlCenterDateTextBold", false);
        switchTextBold.setChecked(textBoldEnabled);
    }

    private void updateDatePreview(String format) {
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
        if (moduleName.equals("Custom_ControlCenterDate")) {
            llCustomDate.setVisibility(newValue ? VISIBLE : View.GONE);
            if (newValue) {
                EditText editTextDateFormat = findViewById(R.id.edittext_date_format);
                String savedFormat = ZToolPrefs.getString("Custom_ControlCenterDateFormat", getString(R.string.default_date_format));
                editTextDateFormat.setText(savedFormat);
                updateDatePreview(savedFormat);
                loadStyleSettings();
            }
            updateStyleViewsVisibility(newValue);
        }
    }

    private void saveTextSize(float textSize) {
        ZToolPrefs.edit().putFloat("Custom_ControlCenterDateTextSize", textSize).apply();
    }

    private void saveLetterSpacing(float letterSpacing) {
        ZToolPrefs.edit().putFloat("Custom_ControlCenterDateLetterSpacing", letterSpacing).apply();
    }

    private void saveTextColor(int color) {
        ZToolPrefs.edit().putInt("Custom_ControlCenterDateTextColor", color).apply();
    }

    private void saveStyleEnabled(String key, boolean enabled) {
        ZToolPrefs.edit().putBoolean(key, enabled).apply();
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
            return mContext.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        }
    }
}
