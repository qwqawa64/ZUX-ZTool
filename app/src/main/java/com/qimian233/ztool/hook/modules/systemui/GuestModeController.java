package com.qimian233.ztool.hook.modules.systemui;

import android.content.Context;
import android.provider.Settings;
import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 访客模式控制Hook模块
 * 修复系统UI中自动创建访客用户的逻辑
 * 当用户切换器被禁用时，阻止自动添加访客用户
 */
public class GuestModeController extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "guest_mode_controller";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.android.systemui"
        };
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("com.android.systemui".equals(packageName)) {
            hookGuestUserInteractor(lpparam);
        }
    }

    private void hookGuestUserInteractor(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.systemui.user.domain.interactor.GuestUserInteractor",
                    lpparam.classLoader,
                    "isDeviceAllowedToAddGuest",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // 获取应用上下文
                            Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "applicationContext");

                            // 检查用户切换器是否启用
                            int userSwitcherEnabled = Settings.Global.getInt(
                                    context.getContentResolver(),
                                    "user_switcher_enabled",
                                    0
                            );

                            // 如果用户切换器被禁用，则不允许添加访客
                            if (userSwitcherEnabled == 0) {
                                param.setResult(false);
                                log("阻止自动添加访客用户 - 用户切换器已禁用");
                            }
                        }
                    }
            );

            log("成功Hook访客用户交互器");
        } catch (Throwable t) {
            logError("Hook访客用户交互器失败", t);
        }
    }
}
