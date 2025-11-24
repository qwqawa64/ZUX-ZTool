package com.qimian233.ztool.hook.modules.systemui;

import android.content.ContentResolver;
import android.content.Context;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ForceEnableAOD extends BaseHookModule {

    private static final String TAG = "ForceEnableAOD";
    @Override
    public String getModuleName() {
        return TAG;
    }
    @Override
    public String[] getTargetPackages() {return new String[]{"com.android.systemui"};}
    private static final String ZUI_DOZE_TRIGGERS_CLASS = "com.android.systemui.doze.ZuiDozeTriggers";
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        // 直接设置mIsGoingToStartAOD字段
        hookZuiDozeTriggers(lpparam.classLoader);
        // 额外确保AOD相关检查通过
        hookAODChecks(lpparam.classLoader);
    }
    private void hookZuiDozeTriggers(ClassLoader classLoader) {
        try {
            // Hook ZuiDozeTriggers的构造函数，确保实例创建后立即设置标志
            XposedHelpers.findAndHookConstructor(ZUI_DOZE_TRIGGERS_CLASS,
                    classLoader,
                    "com.android.systemui.doze.DozeTriggers",
                    Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // 在构造函数执行后，立即设置AOD启动标志
                            XposedHelpers.setBooleanField(param.thisObject, "mIsGoingToStartAOD", true);
                            log("ZuiDozeTriggers constructed, forced mIsGoingToStartAOD = true");
                        }
                    });
        } catch (Throwable t) {logError("Failed to hook ZuiDozeTriggers: " , t);}
    }
    private void hookAODChecks(ClassLoader classLoader) {
        try {
            // Hook SystemProperties检查
            XposedHelpers.findAndHookMethod("android.os.SystemProperties",
                    classLoader,
                    "getInt",
                    String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[0];
                            if ("ro.config.aod.support".equals(key)) {
                                param.setResult(1); // 强制返回支持AOD
                                log("Bypassed ro.config.aod.support check");
                            }
                        }
                    });

            // Hook AOD设置检查
            XposedHelpers.findAndHookMethod("android.provider.Settings$System",
                    classLoader,
                    "getIntForUser",
                    ContentResolver.class, String.class, int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String setting = (String) param.args[1];
                            if ("always_on_display".equals(setting)) {
                                param.setResult(1);
                                log("Bypassed always_on_display setting check");
                            }
                        }
                    });

        } catch (Exception t) {
            logError("Failed to hook AOD checks: " , t);
        }
    }
}

