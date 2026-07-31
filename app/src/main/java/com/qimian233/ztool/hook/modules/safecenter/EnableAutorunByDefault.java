package com.qimian233.ztool.hook.modules.safecenter;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class EnableAutorunByDefault extends BaseHookModule {
    public static final String FEATURE_NAME = "default_enable_autorun";

    private static final int ATTR_WHITELIST = 0x20000000;
    private static final int ATTR_RELATIVE_WHITELIST = 0x40000000;

    public EnableAutorunByDefault() {}

    @Override
    public String getModuleName() {
        return FEATURE_NAME;
    }

    @Override
    public String[] getTargetPackages() {
        return new String[] {"com.lenovo.safecenter", "com.zui.safecenter"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if ("com.zui.safecenter".equals(packageName) || "com.lenovo.safecenter".equals(packageName)) {
            logger.info("Start hooking safecenter");
            try {
                Class<?> cls = classLoader.loadClass("com.lenovo.performance.autorun.beans.AutoRunDbItem");
                Field fld = cls.getDeclaredField("mAttrs");
                fld.setAccessible(true);

                for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
                    hookWithId(ctor, "ctor", chain -> {
                        chain.proceed();
                        Object obj = chain.getThisObject();
                        int attrs = fld.getInt(obj);
                        attrs |= ATTR_WHITELIST | ATTR_RELATIVE_WHITELIST;
                        fld.setInt(obj, attrs);
                        return null;
                    });
                }
                logger.info("Hooked safecenter [OK]");
            } catch (Exception e) {
                logger.error("Failed hooking safecenter", e);
            }
        }
    }
}
