package com.qimian233.ztool.hook.modules.packageinstaller;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 禁用应用安装后删除APK提示模块
 * 拦截系统包安装器(Com.android.packageinstaller)，修改默认的"安装完成后删除安装包"行为
 * 实现首次安装后默认不勾选删除安装包选项，避免误删安装文件
 */
public class PackageInstallerNoDeleteModule extends BaseHookModule {

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
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("com.android.packageinstaller".equals(lpparam.packageName)) {
            hookPackageInstaller(lpparam);
        }
    }

    /**
     * Hook系统包安装器的核心逻辑
     * 拦截InstallSuccessExtra类的initView方法，修改默认的删除安装包行为
     */
    private void hookPackageInstaller(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Starting hook for package installer");

            // Hook InstallSuccessExtra.initView()方法
            XposedHelpers.findAndHookMethod(
                    "com.android.packageinstaller.InstallSuccessExtra",
                    lpparam.classLoader,
                    "initView",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!isEnabled()) {
                                return; // 根据配置动态判断是否启用
                            }

                            try {
                                log("Inside initView method for package installer");

                                // 获取当前InstallSuccessExtra实例
                                Object instance = param.thisObject;

                                // 检查是否是配置变更（如屏幕旋转）
                                boolean isConfigChange = XposedHelpers.getBooleanField(instance, "isConfigChanage");

                                // 如果是首次创建Activity（非配置变更）
                                if (!isConfigChange) {
                                    log("Modifying default behavior: do not delete APK after install");

                                    // 修改mDeleteApk为false，表示默认不删除安装包
                                    XposedHelpers.setBooleanField(instance, "mDeleteApk", false);

                                    // 更新UI复选框状态
                                    try {
                                        Object checkBox = XposedHelpers.getObjectField(instance, "del_check_box");
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
                        }
                    }
            );

            log("Successfully hooked InstallSuccessExtra.initView()");
        } catch (Throwable t) {
            logError("Failed to initialize package installer hook", t);
        }
    }
}
