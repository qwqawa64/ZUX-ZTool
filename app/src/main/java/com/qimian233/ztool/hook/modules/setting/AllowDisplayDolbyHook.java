package com.qimian233.ztool.hook.modules.setting;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 允许显示杜比音效Hook模块
 * 功能：绕过耳机检测，使杜比音效在非耳机状态下可用
 */
public class AllowDisplayDolbyHook extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "allow_display_dolby";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.android.settings",
                "com.android.systemui",
                "com.zui.game.service"
        };
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        switch (packageName) {
            case "com.android.settings":
                hookSettingsPackage(lpparam);
                break;
            case "com.android.systemui":
                hookSystemUIPackage(lpparam);
                break;
            case "com.zui.game.service":
                hookGameServicePackage(lpparam);
                break;
        }
    }

    /**
     * Hook设置应用中的杜比音效相关功能
     */
    private void hookSettingsPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Android 13 (SDK 33)
            if (android.os.Build.VERSION.SDK_INT == 33) {
                XposedHelpers.findAndHookMethod(
                        "com.android.settings.dolby.DolbyAtmosPreferenceFragment",
                        lpparam.classLoader,
                        "getheadsetStatus",
                        XC_MethodReplacement.returnConstant(1)
                );
                log("Successfully hooked Android 13 DolbyAtmosPreferenceFragment.getheadsetStatus");
            }
            // Android 14 (SDK 34)
            else if (android.os.Build.VERSION.SDK_INT == 34) {
                // Hook 耳机连接状态检测
                XposedHelpers.findAndHookMethod(
                        "com.lenovo.settings.sound.dolby.DolbyAtmosFragment",
                        lpparam.classLoader,
                        "isHeadsetConnected",
                        XC_MethodReplacement.returnConstant(Boolean.TRUE)
                );

                // Hook 初始化视图，清除摘要显示
                XposedHelpers.findAndHookMethod(
                        "com.lenovo.settings.sound.dolby.DolbyAtmosFragment",
                        lpparam.classLoader,
                        "initView",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                try {
                                    Object preference = XposedHelpers.getObjectField(param.thisObject, "mDolbySwitchPreference");
                                    if (preference != null) {
                                        XposedHelpers.callMethod(preference, "setSummary", new Object[]{null});
                                        log("Successfully cleared Dolby switch preference summary");
                                    }
                                } catch (Throwable t) {
                                    logError("Failed to clear Dolby switch preference summary", t);
                                }
                            }
                        }
                );
                log("Successfully hooked Android 14 DolbyAtmosFragment methods");
            }
            // Android 15 (SDK 35)
            else if (android.os.Build.VERSION.SDK_INT == 35) {
                // Hook 工具类中的耳机连接检测
                XposedHelpers.findAndHookMethod(
                        "com.lenovo.settings.sound.dolby.DolbyAtmosUtils",
                        lpparam.classLoader,
                        "isHeadsetConnected",
                        android.content.Context.class,
                        XC_MethodReplacement.returnConstant(Boolean.TRUE)
                );

                // Hook 控制器更新状态，清除摘要
                XposedHelpers.findAndHookMethod(
                        "com.lenovo.settings.sound.dolby.DolbySwitchPreferenceController",
                        lpparam.classLoader,
                        "updateState",
                        "androidx.preference.Preference",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    if (param.args[0] != null) {
                                        XposedHelpers.callMethod(param.args[0], "setSummary", new Object[]{null});
                                        log("Successfully cleared preference summary in updateState");
                                    }
                                } catch (Throwable t) {
                                    logError("Failed to clear preference summary in updateState", t);
                                }
                            }
                        }
                );
                log("Successfully hooked Android 15 DolbyAtmosUtils and DolbySwitchPreferenceController");
            }
        } catch (Throwable t) {
            logError("Failed to hook Settings package", t);
        }
    }

    /**
     * Hook SystemUI中的杜比音效磁贴
     */
    private void hookSystemUIPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook QDolbyAtmosTile 耳机检测方法
            if (android.os.Build.VERSION.SDK_INT <= 34) {
                XposedHelpers.findAndHookMethod(
                        "com.android.systemui.qs.tiles.QDolbyAtmosTile",
                        lpparam.classLoader,
                        "isHeadSetConnect",
                        XC_MethodReplacement.returnConstant(Boolean.TRUE)
                );
                log("Successfully hooked QDolbyAtmosTile.isHeadSetConnect (SDK <= 34)");
            } else {
                XposedHelpers.findAndHookMethod(
                        "com.android.systemui.qs.tiles.QDolbyAtmosTile",
                        lpparam.classLoader,
                        "isHeadSetConnect$2",
                        XC_MethodReplacement.returnConstant(Boolean.TRUE)
                );
                log("Successfully hooked QDolbyAtmosTile.isHeadSetConnect$2 (SDK > 34)");
            }

            // Hook 详情视图中的耳机检测
            XposedHelpers.findAndHookMethod(
                    "com.android.systemui.qs.tiles.QDolbyAtmosDetailView",
                    lpparam.classLoader,
                    "isHeadSetConnect",
                    XC_MethodReplacement.returnConstant(Boolean.TRUE)
            );
            log("Successfully hooked QDolbyAtmosDetailView.isHeadSetConnect");

        } catch (Throwable t) {
            logError("Failed to hook SystemUI package", t);
        }
    }

    /**
     * Hook 游戏服务中的杜比音效处理
     */
    private void hookGameServicePackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.zui.game.service.util.DolbyUtils",
                    lpparam.classLoader,
                    "handleDolbyGameSound",
                    android.content.Context.class,
                    Integer.TYPE,
                    XC_MethodReplacement.returnConstant(null)
            );
            log("Successfully hooked DolbyUtils.handleDolbyGameSound - disabled game sound processing");
        } catch (Throwable t) {
            logError("Failed to hook GameService package", t);
        }
    }
}
