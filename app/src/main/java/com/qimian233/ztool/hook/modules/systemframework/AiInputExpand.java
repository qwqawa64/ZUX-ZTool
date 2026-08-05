package com.qimian233.ztool.hook.modules.systemframework;

import com.qimian233.ztool.data.PreferenceKeys;
import com.qimian233.ztool.hook.base.AppHookModule;

import android.annotation.SuppressLint;
import android.text.TextUtils;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * AI输入法扩展功能Hook模块
 * 功能：扩展AI触发符号，强制开启LGSI AI功能特性
 * 作用域：全局（动态检测类是否存在）
 */

@SuppressLint("PrivateApi")
public class AiInputExpand extends AppHookModule {

    public AiInputExpand() {}

    @Override
    public String getModuleName() {
        return "ai_input_expand"; // 模块唯一标识
    }

    @Override
    public String[] getTargetPackages() {
        return null; // 返回null，表示不限制特定包名，由supportsPackage控制
    }

    /**
     * 重写此方法以支持全局Hook
     * 因为RemoteInputConnectionImpl会在各个应用进程中加载
     */
    @Override
    public boolean supportsPackage(String packageName) {
        return true; // 允许所有包进入handleLoadPackage，我们在内部通过类是否存在来判断
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();

        // 核心逻辑分为两部分，分别放在try-catch块中，互不影响

        // 1. 修改 RemoteInputConnectionImpl 触发符号
        try {
            hookRemoteInputConnection(classLoader);
        } catch (Throwable t) {
            // 某些进程可能没有这个类，属于正常现象，仅在调试时关注
            // logError("RemoteInputConnection hook failed in " + packageName, t);
        }

        // 2. 强制开启 LgsiFeatures 功能
        try {
            hookLgsiFeatures(classLoader);
        } catch (Throwable t) {
            // logError("LgsiFeatures hook failed in " + packageName, t);
        }
    }

    private void hookRemoteInputConnection(ClassLoader classLoader) {
        String className = "android.view.inputmethod.RemoteInputConnectionImpl";

        // 检查类是否存在，不存在直接返回，避免无效Hook尝试
        Class<?> targetClass;
        try {
            targetClass = classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            return;
        }

        // 定义新的触发符号数组，使用新的符号
        String[] newSignArray = getPrefStringArray();

        // 修改静态常量数组 AI_COMMAND_SIGN_ARRAYS
        setStaticObjectField(targetClass, "AI_COMMAND_SIGN_ARRAYS", newSignArray);

        // 修改默认的 AI_COMMAND_SIGN
        setStaticObjectField(targetClass, "AI_COMMAND_SIGN", "&&");

        logger.info("Successfully expanded AI input signs [&&] for package");
    }

    private void hookLgsiFeatures(ClassLoader classLoader) {
        String className = "com.lgsi.config.LgsiFeatures";

        Class<?> featureClass;
        try {
            featureClass = classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            return;
        }

        // 强制 enabled 方法返回 true
        try {
            Method method = featureClass.getDeclaredMethod("enabled", int.class);
            hookWithId(method, "lgsi_features_enabled", chain -> true);
            logger.info("Successfully forced LgsiFeatures check to TRUE");
        } catch (NoSuchMethodException e) {
            // 方法不存在，忽略
        }
    }

    // ── reflection helpers ──

    private static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = findField(clazz, fieldName); // null-safe, a check is not required
            field.setAccessible(true);
            field.set(null, value);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Read comma-separated string array from preferences.
     */
    private String[] getPrefStringArray() {
        String value;
        try {
            value = getRemotePreferences().getString(PreferenceKeys.AI_INPUT_EXPAND_SIGNS.name, "");
        } catch (Throwable t) {
            value = "";
        }
        if (TextUtils.isEmpty(value)) return new String[0];
        return value.split(",");
    }
}
