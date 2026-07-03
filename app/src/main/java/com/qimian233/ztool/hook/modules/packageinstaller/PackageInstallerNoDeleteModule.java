package com.qimian233.ztool.hook.modules.packageinstaller;

import android.annotation.SuppressLint;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 禁用应用安装后删除APK提示模块
 * 拦截系统包安装器(com.android.packageinstaller)，修改默认的"安装完成后删除安装包"行为
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
     * 拦截InstallSuccessExtra类的initView方法，修改默认的删除安装包行为。
     * 新版 PackageInstaller 将 CheckBox 改为 initView() 中的局部变量，
     * 且 OnCheckedChangeListener 会直接覆写 mDeleteApk，因此需要：
     * 1. 强制 mDeleteApk = false
     * 2. 通过 findViewById 定位 CheckBox 并替换其 listener，防止用户手动勾选后覆盖布尔值
     * 3. 兜底：Hook clearCachedApkIfNeededAndFinish 再次确保 mDeleteApk = false
     */
    private void hookPackageInstaller(ClassLoader classLoader) {
        try {
            log("Starting hook for package installer");

            @SuppressLint("PrivateApi") Class<?> installSuccessExtraClass = classLoader.loadClass(
                    "com.android.packageinstaller.InstallSuccessExtra");

            // --- Hook 1: initView() — 首次设置 + UI 修复 ---
            Method initView = installSuccessExtraClass.getDeclaredMethod("initView");
            Field mDeleteApkField = installSuccessExtraClass.getDeclaredField("mDeleteApk");
            mDeleteApkField.setAccessible(true);

            this.xposed.hook(initView).intercept(chain -> {
                Object result = chain.proceed();

                if (!isEnabled()) {
                    return result;
                }

                try {
                    Object instance = chain.getThisObject();
                    log("Inside initView method for package installer");

                    // 强制 mDeleteApk = false（无论是否配置变更）
                    mDeleteApkField.setBoolean(instance, false);

                    // 通过 findViewById 定位 CheckBox（新版是局部变量，不能通过字段反射）
                    try {
                        android.app.Activity activity = (android.app.Activity) instance;
                        @SuppressLint("DiscouragedApi") int checkBoxId = activity.getResources().getIdentifier(
                                "del_check_box", "id", "com.android.packageinstaller");
                        if (checkBoxId != 0) {
                            android.view.View view = activity.findViewById(checkBoxId);
                            if (view instanceof android.widget.CheckBox) {
                                android.widget.CheckBox checkBox = (android.widget.CheckBox) view;
                                // 更新 UI 为未勾选状态
                                checkBox.setChecked(false);
                                // 替换监听器：防止用户手动勾选后覆盖 mDeleteApk
                                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                                    try {
                                        mDeleteApkField.setBoolean(instance, false);
                                    } catch (Throwable ignored) {}
                                    // 永远显示未勾选
                                    if (isChecked) {
                                        buttonView.setChecked(false);
                                    }
                                });
                                log("Successfully updated UI checkbox and replaced listener");
                            }
                        } else {
                            log("CheckBox resource ID 'del_check_box' not found, may be new version");
                        }
                    } catch (Throwable uiError) {
                        logError("Failed to update checkbox UI", uiError);
                    }
                } catch (Throwable t) {
                    logError("Error in afterHookedMethod for initView", t);
                }

                return result;
            });

            log("Successfully hooked InstallSuccessExtra.initView()");

            // --- Hook 2: clearCachedApkIfNeededAndFinish() — 兜底防护 ---
            // 该方法在删除线程执行完毕后被调用，或在 onStop 中被调用。
            // 再次确保 mDeleteApk = false，作为多层防护。
            try {
                Method clearMethod = installSuccessExtraClass.getDeclaredMethod(
                        "clearCachedApkIfNeededAndFinish");
                this.xposed.hook(clearMethod).intercept(chain -> {
                    if (isEnabled()) {
                        try {
                            mDeleteApkField.setBoolean(chain.getThisObject(), false);
                        } catch (Throwable ignored) {}
                    }
                    return chain.proceed();
                });
                log("Successfully hooked InstallSuccessExtra.clearCachedApkIfNeededAndFinish()");
            } catch (Throwable t) {
                logError("Failed to hook clearCachedApkIfNeededAndFinish", t);
            }

        } catch (Throwable t) {
            logError("Failed to initialize package installer hook", t);
        }
    }
}
