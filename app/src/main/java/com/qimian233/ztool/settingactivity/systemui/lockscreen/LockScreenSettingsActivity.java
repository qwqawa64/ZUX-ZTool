package com.qimian233.ztool.settingactivity.systemui.lockscreen;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
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

    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchYiYan;
    private EditText editApiAddress, editRegex;
    private LoadingDialog loadingDialog;
    private ModulePreferencesUtils ZToolPrefs, yiYanPrefs;
    private Spinner spinnerChargeWatts;
    private String[] wattOptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lock_screen_settings);

        String appName = getIntent().getStringExtra("app_name");
        //String appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + getString(R.string.lock_screen_settings_title_suffix));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mPrefsUtils = new ModulePreferencesUtils(this);
        ZToolPrefs = new ModulePreferencesUtils(this);
        wattOptions = getResources().getStringArray(R.array.watt_options);
        initYiYanViews();
        initChargeWattsViews();
        loadSettings();
    }

    private void initYiYanViews() {
        switchYiYan = findViewById(R.id.switch_YiYan);
        editApiAddress = findViewById(R.id.edit_api_address);
        editRegex = findViewById(R.id.edit_regex);
        Button buttonTestApi = findViewById(R.id.button_test_api);

        loadingDialog = new LoadingDialog(this);
        yiYanPrefs = new ModulePreferencesUtils(this);

        switchYiYan.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSettings("YiYan", isChecked);
            findViewById(R.id.YiYanAPI).setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (!isChecked) {
                saveSettings("auto_owner_info", false);
            } else {
                String savedApiUrl = yiYanPrefs.loadStringSetting("API_URL", "");
                if (!savedApiUrl.isEmpty()) {
                    saveSettings("auto_owner_info", true);
                }
            }
        });

        buttonTestApi.setOnClickListener(v -> testApiConnection());

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
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
                if (!mPrefsUtils.loadBooleanSetting("isSystemUIPermissionConfirmed",false)) {
                    builder.setTitle(R.string.tooltip_content_description)
                            .setMessage(R.string.systemui_root_permission_required_message)
                            .setNegativeButton(R.string.confirm, null)
                            .setPositiveButton(R.string.do_not_show_again, (dialogInterface, i) -> {
                                saveSettings("isSystemUIPermissionConfirmed", true);
                                android.widget.Toast.makeText(this, R.string.no_tip_next_time, android.widget.Toast.LENGTH_SHORT).show();
                            })
                            .show();
                }
                break;
        }
        ZToolPrefs.saveStringSetting("charge_watts_selected_option", selectedOption);
    }

    private void testApiConnection() {
        String apiUrl = editApiAddress.getText().toString().trim();
        String regex = editRegex.getText().toString().trim();

        if (apiUrl.isEmpty()) {
            Toast.makeText(this, R.string.please_input_api_address, Toast.LENGTH_SHORT).show();
            return;
        }

        loadingDialog.show(getString(R.string.testing_api_connection));

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
                    showTestResultDialog(getString(R.string.request_failed), getString(R.string.error_message_prefix) + e.getMessage(), false);
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
                throw new Exception(getString(R.string.http_error_prefix) + responseCode);
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
                    assert extractedContent != null;
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
                    showTestResultDialog(getString(R.string.regex_match_failed),
                            getString(R.string.response_body_prefix) + response + getString(R.string.regex_no_match_message), false);
                    return;
                }
            } catch (Exception e) {
                showTestResultDialog(getString(R.string.regex_error),
                        getString(R.string.error_message_prefix) + e.getMessage() + getString(R.string.response_body_prefix) + response, false);
                return;
            }
        }

        String message = getString(R.string.api_request_success);
        if (hasRegex) {
            message += getString(R.string.regex_match_result_prefix) + extractedContent + "\n\n";
        }
        message += getString(R.string.original_response_prefix) + response;

        showTestResultDialog(getString(R.string.test_success), message, true);
    }

    private void showTestResultDialog(String title, String message, boolean success) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message);

        if (success) {
            builder.setPositiveButton(R.string.save_configuration_button, (dialog, which) -> saveYiYanConfiguration());
        }

        builder.setNegativeButton(R.string.restart_no, null)
                .show();
    }

    private void saveYiYanConfiguration() {
        String apiUrl = editApiAddress.getText().toString().trim();
        String regex = editRegex.getText().toString().trim();

        yiYanPrefs.saveStringSetting("API_URL", apiUrl);
        yiYanPrefs.saveStringSetting("Regular", regex);

        saveSettings("auto_owner_info", true);
        Toast.makeText(this, R.string.configuration_saved_message, Toast.LENGTH_SHORT).show();
    }

    private void loadSettings() {
        loadYiYanSettings();
        loadChargeWattsOption();
    }

    private void loadYiYanSettings() {
        String savedApiUrl = yiYanPrefs.loadStringSetting("API_URL", "");
        String savedRegex = yiYanPrefs.loadStringSetting("Regular", "");

        editApiAddress.setText(savedApiUrl);
        editRegex.setText(savedRegex);

        boolean yiYanEnabled = mPrefsUtils.loadBooleanSetting("auto_owner_info", false);
        switchYiYan.setChecked(yiYanEnabled);
        findViewById(R.id.YiYanAPI).setVisibility(yiYanEnabled ? View.VISIBLE : View.GONE);
    }

    private void loadChargeWattsOption() {
        // String savedOption = ZToolPrefs.loadStringSetting("charge_watts_selected_option", getString(R.string.watt_option_disabled));

        boolean chargeWattsEnabled = mPrefsUtils.loadBooleanSetting("systemui_charge_watts", false);
        boolean realWattsEnabled = mPrefsUtils.loadBooleanSetting("systemUI_RealWatts", false);

        String currentOption;
        if (chargeWattsEnabled && !realWattsEnabled) {
            currentOption = getString(R.string.watt_option_handshake);
        } else if (!chargeWattsEnabled && realWattsEnabled) {
            currentOption = getString(R.string.watt_option_actual);
        } else {
            currentOption = getString(R.string.watt_option_disabled);
        }

        int position = Arrays.asList(wattOptions).indexOf(currentOption);
        if (position >= 0) {
            spinnerChargeWatts.setSelection(position);
        }

        ZToolPrefs.saveStringSetting("charge_watts_selected_option", currentOption);
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
