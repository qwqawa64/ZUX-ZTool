package com.qimian233.ztool.settingactivity.systemframework;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.qimian233.ztool.R;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;
import com.qimian233.ztool.utils.CountdownDialog;

public class FrameworkSettingsActivity extends AppCompatActivity implements CountdownDialog.OnCountdownFinishListener {

    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchKeepRotation, switchDisableZUIApplist, switchDisableFlagSecure, switchAi_input_expand;
    private TextInputEditText etCustomDetector;
    private TextInputLayout tilCustomDetector;
    private View containerCustomDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_framework_settings);

        String appName = getIntent().getStringExtra("app_name");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + getString(R.string.framework_settings_title_suffix));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mPrefsUtils = new ModulePreferencesUtils(this);
        initViews();
        loadSettings();
        initRestartButton();
    }

    private void initViews() {
        // 设置保持屏幕方向按钮点击监听
        switchKeepRotation = findViewById(R.id.switch_keep_rotation);
        switchKeepRotation.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("keep_rotation", isChecked));

        // 设置禁用系统应用列表管理点击监听
        switchDisableZUIApplist = findViewById(R.id.switch_disable_zuiapplist);
        switchDisableZUIApplist.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("allow_get_packages", isChecked));

        // 设置禁用每24H验证一次锁屏密码
