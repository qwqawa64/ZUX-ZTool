package com.qimian233.ztool.hook;

import android.os.Build;
import android.util.Log;

import com.qimian233.ztool.hook.base.HookManager;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed模块主入口
 * 负责初始化和管理所有Hook模块
 */
public class HookInit implements IXposedHookLoadPackage {

    private static final String TAG = "ZTool-Hook";

    static {
        // 预初始化Hook管理器
        HookManager.initialize();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("");
            } catch (Throwable t) {
                Log.e(TAG, "HiddenApiBypass 初始化失败", t);
            }
        }

        if (lpparam.packageName.equals("com.qimian233.ztool")) {
            Log.d(TAG, "检测到自身应用，开始Hook自检测方法");
            hookSelfStatus(lpparam);
        }

        HookManager.handleLoadPackage(lpparam);
    }

    /**
     * Hook 自身 App 的状态检测方法
     */
    private void hookSelfStatus(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.qimian233.ztool.HomeFragment", // 确保类名路径正确，不要混淆此 Fragment
                    lpparam.classLoader,
                    "isModuleActive",
                    XC_MethodReplacement.returnConstant(true)
            );
            Log.i(TAG, "模块自检测Hook成功");
        } catch (Throwable t) {
            // 这里使用 XposedBridge.log 也可以，但在 LSPosed 环境下 Log.e 也能看到
            Log.e(TAG, "模块自检测Hook失败: " + t.getMessage());
        }
    }
}
