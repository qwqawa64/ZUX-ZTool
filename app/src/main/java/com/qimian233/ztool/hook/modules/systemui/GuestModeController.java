package com.qimian233.ztool.hook.modules.systemui;

import android.content.Context;
import android.provider.Settings;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 访客模式控制Hook模块
 * 修复系统UI中自动创建访客用户的逻辑
 * 当用户切换器被禁用时，阻止自动添加访客用户
 */
public class GuestModeController extends BaseHookModule {

    public GuestModeController() {}

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
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if ("com.android.systemui".equals(packageName)) {
            hookGuestUserInteractor(classLoader);
        }
    }

    private void hookGuestUserInteractor(ClassLoader classLoader) {
        try {
            Method isAllowedMethod = classLoader
                    .loadClass("com.android.systemui.user.domain.interactor.GuestUserInteractor")
                    .getDeclaredMethod("isDeviceAllowedToAddGuest");
            hookWithId(isAllowedMethod, "is_allowed", chain -> {
                // 获取应用上下文
                Context context = (Context) chain.getThisObject().getClass()
                        .getDeclaredField("applicationContext").get(chain.getThisObject());

                // 检查用户切换器是否启用
                int userSwitcherEnabled = Settings.Global.getInt(
                        context.getContentResolver(),
                        "user_switcher_enabled",
                        0
                );

                // 如果用户切换器被禁用，则不允许添加访客
                if (userSwitcherEnabled == 0) {
                    log("阻止自动添加访客用户 - 用户切换器已禁用");
                    return false;
                }
                return chain.proceed();
            });

            log("成功Hook访客用户交互器");
        } catch (Throwable t) {
            logError("Hook访客用户交互器失败", t);
        }
    }
}
