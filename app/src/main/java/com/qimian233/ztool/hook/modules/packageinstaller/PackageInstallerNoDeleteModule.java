package com.qimian233.ztool.hook.modules.packageinstaller;

import com.qimian233.ztool.hook.base.BaseHookModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 禁用应用安装后删除APK提示模块
 * 拦截系统包安装器(Com.android.packageinstaller)，修改默认的"安装完成后删除安装包"行为
 * 实现首次安装后默认不勾选删除安装包选项，避免误删安装文件
 */
public class PackageInstallerNoDeleteModule extends BaseHookModule {

    public PackageInstallerNoDeleteModule() {}

    @Override
    public String getModuleName() {
        return "package_installer_disable_delete";  // 模块唯一标识，用于配置管理
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.android.packageinstaller"  // 系统包安装器应用
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if ("com.android.packageinstaller".equals(packageName)) {
            hookPackageInstaller(classLoader);
        }
    }

    /**
     * Hook系统包安装器的核心逻辑
     * 拦截InstallSuccessExtra类的initView方法，修改默认的删除安装包行为
     */
    private void hookPackageInstaller(ClassLoader classLoader) {
        try {
            log("Starting hook for package installer");

            // Hook InstallSuccessExtra.initView()方法
            Class<?> installSuccessExtraClass = classLoader.loadClass(
                    "com.android.packageinstaller.InstallSuccessExtra");
            Method initView = installSuccessExtraClass.getDeclaredMethod("initView");
            this.xposed.hook(initView).intercept(chain -> {
                Object result = chain.proceed();

                if (!isEnabled()) {
                    return result; // 根据配置动态判断是否启用
                }

                try {
                    log("Inside initView method for package installer");

                    // 获取当前InstallSuccessExtra实例
                    Object instance = chain.getThisObject();

                    // 检查是否是配置变更（如屏幕旋转）
                    Field isConfigChangeField = instance.getClass().getDeclaredField("isConfigChanage");
                    isConfigChangeField.setAccessible(true);
                    boolean isConfigChange = isConfigChangeField.getBoolean(instance);

                    // 如果是首次创建Activity（非配置变更）
                    if (!isConfigChange) {
                        log("Modifying default behavior: do not delete APK after install");

                        // 修改mDeleteApk为false，表示默认不删除安装包
                        Field mDeleteApkField = instance.getClass().getDeclaredField("mDeleteApk");
                        mDeleteApkField.setAccessible(true);
                        mDeleteApkField.setBoolean(instance, false);

                        // 更新UI复选框状态
                        try {
                            Field checkBoxField = instance.getClass().getDeclaredField("del_check_box");
                            checkBoxField.setAccessible(true);
                            Object checkBox = checkBoxField.get(instance);
                            if (checkBox instanceof android.widget.CheckBox) {
                                ((android.widget.CheckBox) checkBox).setChecked(false);
                                log("Successfully updated UI checkbox state");
                            }
                        } catch (Throwable uiError) {
                            logError("Failed to update checkbox UI", uiError);
                            // UI更新失败不影响核心功能，继续执行
                        }
                    }
                } catch (Throwable t) {
                    logError("Error in afterHookedMethod for initView", t);
                }

                return result;
            });

            log("Successfully hooked InstallSuccessExtra.initView()");
        } catch (Throwable t) {
            logError("Failed to initialize package installer hook", t);
        }
    }
}
