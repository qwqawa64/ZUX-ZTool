package com.qimian233.ztool.hook.modules.setting;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * Split Screen强制分屏功能Hook模块
 * 通过Hook OneModeService清空分屏黑名单，实现强制分屏功能
 */
public class SplitScreenMandatory extends BaseHookModule {

    public SplitScreenMandatory() {}

    @Override
    public String getModuleName() {
        return "Split_Screen_mandatory";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "android",  // 系统进程
                "com.android.settings"  // 设置应用
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if ("android".equals(packageName)) {
            hookSystemProcess(classLoader);
        }
    }

    /**
     * Hook系统进程中的OneModeService
     */
    private void hookSystemProcess(ClassLoader classLoader) {
        try {
            Method m = classLoader
                    .loadClass("com.android.server.wm.OneModeService")
                    .getDeclaredMethod("initLocalBlackList");
            hookWithId(m, "hook_50", chain -> {
                // 检查模块是否启用
                if (!isEnabled()) {
                    return chain.proceed();
                }

                // 获取OneModeService实例
                Object instance = chain.getThisObject();

                // 获取mLocalmap字段（存储分屏黑名单的HashMap）
                Field field = instance.getClass().getDeclaredField("mLocalmap");
                field.setAccessible(true);
                HashMap<?, ?> mLocalmap = (HashMap<?, ?>) field.get(instance);

                // 清空mLocalmap，确保分屏黑名单为空
                if (mLocalmap != null) {
                    mLocalmap.clear();
                    logger.debug("Successfully cleared split screen blacklist");
                }

                // 跳过原方法执行，防止从XML文件读取黑名单数据
                return null;
            });

            logger.info("Successfully hooked OneModeService.initLocalBlackList");

        } catch (Throwable t) {
            logger.error("Failed to hook OneModeService", t);
        }
    }
}
