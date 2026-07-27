package com.qimian233.ztool.hook.modules.packageinstaller;

import com.qimian233.ztool.hook.base.BaseHookModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 跳过包安装器警告页面Hook模块
 * 自动点击安装按钮，跳过用户确认步骤
 */
public class Hook_Skip_WarnPage extends BaseHookModule {

    public Hook_Skip_WarnPage() {}

    @Override
    public String getModuleName() {
        return "Skip_WarnPage";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.android.packageinstaller"
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        hookPackageInstallerActivity(classLoader);
    }

    private void hookPackageInstallerActivity(ClassLoader classLoader) {
        try {
            // Hook onResume 方法，在界面显示后执行
            Class<?> activityExtraClass = classLoader.loadClass(
                    "com.android.packageinstaller.PackageInstallerActivityExtra");
            Method onResume = activityExtraClass.getDeclaredMethod("onResume");
            hookWithId(onResume, "on_resume", chain -> {
                Object result = chain.proceed();

                final Object activity = chain.getThisObject();

                // 延迟执行，确保界面完全加载
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        // 直接调用 handleDirectInstallInFindSameAppCase 方法
                        activity.getClass().getDeclaredMethod("handleDirectInstallInFindSameAppCase")
                                .invoke(activity);
                        log("Successfully called handleDirectInstallInFindSameAppCase");
                    } catch (Exception e) {
                        // 如果上面的方法不存在，尝试调用 onDirectInstall 方法
                        try {
                            activity.getClass().getDeclaredMethod("onDirectInstall")
                                    .invoke(activity);
                            log("Successfully called onDirectInstall");
                        } catch (Exception e2) {
                            logError("Both installation methods failed", e2);
                        }
                    }
                }, 50); // 立刻执行

                return result;
            });

            log("Successfully hooked PackageInstallerActivityExtra.onResume");
        } catch (Throwable t) {
            logError("Failed to hook PackageInstallerActivityExtra", t);
        }
    }
}
