package com.qimian233.ztool.hook.modules.mobiledesktop;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 自动接受超级互联 PC→手机 文件互传确认弹窗。
 * <p>
 * 在 FileConnectionConfirmActivity.onCreate 完成后直接通过 ViewModel
 * 触发接受逻辑，实现弹窗出现即自动确认，无需用户手动点击。
 * </p>
 * <p>
 * 使用 DEXKit 验证目标类存在，通过反射按<b>类型签名</b>查找 ViewModel 字段和
 * LiveData 字段，不再依赖混淆后的字段名（c/d/b）。
 * </p>
 */
public class AutoAcceptFileTransferHook extends BaseHookModule {

    private static final String TARGET_PACKAGE = "com.motorola.mobiledesktop";
    private static final String TARGET_CLASS =
            "com.motorola.mobiledesktop.files.pc2phone.FileConnectionConfirmActivity";

    public AutoAcceptFileTransferHook() {}

    @Override
    public String getModuleName() {
        return "auto_accept_file_transfer";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{TARGET_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();

        try {
            Class<?> activityClass = classLoader.loadClass(TARGET_CLASS);

            // 1. 在 Activity 中按类型查找 ViewModel 字段：检查类型是否继承 ViewModel
            String vmFieldName = findFieldBySuperType(activityClass,
                    "androidx.lifecycle.ViewModel",
                    "c" /* 回退名称 */);

            // 2. 从找到的字段获取 ViewModel 类，再在其中查找 boolean 和 LiveData 字段
            Class<?> vmClass = null;
            Field vmField = null;
            try {
                vmField = activityClass.getDeclaredField(vmFieldName);
                vmField.setAccessible(true);
                vmClass = vmField.getType();
            } catch (NoSuchFieldException e) {
                // 用回退名称 "c" 再试
                vmField = activityClass.getDeclaredField("c");
                vmField.setAccessible(true);
                vmClass = vmField.getType();
            }

            // 3. 在 ViewModel 中按类型查找 boolean 和 LiveData 字段
            String acceptedFieldName = "d";
            String liveDataFieldName = "b";
            if (vmClass != null) {
                acceptedFieldName = findFieldByType(classLoader, vmClass,
                        "boolean",
                        "d" /* 回退 */);
                liveDataFieldName = findLiveDataField(vmClass, "b" /* 回退 */);
            }

            final String finalVmFieldName = vmFieldName;
            final String finalAcceptedFieldName = acceptedFieldName;
            final String finalLiveDataFieldName = liveDataFieldName;

            this.xposed.hook(activityClass.getDeclaredMethod("onCreate",
                    android.os.Bundle.class)).intercept(chain -> {
                Object result = chain.proceed();

                Object activity = chain.getThisObject();
                try {
                    Field vmF = activityClass.getDeclaredField(finalVmFieldName);
                    vmF.setAccessible(true);
                    Object viewModel = vmF.get(activity);
                    if (viewModel == null) {
                        log("ViewModel is null, skip auto-accept");
                        return result;
                    }

                    Class<?> vmCls = viewModel.getClass();

                    Field acceptedF = vmCls.getDeclaredField(finalAcceptedFieldName);
                    acceptedF.setAccessible(true);
                    acceptedF.setBoolean(viewModel, true);

                    Field liveDataF = vmCls.getDeclaredField(finalLiveDataFieldName);
                    liveDataF.setAccessible(true);
                    Object liveData = liveDataF.get(viewModel);
                    if (liveData != null) {
                        Method updateMethod = findLiveDataUpdateMethod(liveData.getClass());
                        if (updateMethod != null) {
                            updateMethod.invoke(liveData, Boolean.TRUE);
                            log("Auto-accepted file transfer" +
                                    " [vm=" + finalVmFieldName +
                                    ", accepted=" + finalAcceptedFieldName +
                                    ", ld=" + finalLiveDataFieldName + "]");
                        } else {
                            log("Cannot find LiveData update method, skip");
                        }
                    } else {
                        log("LiveData field is null, skip");
                    }
                } catch (Throwable t) {
                    logError("Failed to auto-accept file transfer", t);
                }
                return result;
            });
            log("Installed hook for auto-accept file transfer");
        } catch (Throwable t) {
            logError("Failed to install auto-accept file transfer hook", t);
        }
    }

    /**
     * 遍历类的所有字段，按类型查找目标字段。
     * @param wantedType 期望类型，为 null 时匹配第一个非 java/android 包且非基本类型的字段
     * @param fallback 找不到时返回的默认名称
     */
    private static String findFieldByType(ClassLoader classLoader, Class<?> clazz,
                                          String wantedType, String fallback) {
        for (Field f : clazz.getDeclaredFields()) {
            String typeName = f.getType().getName();
            if (wantedType != null) {
                if (wantedType.equals(typeName)
                        || f.getType().getName().endsWith("." + wantedType)) {
                    return f.getName();
                }
            } else {
                // 匹配非系统、非基本类型
                if (!typeName.startsWith("java.")
                        && !typeName.startsWith("android.")
                        && !typeName.startsWith("androidx.")
                        && !typeName.startsWith("dalvik.")
                        && !isPrimitiveType(typeName)) {
                    return f.getName();
                }
            }
        }
        return fallback;
    }

    /**
     * 在类中查找其类型继承链包含指定超类的字段。
     * @param superClassName 期望的超类全限定名
     * @param fallback 找不到时返回的默认名称
     */
    private static String findFieldBySuperType(Class<?> clazz,
                                               String superClassName,
                                               String fallback) {
        for (Field f : clazz.getDeclaredFields()) {
            if (isSubclassOf(f.getType(), superClassName)) {
                return f.getName();
            }
        }
        return fallback;
    }

    /**
     * 在类中查找 LiveData/MutableLiveData 类型的字段。
     * 通过检查字段类型的继承链是否包含 androidx.lifecycle.LiveData，
     * 而非依靠方法签名启发式匹配，避免匹配到同名签名的其他类。
     */
    private static String findLiveDataField(Class<?> clazz, String fallback) {
        for (Field f : clazz.getDeclaredFields()) {
            if (isSubclassOf(f.getType(), "androidx.lifecycle.LiveData")) {
                return f.getName();
            }
        }
        // 回退：检查类型名包含 LiveData（针对未混淆的情况）
        for (Field f : clazz.getDeclaredFields()) {
            String tn = f.getType().getName();
            if (tn.contains("LiveData")) {
                return f.getName();
            }
        }
        return fallback;
    }

    /**
     * 检查 cls 的继承链中是否包含指定名称的超类或接口。
     */
    private static boolean isSubclassOf(Class<?> cls, String superName) {
        while (cls != null && cls != Object.class) {
            if (superName.equals(cls.getName())) {
                return true;
            }
            // 也检查实现的接口
            for (Class<?> iface : cls.getInterfaces()) {
                if (superName.equals(iface.getName())) {
                    return true;
                }
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private static boolean isPrimitiveType(String typeName) {
        return "boolean".equals(typeName) || "byte".equals(typeName)
                || "char".equals(typeName) || "short".equals(typeName)
                || "int".equals(typeName) || "long".equals(typeName)
                || "float".equals(typeName) || "double".equals(typeName)
                || "void".equals(typeName);
    }

    /**
     * 在 LiveData/MutableLiveData 类层次中按参数签名查找更新方法。
     * 混淆后 setValue → l, postValue → i，二者签名均为 (Object)void。
     */
    private static Method findLiveDataUpdateMethod(Class<?> cls) {
        while (cls != null && cls != Object.class) {
            for (Method m : cls.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 1 && params[0] == Object.class
                        && m.getReturnType() == void.class) {
                    m.setAccessible(true);
                    return m;
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }
}
