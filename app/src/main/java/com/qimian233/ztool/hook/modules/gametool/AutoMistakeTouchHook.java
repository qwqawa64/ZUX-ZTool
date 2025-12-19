package com.qimian233.ztool.hook.modules.gametool;

import android.content.Context;
import android.text.TextUtils;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.PreferenceHelper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 自动开启游戏防误触功能Hook模块
 * 为特定游戏自动开启ZUI游戏助手的防误触功能
 */
public class AutoMistakeTouchHook extends BaseHookModule {

    private static final String TARGET_PACKAGE = "com.zui.game.service";
    private static final String SETTINGS_UTIL_CLASS = "com.zui.util.SettingsValueUtilKt";

    // 配置键名常量
    private static final String KEY_MISTAKE_TOUCH_WHITELIST_ENABLED = "MistakeTouchWhiteList";
    private static final String KEY_MISTAKE_TOUCH_WHITELIST_GAMES = "MistakeTouchWhiteListGame";

    // 配置工具类实例
    private final PreferenceHelper mPrefHelper = PreferenceHelper.getInstance();

    @Override
    public String getModuleName() {
        return "auto_mistake_touch";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if (TARGET_PACKAGE.equals(packageName)) {
            hookGameService(lpparam);
        }
    }

    private void hookGameService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook GameHelperViewController 的初始化
            hookGameHelperViewController(lpparam);

            // Hook ItemBlockMistakeTouch 的状态同步
            hookItemBlockMistakeTouch(lpparam);

            // Hook LiveData 的状态同步
            hookLiveDataPostValue(lpparam);

            log("AutoMistakeTouch Hook initialized successfully");

        } catch (Throwable e) {
            logError("Hook GameService failed", e);
        }
    }

    private void hookGameHelperViewController(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            String className = "com.zui.game.service.ui.GameHelperViewController";

            // Hook setPkgName 方法（游戏启动时调用）
            XposedHelpers.findAndHookMethod(className, lpparam.classLoader,
                    "setPkgName",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String pkgName = (String) param.args[0];
                            if (pkgName != null && !pkgName.isEmpty()) {
                                // 检查是否为白名单游戏
                                if (isTargetGame(pkgName)) {
                                    log("Target game detected: " + pkgName);

                                    // 延迟设置，确保游戏助手完全初始化
                                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> enableMistakeTouchWithSync(param.thisObject), 1000);
                                }
                            }
                        }
                    });

            log("Successfully hooked GameHelperViewController");

        } catch (Throwable e) {
            logError("Hook GameHelperViewController failed", e);
        }
    }

    private void hookItemBlockMistakeTouch(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            String itemClassName = "com.zui.game.service.sys.item.ItemBlockMistakeTouch";

            // Hook change2Status 方法，确保状态正确同步
            XposedHelpers.findAndHookMethod(itemClassName, lpparam.classLoader,
                    "change2Status",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int targetStatus = (int) param.args[0];
                            log("ItemBlockMistakeTouch.change2Status called with: " + targetStatus);
                        }
                    });

            log("Successfully hooked ItemBlockMistakeTouch");

        } catch (Throwable e) {
            logError("Hook ItemBlockMistakeTouch failed", e);
        }
    }

    private void hookLiveDataPostValue(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook LiveData的postValue方法，确保状态同步
            XposedHelpers.findAndHookMethod("androidx.lifecycle.MutableLiveData", lpparam.classLoader,
                    "postValue",
                    Object.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object value = param.args[0];
                            if (value instanceof Integer) {
                                int status = (Integer) value;
                                // 检查这个LiveData是否是防误触的LiveData
                                String stackTrace = android.util.Log.getStackTraceString(new Throwable());
                                if (stackTrace.contains("ItemBlockMistakeTouch") ||
                                        stackTrace.contains("change2Status")) {
                                    log("LiveData postValue for mistake touch: " + status);
                                }
                            }
                        }
                    });

            log("Successfully hooked LiveData");

        } catch (Throwable e) {
            logError("Hook LiveData failed", e);
        }
    }

    private void enableMistakeTouchWithSync(Object gameHelper) {
        try {
            // 获取Context
            Object context = XposedHelpers.callMethod(gameHelper, "getContext");
            if (context == null) {
                context = XposedHelpers.callMethod(gameHelper, "getNotNullContext");
            }

            if (context instanceof Context) {
                // 先获取当前系统设置状态
                int currentStatus = getCurrentMistakeTouchStatus((Context) context);
                log("Current mistake touch status: " + currentStatus);

                if (currentStatus != 1) {
                    // 通过游戏助手内部方法设置，确保状态同步
                    setMistakeTouchThroughGameHelper(gameHelper);

                    log("Auto-enabled mistake touch with sync");
                } else {
                    log("Mistake touch already enabled");
                }
            }

        } catch (Throwable e) {
            logError("Enable mistake touch with sync failed", e);
        }
    }

    private void setMistakeTouchThroughGameHelper(Object gameHelper) {
        try {
            // 调用游戏助手内部的changeMistouchStatus方法
            XposedHelpers.callMethod(gameHelper, "changeMistouchStatus", true);

            // 同时确保ItemBlockMistakeTouch的状态同步
            Object mItemBlockMistakeTouch = XposedHelpers.getObjectField(gameHelper, "mItemBlockMistakeTouch");
            if (mItemBlockMistakeTouch != null) {
                XposedHelpers.callMethod(mItemBlockMistakeTouch, "change2Status", 0);
            }

        } catch (Throwable e) {
            logError("Set through game helper failed", e);
        }
    }

    private int getCurrentMistakeTouchStatus(Context context) {
        try {
            // 使用反射调用SettingsValueUtilKt.getPreventMisoperation
            Class<?> settingsUtilClass = Class.forName(SETTINGS_UTIL_CLASS);
            java.lang.reflect.Method method = settingsUtilClass.getMethod("getPreventMisoperation", Context.class);
            Object result = method.invoke(null, context);
            if (result != null) {
                return (Integer) result;
            } else {
                log("getPreventMisoperation returned null");
                return -1;
            }
        } catch (Throwable e) {
            logError("Get current status failed", e);
            return -1;
        }
    }

    /**
     * 检查防误触白名单功能是否启用
     */
    private boolean isMistakeTouchWhiteListEnabled() {
        return mPrefHelper.getBoolean(KEY_MISTAKE_TOUCH_WHITELIST_ENABLED, false);
    }

    /**
     * 获取防误触白名单中的所有游戏包名
     */
    private String[] getMistakeTouchWhiteListGames() {
        return mPrefHelper.getStringArray(KEY_MISTAKE_TOUCH_WHITELIST_GAMES);
    }

    /**
     * 检查指定游戏是否在防误触白名单中
     */
    private boolean isGameInMistakeTouchWhiteList(String packageName) {
        String[] whiteListGames = getMistakeTouchWhiteListGames();
        for (String gamePackage : whiteListGames) {
            if (TextUtils.isEmpty(gamePackage)) {
                continue;
            }
            if (gamePackage.trim().equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否为特定目标游戏
     * 逻辑：如果白名单功能启用，则只对白名单中的游戏生效
     *       如果白名单功能未启用，则对所有游戏生效
     */
    private boolean isTargetGame(String packageName) {
        if (isMistakeTouchWhiteListEnabled()) {
            // 白名单功能启用，只对白名单中的游戏生效
            return isGameInMistakeTouchWhiteList(packageName);
        } else {
            // 白名单功能未启用，对所有游戏生效
            return true;
        }
    }
}
