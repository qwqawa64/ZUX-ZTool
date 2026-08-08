package com.qimian233.ztool.hook.modules.systemui.keyguard;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;

import com.qimian233.ztool.data.keys.ScopeKeys;
import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

@SuppressLint("PrivateApi")
public class ForceLenovoAOD extends AppHookModule {

    private static final String TAG = "ForceLenovoAOD";

    public ForceLenovoAOD() {}

    @Override
    public String getModuleName() {
        return TAG;
    }

    @Override
    public String[] getTargetPackages() {return new String[]{ScopeKeys.SYSTEM_UI.packageName};}

    private static final String ZUI_DOZE_TRIGGERS_CLASS = "com.android.systemui.doze.ZuiDozeTriggers";

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (!ScopeKeys.SYSTEM_UI.packageName.equals(packageName)) {
            return;
        }

        // 直接设置mIsGoingToStartAOD字段
        hookZuiDozeTriggers(classLoader);
        // 额外确保AOD相关检查通过
        hookAODChecks();
    }

    private void hookZuiDozeTriggers(ClassLoader classLoader) {
        try {
            // Hook ZuiDozeTriggers的构造函数，确保实例创建后立即设置标志
            Constructor<?> ctor = classLoader.loadClass(ZUI_DOZE_TRIGGERS_CLASS)
                    .getDeclaredConstructor(
                            classLoader.loadClass("com.android.systemui.doze.DozeTriggers"),
                            Context.class);
            hookWithId(ctor, "ctor", chain -> {
                chain.proceed();
                // 在构造函数执行后，立即设置AOD启动标志
                chain.getThisObject().getClass().getDeclaredField("mIsGoingToStartAOD")
                        .setBoolean(chain.getThisObject(), true);
                logger.debug("ZuiDozeTriggers constructed, forced mIsGoingToStartAOD = true");
                return null;
            });
        } catch (Throwable t) {
            logger.error("Failed to hook ZuiDozeTriggers: ", t);
        }
    }

    private void hookAODChecks() {
        try {
            // Hook SystemProperties检查
            Method getIntMethod = Class.forName("android.os.SystemProperties")
                    .getDeclaredMethod("getInt", String.class, int.class);
            hookWithId(getIntMethod, "get_int", chain -> {
                String key = (String) chain.getArg(0);
                if ("ro.config.aod.support".equals(key)) {
                    logger.debug("Bypassed ro.config.aod.support check");
                    return 1; // 强制返回支持AOD
                }
                return chain.proceed();
            });

            // Hook AOD设置检查
            Method getIntForUserMethod = android.provider.Settings.System.class
                    .getDeclaredMethod("getIntForUser",
                            ContentResolver.class, String.class, int.class, int.class);
            hookWithId(getIntForUserMethod, "get_int_for_user", chain -> {
                String setting = (String) chain.getArg(1);
                if ("always_on_display".equals(setting)) {
                    logger.debug("Bypassed always_on_display setting check");
                    return 1;
                }
                return chain.proceed();
            });

        } catch (Exception t) {
            logger.error("Failed to hook AOD checks: ", t);
        }
    }
}
