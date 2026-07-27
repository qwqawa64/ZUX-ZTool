package com.qimian233.ztool.hook.modules.setting;

import android.annotation.SuppressLint;
import android.content.Context;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 允许显示杜比音效Hook模块
 * 功能：绕过耳机检测，使杜比音效在非耳机状态下可用
 */
@SuppressLint("PrivateApi")
public class AllowDisplayDolbyHook extends BaseHookModule {

    public AllowDisplayDolbyHook() {}

    @Override
    public String getModuleName() {
        return "allow_display_dolby";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.android.settings",
                "com.android.systemui",
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        switch (packageName) {
            case "com.android.settings":
                hookSettingsPackage(classLoader);
                break;
            case "com.android.systemui":
                hookSystemUIPackage(classLoader);
                break;
        }
    }

    /**
     * Hook设置应用中的杜比音效相关功能
     */
    private void hookSettingsPackage(ClassLoader classLoader) {
        try {
            // Android 13 (SDK 33)
            if (android.os.Build.VERSION.SDK_INT == 33) {
                Method m = classLoader
                        .loadClass("com.android.settings.dolby.DolbyAtmosPreferenceFragment")
                        .getDeclaredMethod("getheadsetStatus");
                hookWithId(m, "hook_59", chain -> 1);
                log("Successfully hooked Android 13 DolbyAtmosPreferenceFragment.getheadsetStatus");
            }
            // Android 14 (SDK 34)
            else if (android.os.Build.VERSION.SDK_INT == 34) {
                // Hook 耳机连接状态检测
                Method isHeadsetMethod = classLoader
                        .loadClass("com.lenovo.settings.sound.dolby.DolbyAtmosFragment")
                        .getDeclaredMethod("isHeadsetConnected");
                hookWithId(isHeadsetMethod, "is_headset_1", chain -> Boolean.TRUE);

                // Hook 初始化视图，清除摘要显示
                Method initViewMethod = classLoader
                        .loadClass("com.lenovo.settings.sound.dolby.DolbyAtmosFragment")
                        .getDeclaredMethod("initView");
                hookWithId(initViewMethod, "init_view", chain -> {
                    Object result = chain.proceed();
                    try {
                        Field field = chain.getThisObject().getClass()
                                .getDeclaredField("mDolbySwitchPreference");
                        field.setAccessible(true);
                        Object preference = field.get(chain.getThisObject());
                        if (preference != null) {
                            Method setSummary = preference.getClass()
                                    .getDeclaredMethod("setSummary", CharSequence.class);
                            setSummary.invoke(preference, (Object) null);
                            log("Successfully cleared Dolby switch preference summary");
                        }
                    } catch (Throwable t) {
                        logError("Failed to clear Dolby switch preference summary", t);
                    }
                    return result;
                });
                log("Successfully hooked Android 14 DolbyAtmosFragment methods");
            }
            // Android 15+ (SDK 35+)
            else if (android.os.Build.VERSION.SDK_INT >= 35) {
                // Hook 工具类中的耳机连接检测
                Method isHeadsetMethod = classLoader
                        .loadClass("com.lenovo.settings.sound.dolby.DolbyAtmosUtils")
                        .getDeclaredMethod("isHeadsetConnected", Context.class);
                hookWithId(isHeadsetMethod, "is_headset_2", chain -> Boolean.TRUE);

                // Hook 控制器更新状态，清除摘要
                Class<?> prefClass = classLoader.loadClass("androidx.preference.Preference");
                Method updateStateMethod = classLoader
                        .loadClass("com.lenovo.settings.sound.dolby.DolbySwitchPreferenceController")
                        .getDeclaredMethod("updateState", prefClass);
                hookWithId(updateStateMethod, "update_state", chain -> {
                    try {
                        Object arg0 = chain.getArg(0);
                        if (arg0 != null) {
                            Method setSummary = findMethod(arg0.getClass(),
                                    "setSummary", CharSequence.class);
                            setSummary.invoke(arg0, (Object) null);
                            log("Successfully cleared preference summary in updateState");
                        }
                    } catch (Throwable t) {
                        logError("Failed to clear preference summary in updateState", t);
                    }
                    return chain.proceed();
                });
                log("Successfully hooked Android 15 DolbyAtmosUtils and DolbySwitchPreferenceController");
            }
        } catch (Throwable t) {
            logError("Failed to hook Settings package", t);
        }
    }

    /**
     * Hook SystemUI中的杜比音效磁贴
     */
    private void hookSystemUIPackage(ClassLoader classLoader) {
        try {
            // Hook QDolbyAtmosTile 耳机检测方法
            if (android.os.Build.VERSION.SDK_INT <= 34) {
                Method m = classLoader
                        .loadClass("com.android.systemui.qs.tiles.QDolbyAtmosTile")
                        .getDeclaredMethod("isHeadSetConnect");
                hookWithId(m, "hook_138", chain -> Boolean.TRUE);
                log("Successfully hooked QDolbyAtmosTile.isHeadSetConnect (SDK <= 34)");
            } else {
                Method m = classLoader
                        .loadClass("com.android.systemui.qs.tiles.QDolbyAtmosTile")
                        .getDeclaredMethod("isHeadSetConnect$2");
                hookWithId(m, "hook_144", chain -> Boolean.TRUE);
                log("Successfully hooked QDolbyAtmosTile.isHeadSetConnect$2 (SDK > 34)");
            }

            // Hook 详情视图中的耳机检测
            Method detailMethod = classLoader
                    .loadClass("com.android.systemui.qs.tiles.QDolbyAtmosDetailView")
                    .getDeclaredMethod("isHeadSetConnect");
            hookWithId(detailMethod, "detail", chain -> Boolean.TRUE);
            log("Successfully hooked QDolbyAtmosDetailView.isHeadSetConnect");

        } catch (Throwable t) {
            logError("Failed to hook SystemUI package", t);
        }
    }

    /**
     * Hook 游戏服务中的杜比音效处理
     */

}
