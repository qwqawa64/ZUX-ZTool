package com.qimian233.ztool.settingactivity.systemui.lockscreen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.qimian233.ztool.LoadingDialog;
import com.qimian233.ztool.R;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LockScreenSettingsActivity extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchYiYan;
    private EditText editApiAddress, editRegex;
    private Button buttonTestApi;
    private LoadingDialog loadingDialog;
    private SharedPreferences yiYanPrefs, ZToolPrefs;
    private Spinner spinnerChargeWatts;
    private String[] wattOptions = {"不启用", "握手功率", "实际功率"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lock_screen_settings);

        String appName = getIntent().getStringExtra("app_name");
        appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + " - 锁屏设置");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mPrefsUtils = new ModulePreferencesUtils(this);
        ZToolPrefs = getZToolPreferences();
        initYiYanViews();
        initChargeWattsViews();
        loadSettings();
    }

    private void initYiYanViews() {
        switchYiYan = findViewById(R.id.switch_YiYan);
        editApiAddress = findViewById(R.id.edit_api_address);
        editRegex = findViewById(R.id.edit_regex);
        buttonTestApi = findViewById(R.id.button_test_api);

        loadingDialog = new LoadingDialog(this);
        yiYanPrefs = getYiYanPreferences();

        switchYiYan.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveSettings("YiYan", isChecked);
                findViewById(R.id.YiYanAPI).setVisibility(isChecked ? View.VISIBLE : View.GONE);
                if (!isChecked) {
                    saveSettings("auto_owner_info", false);
                } else {
                    String savedApiUrl = yiYanPrefs.getString("API_URL", "");
                    if (!savedApiUrl.isEmpty()) {
                        saveSettings("auto_owner_info", true);
                    }
                }
            }
        });

        buttonTestApi.setOnClickListener(v -> {
            testApiConnection();
        });

        loadYiYanSettings();
    }

    private void initChargeWattsViews() {
        spinnerChargeWatts = findViewById(R.id.spinner_charge_watts);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                wattOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerChargeWatts.setAdapter(adapter);

        spinnerChargeWatts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedOption = wattOptions[position];
                handleWattOptionSelected(selectedOption);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void handleWattOptionSelected(String selectedOption) {
        switch (selectedOption) {
            case "不启用":
                saveSettings("systemui_charge_watts", false);
                saveSettings("systemUI_RealWatts", false);
                break;
            case "握手功率":
                saveSettings("systemui_charge_watts", true);
                saveSettings("systemUI_RealWatts", false);
                break;
            case "实际功率":
                saveSettings("systemui_charge_watts", false);
                saveSettings("systemUI_RealWatts", true);
                break;
        }
        ZToolPrefs.edit().putString("charge_watts_selected_option", selectedOption).apply();
    }

    private void testApiConnection() {
        String apiUrl = editApiAddress.getText().toString().trim();
        String regex = editRegex.getText().toString().trim();

        if (apiUrl.isEmpty()) {
            Toast.makeText(this, "请输入API地址", Toast.LENGTH_SHORT).show();
            return;
        }

        loadingDialog.show("正在测试API连接...");

        new Thread(() -> {
            try {
                String response = performHttpGet(apiUrl);
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    handleApiResponse(response, regex);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    showTestResultDialog("请求失败", "错误信息: " + e.getMessage(), false);
                });
            }
        }).start();
    }

    private String performHttpGet(String urlString) throws Exception {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "ZTool/1.0");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            } else {
                throw new Exception("HTTP错误: " + responseCode);
            }
        } finally {
            try {
                if (reader != null) reader.close();
                if (connection != null) connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleApiResponse(String response, String regex) {
        String extractedContent = response;
        boolean hasRegex = !regex.isEmpty();

        if (hasRegex) {
            try {
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(response);

                if (matcher.find()) {
                    extractedContent = matcher.group(1);
                    extractedContent = extractedContent
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                            .replace("\\/", "/")
                            .replace("\\b", "\b")
                            .replace("\\f", "\f")
                            .replace("\\n", "\n")
                            .replace("\\r", "\r")
                            .replace("\\t", "\t");
                } else {
                    showTestResultDialog("正则匹配失败",
                            "响应体: " + response + "\n\n未找到匹配正则表达式的内容", false);
                    return;
                }
            } catch (Exception e) {
                showTestResultDialog("正则表达式错误",
                        "错误信息: " + e.getMessage() + "\n\n响应体: " + response, false);
                return;
            }
        }

        String message = "API请求成功!\n\n";
        if (hasRegex) {
            message += "正则匹配结果: " + extractedContent + "\n\n";
        }
        message += "原始响应: " + response;

        showTestResultDialog("测试成功", message, true);
    }

    private void showTestResultDialog(String title, String message, boolean success) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message);

        if (success) {
            builder.setPositiveButton("保存配置", (dialog, which) -> {
                saveYiYanConfiguration();
            });
        }

        builder.setNegativeButton("取消", null)
                .show();
    }

    private void saveYiYanConfiguration() {
        String apiUrl = editApiAddress.getText().toString().trim();
        String regex = editRegex.getText().toString().trim();

        SharedPreferences.Editor editor = yiYanPrefs.edit();
        editor.putString("API_URL", apiUrl);
        editor.putString("Regular", regex);
        editor.apply();

        saveSettings("auto_owner_info", true);
        Toast.makeText(this, "配置已保存并启用锁屏一言功能", Toast.LENGTH_SHORT).show();
    }

    private void loadSettings() {
        loadYiYanSettings();
        loadChargeWattsOption();
    }

    private void loadYiYanSettings() {
        String savedApiUrl = yiYanPrefs.getString("API_URL", "");
        String savedRegex = yiYanPrefs.getString("Regular", "");

        editApiAddress.setText(savedApiUrl);
        editRegex.setText(savedRegex);

        boolean yiYanEnabled = mPrefsUtils.loadBooleanSetting("auto_owner_info", false);
        switchYiYan.setChecked(yiYanEnabled);
        findViewById(R.id.YiYanAPI).setVisibility(yiYanEnabled ? View.VISIBLE : View.GONE);
    }

    private void loadChargeWattsOption() {
        String savedOption = ZToolPrefs.getString("charge_watts_selected_option", "不启用");

        boolean chargeWattsEnabled = mPrefsUtils.loadBooleanSetting("systemui_charge_watts", false);
        boolean realWattsEnabled = mPrefsUtils.loadBooleanSetting("systemUI_RealWatts", false);

        String currentOption;
        if (chargeWattsEnabled && !realWattsEnabled) {
            currentOption = "握手功率";
        } else if (!chargeWattsEnabled && realWattsEnabled) {
            currentOption = "实际功率";
        } else {
            currentOption = "不启用";
        }

        int position = Arrays.asList(wattOptions).indexOf(currentOption);
        if (position >= 0) {
            spinnerChargeWatts.setSelection(position);
        }

        ZToolPrefs.edit().putString("charge_watts_selected_option", currentOption).apply();
    }

    private void saveSettings(String moduleName, Boolean newValue) {
        mPrefsUtils.saveBooleanSetting(moduleName, newValue);
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
            return moduleContext.getSharedPreferences("StatusBar_Clock", Context.MODE_WORLD_READABLE);
        } catch (Exception e) {
            Log.e("ModulePreferences", "Failed to get module preferences, using fallback", e);
            return mContext.getSharedPreferences("StatusBar_Clock", Context.MODE_WORLD_READABLE);
        }
    }

    public SharedPreferences getYiYanPreferences() {
        Context mContext = this;
        try {
            Context moduleContext = mContext.createPackageContext("com.qimian233.ztool", Context.CONTEXT_IGNORE_SECURITY);
            return moduleContext.getSharedPreferences("YiYanConfig", Context.MODE_WORLD_READABLE);
        } catch (Exception e) {
            Log.e("YiYanPreferences", "Failed to get module preferences, using fallback", e);
            return mContext.getSharedPreferences("YiYanConfig", Context.MODE_WORLD_READABLE);
        }
    }
}
