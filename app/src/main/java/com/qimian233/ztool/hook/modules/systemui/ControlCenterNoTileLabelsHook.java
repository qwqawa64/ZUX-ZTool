package com.qimian233.ztool.hook.modules.systemui;

import android.view.View;
import android.view.ViewGroup;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

public class ControlCenterNoTileLabelsHook extends BaseHookModule {

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
        String packageName = param.getPackageName();
        Method createAndAddLabelsMethod = classLoader
                .loadClass("com.android.systemui.qs.tileimpl.CustomQSTileViewImpl")
                .getDeclaredMethod("createAndAddLabels");
        this.xposed.hook(createAndAddLabelsMethod).intercept(chain -> {
            Object result = chain.proceed();
            Class<?> cl = chain.getThisObject().getClass();
            ViewGroup labelContainer = (ViewGroup) cl.getDeclaredField("labelContainer")
                    .get(chain.getThisObject());
            if (labelContainer != null) {
                labelContainer.setVisibility(View.GONE);
                labelContainer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
            return result;
        });
    }
}
