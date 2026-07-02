package com.qimian233.ztool.hook.modules.mobiledesktop;

import android.content.Context;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.DexKitHelper;

import io.github.libxposed.api.XposedModuleInterface;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.MethodsMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 绕过超级互联分享警告弹窗的 Hook。
 * <p>
 * 使用 DEXKit 通过方法签名（参数类型+返回类型）动态匹配混淆后的方法名，
 * 不再依赖硬编码的单字母名称。
 * </p>
 */
public class BypassShareWarningHook extends BaseHookModule {

    private static final String TARGET_PACKAGE = "com.motorola.mobiledesktop";
    private static final String TARGET_CLASS = "com.motorola.readyfor.tile.BaseFileUnionTile";
    private static final String DIALOG_CLASS =
            "com.motorola.readyfor.common.dialog.ActionNoticeCommonDialogActivity";
    private static final String MANAGER_PKG = "com.motorola.mobiledesktop.manager";
    private static final String PREFS_NAME = "moto_ble_preference";
    private static final String PREF_KEY = "file_union_transfer_switch";

    public BypassShareWarningHook() {}

    @Override
    public String getModuleName() {
        return "bypass_share_warning";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();

        // ── DEXKit：预解析管理器类和方法 ─────────────────────────────
        DexKitBridge bridge = DexKitHelper.INSTANCE.getBridgeForClass(
                classLoader, TARGET_CLASS);

        String managerClassName = null;
        String managerFactoryMethodName = null;   // static factory: (Context) → manager
        String managerSetMethodName = null;        // instance: (boolean) → void

        if (bridge != null) {
            try {
                // 1. 查找管理器类：在 manager 包中找一个有 static (Context) 方法和 (boolean)void 方法的类
                ClassData managerData = bridge.findClass(FindClass.create()
                        .searchPackages(MANAGER_PKG)
                        .matcher(ClassMatcher.create()
                                .methods(MethodsMatcher.create()
                                        .add(MethodMatcher.create()
                                                .modifiers(java.lang.reflect.Modifier.STATIC
                                                        | java.lang.reflect.Modifier.PUBLIC)
                                                .paramTypes("android.content.Context"))
                                        .add(MethodMatcher.create()
                                                .paramTypes("boolean")
                                                .returnType("void"))
                                )
                        )
                ).singleOrNull();

                if (managerData != null) {
                    managerClassName = managerData.getName();
                    List<MethodData> methods = managerData.getMethods();
                    for (MethodData md : methods) {
                        List<String> params = md.getParamTypeNames();
                        if (params.size() == 1 && "boolean".equals(params.get(0)) && "void".equals(md.getReturnTypeName())) {
                            managerSetMethodName = md.getName();
                        } else if (params.size() == 1 && "android.content.Context".equals(params.get(0)) && !"void".equals(md.getReturnTypeName())) {
                            managerFactoryMethodName = md.getName();
                        }
                    }
                }

                // 回退硬编码名称
                if (managerClassName == null) managerClassName = MANAGER_PKG + ".c0";
                if (managerFactoryMethodName == null) managerFactoryMethodName = "l";
                if (managerSetMethodName == null) managerSetMethodName = "z";

            } catch (Throwable dexKitError) {
                logError("DEXKit method discovery failed, using hardcoded names", dexKitError);
                managerClassName = MANAGER_PKG + ".c0";
                managerFactoryMethodName = "l";
                managerSetMethodName = "z";
            }
        } else {
            managerClassName = MANAGER_PKG + ".c0";
            managerFactoryMethodName = "l";
            managerSetMethodName = "z";
        }

        final String finalManagerClass = managerClassName;
        final String finalFactoryMethod = managerFactoryMethodName;
        final String finalSetMethod = managerSetMethodName;

        try {
            // ── Hook 1: 磁贴点击 ───────────────────────────────────
            Class<?> baseFileUnionTileClass = classLoader.loadClass(TARGET_CLASS);
            Method onClickMethod = baseFileUnionTileClass.getDeclaredMethod("onClick");
            this.xposed.hook(onClickMethod).intercept(chain -> {
                Object tile = chain.getThisObject();
                Context context = getContext(tile);
                if (context == null) {
                    return chain.proceed();
                }

                boolean enabled = isNearbyShareEnabled(context);
                log("IsNearbyShareEnabled: " + enabled);
                if (enabled) {
                    log("Nearby share already enabled, keep original disable flow.");
                    return chain.proceed();
                }

                setNearbyShareEnabled(tile, context, classLoader,
                        finalManagerClass, finalFactoryMethod, finalSetMethod);
                log("Bypassed warning and enabled nearby share directly.");
                return null;
            });
            log("Installed hook for BaseFileUnionTile.onClick");
        } catch (Throwable t) {
            logError("Failed to hook BaseFileUnionTile.onClick", t);
        }

        try {
            // ── Hook 2: 通用弹窗场景 ─────────────────────────────────
            Class<?> actionNoticeClass = classLoader.loadClass(DIALOG_CLASS);

            // 通过 DEXKit 动态查找 p() 方法 — 无参 void 方法
            String pMethodName = "p"; // 默认回退
            if (bridge != null) {
                try {
                    MethodData md = bridge.findMethod(FindMethod.create()
                            .searchPackages(TARGET_PACKAGE)
                            .matcher(MethodMatcher.create()
                                    .paramTypes()
                                    .returnType("void")
                                    .declaredClass(DIALOG_CLASS)
                            )
                    ).singleOrNull();
                    if (md != null) pMethodName = md.getName();
                } catch (Throwable ignored) {}
            }
            final String finalPMethodName = pMethodName;

            Method pMethod = actionNoticeClass.getDeclaredMethod(finalPMethodName);
            this.xposed.hook(pMethod).intercept(chain -> {
                Object myObject = chain.getThisObject();
                Context context = getContext(myObject);

                Class<?> managerClass = classLoader.loadClass(finalManagerClass);
                Method lMethod = managerClass.getDeclaredMethod(finalFactoryMethod, Context.class);
                Object manager = lMethod.invoke(null, context);
                if (manager == null) {
                    log("Unable to get manager!");
                    return null;
                }
                Method zMethod = manager.getClass().getDeclaredMethod(finalSetMethod, boolean.class);
                zMethod.setAccessible(true);
                zMethod.invoke(manager, true);
                return null;
            });
            log("Installed hook for dialog activity method: " + finalPMethodName);
        } catch (Exception e) {
            logError("Failed to hook createAndStartExposureWarnIntent: ", e);
        }
    }

