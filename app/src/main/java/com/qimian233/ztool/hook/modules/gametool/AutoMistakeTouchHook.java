package com.qimian233.ztool.hook.modules.gametool;

import android.content.Context;
import android.text.TextUtils;

import com.qimian233.ztool.data.keys.ScopeKeys;
import com.qimian233.ztool.data.keys.PreferenceKeys;
import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 自动开启游戏防误触功能Hook模块
 * 为特定游戏自动开启ZUI游戏助手的防误触功能
 */
public class AutoMistakeTouchHook extends AppHookModule {

    private static final String TARGET_PACKAGE = ScopeKeys.GAME_SERVICE.packageName;
    private static final String SETTINGS_UTIL_CLASS = "com.zui.util.SettingsValueUtilKt";

    // 配置工具类实例 - use getPref* methods from base class

    // 持久化拦截标志：当通过本Hook自动开启防误触时，阻止写入Settings.Global
    private volatile boolean mBlockPersistence = false;

    public AutoMistakeTouchHook() {}

    @Override
    public String getModuleName() {
        return "auto_mistake_touch";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (TARGET_PACKAGE.equals(packageName)) {
            hookGameService(classLoader);
        }
    }

    private void hookGameService(ClassLoader classLoader) {
        try {
            // Hook GameHelperViewController 的初始化
            hookGameHelperViewController(classLoader);

            // Hook ItemBlockMistakeTouch 的状态同步
            hookItemBlockMistakeTouch(classLoader);

            // Hook LiveData 的状态同步
            hookLiveDataPostValue(classLoader);

            // Hook setPreventMisoperation 持久化拦截
            hookPreventMisoperationPersistence(classLoader);

            logger.info("AutoMistakeTouch Hook initialized successfully");

        } catch (Throwable e) {
            logger.error("Hook GameService failed", e);
        }
    }

