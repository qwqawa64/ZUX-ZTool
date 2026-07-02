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
 */
public class AutoAcceptFileTransferHook extends BaseHookModule {

    private static final String TARGET_PACKAGE = "com.motorola.mobiledesktop";
    private static final String TARGET_CLASS =
            "com.motorola.mobiledesktop.files.pc2phone.FileConnectionConfirmActivity";
    // smali: FileConnectionConfirmActivity.c → Lda/c; (FileConnectionConfirmViewModel)
    private static final String VM_FIELD_IN_ACTIVITY = "c";
    // smali: FileConnectionConfirmViewModel.d → Z (boolean accepted)
    private static final String ACCEPTED_FIELD_IN_VM = "d";
    // smali: FileConnectionConfirmViewModel.b → Landroidx/lifecycle/m0;
    private static final String LIVE_DATA_FIELD_IN_VM = "b";

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
            this.xposed.hook(activityClass.getDeclaredMethod("onCreate",
                    android.os.Bundle.class)).intercept(chain -> {
                // 先执行原始 onCreate
                Object result = chain.proceed();

                Object activity = chain.getThisObject();
                try {
                    // 1. 获取 ViewModel: activity.c
                    Field vmField = activityClass.getDeclaredField(VM_FIELD_IN_ACTIVITY);
                    vmField.setAccessible(true);
                    Object viewModel = vmField.get(activity);
                    if (viewModel == null) {
                        log("ViewModel is null, skip auto-accept");
                        return result;
                    }

                    Class<?> vmClass = viewModel.getClass();

                    // 2. 设置 accepted = true: viewModel.d
                    Field acceptedField = vmClass.getDeclaredField(ACCEPTED_FIELD_IN_VM);
                    acceptedField.setAccessible(true);
                    acceptedField.setBoolean(viewModel, true);

                    // 3. 触发 LiveData: viewModel.b.setValue(Boolean.TRUE)
                    // 混淆后 setValue → l, postValue → i，均接受单 Object 参数
                    Field liveDataField = vmClass.getDeclaredField(LIVE_DATA_FIELD_IN_VM);
                    liveDataField.setAccessible(true);
                    Object liveData = liveDataField.get(viewModel);
                    if (liveData != null) {
                        Method updateMethod = findLiveDataUpdateMethod(liveData.getClass());
                        if (updateMethod != null) {
                            updateMethod.invoke(liveData, Boolean.TRUE);
                            log("Auto-accepted file transfer confirm dialog");
                        } else {
                            log("Cannot find LiveData setValue/postValue method, skip");
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
     * 在 LiveData/MutableLiveData 类层次中按参数签名查找更新方法。
     * 混淆后 setValue → l, postValue → i，二者签名均为 (Object)void。
     * 停止在 Object.class，避免匹配到不相关的方法。
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
