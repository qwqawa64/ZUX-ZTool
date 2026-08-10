package com.qimian233.ztool.hook.modules.systemui.misc;

import android.annotation.SuppressLint;
import android.os.Message;

import com.qimian233.ztool.data.keys.ScopeKeys;
import com.qimian233.ztool.dexindex.base.DexIndexConstants;
import com.qimian233.ztool.hook.base.AppHookModule;
import com.qimian233.ztool.hook.base.DexIndexStore;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 移除充电动画 Hook。
 * <p>
 * 使用 DEXKit 通过字段类型而非混淆后的名称（H）定位 Handler 字段，
 * 确保跨版本兼容。
 * </p>
 */
public class NoChargeAnimation extends AppHookModule {

    private static final String SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName;
    private static final String TARGET_CLASS =
            "com.android.keyguard.lockscreen.charge.ChargingAnimationController";

    public NoChargeAnimation() {}

    @Override
    public String getModuleName() {
        return "No_ChargeAnimation";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEMUI_PACKAGE};
    }

    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        if (!isEnabled()) return;
        logger.info("Loading module No_ChargeAnimation.");
        handleLoadSystemUi(classLoader);
    }

    public void handleLoadSystemUi(ClassLoader classLoader) {
        try {
            logger.info("Hooking ChargingAnimationController...");
            @SuppressLint("PrivateApi") Class<?> controllerClass = classLoader.loadClass(TARGET_CLASS);

            // 从离线索引读取 Handler 字段名
            String handlerFieldName = DexIndexStore.INSTANCE.string(
                    getXposed(), SYSTEMUI_PACKAGE,
                    DexIndexConstants.ModuleKeys.NO_CHARGE_ANIMATION,
                    DexIndexConstants.Keys.HANDLER_FIELD_NAME);
            if (handlerFieldName == null) handlerFieldName = "H"; // 默认回退

            logger.debug("Using handler field name: " + handlerFieldName);
            java.lang.reflect.Field handlerField = controllerClass.getDeclaredField(handlerFieldName);
            handlerField.setAccessible(true);
            Class<?> handlerType = handlerField.getType();
            Method handleMessageMethod = handlerType.getDeclaredMethod("handleMessage", Message.class);
            hookWithId(handleMessageMethod, "handle_message", chain -> null);
            logger.info("Hooked ChargingAnimationController [OK]");
        } catch (Exception e) {
            logger.error("Error hooking ChargingAnimationController", e);
        }
    }
}
