package com.qimian233.ztool.hook.modules.systemui.qs;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.ViewGroup;

import com.qimian233.ztool.hook.base.AppHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

@SuppressLint("PrivateApi")
public class ControlCenterNoTileLabelsHook extends AppHookModule {

    public ControlCenterNoTileLabelsHook() {}

    @Override
    public String getModuleName() {
        return "control_center_no_tile_labels";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.systemui"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        Method createAndAddLabelsMethod = classLoader
                .loadClass("com.android.systemui.qs.tileimpl.CustomQSTileViewImpl")
                .getDeclaredMethod("createAndAddLabels");
        hookWithId(createAndAddLabelsMethod, "create_and_add_labels", chain -> {
            Object result = chain.proceed();
            try {
                Class<?> cl = chain.getThisObject().getClass();
                ViewGroup labelContainer = (ViewGroup) findField(cl,  "labelContainer")
                        .get(chain.getThisObject());
                if (labelContainer != null) {
                    labelContainer.setVisibility(View.GONE);
                    labelContainer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                }
            } catch (Exception e) {
                logger.error("Cannot apply no-label mode to tiles!", e);
            }
            return result;
        });
    }
}
