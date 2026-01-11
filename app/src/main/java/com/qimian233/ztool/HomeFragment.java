package com.qimian233.ztool;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.cardview.widget.CardView;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.qimian233.ztool.service.LogCollectorService;
import com.qimian233.ztool.utils.ConfigUpgrade;
import com.qimian233.ztool.hook.modules.SharedPreferencesTool.ModulePreferencesUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final String UPDATE_URL = "https://raw.githubusercontent.com/qwqawa64/ZUX-ZTool/refs/heads/master/UpdateCheck.json";
    private static final String PREF_NAME_UPDATE = "update_prefs";
    private static final String KEY_IGNORE_VERSION = "ignore_version_code";
    private boolean isUpdateCardExpanded = false;
    private CardView cardRequirements, cardModuleStatus, cardSystemInfo;
    private CardView cardUpdate;
    private TextView textUpdateVersion, textUpdateChangelog;
    private Button btnIgnoreUpdate, btnDoUpdate;
    private ImageButton restartButton;

    private TextView textModuleStatus, textModuleVersion, textRootSource, textFrameworkVersion;
    private TextView textDeviceModel, textAndroidVersion, textBuildVersion, textKernelVersion, textCurrentSlot, textRomRegion;
    private TextView textHint;

    // 环境检测状态
    private boolean isModuleActive = false;
    private boolean isRootAvailable = false;

    // Shell执行器
    private EnhancedShellExecutor shellExecutor;

    // 防止重复执行的标志
    private final AtomicBoolean isCheckingEnvironment = new AtomicBoolean(false);
    private final AtomicBoolean isUpdatingUI = new AtomicBoolean(false);

    // 环境状态监听器
    public interface EnvironmentStateListener {
        void onEnvironmentStateChanged(boolean environmentReady);
    }

    private EnvironmentStateListener environmentStateListener;
    private boolean lastEnvironmentState = false;

    // 缓存系统信息
    private String cachedKernelVersion = "";
    private String cachedRootSource = "";
    private String cachedFrameworkVersion = "";
    private String cachedCurrentSlot = "";
    private String cachedRomRegion = "";
    private long lastSystemInfoUpdate = 0;
    private static final long SYSTEM_INFO_CACHE_DURATION = 60000; // 1分钟缓存

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: 创建主页视图");

        // 初始化Shell执行器
        shellExecutor = EnhancedShellExecutor.getInstance();

        // 创建通知渠道
        LogCollectorService service = new LogCollectorService();
        service.createNotificationChannel();

        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated: 初始化UI组件");

        initViews(view);

        setupMenuProvider();

        // 延迟执行环境检测，避免界面卡顿
        view.postDelayed(() -> {
            checkEnvironment();
            updateUI();
            checkAppUpdate(); // [新增] 启动时检查更新
        }, 100);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // 设置环境状态监听器
        if (context instanceof EnvironmentStateListener) {
            environmentStateListener = (EnvironmentStateListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        environmentStateListener = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 清理Shell执行器资源
        if (shellExecutor != null) {
            shellExecutor.clearCache(); // 只清理缓存，不销毁单例
        }
        Log.d(TAG, "onDestroy: 销毁主页");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: 恢复主页");

        // 检查是否需要更新系统信息（如果缓存过期）
        if (System.currentTimeMillis() - lastSystemInfoUpdate > SYSTEM_INFO_CACHE_DURATION) {
            updateSystemInfoAsync();
        }
    }

    /**
     * 初始化UI组件
     */
    private void initViews(View view) {
        cardRequirements = view.findViewById(R.id.card_requirements);
        cardModuleStatus = view.findViewById(R.id.card_module_status);
        cardSystemInfo = view.findViewById(R.id.card_system_info);

        cardUpdate = view.findViewById(R.id.card_update);
        textUpdateVersion = view.findViewById(R.id.text_update_version);
        textUpdateChangelog = view.findViewById(R.id.text_update_changelog);
        btnIgnoreUpdate = view.findViewById(R.id.btn_ignore_update);
        btnDoUpdate = view.findViewById(R.id.btn_do_update);

        textModuleStatus = view.findViewById(R.id.text_module_status);
        textModuleVersion = view.findViewById(R.id.text_module_version);
        textRootSource = view.findViewById(R.id.text_root_source);
        textFrameworkVersion = view.findViewById(R.id.text_framework_version);

        textDeviceModel = view.findViewById(R.id.text_device_model);
        textAndroidVersion = view.findViewById(R.id.text_android_version);
        textBuildVersion = view.findViewById(R.id.text_build_version);
        textKernelVersion = view.findViewById(R.id.text_kernel_version);
        textCurrentSlot = view.findViewById(R.id.text_current_slot);
        textRomRegion = view.findViewById(R.id.text_rom_region);

        textHint = view.findViewById(R.id.text_hint);

        restartButton = view.findViewById(R.id.restart_button);

        Log.d(TAG, "initViews: UI组件初始化完成");
    }

    /**
     * [新增] 检查应用更新
     */
    private void checkAppUpdate() {
        new Thread(() -> {
            try {
                if (getContext() == null) return;

                // --- 获取本地版本号 ---
                int currentVersionCode;
                try {
                    PackageInfo pInfo = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        currentVersionCode = (int) pInfo.getLongVersionCode();
                    } else {
                        currentVersionCode = pInfo.versionCode;
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e("Update", "无法获取本地版本信息");
                    return;
                }
                java.net.URL url = new java.net.URL(UPDATE_URL);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);

                if (connection.getResponseCode() == 200) {
                    // 解析 JSON (假设 getJsonObject 是你封装好的方法)
                    org.json.JSONObject json = getJsonObject(connection);

                    int newVersionCode = json.getInt("newVersionCode");
                    String newVersionName = json.getString("newVersionName");
                    String whatNew = json.getString("whatNew"); // 更新日志
                    String downloadUrl = json.getString("url");
                    // 检查是否已忽略
                    SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME_UPDATE, Context.MODE_PRIVATE);
                    int ignoredVersion = prefs.getInt(KEY_IGNORE_VERSION, 0);
                    // --- 判断是否有新版本 ---
                    if (newVersionCode > currentVersionCode && newVersionCode != ignoredVersion) {
                        // 切换回主线程更新 UI
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() ->
                                    showUpdateCard(newVersionName, newVersionCode, whatNew, downloadUrl)
                            );
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("Update", "检查更新失败", e);
            }
        }).start();
    }

    /**
     * [修改后] 显示更新卡片：包含入场动画和点击展开逻辑
     */
    private void showUpdateCard(String versionName, int versionCode, String changelog, String downloadUrl) {
        if (!isAdded() || getContext() == null) return;
        textUpdateVersion.setText(String.format("%s (Build %d)", versionName, versionCode));

        textUpdateChangelog.setText(changelog);

        // --- 初始化状态 ---
        isUpdateCardExpanded = false;
        textUpdateChangelog.setMaxLines(4); // 初始折叠

        // 清除旧的点击监听和波纹
        cardUpdate.setClickable(false);
        cardUpdate.setForeground(null);
        cardUpdate.setOnClickListener(null);
        textUpdateChangelog.setOnClickListener(null); // 清除旧监听
        // --- 核心修复：检测文本是否真正被截断 ---
        textUpdateChangelog.post(() -> {
            if (textUpdateChangelog.getLayout() != null) {
                int lineCount = textUpdateChangelog.getLayout().getLineCount();
                if (lineCount > 0) {
                    // getEllipsisCount > 0 表示该行有省略号
                    if (textUpdateChangelog.getLayout().getEllipsisCount(lineCount - 1) > 0) {
                        enableCardExpandFeature();
                    }
                }
            }
        });
        // --- 按钮事件 ---
        btnDoUpdate.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "无法打开链接", Toast.LENGTH_SHORT).show();
            }
        });
        btnIgnoreUpdate.setOnClickListener(v -> dismissUpdateCardWithAnimation(versionCode));
        // --- 执行入场动画 ---
        animateCardEntrance();
    }

    /**
     * 辅助方法：启用点击展开功能
     */
    private void enableCardExpandFeature() {
        // 1. 设置点击波纹效果 (Ripple)
        cardUpdate.setClickable(true);
        android.util.TypedValue outValue = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        cardUpdate.setForeground(AppCompatResources.getDrawable(requireContext(), outValue.resourceId));
        // 2. 定义点击动作
        View.OnClickListener toggleListener = v -> {
            // 准备布局过渡动画
            AutoTransition transition = new AutoTransition();
            transition.setDuration(250);
            transition.setInterpolator(new DecelerateInterpolator());
            TransitionManager.beginDelayedTransition((android.view.ViewGroup) cardUpdate.getParent(), transition);
            isUpdateCardExpanded = !isUpdateCardExpanded;
            if (isUpdateCardExpanded) {
                textUpdateChangelog.setMaxLines(Integer.MAX_VALUE); // 展开
            } else {
                textUpdateChangelog.setMaxLines(4); // 折叠
            }
        };
        // 3. 绑定监听器 (Card 和 Text 都要绑定)
        cardUpdate.setOnClickListener(toggleListener);
        textUpdateChangelog.setOnClickListener(toggleListener);
    }

    /**
     * 卡片入场动画：使用 TransitionManager 让布局平滑撑开
     */
    private void animateCardEntrance() {
        if (cardUpdate.getVisibility() != View.VISIBLE) {
            ViewGroup parent = (ViewGroup) cardUpdate.getParent();

            // 使用 AutoTransition，它包含了淡入淡出、移动和改变大小
            AutoTransition transition = new AutoTransition();
            transition.setDuration(400);
            transition.setInterpolator(new DecelerateInterpolator());

            TransitionManager.beginDelayedTransition(parent, transition);
            cardUpdate.setVisibility(View.VISIBLE);
        }
    }
    /**
     * 忽略更新时的离场动画：先淡出，再收缩布局
     */
    private void dismissUpdateCardWithAnimation(int versionCode) {
        // 第一步：视觉上先淡出、上漂 (让用户感觉到它被扔掉了)
        cardUpdate.animate()
                .alpha(0f)
                .translationY(-50f)
                .setDuration(300)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // 动画结束后的操作
                        if (getContext() == null) return;

                        // 第二步：使用 TransitionManager 平滑收起空间
                        ViewGroup parent = (ViewGroup) cardUpdate.getParent();
                        AutoTransition transition = new AutoTransition();
                        transition.setDuration(300);
                        // 使用这种插值器，收起时的动作会更有弹性
                        transition.setInterpolator(new DecelerateInterpolator());

                        TransitionManager.beginDelayedTransition(parent, transition);

                        // 真正设置为 GONE，下方的 View 会平滑向上填补空缺
                        cardUpdate.setVisibility(View.GONE);

                        // 重置属性（为下次显示做准备）
                        cardUpdate.setAlpha(1f);
                        cardUpdate.setTranslationY(0f);

                        // 保存忽略设置
                        SharedPreferences prefs = requireContext().getSharedPreferences(PREF_NAME_UPDATE, Context.MODE_PRIVATE);
                        prefs.edit().putInt(KEY_IGNORE_VERSION, versionCode).apply();
                    }
                })
                .start();
    }



    /**
     * 检查模块激活状态
     */
    private boolean isModuleActive() {
        Log.d(TAG, "isModuleActive: 模块自检测方法被调用，默认返回false");
        return false;
    }

    /**
     * 检测环境状态 - 线程安全版本
     */
    private void checkEnvironment() {
        if (isCheckingEnvironment.getAndSet(true)) {
            Log.d(TAG, "环境检测已在执行，跳过本次检测");
            return;
        }

        try {
            Log.d(TAG, "开始检测环境状态...");

            // 检测模块激活状态
            isModuleActive = isModuleActive();

            // 使用ShellExecutor检测Root权限可用性
            EnhancedShellExecutor.ShellResult rootResult = shellExecutor.checkRootAccess();
            isRootAvailable = rootResult.isSuccess();

            Log.i(TAG, "环境检测结果 - 模块激活: " + isModuleActive + ", Root可用: " + isRootAvailable);

        } finally {
            isCheckingEnvironment.set(false);
        }
    }

    /**
     * 根据环境检测结果更新UI - 线程安全版本
     */
    private void updateUI() {

        ModulePreferencesUtils utils = new ModulePreferencesUtils(requireContext());

        if (isUpdatingUI.getAndSet(true)) {
            Log.d(TAG, "UI更新已在执行，跳过本次更新");
            return;
        }

        try {
            boolean environmentReady = isModuleActive && isRootAvailable;

            Log.d(TAG, "更新UI - 环境就绪: " + environmentReady +
                    " (模块激活: " + isModuleActive + ", Root可用: " + isRootAvailable + ")");

            // Reboot function depends on root permission only, it's not necessary to check module activation.
            if (isRootAvailable) {
                // Set reboot button to visible and set listener
                restartButton.setVisibility(View.VISIBLE);
                restartButton.setOnClickListener(v -> showRebootMenu());
            } else {
                // Hide restart button
                restartButton.setVisibility(View.GONE);
            }

            if (environmentReady) {
                // 环境完备，隐藏要求卡片，显示状态和系统信息
                cardRequirements.setVisibility(View.GONE);
                cardModuleStatus.setVisibility(View.VISIBLE);
                cardSystemInfo.setVisibility(View.VISIBLE);

                // 更新模块状态信息（避免阻塞UI线程）
                updateModuleStatusAsync();
                updateSystemInfoAsync();

                if (!utils.loadBooleanSetting("enable_homepage_yiyan", true)) {
                    // 如果用户不使用主界面一言，那么同样显示“环境就绪”
                    textHint.setText(getString(R.string.environment_ready));
                } else {
                    // 立即显示默认文本，确保界面快速渲染
                    textHint.setText(getString(R.string.environment_ready));
                    // 异步获取API内容
                    fetchHintFromAPI();
                }
                Log.i(TAG, "环境完备，显示完整功能界面");
                Log.i(TAG,"开始检查配置是否为最新版本");
                if(ConfigUpgrade.configUpgrader(requireContext())){
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.config_upgraded_tip_title)
                            .setMessage(R.string.config_upgraded_tip_message)
                            .setPositiveButton(R.string.restart_system_button, (dialog, which) -> shellExecutor.executeRootCommand("su -c reboot", 3))
                            .setNegativeButton(R.string.do_not_restart_system_button, (dialog, which) -> android.widget.Toast.makeText(getContext(), R.string.have_not_restart_warn, android.widget.Toast.LENGTH_SHORT).show())
                            .show();
                    Log.i(TAG,"配置升级成功");
                } else{
                    Log.i(TAG,"配置已是最新版本");
                }
            } else {
                // 环境不完整，只显示要求卡片
                cardRequirements.setVisibility(View.VISIBLE);
                cardModuleStatus.setVisibility(View.GONE);
                cardSystemInfo.setVisibility(View.GONE);
                // 环境不满足时也建议隐藏更新卡片，以免干扰
                if(cardUpdate != null) cardUpdate.setVisibility(View.GONE);

                // 更新提示信息，明确指出缺少什么
                StringBuilder hintBuilder = new StringBuilder(getString(R.string.missing_environment));
                if (!isModuleActive && !isRootAvailable) {
                    hintBuilder.append(getString(R.string.module_not_active));
                    hintBuilder.append(", ");
                    hintBuilder.append(getString(R.string.root_not_available));
                } else {
                    if (!isModuleActive) hintBuilder.append(getString(R.string.module_not_active));
                    if (!isRootAvailable) hintBuilder.append(getString(R.string.root_not_available));
                }
                textHint.setText(hintBuilder.toString());
                Log.w(TAG, "环境不完整: " + hintBuilder);
            }

            // 通知Activity环境状态变化
            if (environmentStateListener != null && environmentReady != lastEnvironmentState) {
                environmentStateListener.onEnvironmentStateChanged(environmentReady);
                lastEnvironmentState = environmentReady;
            }
        } finally {
            isUpdatingUI.set(false);
        }
    }

    /**
     * 异步更新模块状态信息
     */
    private void updateModuleStatusAsync() {
        new Thread(() -> {
            try {
                updateModuleStatus();
            } catch (Exception e) {
                Log.e(TAG, "异步更新模块状态失败: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 异步更新系统信息
     */
    private void updateSystemInfoAsync() {
        new Thread(() -> {
            try {
                updateSystemInfo();
            } catch (Exception e) {
                Log.e(TAG, "异步更新系统信息失败: " + e.getMessage());
            }
        }).start();
    }

    private void fetchHintFromAPI() {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.xygeng.cn/one");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    JSONObject jsonResponse = getJsonObject(connection);
                    if (jsonResponse.getInt("code") == 200) {
                        JSONObject data = jsonResponse.getJSONObject("data");
                        String content = data.getString("content");
                        String origin = data.getString("origin");

                        if (isAdded() && isModuleActive && isRootAvailable) {
                            textHint.post(() -> {
                                textHint.setText(String.format(getString(R.string.homepage_yiyan),
                                        content,
                                        origin));
                                Log.i(TAG, "成功从API获取提示文本: " + content);
                            });
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "获取API提示失败: " + e.getMessage());
            }
        }).start();
    }

    @NonNull
    private static JSONObject getJsonObject(HttpURLConnection connection) throws IOException, JSONException {
        InputStream inputStream = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return new JSONObject(response.toString());
    }

    /**
     * 更新模块状态信息
     */
    private void updateModuleStatus() {
        try {
            updateModuleVersionInfo();

            if (cachedRootSource.isEmpty() ||
                    System.currentTimeMillis() - lastSystemInfoUpdate > SYSTEM_INFO_CACHE_DURATION) {
                cachedRootSource = detectRootSource();
            }
            textRootSource.post(() -> textRootSource.setText(getString(R.string.root_manager_prefix, cachedRootSource)));

            if (cachedFrameworkVersion.isEmpty() ||
                    System.currentTimeMillis() - lastSystemInfoUpdate > SYSTEM_INFO_CACHE_DURATION) {
                cachedFrameworkVersion = detectFrameworkVersionAndMode();
            }
            textFrameworkVersion.post(() -> textFrameworkVersion.setText(getString(R.string.xp_framework_prefix, cachedFrameworkVersion)));

            textModuleStatus.post(() -> {
                if (isModuleActive) {
                    textModuleStatus.setText(getString(R.string.module_active));
                } else {
                    textModuleStatus.setText(getString(R.string.module_inactive));
                    textModuleStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "更新模块状态失败: " + e.getMessage());
        }
    }

    /**
     * 更新模块版本信息
     */
    private void updateModuleVersionInfo() {
        try {
            Activity activity = getActivity();
            if (activity != null) {
                PackageInfo packageInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                String versionName = packageInfo.versionName;
                int versionCode = packageInfo.versionCode;
                String moduleVersion = versionName + " (" + versionCode + ")";
                textModuleVersion.post(() -> textModuleVersion.setText(getString(R.string.module_version_prefix, moduleVersion)));
            } else {
                textModuleVersion.post(() -> textModuleVersion.setText(getString(R.string.module_version_unknown_activity)));
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "获取模块版本信息失败: " + e.getMessage());
            textModuleVersion.post(() -> textModuleVersion.setText(getString(R.string.module_version_unknown)));
        }
    }

    /**
     * 检测Root来源
     */
    private String detectRootSource() {
        try {
            String[] detectionCommands = {
                    "magisk -v",           // Magisk
                    "su -v",               // KernelSU
                    "apd -v"               // APatch
            };

            for (String cmd : detectionCommands) {
                EnhancedShellExecutor.ShellResult result = shellExecutor.executeRootCommand(cmd, 3);
                if (result.isSuccess() && result.output != null && !result.output.isEmpty()) {
                    String output = result.output.trim();
                    if (cmd.contains("magisk")) {
                        return getString(R.string.magisk_su_format, output);
                    } else if (cmd.contains("su -v") && output.contains("KernelSU")) {
                        int endPosition = output.indexOf("KernelSU");
                        return getString(R.string.kernelsu_format, output.substring(0, endPosition - 1));
                    } else if (cmd.contains("apd")) {
                        return getString(R.string.apatch_format, output);
                    }
                }
            }

        } catch (Exception e) {
            Log.w(TAG, "检测Root来源失败: " + e.getMessage());
        }
        return getString(R.string.unknown_root_available);
    }

    /**
     * 检测LSPosed框架版本和工作模式
     */
    private String detectFrameworkVersionAndMode() {
        try {
            EnhancedShellExecutor.ShellResult propResult = shellExecutor.executeRootCommand("getprop ro.lsposed.version", 3);
            if (propResult.isSuccess() && propResult.output != null && !propResult.output.trim().isEmpty()) {
                String version = "v" + propResult.output.trim();
                return getString(R.string.lsposed_standard_format, version);
            }
        } catch (Exception e) {
            Log.w(TAG, "系统属性检测失败: " + e.getMessage());
        }

        try {
            EnhancedShellExecutor.ShellResult lsResult = shellExecutor.executeRootCommand("ls -la /data/adb/modules/ | grep -i lsposed", 3);
            if (lsResult.isSuccess() && lsResult.output != null && !lsResult.output.trim().isEmpty()) {
                return getString(R.string.lsposed_zygisk);
            }
        } catch (Exception e) {
            Log.w(TAG, "目录检测失败: " + e.getMessage());
        }

        return getString(R.string.unknown_framework);
    }

    /**
     * 更新系统信息
     */
    private void updateSystemInfo() {
        try {
            String deviceModel = Build.MODEL;
            textDeviceModel.post(() -> textDeviceModel.setText(deviceModel.isEmpty() ? getString(R.string.unknown) : deviceModel));

            String androidVersion = Build.VERSION.RELEASE;
            textAndroidVersion.post(() -> textAndroidVersion.setText(androidVersion.isEmpty() ? getString(R.string.unknown) : getString(R.string.android_version_prefix, androidVersion)));

            String buildVersion = Build.DISPLAY;
            textBuildVersion.post(() -> textBuildVersion.setText(buildVersion.isEmpty() ? getString(R.string.unknown) : buildVersion));

            if (cachedKernelVersion.isEmpty() ||
                    System.currentTimeMillis() - lastSystemInfoUpdate > SYSTEM_INFO_CACHE_DURATION) {
                cachedKernelVersion = getKernelVersion();
            }
            textKernelVersion.post(() -> textKernelVersion.setText(cachedKernelVersion.isEmpty() ? getString(R.string.unknown) : cachedKernelVersion));

            if (cachedCurrentSlot.isEmpty() || System.currentTimeMillis() - lastSystemInfoUpdate > SYSTEM_INFO_CACHE_DURATION) {
                cachedCurrentSlot = getCurrentBootSlot();
            }
            textCurrentSlot.post(() -> textCurrentSlot.setText(cachedCurrentSlot.isEmpty() ? getString(R.string.unknown) : cachedCurrentSlot));

            if (cachedRomRegion.isEmpty() || System.currentTimeMillis() - lastSystemInfoUpdate > SYSTEM_INFO_CACHE_DURATION) {
                cachedRomRegion = getRomRegion();
                ModulePreferencesUtils utils = new ModulePreferencesUtils(getActivity());
                utils.saveStringSetting("RomRegion", cachedRomRegion);
                // 存储到SharedPreferences中，方便其他模块调用
                // 模块开关改变必然需要访问对应设置页面，那么就一定会访问主界面触发本更新逻辑
                // 因此将存储逻辑放置在这里是可以接受的
                // 如果用户非常喜欢创建快捷方式等APP，那和我的issue被关闭说去吧
            }
            textRomRegion.post(() -> textRomRegion.setText(cachedRomRegion.isEmpty() ? getString(R.string.unknown) : cachedRomRegion));

            lastSystemInfoUpdate = System.currentTimeMillis();

        } catch (Exception e) {
            Log.e(TAG, "更新系统信息失败: " + e.getMessage());
        }
    }

    private String getKernelVersion() {
        EnhancedShellExecutor.ShellResult result = shellExecutor.executeRootCommand("uname -r", 3);
        if (result.isSuccess() && result.output != null && !result.output.isEmpty()) {
            return result.output.trim();
        }
        return "";
    }

    private String getCurrentBootSlot() {
        EnhancedShellExecutor.ShellResult result = shellExecutor.executeRootCommand("getprop ro.boot.slot_suffix", 3);
        if (result.isSuccess() && result.output != null && !result.output.isEmpty()) {
            if (result.output.trim().equals("_a")) {
                return getString(R.string.slot_a);
            } else if (result.output.trim().equals("_b")) {
                return getString(R.string.slot_b);
            } else return getString(R.string.unknown);
        }
        return getString(R.string.unknown);
    }

    private String getRomRegion() {
        EnhancedShellExecutor.ShellResult result;
        // 依次尝试不同的prop，直到找到不为空的prop
        try {
            // 先尝试最正常的prop，这个时候其实应该已经可以读取到内容了
            result = shellExecutor.executeRootCommand("getprop ro.boot.region", 3);
            // 现在依次尝试几个不太常见的prop，如果还不行就返回unknown
            if (result.output.trim().isEmpty()) {
                result = shellExecutor.executeRootCommand("getprop ro.config.zui.region", 3);
            }
            if (result.output.trim().isEmpty()) {
                result = shellExecutor.executeRootCommand("getprop ro.vendor.config.zui.region", 3);
            }
            // 返回
            if (result.output.trim().isEmpty()) {
                return getString(R.string.unknown);
            } else {
                return result.output.trim();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch ROM region: " + e.getMessage());
            return getString(R.string.unknown);
        }
    }

    private void showRebootMenu() {
        android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(requireContext(), restartButton);
        popupMenu.getMenuInflater().inflate(R.menu.reboot_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(this::handleMenuItemClick);
        popupMenu.show();
    }

    private void setupMenuProvider() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {}

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                return handleMenuItemClick(menuItem);
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private boolean handleMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.menu_soft_reboot) {
            showSoftRebootConfirmation();
            return true;
        } else if (itemId == R.id.menu_bootloader) {
            showRebootConfirmation("bootloader");
            return true;
        } else if (itemId == R.id.menu_recovery) {
            showRebootConfirmation("recovery");
            return true;
        } else if (itemId == R.id.menu_edl) {
            showRebootConfirmation("edl");
            return true;
        } else if (itemId == R.id.menu_reboot) {
            showRebootConfirmation("system");
            return true;
        }
        return false;
    }

    private void showSoftRebootConfirmation() {
        // 检查Android版本，软重启在Android 15已废弃
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Toast.makeText(requireContext(), getString(R.string.soft_reboot_not_supported), Toast.LENGTH_LONG).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.reboot_confirm_title))
                .setMessage(getString(R.string.soft_reboot_confirm_message))
                .setPositiveButton(getString(R.string.confirm), (dialog, which) -> executeReboot("userspace"))
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    // Use parameter "rebootTarget" to specify the target for reboot, e.g., "recovery", "bootloader
    private void showRebootConfirmation(String rebootTarget) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        switch (rebootTarget) {
            case "bootloader":
                builder.setMessage(getString(R.string.bootloader_confirm_message));
                break;
            case "recovery":
                builder.setMessage(getString(R.string.recovery_confirm_message));
                break;
            case "edl":
                builder.setMessage(getString(R.string.edl_confirm_message));
                break;
            default:
                builder.setMessage(getString(R.string.reboot_confirm_message));
                break;
        }
        builder.setTitle(getString(R.string.reboot_confirm_title))
                .setPositiveButton(getString(R.string.confirm), (dialog, which) -> executeReboot(rebootTarget))
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void executeReboot(String rebootTarget) {
        // argument "rebootTarget" is reused to build reboot command in this switch-case block
        switch (rebootTarget) {
            case "bootloader":
                rebootTarget = "reboot bootloader";
                break;
            case "recovery":
                rebootTarget = "reboot recovery";
                break;
            case "edl":
                rebootTarget = "reboot edl";
                break;
            case "userspace":
                rebootTarget = "reboot userspace";
                break;
            default:
                rebootTarget = "reboot";
        }

        EnhancedShellExecutor.ShellResult result = EnhancedShellExecutor.getInstance()
                .executeRootCommand(rebootTarget, 5);

        if (result.isSuccess()) {
            Toast.makeText(requireContext(), getString(R.string.reboot_success), Toast.LENGTH_SHORT).show();
        } else {
            String errorMsg = String.format(getString(R.string.reboot_failed), result.error);
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
        }
    }
}
