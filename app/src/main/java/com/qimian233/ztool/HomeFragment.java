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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

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

    // 环境状态监听器
    public interface EnvironmentStateListener {
        void onEnvironmentStateChanged(boolean environmentReady);
    }

    private EnvironmentStateListener environmentStateListener;
    private boolean lastEnvironmentState = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: 创建主页视图");
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated: 初始化UI组件");

        initViews(view);
        checkEnvironment();
        updateUI();
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
     * 检测Root权限可用性 - 直接执行su命令测试
     */
    private boolean checkRootAccess() {
        Log.d(TAG, "开始检测Root权限可用性...");

        Process process = null;
        BufferedReader reader = null;

        try {
            // 尝试执行su命令并获取root权限
            process = Runtime.getRuntime().exec("su -c id");
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // 读取命令输出
            String line = reader.readLine();
            int exitCode = process.waitFor();

            Log.d(TAG, "su命令执行结果 - 输出: " + line + ", 退出码: " + exitCode);

            // 检查是否成功获取root权限 (uid=0表示root)
            if (line != null && line.contains("uid=0")) {
                Log.i(TAG, "Root权限检测成功: 已获取root权限");
                return true;
            }

            // 如果上面的方法失败，尝试另一种检测方式
            process = Runtime.getRuntime().exec("su -c echo 'root_test'");
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            process.waitFor();

            // 检查进程是否正常结束
            if (process.exitValue() == 0) {
                Log.i(TAG, "Root权限检测成功: su命令执行成功");
                return true;
            }

        } catch (Exception e) {
            Log.w(TAG, "Root权限检测失败: " + e.getMessage());
        } finally {
            // 清理资源
            try {
                if (reader != null) reader.close();
                if (process != null) process.destroy();
            } catch (Exception e) {
                Log.w(TAG, "清理资源时出错: " + e.getMessage());
            }
        }

        Log.w(TAG, "Root权限检测失败: 无法获取root权限");
        return false;
    }

    /**
     * 检测环境状态
     */
    private void checkEnvironment() {
        Log.d(TAG, "开始检测环境状态...");

        // 检测模块激活状态
        isModuleActive = isModuleActive();

        // 检测Root权限可用性
        isRootAvailable = checkRootAccess();

        Log.i(TAG, "环境检测结果 - 模块激活: " + isModuleActive + ", Root可用: " + isRootAvailable);
    }

    /**
     * 根据环境检测结果更新UI
     */
    private void updateUI() {
        boolean environmentReady = isModuleActive && isRootAvailable;

        Log.d(TAG, "更新UI - 环境就绪: " + environmentReady +
                " (模块激活: " + isModuleActive + ", Root可用: " + isRootAvailable + ")");

        if (environmentReady) {
            // 环境完备，隐藏要求卡片，显示状态和系统信息
            cardRequirements.setVisibility(View.GONE);
            cardModuleStatus.setVisibility(View.VISIBLE);
            cardSystemInfo.setVisibility(View.VISIBLE);

            // 更新模块状态信息
            updateModuleStatus();
            updateSystemInfo();

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

                    // 解析JSON响应
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    if (jsonResponse.getInt("code") == 200) {
                        JSONObject data = jsonResponse.getJSONObject("data");
                        String content = data.getString("content");
                        String origin = data.getString("origin");

                        // 检查当前环境是否仍然就绪
                        if (isModuleActive && isRootAvailable) {
                            // 使用View.post()更新UI
                            textHint.post(() -> {
                                textHint.setText(content + " - " + origin);
                                Log.i(TAG, "成功从API获取提示文本: " + content);
                            });
                        } else {
                            Log.i(TAG, "环境状态已改变，不再更新API提示");
                        }
                        return;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "获取API提示失败: " + e.getMessage());
            }

            // 如果API请求失败，并且当前环境仍然就绪，则显示默认提示
            if (isModuleActive && isRootAvailable) {
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
            try {
                Activity activity = getActivity();
                if (activity != null) {
                    PackageInfo packageInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                    String versionName = packageInfo.versionName;
                    int versionCode = packageInfo.versionCode;
                    String moduleVersion = versionName + " (" + versionCode + ")";
                    textModuleVersion.setText("模块版本：" + moduleVersion);
                } else {
                    textModuleVersion.setText("模块版本: 未知 (Activity 为空)");
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                textModuleVersion.setText("模块版本: 未知");
            }

            // Root来源信息
            String rootSource = detectRootSource();
            textRootSource.setText("Root管理器: " + rootSource);

            // 框架版本信息
            String frameworkVersion = detectFrameworkVersionAndMode();
            textFrameworkVersion.setText("XP框架: " + frameworkVersion);

            // 更新模块状态显示
            if (isModuleActive) {
                textModuleStatus.setText("模块已激活");
            } else {
                textModuleStatus.setText("模块未激活");
                textModuleStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
            }

            Log.i(TAG, "模块状态更新完成");

        } catch (Exception e) {
            Log.e(TAG, "更新模块状态失败: " + e.getMessage());
        }
    }

    /**
     * 检测Root来源（仅用于信息显示）
     */
    private String detectRootSource() {
        try {
            Log.d(TAG, "开始检测Root来源...");

            // 检查Magisk - 尝试多个可能路径
            String[] magiskPaths = {
                    "magisk",
                    "/system/bin/magisk",
                    "/system/xbin/magisk",
                    "/sbin/magisk",
                    "/data/adb/magisk/magisk"
            };

            for (String magiskPath : magiskPaths) {
                try {
                    Process process = Runtime.getRuntime().exec("su -c " + magiskPath + " -v");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String version = reader.readLine();
                    int exitCode = process.waitFor();

                    if (exitCode == 0 && version != null && !version.isEmpty()) {
                        Log.i(TAG, "检测到Magisk: " + version);
                        return "MagiskSU (" + version + ")";
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Magisk检测路径 " + magiskPath + " 失败: " + e.getMessage());
                }
            }

            // 检查KernelSU - 使用[ ]语法
            try {
                Process process = Runtime.getRuntime().exec("su -c su -v");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String version = reader.readLine();
                int exitCode = process.waitFor();

                if (exitCode == 0 && version != null && version.contains("KernelSU")) {
                    Log.i(TAG, "检测到KernelSU: " + version);
                    int endPosition = version.indexOf("KernelSU");
                    return "KernelSU (" + version.substring(0,endPosition-1) + ")";
                }
            } catch (Exception e) {
                Log.d(TAG, "KernelSU检测失败: " + e.getMessage());
            }

            // 检查APatch
            try {
                Process process = Runtime.getRuntime().exec("su -c apd -v");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String version = reader.readLine();
                int exitCode = process.waitFor();

                if (exitCode == 0 && version != null && !version.isEmpty()) {
                    Log.i(TAG, "检测到APatch: " + version);
                    return "APatch (" + version + ")";
                }
            } catch (Exception e) {
                Log.d(TAG, "APatch检测失败: " + e.getMessage());
            }

            // 通用su检测
            try {
                Process process = Runtime.getRuntime().exec("su -c su -v");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String version = reader.readLine();
                int exitCode = process.waitFor();

                if (exitCode == 0 && version != null && !version.isEmpty()) {
                    Log.i(TAG, "检测到通用SU: " + version);
                    return "GenericSU (" + version + ")";
                }
            } catch (Exception e) {
                Log.d(TAG, "通用SU检测失败: " + e.getMessage());
            }

        } catch (Exception e) {
            Log.w(TAG, "检测Root来源失败: " + e.getMessage());
        }

        Log.w(TAG, "未检测到明确的Root来源");
        return "Unknown (但Root可用)";
    }

    /**
     * 检测LSPosed框架版本和工作模式（用于信息显示）
     * 返回格式："LSPosed v1.9.2-it (Zygisk, Enabled)" 或 "Unknown"
     */
    private String detectFrameworkVersionAndMode() {
        String version = "Unknown";
        String mode = "Unknown";
        String frameworkName = "LSPosed";

        Log.i(TAG, "开始检测LSPosed框架版本和工作模式");

        try {
            // 首先尝试通过系统属性检测版本（兼容旧版）
            Log.d(TAG, "尝试通过系统属性 ro.lsposed.version 检测版本");
            Process propProcess = Runtime.getRuntime().exec("su -c getprop ro.lsposed.version");
            BufferedReader propReader = new BufferedReader(new InputStreamReader(propProcess.getInputStream()));
            String propVersion = propReader.readLine();
            int propExitCode = propProcess.waitFor();

            if (propExitCode == 0 && propVersion != null && !propVersion.trim().isEmpty()) {
                version = "v" + propVersion.trim();
                frameworkName = "LSPosed";
                mode = "Standard";
                Log.i(TAG, "通过系统属性检测到版本: " + version + ", 框架: " + frameworkName);
            } else {
                Log.d(TAG, "系统属性 ro.lsposed.version 未找到或为空");
            }
        } catch (Exception e) {
            Log.w(TAG, "通过系统属性检测版本失败: " + e.getMessage());
        }

        // 如果版本未知或想获取更详细模式，尝试从模块目录检测
        if (version.equals("Unknown")) {
            Log.d(TAG, "系统属性检测失败，尝试通过模块目录检测");
            try {
                // 查找LSPosed模块目录 - 使用更健壮的方法
                Log.d(TAG, "扫描 /data/adb/modules 目录查找LSPosed模块");
                Process lsProcess = Runtime.getRuntime().exec("su -c ls /data/adb/modules");
                BufferedReader lsReader = new BufferedReader(new InputStreamReader(lsProcess.getInputStream()));
                String line;
                String moduleDir = null;

                // 记录所有发现的目录用于调试
                StringBuilder dirs = new StringBuilder();
                while ((line = lsReader.readLine()) != null) {
                    dirs.append(line).append(", ");
                    Log.d(TAG, "发现模块目录: " + line);

                    // 使用不区分大小写的匹配，支持多种变体
                    if (line.toLowerCase().contains("lsposed") ||
                            line.toLowerCase().contains("zygisk_lsposed") ||
                            line.toLowerCase().contains("lsposed_zygisk")) {
                        moduleDir = line;
                        Log.i(TAG, "找到可能的LSPosed模块目录: " + moduleDir);
                        break;
                    }
                }
                int lsExitCode = lsProcess.waitFor();

                Log.d(TAG, "模块目录扫描完成，退出码: " + lsExitCode + ", 发现目录: " + dirs.toString());

                if (moduleDir == null) {
                    Log.w(TAG, "未在 /data/adb/modules 中找到LSPosed相关目录");
                    // 尝试直接访问已知目录
                    String[] knownDirs = {"zygisk_lsposed", "lsposed", "lsposed_zygisk"};
                    for (String knownDir : knownDirs) {
                        try {
                            Process testProcess = Runtime.getRuntime().exec("su -c test -d /data/adb/modules/" + knownDir);
                            int testExitCode = testProcess.waitFor();
                            if (testExitCode == 0) {
                                moduleDir = knownDir;
                                Log.i(TAG, "通过已知目录检测到: " + moduleDir);
                                break;
                            }
                        } catch (Exception e) {
                            Log.d(TAG, "测试目录 " + knownDir + " 失败: " + e.getMessage());
                        }
                    }
                }

                if (moduleDir != null) {
                    // 读取module.prop文件获取版本和元数据
                    Log.d(TAG, "读取模块配置文件: /data/adb/modules/" + moduleDir + "/module.prop");
                    Process catProcess = Runtime.getRuntime().exec("su -c cat /data/adb/modules/" + moduleDir + "/module.prop");
                    BufferedReader catReader = new BufferedReader(new InputStreamReader(catProcess.getInputStream()));
                    String propLine;
                    boolean foundVersion = false;
                    boolean foundName = false;
                    boolean foundId = false;

                    while ((propLine = catReader.readLine()) != null) {
                        Log.d(TAG, "解析module.prop行: " + propLine);

                        if (propLine.startsWith("version=")) {
                            version = propLine.substring("version=".length()).trim();
                            foundVersion = true;
                            Log.d(TAG, "解析到版本: " + version);
                        } else if (propLine.startsWith("name=")) {
                            frameworkName = propLine.substring("name=".length()).trim();
                            foundName = true;
                            Log.d(TAG, "解析到框架名称: " + frameworkName);
                        } else if (propLine.startsWith("id=")) {
                            String id = propLine.substring("id=".length()).trim();
                            foundId = true;
                            // 根据ID推断模式
                            if (id.contains("zygisk")) {
                                mode = "Zygisk";
                                Log.d(TAG, "检测到Zygisk工作模式，模块ID: " + id);
                            } else {
                                mode = "Standard";
                                Log.d(TAG, "检测到Standard工作模式，模块ID: " + id);
                            }
                        }
                    }
                    int catExitCode = catProcess.waitFor();

                    if (catExitCode != 0) {
                        Log.w(TAG, "读取module.prop文件失败，退出码: " + catExitCode);
                    }

                    if (!foundVersion) {
                        Log.w(TAG, "在module.prop中未找到version字段");
                    }
                    if (!foundName) {
                        Log.w(TAG, "在module.prop中未找到name字段");
                    }
                    if (!foundId) {
                        Log.w(TAG, "在module.prop中未找到id字段");
                    }

                    // 检测启用状态
                    Log.d(TAG, "检查模块启用状态");
                    Process checkProcess = Runtime.getRuntime().exec("su -c test -f /data/adb/modules/" + moduleDir + "/disable && echo disabled || echo enabled");
                    BufferedReader checkReader = new BufferedReader(new InputStreamReader(checkProcess.getInputStream()));
                    String status = checkReader.readLine();
                    int checkExitCode = checkProcess.waitFor();

                    if (checkExitCode == 0) {
                        if ("disabled".equals(status)) {
                            mode += ", Disabled";
                            Log.d(TAG, "模块状态: 已禁用");
                        } else {
                            mode += ", Enabled";
                            Log.d(TAG, "模块状态: 已启用");
                        }
                    } else {
                        Log.w(TAG, "检查模块状态失败，退出码: " + checkExitCode);
                    }
                } else {
                    Log.w(TAG, "最终未找到LSPosed模块目录");
                }
            } catch (Exception e) {
                Log.e(TAG, "通过模块目录检测失败: " + e.getMessage(), e);
            }
        } else {
            Log.d(TAG, "已通过系统属性获取版本，跳过模块目录检测");
        }

        // 格式化返回字符串
        String result;
        if (!version.equals("Unknown")) {
            result = frameworkName + " " + version + " (" + mode + ")";
            Log.i(TAG, "框架检测完成: " + result);
        } else {
            result = "Unknown";
            Log.w(TAG, "框架检测完成: 未检测到LSPosed框架");
        }

        return result;
    }

    /**
     * 更新系统信息
     */
    private void updateSystemInfo() {
        try {
            // 设备型号
            String deviceModel = Build.MODEL;
            textDeviceModel.setText(deviceModel.isEmpty() ? "Unknown" : deviceModel);

            // Android版本
            String androidVersion = Build.VERSION.RELEASE;
            textAndroidVersion.setText(androidVersion.isEmpty() ? "Unknown" : "Android " + androidVersion);

            // 构建版本
            String buildVersion = Build.DISPLAY;
            textBuildVersion.setText(buildVersion.isEmpty() ? "Unknown" : buildVersion);

            // 内核版本
            String kernelVersion = getKernelVersion();
            textKernelVersion.setText(kernelVersion.isEmpty() ? "Unknown" : kernelVersion);

            Log.i(TAG, "系统信息更新完成");

        } catch (Exception e) {
            Log.e(TAG, "更新系统信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取内核版本 - 改进版本
     */
    private String getKernelVersion() {
        Process process = null;
        BufferedReader reader = null;

        try {
            Log.d(TAG, "开始获取内核版本");
            process = Runtime.getRuntime().exec("su -c cat /proc/version");
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String version = reader.readLine();
            int exitCode = process.waitFor();

            Log.d(TAG, "内核版本命令执行结果 - 退出码: " + exitCode + ", 原始输出: " + version);

            if (exitCode == 0 && version != null) {
                // 提取内核版本信息 - 更健壮的解析方法
                String[] parts = version.split("\\s+");
                Log.d(TAG, "内核版本分割结果数量: " + parts.length);

                for (int i = 0; i < parts.length; i++) {
                    Log.v(TAG, "内核版本部分[" + i + "]: " + parts[i]);
                }

                // 通常第三个部分是内核版本，但进行验证
                if (parts.length >= 3) {
                    String kernelVer = parts[2];
                    // 验证是否是有效的版本格式 (包含数字和点)
                    if (kernelVer.matches(".*[0-9]+\\.[0-9]+.*")) {
                        Log.i(TAG, "成功提取内核版本: " + kernelVer);
                        return kernelVer;
                    } else {
                        Log.w(TAG, "提取的内核版本格式异常: " + kernelVer);
                    }
                }

                // 如果标准解析失败，尝试其他方法
                // 查找包含版本号的字段
                for (String part : parts) {
                    if (part.matches("[0-9]+\\.[0-9]+\\..*")) {
                        Log.i(TAG, "通过模式匹配找到内核版本: " + part);
                        return part;
                    }
                }

                // 最后返回整个字符串
                Log.i(TAG, "返回完整内核版本字符串: " + version);
                return version;
            } else {
                Log.w(TAG, "读取内核版本失败，退出码: " + exitCode);
            }

        } catch (Exception e) {
            Log.e(TAG, "获取内核版本失败: " + e.getMessage());

            // 备用方法：尝试使用uname命令
            try {
                Log.d(TAG, "尝试使用uname命令获取内核版本");
                process = Runtime.getRuntime().exec("su -c uname -r");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String version = reader.readLine();
                int exitCode = process.waitFor();

                if (exitCode == 0 && version != null && !version.isEmpty()) {
                    Log.i(TAG, "通过uname获取内核版本: " + version);
                    return version;
                }
            } catch (Exception e2) {
                Log.w(TAG, "uname命令也失败: " + e2.getMessage());
            }
        } finally {
            // 清理资源
            try {
                if (reader != null) reader.close();
                if (process != null) process.destroy();
            } catch (Exception e) {
                Log.w(TAG, "清理内核版本检测资源时出错: " + e.getMessage());
            }
        }

        return "";
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: 暂停主页");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: 销毁主页");
    }
}
