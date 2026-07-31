package com.qimian233.ztool.hook.modules.systemui.misc;

import android.annotation.SuppressLint;
import android.os.Message;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.DexKitHelper;

import io.github.libxposed.api.XposedModuleInterface;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldsMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.FieldData;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 移除充电动画 Hook。
 * <p>
 * 使用 DEXKit 通过字段类型而非混淆后的名称（H）定位 Handler 字段，
 * 确保跨版本兼容。
 * </p>
 */
public class NoChargeAnimation extends BaseHookModule {

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
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

            // 通过 DEXKit 按类型查找 Handler 字段
            String handlerFieldName = "H"; // 默认回退
            DexKitBridge bridge = DexKitHelper.INSTANCE.getBridgeForClass(
                    classLoader, TARGET_CLASS);
            if (bridge != null) {
                try {
                    ClassData classData = bridge.findClass(FindClass.create()
                            .searchPackages(SYSTEMUI_PACKAGE)
                            .matcher(ClassMatcher.create()
                                    .className(TARGET_CLASS)
                                    .fields(FieldsMatcher.create()
                                            .add(org.luckypray.dexkit.query.matchers.FieldMatcher.create()
                                                    .type("android.os.Handler"))
                                    )
                            )
                    ).singleOrNull();

                    if (classData != null) {
                        List<FieldData> fields = classData.getFields();
                        for (FieldData fd : fields) {
                            String ft = fd.getTypeName();
                            if (ft.equals("android.os.Handler") || ft.endsWith(".Handler") || ft.contains("$")) {
                                handlerFieldName = fd.getName();
                                break;
                            }
                        }
                        // 如果 Handler 类型匹配失败，回退：找第一个非基本类型的字段
                        if ("H".equals(handlerFieldName)) {
                            for (FieldData fd : fields) {
                                String ft = fd.getTypeName();
                                if (!ft.startsWith("java.") && !ft.startsWith("android.") && !isPrimitiveType(ft)) {
                                    handlerFieldName = fd.getName();
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable dexKitError) {
                    logger.error("DEXKit field discovery failed, using hardcoded name", dexKitError);
                }
            }

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

    private static boolean isPrimitiveType(String typeName) {
        return "boolean".equals(typeName) || "byte".equals(typeName)
                || "char".equals(typeName) || "short".equals(typeName)
                || "int".equals(typeName) || "long".equals(typeName)
                || "float".equals(typeName) || "double".equals(typeName)
                || "void".equals(typeName);
    }
}