//         switchDisableForcedLockscreenPassword = findViewById(R.id.switch_disable_lockscreen_password_per24h);
//         switchDisableForcedLockscreenPassword.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("NoMorePasswordPer24H", isChecked));

        // 移除安全窗口标识
        switchDisableFlagSecure = findViewById(R.id.switch_DisableFlagSecure);
        switchDisableFlagSecure.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("disable_flag_secure", isChecked));

        // 自定义AI输入检测符
        switchAi_input_expand = findViewById(R.id.switch_ai_input_expand);
        containerCustomDetector = findViewById(R.id.container_custom_detector);
        tilCustomDetector = findViewById(R.id.til_custom_detector);
        etCustomDetector = findViewById(R.id.et_custom_detector);

        // 提示弹窗
        ImageView infoImg = findViewById(R.id.info_img);
        infoImg.setOnClickListener(v -> showAiInputInfoDialog());

        // 开关监听器：控制输入框的显示/隐藏
        switchAi_input_expand.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSettings("ai_input_expand", isChecked);
            containerCustomDetector.setVisibility(isChecked ? View.VISIBLE : View.GONE);

            // 当开关关闭时，清空错误提示
            if (!isChecked) {
                tilCustomDetector.setError(null);
            }
        });

        // 输入框文本变化监听器：实时检查格式并保存
        etCustomDetector.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String input = s.toString().trim();

                if (input.isEmpty()) {
                    // 输入为空，清空错误提示并保存空值
                    tilCustomDetector.setError(null);
                    tilCustomDetector.setHelperText(null);
                    mPrefsUtils.saveStringSetting("AI_INPUT_EXPAND_SIGNS", "");
                    return;
                }

                // 检查格式
                if (isValidFormat(input)) {
                    // 格式正确，清空错误提示
                    tilCustomDetector.setError(null);
                    tilCustomDetector.setHelperText("");

                    // 保存到偏好文件
                    mPrefsUtils.saveStringSetting("AI_INPUT_EXPAND_SIGNS", input);
                } else {
                    // 格式错误，显示错误提示
                    tilCustomDetector.setError(getString(R.string.custom_detector_err));
                    tilCustomDetector.setHelperText(null);
                }
            }
        });
    }

    private void loadSettings() {
        // 加载禁用系统应用列表管理开关状态
        boolean disableZUIApplist = mPrefsUtils.loadBooleanSetting("allow_get_packages", false);
        switchDisableZUIApplist.setChecked(disableZUIApplist);

        // 加载保持屏幕方向设置
        boolean isKeepRotation = mPrefsUtils.loadBooleanSetting("keep_rotation", false);
        switchKeepRotation.setChecked(isKeepRotation);

        // 加载禁止24H验证一次锁屏密码的设置
        // boolean isNotRequireLockscreenPasswordPer24H = mPrefsUtils.loadBooleanSetting("NoMorePasswordPer24H", false);
        // switchDisableForcedLockscreenPassword.setChecked(isNotRequireLockscreenPasswordPer24H);

        // 加载移除安全窗口标识
        boolean isDisableFlagSecure = mPrefsUtils.loadBooleanSetting("disable_flag_secure", false);
        switchDisableFlagSecure.setChecked(isDisableFlagSecure);

        // 自定义AI输入检测符
        boolean isAiInputExpand = mPrefsUtils.loadBooleanSetting("ai_input_expand", false);
        switchAi_input_expand.setChecked(isAiInputExpand);
        containerCustomDetector.setVisibility(isAiInputExpand ? View.VISIBLE : View.GONE);

        // 加载自定义检测符
        String savedSigns = mPrefsUtils.loadStringSetting("AI_INPUT_EXPAND_SIGNS", "");
        etCustomDetector.setText(savedSigns);
    }

    /**
     * 检查输入格式是否有效
     * 有效的格式包括：
     * 1. 单个检测符（无逗号）
     * 2. 多个检测符用英文逗号分隔，且逗号前后不能为空
     */
    private boolean isValidFormat(String input) {
        if (input.isEmpty()) {
            return true; // 空输入视为有效
        }

        // 检查是否包含中文逗号
        if (input.contains("，")) {
            return false;
        }

        // 分割字符串
        String[] parts = input.split(",");

        // 检查每个部分是否为空
        for (String part : parts) {
            if (part.trim().isEmpty()) {
                return false; // 有空的部分（连续的逗号或逗号在开头/结尾）
            }
        }

        return true;
    }

    /**
     * 显示AI输入扩展的说明弹窗，包含一个测试输入框
     */
    private void showAiInputInfoDialog() {
        // 1. 创建容器，用于设置边距
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        // 设置左右边距，符合Material Design规范 (24dp)
        int paddingHorizontal = (int) (24 * getResources().getDisplayMetrics().density);
        int paddingTop = (int) (10 * getResources().getDisplayMetrics().density);
        container.setPadding(paddingHorizontal, paddingTop, paddingHorizontal, 0);

        // 2. 创建 TextInputLayout (Outlined 风格)
        // 使用 textInputOutlinedStyle 属性确保样式正确
        TextInputLayout textInputLayout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textInputLayout.setLayoutParams(params);
        textInputLayout.setHint(getString(R.string.test_Input)); // 输入框提示

        // 3. 创建 TextInputEditText
        TextInputEditText editText = getTextInputEditText(textInputLayout);

        // 4. 组装视图
        textInputLayout.addView(editText);
        container.addView(textInputLayout);

        // 5. 显示对话框
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.Custom_attention))
                .setMessage(getString(R.string.Custom_Attention_content))
                .setView(container) // 设置自定义视图
                .setPositiveButton(android.R.string.ok, null) // 确定按钮
                .show();
    }

    @NonNull
    private static TextInputEditText getTextInputEditText(TextInputLayout textInputLayout) {
        TextInputEditText editText = new TextInputEditText(textInputLayout.getContext());
        editText.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // 设置为多行文本，最小高度5行
        editText.setMinLines(5);
        editText.setMaxLines(10);
        editText.setGravity(Gravity.TOP | Gravity.START); // 文字从左上角开始
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return editText;
    }


    private void initRestartButton() {
        FloatingActionButton fabRestart = findViewById(R.id.fab_restart);
        // fabRestart.setOnClickListener(v -> showRestartConfirmationDialog(this));
        fabRestart.setOnClickListener(v -> {
            CountdownDialog.Builder dialog = new CountdownDialog.Builder(this, this);
            dialog.setPositiveText(getString(R.string.confirm));
            dialog.setNegativeText(getString(R.string.restart_no));
            dialog.setCountdownSeconds(3);
            dialog.setCancelable(true);
            dialog.setMessage(getString(R.string.restart_system_message));
            dialog.setTitle(getString(R.string.restart_system_title));
            dialog.build().show();
        });
    }

    private void restartOS() {
        try {
            // 使用root权限执行重启命令
            Process process = Runtime.getRuntime().exec("su -c reboot");
            process.waitFor();
        } catch (Exception e) {
            e.getMessage();
            Toast.makeText(this, getString(R.string.restart_fail_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show();
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

    @Override
    public void onPositiveButtonClick() {restartOS();}

    @Override
    public void onNegativeButtonClick() {}

    @Override
    public void onCountdownFinished() {}
}