    private void hookGameHelperViewController(final ClassLoader classLoader) {
        try {
            String className = "com.zui.game.service.ui.GameHelperViewController";
            Class<?> controllerClass = classLoader.loadClass(className);

            // Hook setPkgName 方法（游戏启动时调用）
            Method setPkgNameMethod = controllerClass.getDeclaredMethod("setPkgName", String.class);
            hookWithId(setPkgNameMethod, "set_pkg_name", chain -> {
                chain.proceed();
                String pkgName = (String) chain.getArg(0);
                if (pkgName != null && !pkgName.isEmpty()) {
                    // 检查是否为白名单游戏
                    if (isTargetGame(pkgName)) {
                        logger.debug("Target game detected: " + pkgName);

                        // 延迟设置，确保游戏助手完全初始化
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                                () -> enableMistakeTouchWithSync(chain.getThisObject()), 1000);
                    }
                }
                return null;
            });

            logger.info("Successfully hooked GameHelperViewController");

        } catch (Throwable e) {
            logger.error("Hook GameHelperViewController failed", e);
        }
    }

    private void hookItemBlockMistakeTouch(ClassLoader classLoader) {
        try {
            String itemClassName = "com.zui.game.service.sys.item.ItemBlockMistakeTouch";
            Class<?> itemClass = classLoader.loadClass(itemClassName);

            // Hook change2Status 方法，确保状态正确同步
            Method change2StatusMethod = itemClass.getDeclaredMethod("change2Status", int.class);
            hookWithId(change2StatusMethod, "change2_status", chain -> {
                int targetStatus = (int) chain.getArg(0);
                logger.debug("ItemBlockMistakeTouch.change2Status called with: " + targetStatus);
                return chain.proceed();
            });

            logger.info("Successfully hooked ItemBlockMistakeTouch");

        } catch (Throwable e) {
            logger.error("Hook ItemBlockMistakeTouch failed", e);
        }
    }

    private void hookLiveDataPostValue(ClassLoader classLoader) {
        try {
            // Hook LiveData的postValue方法，确保状态同步
            Class<?> liveDataClass = classLoader.loadClass("androidx.lifecycle.MutableLiveData");
            Method postValueMethod = liveDataClass.getDeclaredMethod("postValue", Object.class);
            hookWithId(postValueMethod, "post_value", chain -> {
                Object value = chain.getArg(0);
                if (value instanceof Integer) {
                    int status = (Integer) value;
                    // 检查这个LiveData是否是防误触的LiveData
                    String stackTrace = android.util.Log.getStackTraceString(new Throwable());
                    if (stackTrace.contains("ItemBlockMistakeTouch") ||
                            stackTrace.contains("change2Status")) {
                        logger.debug("LiveData postValue for mistake touch: " + status);
                    }
                }
                return chain.proceed();
            });

            logger.info("Successfully hooked LiveData");

        } catch (Throwable e) {
            logger.error("Hook LiveData failed", e);
        }
    }

    private void hookPreventMisoperationPersistence(ClassLoader classLoader) {
        try {
            // Hook SettingsValueUtilKt.setPreventMisoperation 静态方法
            // 当通过本Hook自动开启防误触时(mBlockPersistence=true)，阻止写入Settings.Global
            // 这样防误触行为仅在内存态生效，关闭Hook后自动恢复原始设置
            Class<?> settingsUtilClass = classLoader.loadClass(SETTINGS_UTIL_CLASS);
            Method setPreventMethod = settingsUtilClass.getDeclaredMethod(
                    "setPreventMisoperation", Context.class, int.class);
            hookWithId(setPreventMethod, "set_prevent", chain -> {
                if (mBlockPersistence) {
                    logger.debug("Blocked setPreventMisoperation persistence");
                    return null;
                }
                return chain.proceed();
            });

            logger.info("Successfully hooked setPreventMisoperation for anti-persistence");

        } catch (Throwable e) {
            logger.error("Hook setPreventMisoperation failed", e);
        }
    }

    private void enableMistakeTouchWithSync(Object gameHelper) {
        try {
            // 获取Context
            Method getContextMethod = gameHelper.getClass().getMethod("getContext");
            Object context = getContextMethod.invoke(gameHelper);
            if (context == null) {
                Method getNotNullContextMethod = gameHelper.getClass().getMethod("getNotNullContext");
                context = getNotNullContextMethod.invoke(gameHelper);
            }

            if (context instanceof Context) {
                // 先获取当前系统设置状态
                int currentStatus = getCurrentMistakeTouchStatus((Context) context);
                logger.debug("Current mistake touch status: " + currentStatus);

                if (currentStatus != 1) {
                    // 通过游戏助手内部方法设置，确保状态同步
                    setMistakeTouchThroughGameHelper(gameHelper);

                    logger.debug("Auto-enabled mistake touch with sync");
                } else {
                    logger.debug("Mistake touch already enabled");
                }
            }

        } catch (Throwable e) {
            logger.error("Enable mistake touch with sync failed", e);
        }
    }

    private void setMistakeTouchThroughGameHelper(Object gameHelper) {
        try {
            // 开启持久化拦截，阻止 changeMistouchStatus 异步 observer
            // 将防误触状态写入 Settings.Global
            mBlockPersistence = true;

            // 调用游戏助手内部的changeMistouchStatus方法
            Method changeMistouchStatusMethod = gameHelper.getClass().getMethod("changeMistouchStatus", boolean.class);
            changeMistouchStatusMethod.invoke(gameHelper, true);

            // 同时确保ItemBlockMistakeTouch的状态同步
            // 注意：mItemBlockMistakeTouch 是 Kotlin Lazy 委托，必须通过 getter 获取
            Method getMItemMethod = gameHelper.getClass().getMethod("getMItemBlockMistakeTouch");
            Object mItemBlockMistakeTouch = getMItemMethod.invoke(gameHelper);
            if (mItemBlockMistakeTouch != null) {
                Method change2StatusMethod = mItemBlockMistakeTouch.getClass().getMethod("change2Status", int.class);
                change2StatusMethod.invoke(mItemBlockMistakeTouch, 0);
            }

            // 延迟清除拦截标志，确保所有异步 observer 回调执行完毕
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                mBlockPersistence = false;
                logger.debug("Persistence block cleared");
            }, 3000);

        } catch (Throwable e) {
            mBlockPersistence = false;
            logger.error("Set through game helper failed", e);
        }
    }

    private int getCurrentMistakeTouchStatus(Context context) {
        try {
            // 使用反射调用SettingsValueUtilKt.getPreventMisoperation
            Class<?> settingsUtilClass = Class.forName(SETTINGS_UTIL_CLASS);
            Method method = settingsUtilClass.getMethod("getPreventMisoperation", Context.class);
            Object result = method.invoke(null, context);
            if (result != null) {
                return (Integer) result;
            } else {
                logger.warn("getPreventMisoperation returned null");
                return -1;
            }
        } catch (Throwable e) {
            logger.error("Get current status failed", e);
            return -1;
        }
    }

    /**
     * 检查防误触白名单功能是否启用
     */
    private boolean isMistakeTouchWhiteListEnabled() {
        try {
            return getRemotePreferences().getBoolean(PreferenceKeys.MISTAKE_TOUCH_WHITE_LIST.name, false);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 获取防误触白名单中的所有游戏包名
     */
    private String[] getMistakeTouchWhiteListGames() {
        String value;
        try {
            value = getRemotePreferences().getString(PreferenceKeys.MISTAKE_TOUCH_WHITE_LIST_GAME.name, "");
        } catch (Throwable t) {
            value = "";
        }
        if (TextUtils.isEmpty(value)) return new String[0];
        return value.split(",");
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
