package com.qimian233.ztool;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.qimian233.ztool.service.LogServiceManager;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;
import com.qimian233.ztool.utils.FileManager;

public class SettingsFragment extends Fragment {
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // 获取视图组件
        LinearLayout backupConfigToFile = view.findViewById(R.id.backup_config_to_file);
        LinearLayout restoreConfigFromFile = view.findViewById(R.id.restore_config_from_file);
        LinearLayout restoreDefaultConfig = view.findViewById(R.id.restore_default_config);
        MaterialSwitch switchEnableLogService = view.findViewById(R.id.switch_enable_log_service);
        MaterialSwitch switchEnableHomepageYiyan = view.findViewById(R.id.switch_enable_homepage_yiyan);
        CardView showAboutPage = view.findViewById(R.id.show_about_page);
        CardView checkZToolUpdate = view.findViewById(R.id.check_ztool_update);

        // 设置点击监听器
        backupConfigToFile.setOnClickListener(v ->
                performBackup());
        restoreConfigFromFile.setOnClickListener(v ->
                openDocumentLauncherForRestore.launch(new String[]{"application/json"}));
        restoreDefaultConfig.setOnClickListener(v -> restoreDefaultSettings());
        showAboutPage.setOnClickListener(v -> showAboutPage());
        checkZToolUpdate.setOnClickListener(v -> checkForZToolUpdates());

        // 设置开关监听器
        boolean isLogServiceEnabled = LogServiceManager.isServiceEnabled(requireContext());
        switchEnableLogService.setChecked(isLogServiceEnabled);
        switchEnableLogService.setOnCheckedChangeListener((buttonView, isChecked) -> handleLogServiceSwitch(isChecked));

        ModulePreferencesUtils utils = new ModulePreferencesUtils(requireContext());
        boolean isHomepageYiyanEnabled = utils.loadBooleanSetting("enable_homepage_yiyan", true);
        switchEnableHomepageYiyan.setChecked(isHomepageYiyanEnabled);
        switchEnableHomepageYiyan.setOnCheckedChangeListener((buttonView, isChecked) -> utils.saveBooleanSetting("enable_homepage_yiyan", isChecked));

        return view;
    }
    /**
     * 显示Toast消息
     */
    private void showToast(String message) {
        if (getContext() != null) {
            android.widget.Toast.makeText(getContext(), message, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 预先注册ActivityResultLauncher
        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                    if (uri != null) {
                        boolean result = FileManager.saveConfigWithSAF(requireContext(),
                                uri,
                                FileManager.generateBackupFileName(),
                                ModulePreferencesUtils.getAllSettingsAsJSON(requireContext()));
                        if (result) {
                            Log.d("SAF", "成功存储配置到用户指定的目录" + uri);
                            showToast(getString(R.string.config_backup_success));
                        } else {
                            Log.e("SAF", "备份失败");
                        }
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次进入设置页面时更新开关状态
        refreshSwitchStates();
    }

    /**
     * 刷新所有开关状态
     */
    private void refreshSwitchStates() {
        // 更新日志服务开关状态
        boolean isLogServiceEnabled = LogServiceManager.isServiceEnabled(requireContext());
        MaterialSwitch switchEnableLogService = requireView().findViewById(R.id.switch_enable_log_service);
        switchEnableLogService.setChecked(isLogServiceEnabled);
    }

    // 打开系统默认浏览器，访问项目的Github Releases
    private void checkForZToolUpdates(){
        String url = "https://github.com/qwqawa64/ZUX-ZTool/releases";
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            showToast(getString(R.string.open_browser_failed));
            Log.e("SettingsFragment", "无法打开浏览器: " + e.getMessage());
        }
    }

    private void showAboutPage() {
        // 使用 HTML 标签创建带链接的文本
        String htmlText = getString(R.string.about_description, updateModuleStatus());

        Spanned message = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_ztool_title)
                .setMessage(message)
                .setPositiveButton(R.string.restart_yes, null)
                .create();

        dialog.show();

        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private void restoreDefaultSettings() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.final_confirmation_title)
                .setMessage(R.string.restore_default_confirmation)
                .setPositiveButton(R.string.restart_yes,
                        (dialogInterface, which) -> performRestore())
                .setNegativeButton(R.string.restart_no, null)
                .create();
        dialog.show();
    }

    private void performRestore() {
        new ModulePreferencesUtils(requireContext()).clearAllSettings();
        showToast(getString(R.string.default_config_restored));
    }

    // 类成员变量 - 预先注册的ActivityResultLauncher
    private ActivityResultLauncher<String> backupLauncher;

    private void performBackup(){
        String backupFileName = FileManager.generateBackupFileName();
        backupLauncher.launch(backupFileName);
    }

    private final ActivityResultLauncher<String[]> openDocumentLauncherForRestore =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    String content = FileManager.readConfigWithSAF(requireContext(), uri);
                    if (content != null) {
                        // 处理读取到的内容
                        Log.d("SAF", "读取到的内容: " + content);
                        ModulePreferencesUtils.restoreConfig(requireContext(), content);
                        showToast(getString(R.string.config_restore_success));
                    } else {
                        Log.e("SAF", "文件读取失败或内容为空");
                    }
                }
            });

    /**
     * 处理日志服务开关状态变化
     */
    private void handleLogServiceSwitch(boolean isEnabled) {
        if (isEnabled) {
            // 启动日志采集服务
            LogServiceManager.startLogService(requireContext());
            showToast(getString(R.string.log_service_started));
        } else {
            // 停止日志采集服务
            LogServiceManager.stopLogService(requireContext());
            showToast(getString(R.string.log_service_stopped));
        }
    }

    private String updateModuleStatus() {
        final String TAG = "SettingsFragment";
        String moduleVersion;
        try {
            try {
                Activity activity = getActivity();
                if (activity != null) {
                    PackageInfo packageInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                    String versionName = packageInfo.versionName;
                    int versionCode = packageInfo.versionCode;
                    moduleVersion = versionName + " (" + versionCode + ")";
                } else {
                    moduleVersion = getString(R.string.unknown_activity_null);
                }
            } catch (PackageManager.NameNotFoundException e) {
                //e.printStackTrace();
                Log.w(TAG, "无法获取模块版本信息: " + e.getMessage());
                moduleVersion = getString(R.string.unknown);
            }

            Log.i(TAG, "模块状态更新完成");
            return moduleVersion;

        } catch (Exception e) {
            Log.e(TAG, "更新模块状态失败: " + e.getMessage());
            return null;
        }
    }
}
