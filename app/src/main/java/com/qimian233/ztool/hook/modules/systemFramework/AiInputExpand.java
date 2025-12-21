package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.PreferenceHelper;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * AI输入法扩展功能Hook模块
 * 功能：扩展AI触发符号，强制开启LGSI AI功能特性
 * 作用域：全局（动态检测类是否存在）
 */
public class AiInputExpand extends BaseHookModule {
    private final PreferenceHelper mPrefHelper = PreferenceHelper.getInstance();

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
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 核心逻辑分为两部分，分别放在try-catch块中，互不影响

        // 1. 修改 RemoteInputConnectionImpl 触发符号
        try {
            hookRemoteInputConnection(lpparam.classLoader);
        } catch (Throwable t) {
            // 某些进程可能没有这个类，属于正常现象，仅在调试时关注
            // logError("RemoteInputConnection hook failed in " + lpparam.packageName, t);
        }

        // 2. 强制开启 LgsiFeatures 功能
        try {
            hookLgsiFeatures(lpparam.classLoader);
        } catch (Throwable t) {
            // logError("LgsiFeatures hook failed in " + lpparam.packageName, t);
        }
    }

    private void hookRemoteInputConnection(ClassLoader classLoader) {
        String className = "android.view.inputmethod.RemoteInputConnectionImpl";

        // 检查类是否存在，不存在直接抛出/返回，避免无效Hook尝试
        Class<?> targetClass = XposedHelpers.findClassIfExists(className, classLoader);
        if (targetClass == null) return;

        // 定义新的触发符号数组，使用新的符号
        String[] newSignArray = mPrefHelper.getStringArray("AI_INPUT_EXPAND_SIGNS");

        // 修改静态常量数组 AI_COMMAND_SIGN_ARRAYS
        XposedHelpers.setStaticObjectField(targetClass, "AI_COMMAND_SIGN_ARRAYS", newSignArray);

        // 修改默认的 AI_COMMAND_SIGN
        XposedHelpers.setStaticObjectField(targetClass, "AI_COMMAND_SIGN", "&&");

        log("Successfully expanded AI input signs [&&] for package");
    }

    private void hookLgsiFeatures(ClassLoader classLoader) {
        String className = "com.lgsi.config.LgsiFeatures";

        Class<?> featureClass = XposedHelpers.findClassIfExists(className, classLoader);
        if (featureClass == null) return;

        // 强制 enabled 方法返回 true
        // 对应原代码：XposedHelpers.findAndHookMethod(featureClass, "enabled", int.class, ...)
        XposedHelpers.findAndHookMethod(
                featureClass,
                "enabled",
                int.class,
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return true; // 强制返回 true
                    }
                }
        );
        log("Successfully forced LgsiFeatures check to TRUE");
    }
}
