package com.qimian233.ztool.settingactivity.launcher;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.qimian233.ztool.R;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;
import com.qimian233.ztool.utils.AppChooserDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class LauncherSettingsActivity extends AppCompatActivity {

    private String appPackageName;
    private ModulePreferencesUtils mPrefsUtils;
    private MaterialSwitch switchMoreBigDock;
    private MaterialSwitch switchCustomizeGridSize;
    private Spinner switchDisableForceStop;
    private TextView forceStopWhiteListCount, inputGridSizeTitle;
    TextInputEditText etCustomGridRow, etCustomGridColumn;
    private View inputGridSizeLayout;
    private List<String> WHITE_LIST;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_launcher_settings);

        String appName = getIntent().getStringExtra("app_name");
        appPackageName = getIntent().getStringExtra("app_package");

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName + getString(R.string.launcher_settings_title_suffix));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mPrefsUtils = new ModulePreferencesUtils(this);
        initViews();
        loadSettings();
        initRestartButton();
    }

    private void initViews() {
        // 初始化禁止强制停止功能Spinner
        List<String> disableForceStop = Arrays.asList(
                getString(R.string.SelectDefault),
                getString(R.string.SelectAllAPP),
                getString(R.string.SelectWhiteList)
        );
        switchDisableForceStop = findViewById(R.id.spinner_disable_force_stop);
        switchDisableForceStop.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, disableForceStop));
        switchDisableForceStop.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 或者使用 position 来判断
                switch (position) {
                    case 0: // 默认
                        SelectDefault();
                        break;
                    case 1: // 全部APP
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
        // 读取白名单
        String whitelist = mPrefsUtils.loadStringSetting("ForceStopWhiteList", "");
        // 处理空字符串
        if (whitelist == null || whitelist.trim().isEmpty()) {
            WHITE_LIST = Collections.emptyList(); // 或 new ArrayList<>()
        } else {
            // 使用 split 并过滤空字符串
            WHITE_LIST = Arrays.stream(whitelist.split(","))
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toList());
        }
        // 白名单文本视图
        forceStopWhiteListCount = findViewById(R.id.txt_protected_apps);
        forceStopWhiteListCount.setText(getString(R.string.protected_apps_summary, WHITE_LIST.size()));
        forceStopWhiteListCount.setOnClickListener((View view) -> SelectUnForceStopAPP());
