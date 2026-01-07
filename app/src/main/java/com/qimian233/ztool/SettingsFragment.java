package com.qimian233.ztool;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;
import com.qimian233.ztool.service.LogServiceManager;
import com.qimian233.ztool.utils.FileManager;

public class SettingsFragment extends Fragment {

    private ActivityResultLauncher<String> backupLauncher;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // 获取视图组件
        LinearLayout backupConfigToFile = view.findViewById(R.id.backup_config_to_file);
        LinearLayout restoreConfigFromFile = view.findViewById(R.id.restore_config_from_file);
        LinearLayout restoreDefaultConfig = view.findViewById(R.id.restore_default_config);
        MaterialSwitch switchEnableLogService = view.findViewById(R.id.switch_enable_log_service);
        MaterialSwitch switchEnableDetailedLogging = view.findViewById(R.id.switch_enable_detailed_logging);
        MaterialSwitch switchEnableHomepageYiyan = view.findViewById(R.id.switch_enable_homepage_yiyan);
        CardView showAboutPage = view.findViewById(R.id.show_about_page);

        // 设置点击监听器
        backupConfigToFile.setOnClickListener(v -> performBackup());
        restoreConfigFromFile.setOnClickListener(v ->
                openDocumentLauncherForRestore.launch(new String[]{"application/json"}));
        restoreDefaultConfig.setOnClickListener(v -> restoreDefaultSettings());
        showAboutPage.setOnClickListener(v -> showAboutPage());

        // 设置开关监听器
        boolean isLogServiceEnabled = LogServiceManager.isServiceEnabled(requireContext());
        switchEnableLogService.setChecked(isLogServiceEnabled);
        switchEnableLogService.setOnCheckedChangeListener((buttonView, isChecked) -> handleLogServiceSwitch(isChecked));

        // 这里初始化一个SharedPreferencesUtils实例，需要使用SharedPreferences来初始化配置的开关放在它后面
        ModulePreferencesUtils utils = new ModulePreferencesUtils(requireContext());

        switchEnableDetailedLogging.setChecked(
                utils.loadBooleanSetting("isDetailedLogging", false));
        switchEnableDetailedLogging.setOnCheckedChangeListener(((buttonView, isChecked) ->
                utils.saveBooleanSetting("isDetailedLogging", isChecked)));

        switchEnableHomepageYiyan.setChecked(
                utils.loadBooleanSetting("enable_homepage_yiyan", true));
        switchEnableHomepageYiyan.setOnCheckedChangeListener((buttonView, isChecked) ->
                utils.saveBooleanSetting("enable_homepage_yiyan", isChecked));

