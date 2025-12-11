package com.qimian233.ztool.hook;

import static android.content.ContentValues.TAG;

import android.util.Log;

import com.qimian233.ztool.hook.base.HookManager;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed模块主入口
 * 负责初始化和管理所有Hook模块
 */
public class HookInit implements IXposedHookLoadPackage {

    static {
        // 预初始化Hook管理器
        HookManager.initialize();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Xposed模块自检测
        if (lpparam.packageName.equals("com.qimian233.ztool")) {
            Log.d(TAG, "检测到自身应用，开始Hook自检测方法");

            try {
                XposedHelpers.findAndHookMethod(
                        "com.qimian233.ztool.HomeFragment",
                        lpparam.classLoader,
                        "isModuleActive",
                        XC_MethodReplacement.returnConstant(true)
                );
                Log.i(TAG, "模块自检测Hook成功");
            } catch (Throwable t) {
                Log.e(TAG, "模块自检测Hook失败: " + t.getMessage());
            }
        }

        // 委托给Hook管理器处理
        HookManager.handleLoadPackage(lpparam);
    }
}
