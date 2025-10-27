package com.qimian233.ztool.hook.modules.setting;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.HashMap;

/**
 * Split Screen强制分屏功能Hook模块
 * 通过Hook OneModeService清空分屏黑名单，实现强制分屏功能
 */
public class SplitScreenMandatory extends BaseHookModule {

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
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("android".equals(packageName)) {
            hookSystemProcess(lpparam);
        }
    }

    /**
     * Hook系统进程中的OneModeService
     */
    private void hookSystemProcess(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.wm.OneModeService",
                    lpparam.classLoader,
                    "initLocalBlackList",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // 检查模块是否启用
                            if (!isEnabled()) {
                                return;
                            }

                            // 获取OneModeService实例
                            Object instance = param.thisObject;

                            // 获取mLocalmap字段（存储分屏黑名单的HashMap）
                            HashMap<?, ?> mLocalmap = (HashMap<?, ?>) XposedHelpers.getObjectField(instance, "mLocalmap");

                            // 清空mLocalmap，确保分屏黑名单为空
                            if (mLocalmap != null) {
                                mLocalmap.clear();
                                log("Successfully cleared split screen blacklist");
                            }

                            // 跳过原方法执行，防止从XML文件读取黑名单数据
                            param.setResult(null);
                        }
                    }
            );

            log("Successfully hooked OneModeService.initLocalBlackList");

        } catch (Throwable t) {
            logError("Failed to hook OneModeService", t);
        }
    }
}
