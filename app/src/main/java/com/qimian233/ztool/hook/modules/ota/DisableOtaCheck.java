package com.qimian233.ztool.hook.modules.ota;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.view.Menu;
import android.view.MenuItem;

/**
 * 禁用联想OTA检查Hook模块
 * 功能：强制显示本地安装菜单项，绕过计数器检查逻辑
 */
public class DisableOtaCheck extends BaseHookModule {

    private static final String TARGET_PACKAGE = "com.lenovo.ota";
    private static final String MAIN_ACTIVITY = "com.lenovo.row.ota.core.d.ui.MainActivity";

    public DisableOtaCheck() {}

    @Override
    public String getModuleName() {
        return "disable_OtaCheck";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }

        log("开始挂钩 com.lenovo.ota - 开启本地安装服务");

        try {
            hookOnCreateOptionsMenu(classLoader);
            hookOnPrepareOptionsMenu(classLoader);
            hookClickCountCallBack(classLoader);

            log("所有OTA检查禁用钩子设置完成");
        } catch (Exception e) {
            logError("初始化OTA检查禁用模块时出错", e);
        }
    }

    /**
     * 钩住 onCreateOptionsMenu 方法，确保菜单项不被默认隐藏
     */
    private void hookOnCreateOptionsMenu(ClassLoader classLoader) {
        try {
            Class<?> mainActivityClass = classLoader.loadClass(MAIN_ACTIVITY);
            Method onCreateOptionsMenu = mainActivityClass.getDeclaredMethod("onCreateOptionsMenu", Menu.class);
            Class<?> rClass = classLoader.loadClass("com.lenovo.ota.R$id");

            this.xposed.hook(onCreateOptionsMenu).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Menu menu = (Menu) chain.getArg(0);
                    // 找到本地安装菜单项并设置为可见
                    Field menuLocalInstallField = rClass.getDeclaredField("memu_localInstall");
                    menuLocalInstallField.setAccessible(true);
                    int menuLocalInstallId = menuLocalInstallField.getInt(null);

                    MenuItem localInstallItem = menu.findItem(menuLocalInstallId);
                    if (localInstallItem != null) {
                        localInstallItem.setVisible(true);
                        log("在 onCreateOptionsMenu 中启用本地安装菜单");
                    }
                } catch (Exception e) {
                    logError("onCreateOptionsMenu 钩子执行错误", e);
                }
                return result;
            });
        } catch (Exception e) {
            logError("设置 onCreateOptionsMenu 钩子失败", e);
        }
    }

    /**
     * 钩住 onPrepareOptionsMenu 方法，绕过条件检查
     */
    private void hookOnPrepareOptionsMenu(ClassLoader classLoader) {
        try {
            Class<?> mainActivityClass = classLoader.loadClass(MAIN_ACTIVITY);
            Method onPrepareOptionsMenu = mainActivityClass.getDeclaredMethod("onPrepareOptionsMenu", Menu.class);
            Class<?> rClass = classLoader.loadClass("com.lenovo.ota.R$id");

            this.xposed.hook(onPrepareOptionsMenu).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Menu menu = (Menu) chain.getArg(0);
                    // 通过反射获取菜单项ID
                    Field menuLocalInstallField = rClass.getDeclaredField("memu_localInstall");
                    menuLocalInstallField.setAccessible(true);
                    int menuLocalInstallId = menuLocalInstallField.getInt(null);

                    MenuItem localInstallItem = menu.findItem(menuLocalInstallId);
                    if (localInstallItem != null) {
                        // 强制设置为可见，绕过原有的 mCount >= 6 检查
                        localInstallItem.setVisible(true);
                        log("在 onPrepareOptionsMenu 中强制显示本地安装菜单");
                    }

                    // 同时设置计数器为6，确保其他相关逻辑正常工作
                    Field mCountField = mainActivityClass.getDeclaredField("mCount");
                    mCountField.setAccessible(true);
                    mCountField.setInt(chain.getThisObject(), 6);

                } catch (Exception e) {
                    logError("onPrepareOptionsMenu 钩子执行错误", e);
                }
                return result;
            });
        } catch (Exception e) {
            logError("设置 onPrepareOptionsMenu 钩子失败", e);
        }
    }

    /**
     * 钩住 clickCountCallBack 方法，确保计数器始终满足条件
     */
    private void hookClickCountCallBack(ClassLoader classLoader) {
        try {
            Class<?> mainActivityClass = classLoader.loadClass(MAIN_ACTIVITY);
            Method clickCountCallBack = mainActivityClass.getDeclaredMethod("clickCountCallBack");
            Field mCountField = mainActivityClass.getDeclaredField("mCount");
            mCountField.setAccessible(true);

            this.xposed.hook(clickCountCallBack).intercept(chain -> {
                // 在调用前直接设置计数器为6
                mCountField.setInt(chain.getThisObject(), 6);
                log("在 clickCountCallBack 前强制设置计数器为6");
                return chain.proceed();
            });
        } catch (Exception e) {
            logError("设置 clickCountCallBack 钩子失败", e);
        }
    }
}
