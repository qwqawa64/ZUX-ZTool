package com.qimian233.ztool.hook.modules.setting;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 锁屏OwnerInfo自动更新Hook模块
 * 功能：自动从API获取每日一言并设置为锁屏OwnerInfo
 * 触发时机：屏幕亮起、用户解锁、用户活动等
 */
public class OwnerInfoHook extends BaseHookModule {

    private String API_URL;
    private BroadcastReceiver mScreenReceiver;
    private boolean mIsReceiverRegistered = false;
    private String mCachedContent = "";
    private static final String PREFS_NAME = "xposed_module_config";
    private static final String MODULE_PACKAGE = "com.qimian233.ztool";

    @Override
    public String getModuleName() {
        return "auto_owner_info";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.android.settings",
                "android"
        };
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("com.android.settings".equals(packageName)) {
            hookSettingsPackage(lpparam);
        } else if ("android".equals(packageName)) {
            hookSystemPackage(lpparam);
        }
    }

    private void hookSettingsPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        log("开始Hook Settings包");

        // Hook点1: 在Settings的SecuritySettings中注册
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.settings.SecuritySettings",
                    lpparam.classLoader,
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            log("SecuritySettings resumed, registering screen receiver");
                            registerScreenReceiver(param.thisObject, lpparam.classLoader);
                        }
                    });

            log("成功Hook SecuritySettings.onResume");
        } catch (Throwable e) {
            logError("Hook SecuritySettings失败", e);
        }

        // Hook点2: ActivityThread中注册屏幕状态监听器
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ActivityThread",
                    lpparam.classLoader,
                    "performResumeActivity",
                    "android.app.ActivityThread$ActivityClientRecord",
                    boolean.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object activityRecord = param.args[0];
                            Object activity = XposedHelpers.getObjectField(activityRecord, "activity");

                            if (activity != null) {
                                registerScreenReceiver(activity, lpparam.classLoader);
                            }
                        }
                    });

            log("成功Hook ActivityThread.performResumeActivity");
        } catch (Throwable e) {
            logError("Hook ActivityThread.performResumeActivity失败", e);
        }
    }

    private void hookSystemPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        log("开始Hook System包");

        // Hook点1: 在系统PowerManagerService中监听屏幕状态
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.power.PowerManagerService",
                    lpparam.classLoader,
                    "setPowerState",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            boolean screenOn = (Boolean) param.args[0];
                            log("电源状态改变，屏幕状态: " + screenOn);

                            if (screenOn) {
                                // 屏幕亮起时更新OwnerInfo
                                updateOwnerInfo(null, lpparam.classLoader);
                            }
                        }
                    });

            log("成功Hook PowerManagerService.setPowerState");
        } catch (Throwable e) {
            logError("Hook PowerManagerService.setPowerState失败", e);
        }

        // Hook点2: 用户活动监听
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.power.PowerManagerService",
                    lpparam.classLoader,
                    "userActivity",
                    int.class,
                    long.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            int event = (Integer) param.args[0];
                            // 用户活动事件，包括屏幕触摸、按键等
                            if (event == 0 || event == 2 || event == 3) { // POWER_BUTTON, TOUCH, etc.
                                log("检测到用户活动，更新OwnerInfo");
                                updateOwnerInfo(null, lpparam.classLoader);
                            }
                        }
                    });

            log("成功Hook PowerManagerService.userActivity");
        } catch (Throwable e) {
            logError("Hook PowerManagerService.userActivity失败", e);
        }

        // Hook点3: 在ContextImpl中注册广播接收器
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ContextImpl",
                    lpparam.classLoader,
                    "registerReceiver",
                    BroadcastReceiver.class,
                    IntentFilter.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            // 检查是否是我们自己的接收器，避免重复注册
                            if (param.args[0] == mScreenReceiver) {
                                return;
                            }

                            IntentFilter filter = (IntentFilter) param.args[1];
                            if (filter != null && hasScreenActions(filter)) {
                                // 这是一个包含屏幕动作的过滤器，我们可以在这里注册自己的接收器
                                registerScreenReceiver(param.thisObject, lpparam.classLoader);
                            }
                        }
                    });

            log("成功Hook ContextImpl.registerReceiver");
        } catch (Throwable e) {
            logError("Hook ContextImpl.registerReceiver失败", e);
        }
    }

    private boolean hasScreenActions(IntentFilter filter) {
        try {
            // 检查过滤器是否包含屏幕相关的动作
            Iterator<String> actions = filter.actionsIterator();
            while (actions != null && actions.hasNext()) {
                String action = actions.next();
                if (Intent.ACTION_SCREEN_ON.equals(action) ||
                        Intent.ACTION_SCREEN_OFF.equals(action) ||
                        Intent.ACTION_USER_PRESENT.equals(action)) {
                    return true;
                }
            }
        } catch (Throwable e) {
            logError("检查IntentFilter动作时出错", e);
        }
        return false;
    }

    private void registerScreenReceiver(Object contextObj, ClassLoader classLoader) {
        if (mIsReceiverRegistered) {
            return;
        }

        try {
            Context context;
            if (contextObj instanceof Context) {
                context = (Context) contextObj;
            } else {
                // 尝试通过反射获取Context
                context = (Context) XposedHelpers.callMethod(contextObj, "getContext");
            }

            if (context == null) {
                log("获取Context失败，无法注册广播接收器");
                return;
            }

            mScreenReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    log("收到广播: " + action);

                    if (Intent.ACTION_SCREEN_ON.equals(action) ||
                            Intent.ACTION_USER_PRESENT.equals(action)) {
                        // 屏幕亮起或用户解锁时更新OwnerInfo
                        updateOwnerInfo(context, classLoader);
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);

            context.registerReceiver(mScreenReceiver, filter);
            mIsReceiverRegistered = true;

            log("成功注册屏幕状态广播接收器");

            // 立即更新一次
            updateOwnerInfo(context, classLoader);

        } catch (Throwable e) {
            logError("注册广播接收器失败", e);
        }
    }

    private void updateOwnerInfo(Object context, ClassLoader classLoader) {
        // 启动新线程获取API数据，避免阻塞UI线程
        new Thread(() -> {
            try {
                API_URL = getString("API_URL");
                // log("API_URL: " + API_URL);
                // 处理可能的URL协议保存问题，这里添加补全协议的逻辑
                if (API_URL != null && !API_URL.isEmpty()) {
                    if (!API_URL.startsWith("http://") && !API_URL.startsWith("https://") &&
                            !API_URL.startsWith("Https://") && !API_URL.startsWith("Http://")) {
                        API_URL = "https://" + API_URL;
                    }
                } else {
                    log("API_URL配置为空，使用默认值");
                    API_URL = "https://api.example.com"; // 设置一个默认URL
                }
                String content = fetchContentFromAPI();
                if (content != null && !content.equals(mCachedContent)) {
                    mCachedContent = content;
                    log("从API获取新内容: " + content);
                    setOwnerInfoContent(content, context, classLoader);
                } else if (content == null) {
                    log("从API获取内容失败" + API_URL);
                } else {
                    log("内容未变化，跳过更新");
                }
            } catch (Exception e) {
                logError("updateOwnerInfo线程出错", e);
            }
        }).start();
    }

    private String fetchContentFromAPI() {
        HttpURLConnection connection;
        BufferedReader reader;

        try {
            // log("URL:" + API_URL);
            URL url = new URL(API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "OwnerInfoHook/1.0");

            int responseCode = connection.getResponseCode();
            log("API响应码: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                String rawResponse = response.toString();
                log("API原始响应: " + rawResponse); // 记录原始响应用于调试

                return parseContentFromJson(rawResponse);
            } else {
                // 读取错误流获取更多信息
                InputStream errorStream = connection.getErrorStream();
                if (errorStream != null) {
                    reader = new BufferedReader(new InputStreamReader(errorStream));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    log("API错误响应: " + errorResponse);
                }
                log("HTTP错误响应: " + responseCode);
            }
        } catch (Exception e) {
            logError("获取API数据时出错", e);
        }
        return null;
    }


    private String parseContentFromJson(String jsonString) {
        try {
            // 使用正则表达式匹配content字段，处理转义字符
            String Regular = getString("Regular");
            // 增加对表达式为空的保护：如果正则表达式为null或空，则跳过匹配
            if (Regular == null || Regular.isEmpty()) {
                return jsonString;
            }
            Pattern pattern = Pattern.compile(Regular);
            Matcher matcher = pattern.matcher(jsonString);

            if (matcher.find()) {
                String content = matcher.group(1);
                // 处理转义字符（如\"转换为"）
                assert content != null;
                content = content.replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\/", "/")
                        .replace("\\b", "\b")
                        .replace("\\f", "\f")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t");
                return content;
            } else {
                log("JSON中未找到content字段");
                return jsonString;
            }
        } catch (Exception e) {
            logError("解析JSON时出错", e);
            return jsonString;
        }
    }



    private void setOwnerInfoContent(final String content, final Object context, final ClassLoader classLoader) {
        // 确保在主线程执行设置操作
        try {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                try {
                    log("设置OwnerInfo内容: " + content);

                    // 方法1: 通过LockPatternUtils
                    try {
                        Object lockPatternUtils = getObject(context, classLoader);

                        // 先启用OwnerInfo
                        XposedHelpers.callMethod(lockPatternUtils, "setOwnerInfoEnabled", true, 0);
                        // 设置OwnerInfo内容
                        XposedHelpers.callMethod(lockPatternUtils, "setOwnerInfo", content, 0);

                        log("通过LockPatternUtils成功更新OwnerInfo");
                        return;
                    } catch (Throwable e) {
                        logError("通过LockPatternUtils更新失败", e);
                    }

                    // 方法2: 通过ILockSettings服务
                    try {
                        Class<?> serviceManagerClass = XposedHelpers.findClass("android.os.ServiceManager", classLoader);
                        Object lockSettingsService = XposedHelpers.callStaticMethod(
                                serviceManagerClass, "getService", "lock_settings");

                        if (lockSettingsService != null) {
                            Class<?> iLockSettingsClass = XposedHelpers.findClass(
                                    "com.android.internal.widget.ILockSettings", classLoader);

                            // 启用OwnerInfo
                            XposedHelpers.callMethod(lockSettingsService, "setBoolean",
                                    "lock_screen_owner_info_enabled", true, 0);
                            // 设置内容
                            XposedHelpers.callMethod(lockSettingsService, "setString",
                                    "lock_screen_owner_info", content, 0);

                            log("通过ILockSettings成功更新OwnerInfo");
                            return;
                        }
                    } catch (Throwable e) {
                        logError("通过ILockSettings更新失败", e);
                    }

                    // 方法3: 直接调用SettingsProvider（备用方法）
                    try {
                        if (context instanceof Context) {
                            Settings.Secure.putString(
                                    ((Context) context).getContentResolver(),
                                    "lock_screen_owner_info_enabled", "1");
                            Settings.Secure.putString(
                                    ((Context) context).getContentResolver(),
                                    "lock_screen_owner_info", content);
                            log("通过SettingsProvider成功更新OwnerInfo");
                        }
                    } catch (Throwable e) {
                        logError("通过SettingsProvider更新失败", e);
                    }

                } catch (Throwable e) {
                    logError("设置OwnerInfo内容失败", e);
                }
            });
        } catch (Throwable e) {
            logError("提交到主Handler失败", e);
        }
    }

    @NonNull
    private static Object getObject(Object context, ClassLoader classLoader) {
        Class<?> lockPatternUtilsClass = XposedHelpers.findClass(
                "com.android.internal.widget.LockPatternUtils", classLoader);

        Object lockPatternUtils;
        if (context instanceof Context) {
            // 从Context创建LockPatternUtils实例
            lockPatternUtils = XposedHelpers.newInstance(lockPatternUtilsClass, context);
        } else {
            // 使用默认构造函数
            lockPatternUtils = XposedHelpers.newInstance(lockPatternUtilsClass);
        }
        return lockPatternUtils;
    }

    public static String getString(String key) {
        XSharedPreferences prefs = new XSharedPreferences(MODULE_PACKAGE, PREFS_NAME);
        prefs.reload();
        return prefs.getString(key, "");
    }


}
