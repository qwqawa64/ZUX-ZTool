package com.qimian233.ztool.hook.modules.ota;

import java.util.Properties;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * Lenovo OTA 参数修改模块
 * 功能：拦截OTA请求，修改 curfirmwarever 和 deviceid
 * 修改原则：仅在配置值有效（非空）时才修改，否则保持原厂逻辑
 */
public class LenovoOTAHook extends BaseHookModule {

    private static final String TARGET_CLASS = "com.lenovo.tbengine.core.serverapi.ServerApi";
    private static final String TARGET_METHOD = "geServerResponseOrThrowError";

    public LenovoOTAHook() {}

    @Override
    public String getModuleName() {
        return "custom_ota_parameters";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.lenovo.ota",
                "com.lenovo.tbengine" // 覆盖相关可能存在的包名
        };
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        hookOTARequest(classLoader, packageName);
    }

    private void hookOTARequest(ClassLoader classLoader, String packageName) {
        try {
            Class<?> serverApiClass;
            try {
                serverApiClass = classLoader.loadClass(TARGET_CLASS);
            } catch (ClassNotFoundException e) {
                return;
            }

            log("Found target class in package: " + packageName);

            Method targetMethod = serverApiClass.getDeclaredMethod(
                    TARGET_METHOD,
                    String.class,      // str
                    Properties.class,  // properties (目标修改对象)
                    String.class       // str2 (URL)
            );

            hookWithId(targetMethod, "target", chain -> {
                Properties properties = (Properties) chain.getArg(1);
                String url = (String) chain.getArg(2);

                // 仅拦截包含 "upgrade" 的请求
                if (url != null && url.contains("upgrade")) {
                    boolean modified = false;

                    // 1. 处理 firmware 版本
                    String targetVer;
                    try {
                        targetVer = this.xposed.getRemotePreferences("xposed_module_config").getString("Custom_ota_target_versionName", "");
                    } catch (Throwable t) {
                        targetVer = "";
                    }
                    // 只有当 targetVer 不为 null 且去除空格后不为空时才修改
                    if (isConfigValid(targetVer)) {
                        properties.put("curfirmwarever", targetVer.trim());
                        log("Modified curfirmwarever: " + targetVer);
                        modified = true;
                    }

                    // 2. 处理 deviceid
                    String targetId;
                    try {
                        targetId = this.xposed.getRemotePreferences("xposed_module_config").getString("Custom_ota_target_deviceID", "");
                    } catch (Throwable t) {
                        targetId = "";
                    }
                    // 同上
                    if (isConfigValid(targetId)) {
                        properties.put("deviceid", targetId.trim());
                        log("Modified deviceid: " + targetId);
                        modified = true;
                    }

                    if (modified) {
                        log("OTA Request intercepted in: " + packageName);
                    }
                }

                return chain.proceed();
            });
        } catch (Throwable t) {
            logError("Failed to hook OTA parameters", t);
        }
    }

    /**
     * 辅助方法：检查配置字符串是否有效
     * @param value 从配置读取的字符串
     * @return 如果不为null且长度大于0，则返回true
     */
    private boolean isConfigValid(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