//        switchDisableForceStop.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings("disable_force_stop", isChecked));
        // 更多大的Dock栏功能开关
        switchMoreBigDock = findViewById(R.id.switch_dock_moreBig);
        switchMoreBigDock.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveSettings("zui_launcher_hotseat", isChecked));

        // 自定义桌面网格功能输入区的标题和输入区本体
        inputGridSizeTitle = findViewById(R.id.inputGridSizeTitle);
        inputGridSizeLayout = findViewById(R.id.customGridSizeLayout_dataInput);

        // 自定义桌面网格功能开关
        switchCustomizeGridSize = findViewById(R.id.switch_custom_grid_size);
        switchCustomizeGridSize.setOnCheckedChangeListener((buttonView, isChecked) ->
        {
            saveSettings("CustomGridSize", isChecked);
            if (isChecked) {
                inputGridSizeTitle.setVisibility(View.VISIBLE);
                inputGridSizeLayout.setVisibility(View.VISIBLE);
            } else {
                inputGridSizeTitle.setVisibility(View.GONE);
                inputGridSizeLayout.setVisibility(View.GONE);
            }
        });

        etCustomGridRow = inputGridSizeLayout.findViewById(R.id.edit_row_number);
        etCustomGridColumn = inputGridSizeLayout.findViewById(R.id.edit_column_number);

        etCustomGridRow.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                saveGridValues();
            }
        });

        etCustomGridColumn.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                saveGridValues();
            }
        });
    }

    private void loadSettings() {
        // 加载禁止强制停止开关状态
        boolean disableForceStopEnabled = mPrefsUtils.loadBooleanSetting("disable_force_stop",false);
        boolean disableForceStopWhiteListEnable = mPrefsUtils.loadBooleanSetting("ForceStopWhiteListEnable",false);
        switchDisableForceStop.setSelection(disableForceStopEnabled ? (disableForceStopWhiteListEnable ? 2 : 1) : 0);
        // 加载更多大的Dock栏开关状态
        boolean moreBigDockEnabled = mPrefsUtils.loadBooleanSetting("zui_launcher_hotseat",false);
        switchMoreBigDock.setChecked(moreBigDockEnabled);
        // 加载自定义桌面网格功能开关状态
        boolean customGridSizeEnabled = mPrefsUtils.loadBooleanSetting("CustomGridSize",false);
        switchCustomizeGridSize.setChecked(customGridSizeEnabled);

        if (customGridSizeEnabled) {
            inputGridSizeTitle.setVisibility(View.VISIBLE);
            inputGridSizeLayout.setVisibility(View.VISIBLE);
            etCustomGridRow.setText(String.valueOf(
                    mPrefsUtils.loadIntegerSetting("CustomLauncherRow", 4)));
            etCustomGridColumn.setText(String.valueOf(
                    mPrefsUtils.loadIntegerSetting("CustomLauncherColumn", 6)));
        } else {
            inputGridSizeTitle.setVisibility(View.GONE);
            inputGridSizeLayout.setVisibility(View.GONE);
        }
    }

    private void SelectDefault() {
        saveSettings("disable_force_stop",false);
        forceStopWhiteListCount.setVisibility(View.GONE);
    }

    private void SelectAllGames() {
        saveSettings("disable_force_stop",true);
        saveSettings("ForceStopWhiteListEnable",false);
        forceStopWhiteListCount.setVisibility(View.GONE);
    }

    private void SelectWhiteList() {
        saveSettings("disable_force_stop",true);
        saveSettings("ForceStopWhiteListEnable",true);
        forceStopWhiteListCount.setVisibility(View.VISIBLE);
    }

    private void SelectUnForceStopAPP() {
        AppChooserDialog.show(this, getUserInstalledPackageNames(LauncherSettingsActivity.this), WHITE_LIST, getString(R.string.force_stop_title), new AppChooserDialog.AppSelectionCallback() {
            @Override
            public void onSelected(List<AppChooserDialog.AppInfo> selectedApps) {
                StringBuilder selectedPackageNames = new StringBuilder();
                // 处理用户选择的应用
                WHITE_LIST = new ArrayList<>();
                for (AppChooserDialog.AppInfo app : selectedApps) {
                    Log.d("AppChooser", "Selected: " + app.getAppName() + " (" + app.getPackageName() + ")");
                    selectedPackageNames.append(app.getPackageName()).append(",");
                    WHITE_LIST.add(app.getPackageName());
                }
                mPrefsUtils.saveStringSetting("ForceStopWhiteList", selectedPackageNames.toString());
                // 刷新白名单数量文本视图
                forceStopWhiteListCount.setText(getString(R.string.protected_apps_summary, WHITE_LIST.size()));
            }

            @Override
            public void onCancel() {
            }
        });
    }

    public static List<String> getUserInstalledPackageNames(Context context) {
        List<String> userPackageNames = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> installedPackages = pm.getInstalledPackages(0);

        for (PackageInfo info : installedPackages) {
            // 判断是否为系统应用
            assert info.applicationInfo != null;
            boolean isSystemApp = (info.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;

            // 如果不是系统应用，或者虽然是系统应用但是用户升级过的
            boolean isUpdatedSystemApp = (info.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

            // 这里仅保留用户应用，你可以根据需求调整逻辑
            if (!isSystemApp || isUpdatedSystemApp) {
                userPackageNames.add(info.packageName);
            }
        }
        return userPackageNames;
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
            Toast.makeText(this, R.string.force_stop_success, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.force_stop_fail, Toast.LENGTH_SHORT).show();
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

    private void saveGrid(int gridRow, int gridColumn) {
        mPrefsUtils.saveIntegerSetting("CustomLauncherRow", gridRow);
        mPrefsUtils.saveIntegerSetting("CustomLauncherColumn", gridColumn);
    }

    private void saveGridValues() {
        try {
            String rowText = Objects.requireNonNull(etCustomGridRow.getText()).toString().trim();
            String columnText = Objects.requireNonNull(etCustomGridColumn.getText()).toString().trim();

            if (!rowText.isEmpty() && !columnText.isEmpty()) {
                int customGridRow = Integer.parseInt(rowText);
                int customGridColumn = Integer.parseInt(columnText);
                saveGrid(customGridRow, customGridColumn);
            }
        } catch (NumberFormatException e) {
            Log.d("GridSettings", "Invalid number format: " + e.getMessage());
        } catch (NullPointerException e) {
            Log.d("GridSettings", "Text is null: " + e.getMessage());
        }
    }
}
