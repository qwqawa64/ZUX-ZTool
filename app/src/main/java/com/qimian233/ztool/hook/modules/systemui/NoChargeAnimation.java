package com.qimian233.ztool.hook.modules.systemui;

import android.os.Message;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import com.qimian233.ztool.hook.base.BaseHookModule;

public class NoChargeAnimation extends BaseHookModule {
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    @Override
    public String getModuleName() {
        return "No_ChargeAnimation";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEMUI_PACKAGE};
    }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!isEnabled()) return;
        log("Loading module No_ChargeAnimation.");
        new NoChargeAnimation().handleLoadSystemUi(lpparam);
    }

    public void handleLoadSystemUi(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Hooking ChargingAnimationController...");
            var classLoader = lpparam.classLoader;
            var ChargingAnimationControllerClass = classLoader.loadClass("com.android.keyguard.lockscreen.charge.ChargingAnimationController");
            var handlerField = XposedHelpers.findField(ChargingAnimationControllerClass, "H");
            XposedHelpers.findAndHookMethod(handlerField.getType(), "handleMessage", Message.class, XC_MethodReplacement.DO_NOTHING);
            log("Hooked ChargingAnimationController [OK]");
        }catch (Exception e){
            logError("Error hooking ChargingAnimationController", e);
        }
    }
}