    private Context getContext(Object tile) {
        try {
            Method getApplicationContextMethod = tile.getClass().getMethod("getApplicationContext");
            Object context = getApplicationContextMethod.invoke(tile);
            if (context instanceof Context) {
                return (Context) context;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isNearbyShareEnabled(Context context) {
        try {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(PREF_KEY, false);
        } catch (Throwable t) {
            logError("Failed to read nearby share state", t);
            return false;
        }
    }

    private void setNearbyShareEnabled(Object tile, Context context, ClassLoader classLoader,
                                       String managerClass, String factoryMethod, String setMethod) {
        try {
            Class<?> mc = classLoader.loadClass(managerClass);
            Method lMethod = mc.getDeclaredMethod(factoryMethod, Context.class);
            Object manager = lMethod.invoke(null, context);
            if (manager == null) {
                log("Unable to get manager!");
                return;
            }
            Method zMethod = manager.getClass().getDeclaredMethod(setMethod, boolean.class);
            zMethod.setAccessible(true);
            zMethod.invoke(manager, true);

            // 同样动态查找 b() 方法
            String bMethodName = "b";
            DexKitBridge bridge = DexKitHelper.INSTANCE.getBridgeForClass(classLoader, TARGET_CLASS);
            if (bridge != null) {
                try {
                    MethodData md = bridge.findMethod(FindMethod.create()
                            .searchPackages(TARGET_PACKAGE)
                            .matcher(MethodMatcher.create()
                                    .paramTypes()
                                    .returnType("void")
                                    .declaredClass(TARGET_CLASS)
                            )
                    ).singleOrNull();
                    if (md != null) bMethodName = md.getName();
                } catch (Throwable ignored) {}
            }
            Method bMethod = findMethod(tile.getClass(), bMethodName);
            bMethod.setAccessible(true);
            bMethod.invoke(tile);

            log("successfully set share to enabled");
        } catch (Exception e) {
            logError("Failed to set nearby share to enable: ", e);
        }
    }
}
