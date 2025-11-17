package com.qimian233.ztool;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    // UI组件
    private CardView cardRequirements, cardModuleStatus, cardSystemInfo;
    private TextView textModuleStatus, textModuleVersion, textRootSource, textFrameworkVersion;
    private TextView textDeviceModel, textAndroidVersion, textBuildVersion, textKernelVersion;
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
    private long lastSystemInfoUpdate = 0;
    private static final long SYSTEM_INFO_CACHE_DURATION = 60000; // 1分钟缓存

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: 创建主页视图");

        // 初始化Shell执行器
        shellExecutor = EnhancedShellExecutor.getInstance();

        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated: 初始化UI组件");

        initViews(view);

        // 延迟执行环境检测，避免界面卡顿
        view.postDelayed(() -> {
            checkEnvironment();
            updateUI();
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

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: 暂停主页");
    }

    /**
     * 初始化UI组件
     */
    private void initViews(View view) {
        cardRequirements = view.findViewById(R.id.card_requirements);
        cardModuleStatus = view.findViewById(R.id.card_module_status);
        cardSystemInfo = view.findViewById(R.id.card_system_info);

        textModuleStatus = view.findViewById(R.id.text_module_status);
        textModuleVersion = view.findViewById(R.id.text_module_version);
        textRootSource = view.findViewById(R.id.text_root_source);
        textFrameworkVersion = view.findViewById(R.id.text_framework_version);

        textDeviceModel = view.findViewById(R.id.text_device_model);
        textAndroidVersion = view.findViewById(R.id.text_android_version);
        textBuildVersion = view.findViewById(R.id.text_build_version);
        textKernelVersion = view.findViewById(R.id.text_kernel_version);

        textHint = view.findViewById(R.id.text_hint);

        Log.d(TAG, "initViews: UI组件初始化完成");
    }

    /**
     * 检查模块激活状态
     * 这个方法会被Xposed Hook，如果模块激活则返回true
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

            // 检测模块激活状态（不涉及Shell命令）
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
        if (isUpdatingUI.getAndSet(true)) {
            Log.d(TAG, "UI更新已在执行，跳过本次更新");
            return;
        }

        try {
            boolean environmentReady = isModuleActive && isRootAvailable;

            Log.d(TAG, "更新UI - 环境就绪: " + environmentReady +
                    " (模块激活: " + isModuleActive + ", Root可用: " + isRootAvailable + ")");

            if (environmentReady) {
                // 环境完备，隐藏要求卡片，显示状态和系统信息
                cardRequirements.setVisibility(View.GONE);
                cardModuleStatus.setVisibility(View.VISIBLE);
                cardSystemInfo.setVisibility(View.VISIBLE);

                // 更新模块状态信息（避免阻塞UI线程）
                updateModuleStatusAsync();
                updateSystemInfoAsync();

                // 立即显示默认文本，确保界面快速渲染
                textHint.setText("环境就绪");

                // 异步获取API内容
                fetchHintFromAPI();
                Log.i(TAG, "环境完备，显示完整功能界面");
            } else {
                // 环境不完整，只显示要求卡片
                cardRequirements.setVisibility(View.VISIBLE);
                cardModuleStatus.setVisibility(View.GONE);
                cardSystemInfo.setVisibility(View.GONE);

                // 更新提示信息，明确指出缺少什么
                StringBuilder hintBuilder = new StringBuilder("缺少必要环境: ");
                if (!isModuleActive) hintBuilder.append("模块未激活 ");
                if (!isRootAvailable) hintBuilder.append("Root权限不可用");

                textHint.setText(hintBuilder.toString());
                Log.w(TAG, "环境不完整: " + hintBuilder);
            }

            // 通知Activity环境状态变化（只有当状态变化时才回调）
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
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    if (jsonResponse.getInt("code") == 200) {
                        JSONObject data = jsonResponse.getJSONObject("data");
                        String content = data.getString("content");
                        String origin = data.getString("origin");

                        // 检查当前环境是否仍然就绪且Fragment仍处于活动状态
                        if (isAdded() && isModuleActive && isRootAvailable) {
                            // 使用View.post()更新UI
                            textHint.post(() -> {
                                textHint.setText(content + " - " + origin);
                                Log.i(TAG, "成功从API获取提示文本: " + content);
                            });
                        } else {
                            Log.i(TAG, "环境状态已改变或Fragment已销毁，不再更新API提示");
                        }
                        return;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "获取API提示失败: " + e.getMessage());
            }

            // 如果API请求失败，并且当前环境仍然就绪，则显示默认提示
            if (isAdded() && isModuleActive && isRootAvailable) {
                textHint.post(() -> {
                    Log.w(TAG, "API请求失败，保持默认提示文本");
                });
            }
        }).start();
    }

    /**
     * 更新模块状态信息
     */
    private void updateModuleStatus() {
        try {
            // 更新模块版本信息（不涉及Shell命令）
            updateModuleVersionInfo();

            // Root来源信息（使用缓存）
            if (cachedRootSource.isEmpty() ||
                    System.currentTimeMillis() - lastSystemInfoUpdate > SYSTEM_INFO_CACHE_DURATION) {
                cachedRootSource = detectRootSource();
            }
            textRootSource.post(() -> textRootSource.setText("Root管理器: " + cachedRootSource));

            // 框架版本信息（使用缓存）
            if (cachedFrameworkVersion.isEmpty() ||
                    System.currentTimeMillis() - lastSystemInfoUpdate > SYSTEM_INFO_CACHE_DURATION) {
                cachedFrameworkVersion = detectFrameworkVersionAndMode();
            }
            textFrameworkVersion.post(() -> textFrameworkVersion.setText("XP框架: " + cachedFrameworkVersion));

            // 更新模块状态显示
            textModuleStatus.post(() -> {
                if (isModuleActive) {
                    textModuleStatus.setText("模块已激活");
                } else {
                    textModuleStatus.setText("模块未激活");
                    textModuleStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
                }
            });

            Log.i(TAG, "模块状态更新完成");

        } catch (Exception e) {
            Log.e(TAG, "更新模块状态失败: " + e.getMessage());
        }
    }

    /**
     * 更新模块版本信息（不涉及Shell命令）
     */
    private void updateModuleVersionInfo() {
        try {
            Activity activity = getActivity();
            if (activity != null) {
                PackageInfo packageInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                String versionName = packageInfo.versionName;
                int versionCode = packageInfo.versionCode;
                String moduleVersion = versionName + " (" + versionCode + ")";
                textModuleVersion.post(() -> textModuleVersion.setText("模块版本：" + moduleVersion));
            } else {
                textModuleVersion.post(() -> textModuleVersion.setText("模块版本: 未知 (Activity 为空)"));
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            textModuleVersion.post(() -> textModuleVersion.setText("模块版本: 未知"));
        }
    }

    /**
     * 检测Root来源（仅用于信息显示）- 简化版本
     */
    private String detectRootSource() {
        try {
            int endPosition; // 存储版本号结束位置（事实上应该是结束位置+1）
            Log.d(TAG, "开始检测Root来源...");

            // 简化检测逻辑，只检查最可能的情况
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
                        return "MagiskSU (" + output + ")";
                    } else if (cmd.contains("su -v") && output.contains("KernelSU")) {
                        endPosition = output.indexOf("KernelSU");
                        return "KernelSU (" + output.substring(0, endPosition - 1) + ")";
                    } else if (cmd.contains("apd")) {
                        return "APatch (" + output + ")";
                    }
                }
            }

        } catch (Exception e) {
            Log.w(TAG, "检测Root来源失败: " + e.getMessage());
        }

        Log.w(TAG, "未检测到明确的Root来源");
        return "Unknown (但Root可用)";
    }

    /**
     * 检测LSPosed框架版本和工作模式 - 简化版本
     */
    private String detectFrameworkVersionAndMode() {
        Log.i(TAG, "开始检测LSPosed框架版本和工作模式");

        // 方法1: 系统属性检测（最快）
        try {
            EnhancedShellExecutor.ShellResult propResult = shellExecutor.executeRootCommand("getprop ro.lsposed.version", 3);
            if (propResult.isSuccess() && propResult.output != null && !propResult.output.trim().isEmpty()) {
                String version = "v" + propResult.output.trim();
                Log.i(TAG, "通过系统属性检测到LSPosed版本: " + version);
                return "LSPosed " + version + " (Standard)";
            }
        } catch (Exception e) {
            Log.w(TAG, "系统属性检测失败: " + e.getMessage());
        }

        // 方法2: 简化目录检测
        try {
            EnhancedShellExecutor.ShellResult lsResult = shellExecutor.executeRootCommand("ls -la /data/adb/modules/ | grep -i lsposed", 3);
            if (lsResult.isSuccess() && lsResult.output != null && !lsResult.output.trim().isEmpty()) {
                return "LSPosed (Zygisk)"; // 简化返回
            }
        } catch (Exception e) {
            Log.w(TAG, "目录检测失败: " + e.getMessage());
        }

        Log.w(TAG, "未检测到LSPosed框架");
        return "Unknown";
    }

    /**
     * 更新系统信息
     */
    private void updateSystemInfo() {
        try {
            // 设备型号（不涉及Shell命令）
            String deviceModel = Build.MODEL;
            textDeviceModel.post(() -> textDeviceModel.setText(deviceModel.isEmpty() ? "Unknown" : deviceModel));

            // Android版本（不涉及Shell命令）
            String androidVersion = Build.VERSION.RELEASE;
            textAndroidVersion.post(() -> textAndroidVersion.setText(androidVersion.isEmpty() ? "Unknown" : "Android " + androidVersion));

            // 构建版本（不涉及Shell命令）
            String buildVersion = Build.DISPLAY;
            textBuildVersion.post(() -> textBuildVersion.setText(buildVersion.isEmpty() ? "Unknown" : buildVersion));

            // 内核版本（使用缓存）
            if (cachedKernelVersion.isEmpty() ||
                    System.currentTimeMillis() - lastSystemInfoUpdate > SYSTEM_INFO_CACHE_DURATION) {
                cachedKernelVersion = getKernelVersion();
            }
            textKernelVersion.post(() -> textKernelVersion.setText(cachedKernelVersion.isEmpty() ? "Unknown" : cachedKernelVersion));

            // 更新缓存时间
            lastSystemInfoUpdate = System.currentTimeMillis();

            Log.i(TAG, "系统信息更新完成");

        } catch (Exception e) {
            Log.e(TAG, "更新系统信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取内核版本 - 使用ShellExecutor重构
     */
    private String getKernelVersion() {
        Log.d(TAG, "开始获取内核版本");

        // 方法1: 使用uname命令（最可靠）
        EnhancedShellExecutor.ShellResult result = shellExecutor.executeRootCommand("uname -r", 3);
        if (result.isSuccess() && result.output != null && !result.output.isEmpty()) {
            Log.i(TAG, "通过uname获取内核版本: " + result.output);
            return result.output.trim();
        }

        return "";
    }
}
