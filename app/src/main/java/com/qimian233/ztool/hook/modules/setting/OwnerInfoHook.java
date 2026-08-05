package com.qimian233.ztool.hook.modules.setting;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.qimian233.ztool.data.PreferenceKeys;
import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
public class OwnerInfoHook extends BaseHookModule {

    private String API_URL;
    private BroadcastReceiver mScreenReceiver;
    private boolean mIsReceiverRegistered = false;
    private String mCachedContent = "";

    public OwnerInfoHook() {}

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
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if ("com.android.settings".equals(packageName)) {
            hookSettingsPackage(classLoader);
        }
    }

    @Override
    public void handleSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) throws Throwable {
        ClassLoader classLoader = param.getClassLoader();
        hookSystemPackage(classLoader);
    }

    private void hookSettingsPackage(ClassLoader classLoader) {
        logger.info("开始Hook Settings包");

        // Hook点1: 在Settings的SecuritySettings中注册
        try {
            Method onResumeMethod = classLoader
                    .loadClass("com.android.settings.SecuritySettings")
                    .getDeclaredMethod("onResume");
            hookWithId(onResumeMethod, "on_resume", chain -> {
                Object result = chain.proceed();
                logger.debug("SecuritySettings resumed, registering screen receiver");
                registerScreenReceiver(chain.getThisObject(), classLoader);
                return result;
            });
            logger.info("成功Hook SecuritySettings.onResume");
        } catch (Throwable e) {
            logger.error("Hook SecuritySettings失败", e);
        }

        // Hook点2: ActivityThread中注册屏幕状态监听器
        try {
            Class<?> activityThreadClass = classLoader.loadClass("android.app.ActivityThread");
            Class<?> activityRecordClass = classLoader.loadClass("android.app.ActivityThread$ActivityClientRecord");
            Method performResumeMethod = activityThreadClass
                    .getDeclaredMethod("performResumeActivity", activityRecordClass, boolean.class, String.class);
            hookWithId(performResumeMethod, "perform_resume", chain -> {
                Object result = chain.proceed();
                Object activityRecord = chain.getArg(0);
                Field activityField = activityRecord.getClass().getDeclaredField("activity");
                activityField.setAccessible(true);
                Object activity = activityField.get(activityRecord);

                if (activity != null) {
                    registerScreenReceiver(activity, classLoader);
                }
                return result;
            });
            logger.info("成功Hook ActivityThread.performResumeActivity");
        } catch (Throwable e) {
            logger.error("Hook ActivityThread.performResumeActivity失败", e);
        }
    }

    private void hookSystemPackage(ClassLoader classLoader) {
        logger.info("开始Hook System包");

        // Hook点1: 在系统PowerManagerService中监听屏幕状态
        try {
            Method setPowerStateMethod = classLoader
                    .loadClass("com.android.server.power.PowerManagerService")
                    .getDeclaredMethod("setPowerState", boolean.class);
            hookWithId(setPowerStateMethod, "set_power_state", chain -> {
                Object result = chain.proceed();
                boolean screenOn = (Boolean) chain.getArg(0);
                logger.debug("电源状态改变，屏幕状态: " + screenOn);

                if (screenOn) {
                    // 屏幕亮起时更新OwnerInfo
                    updateOwnerInfo(null, classLoader);
                }
                return result;
            });
            logger.info("成功Hook PowerManagerService.setPowerState");
        } catch (Throwable e) {
            logger.error("Hook PowerManagerService.setPowerState失败", e);
        }

        // Hook点2: 用户活动监听
        try {
            Method userActivityMethod = classLoader
                    .loadClass("com.android.server.power.PowerManagerService")
                    .getDeclaredMethod("userActivity", int.class, long.class, int.class);
            hookWithId(userActivityMethod, "user_activity", chain -> {
                Object result = chain.proceed();
                int event = (Integer) chain.getArg(0);
                // 用户活动事件，包括屏幕触摸、按键等
                if (event == 0 || event == 2 || event == 3) { // POWER_BUTTON, TOUCH, etc.
                    logger.debug("检测到用户活动，更新OwnerInfo");
                    updateOwnerInfo(null, classLoader);
                }
                return result;
            });
            logger.info("成功Hook PowerManagerService.userActivity");
        } catch (Throwable e) {
            logger.error("Hook PowerManagerService.userActivity失败", e);
        }

        // Hook点3: 在ContextImpl中注册广播接收器
        try {
            Method registerReceiverMethod = classLoader
                    .loadClass("android.app.ContextImpl")
                    .getDeclaredMethod("registerReceiver",
                            BroadcastReceiver.class, IntentFilter.class);
            hookWithId(registerReceiverMethod, "register_receiver", chain -> {
                // 检查是否是我们自己的接收器，避免重复注册
                if (chain.getArg(0) == mScreenReceiver) {
                    return chain.proceed();
                }

                IntentFilter filter = (IntentFilter) chain.getArg(1);
                if (filter != null && hasScreenActions(filter)) {
                    // 这是一个包含屏幕动作的过滤器，我们可以在这里注册自己的接收器
                    registerScreenReceiver(chain.getThisObject(), classLoader);
                }
                return chain.proceed();
            });
            logger.info("成功Hook ContextImpl.registerReceiver");
        } catch (Throwable e) {
            logger.error("Hook ContextImpl.registerReceiver失败", e);
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
            logger.error("检查IntentFilter动作时出错", e);
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
                Method getContextMethod = contextObj.getClass().getDeclaredMethod("getContext");
                context = (Context) getContextMethod.invoke(contextObj);
            }

            mScreenReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    logger.debug("收到广播: " + action);

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

            if (context != null) {
                context.registerReceiver(mScreenReceiver, filter);
                mIsReceiverRegistered = true;
                logger.debug("Successfully registered screen state broadcast receiver");
                // 立即更新一次
                updateOwnerInfo(context, classLoader);
            } else {
                logger.error("Failed to register screen state broadcast receiver: context is null!");
            }

        } catch (Throwable e) {
            logger.error("注册广播接收器失败", e);
        }
    }

    private void updateOwnerInfo(Object context, ClassLoader classLoader) {
        // 启动新线程获取API数据，避免阻塞UI线程
        new Thread(() -> {
            try {
                API_URL = getString(PreferenceKeys.API_URL.name);
                // 处理可能的URL协议保存问题，这里添加补全协议的逻辑
                if (API_URL != null && !API_URL.isEmpty()) {
                    if (!API_URL.startsWith("http://") && !API_URL.startsWith("https://") &&
                            !API_URL.startsWith("Https://") && !API_URL.startsWith("Http://")) {
                        API_URL = "https://" + API_URL;
                    }
                } else {
                    logger.warn("API_URL配置为空，使用默认值");
                    API_URL = "https://api.example.com"; // 设置一个默认URL
                }
                String content = fetchContentFromAPI();
                if (content != null && !content.equals(mCachedContent)) {
                    mCachedContent = content;
                    logger.debug("从API获取新内容: " + content);
                    setOwnerInfoContent(content, context, classLoader);
                } else if (content == null) {
                    logger.warn("从API获取内容失败" + API_URL);
                } else {
                    logger.debug("内容未变化，跳过更新");
                }
            } catch (Exception e) {
                logger.error("updateOwnerInfo线程出错", e);
            }
        }).start();
    }

    private String fetchContentFromAPI() {
        HttpURLConnection connection;
        BufferedReader reader;

        try {
            URL url = new URL(API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "OwnerInfoHook/1.0");

            int responseCode = connection.getResponseCode();
            logger.debug("API响应码: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                //noinspection CharsetObjectCanBeUsed
                reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                String rawResponse = response.toString();
                logger.debug("API原始响应: " + rawResponse); // 记录原始响应用于调试

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
                    logger.debug("API错误响应: " + errorResponse);
                }
                logger.debug("HTTP错误响应: " + responseCode);
            }
        } catch (Exception e) {
            logger.error("获取API数据时出错", e);
        }
        return "If you see this message, your API is broken, check your settings and Internet connection, then restart com.android.settings";
    }

    private String parseContentFromJson(String jsonString) {
        try {
            // 使用正则表达式匹配content字段，处理转义字符
            String Regular = getString(PreferenceKeys.REGULAR.name);
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
                logger.warn("JSON中未找到content字段");
                return jsonString;
            }
        } catch (Exception e) {
            logger.error("解析JSON时出错", e);
            return jsonString;
        }
    }

    private void setOwnerInfoContent(final String content, final Object context, final ClassLoader classLoader) {
        // 确保在主线程执行设置操作
        try {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                try {
                    logger.debug("设置OwnerInfo内容: " + content);

                    // 方法1: 通过LockPatternUtils
                    try {
                        Object lockPatternUtils = getObject(context, classLoader);

                        // 先启用OwnerInfo
                        Method setEnabled = lockPatternUtils.getClass()
                                .getDeclaredMethod("setOwnerInfoEnabled", boolean.class, int.class);
                        setEnabled.invoke(lockPatternUtils, true, 0);
                        // 设置OwnerInfo内容
                        Method setOwnerInfo = lockPatternUtils.getClass()
                                .getDeclaredMethod("setOwnerInfo", String.class, int.class);
                        setOwnerInfo.invoke(lockPatternUtils, content, 0);

                        logger.debug("通过LockPatternUtils成功更新OwnerInfo");
                        return;
                    } catch (Throwable e) {
                        logger.error("通过LockPatternUtils更新失败", e);
                    }

                    // 方法2: 通过ILockSettings服务
                    try {
                        Class<?> serviceManagerClass = classLoader.loadClass("android.os.ServiceManager");
                        Method getServiceMethod = serviceManagerClass
                                .getDeclaredMethod("getService", String.class);
                        Object lockSettingsService = getServiceMethod.invoke(null, "lock_settings");

                        if (lockSettingsService != null) {
                            // 启用OwnerInfo
                            Method setBooleanMethod = lockSettingsService.getClass()
                                    .getDeclaredMethod("setBoolean", String.class, boolean.class, int.class);
                            setBooleanMethod.invoke(lockSettingsService,
                                    "lock_screen_owner_info_enabled", true, 0);
                            // 设置内容
                            Method setStringMethod = lockSettingsService.getClass()
                                    .getDeclaredMethod("setString", String.class, String.class, int.class);
                            setStringMethod.invoke(lockSettingsService,
                                    "lock_screen_owner_info", content, 0);

                            logger.debug("通过ILockSettings成功更新OwnerInfo");
                            return;
                        }
                    } catch (Throwable e) {
                        logger.error("通过ILockSettings更新失败", e);
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
                            logger.debug("通过SettingsProvider成功更新OwnerInfo");
                        }
                    } catch (Throwable e) {
                        logger.error("通过SettingsProvider更新失败", e);
                    }

                } catch (Throwable e) {
                    logger.error("设置OwnerInfo内容失败", e);
                }
            });
        } catch (Throwable e) {
            logger.error("提交到主Handler失败", e);
        }
    }

    @NonNull
    private static Object getObject(Object context, ClassLoader classLoader) {
        try {
            Class<?> lockPatternUtilsClass = classLoader.loadClass(
                    "com.android.internal.widget.LockPatternUtils");

            Object lockPatternUtils;
            if (context instanceof Context) {
                // 从Context创建LockPatternUtils实例
                Constructor<?> ctor = lockPatternUtilsClass.getDeclaredConstructor(Context.class);
                //noinspection JavaReflectionInvocation
                lockPatternUtils = ctor.newInstance(context);
            } else {
                // 使用默认构造函数
                Constructor<?> ctor = lockPatternUtilsClass.getDeclaredConstructor();
                lockPatternUtils = ctor.newInstance();
            }
            return lockPatternUtils;
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create LockPatternUtils", e);
        }
    }

    private String getString(String key) {
        SharedPreferences prefs = getRemotePreferences();
        return prefs.getString(key, "");
    }
}