        return view;
    }

    private void showToast(String message) {
        if (getContext() != null) {
            android.widget.Toast.makeText(getContext(), message, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        refreshSwitchStates();
    }

    private void refreshSwitchStates() {
        if (getContext() == null || getView() == null) return;
        boolean isLogServiceEnabled = LogServiceManager.isServiceEnabled(requireContext());
        MaterialSwitch switchEnableLogService = requireView().findViewById(R.id.switch_enable_log_service);
        if (switchEnableLogService != null) {
            switchEnableLogService.setChecked(isLogServiceEnabled);
        }
    }

    // =======================================================
    // 重写的关于页面逻辑 (全屏沉浸式 + 点击任意空白关闭)
    // =======================================================

    private void showAboutPage() {
        // 1. 初始化 View
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_about_custom, null);

        View backgroundView = dialogView.findViewById(R.id.about_background);
        NestedScrollView scrollView = dialogView.findViewById(R.id.about_scroll_view);
        LinearLayout contentContainer = dialogView.findViewById(R.id.about_content_container);

        View iconCard = dialogView.findViewById(R.id.about_icon_card);
        TextView titleView = dialogView.findViewById(R.id.about_title);
        TextView versionView = dialogView.findViewById(R.id.about_version);
        TextView descView = dialogView.findViewById(R.id.about_description);
        View btnContainerProject = dialogView.findViewById(R.id.btn_container_project_info);
        View btnContainerAuthor = dialogView.findViewById(R.id.btn_container_author_info);
        MaterialButton btnGithub = dialogView.findViewById(R.id.btn_github);
        MaterialButton btnCredits = dialogView.findViewById(R.id.btn_credits);
        MaterialButton btnAuthor = dialogView.findViewById(R.id.btn_qimian233_coolapk);
        MaterialButton btnCollaborator = dialogView.findViewById(R.id.btn_wasddestroy_coolapk);

        // 2. 设置数据
        versionView.setText(updateModuleStatus());
        String rawHtml = getString(R.string.about_description);
        String plainText = Html.fromHtml(rawHtml, Html.FROM_HTML_MODE_LEGACY).toString();

        btnGithub.setOnClickListener(v ->
                openExternalLink("https://github.com/qwqawa64/ZUX-ZTool",
                        false, ""));

        btnCredits.setOnClickListener(v ->
                openExternalLink("https://github.com/dantmnf/UnfuckZUI",
                false, ""));

        btnAuthor.setOnClickListener(v -> openExternalLink("http://www.coolapk.com/u/10099756",
                true, "com.coolapk.market")); // Qimian233

        btnCollaborator.setOnClickListener(v -> openExternalLink("http://www.coolapk.com/u/18634835",
                true, "com.coolapk.market")); // WASD_Destroy

        // 3. 创建 Dialog
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // 4. 【关键修改】设置点击关闭逻辑
        // 定义一个通用的关闭监听器
        View.OnClickListener dismissListener = v -> dialog.dismiss();

        // 给底层背景设置关闭
        backgroundView.setOnClickListener(dismissListener);
        // 给滚动视图层设置关闭 (因为 match_parent 会遮挡背景)
        scrollView.setOnClickListener(dismissListener);
        contentContainer.setOnClickListener(dismissListener);

        // 5. 显示 Dialog 并配置 Window 参数
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setDimAmount(0f);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // ============ 动画编排 ============

        long startDelay = 50;
        long charInterval = 25;
        long totalTypingTime = plainText.length() * charInterval;

        // 背景渐变动画
        backgroundView.setAlpha(0f);
        backgroundView.animate()
                .alpha(1.0f)
                .setDuration(300)
                .setStartDelay(0)
                .setInterpolator(new LinearInterpolator())
                .start();

        // 内容元素入场
        animateEntrance(iconCard, startDelay);
        animateEntrance(titleView, startDelay + 100);
        animateEntrance(versionView, startDelay + 200);

        // 打字机文字
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                        typeWriterAnimation(descView, plainText, charInterval),
                startDelay + 300
        );

        // 底部按钮最后浮现
        animateEntrance(btnContainerProject, startDelay + totalTypingTime + 200);
        animateEntrance(btnContainerAuthor, startDelay + totalTypingTime + 400);
    }

    private void animateEntrance(View view, long delay) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(60f);

        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(600)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
    }

    private void typeWriterAnimation(TextView textView, String text, long interval) {
        if (textView == null || text == null) return;
        final Handler handler = new Handler(Looper.getMainLooper());
        final int length = text.length();
        final StringBuilder builder = new StringBuilder();

        for (int i = 0; i < length; i++) {
            final int index = i;
            handler.postDelayed(() -> {
                if (!isAdded() || getContext() == null) return;
                builder.append(text.charAt(index));
                textView.setText(builder.toString());

                if (index % 2 == 0) {
                    textView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
            }, i * interval);
        }
    }

    // =======================================================

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

    private void performBackup() {
        String backupFileName = FileManager.generateBackupFileName();
        backupLauncher.launch(backupFileName);
    }

    private void openExternalLink(String link, boolean shouldDeterminePackage, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
            if (shouldDeterminePackage) {
                intent.setPackage(packageName);
            }
            startActivity(intent);
        } catch (Exception e) {
            showToast(getString(R.string.open_web_link_failed));
        }
    }

    private final ActivityResultLauncher<String[]> openDocumentLauncherForRestore =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    String content = FileManager.readConfigWithSAF(requireContext(), uri);
                    if (content != null) {
                        Log.d("SAF", "读取到的内容: " + content);
                        ModulePreferencesUtils.restoreConfig(requireContext(), content);
                        showToast(getString(R.string.config_restore_success));
                    } else {
                        Log.e("SAF", "文件读取失败或内容为空");
                    }
                }
            });

    private void handleLogServiceSwitch(boolean isEnabled) {
        if (isEnabled) {
            LogServiceManager.startLogService(requireContext());
            showToast(getString(R.string.log_service_started));
        } else {
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
