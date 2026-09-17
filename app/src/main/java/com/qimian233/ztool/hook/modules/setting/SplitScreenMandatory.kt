package com.qimian233.ztool.hook.modules.setting

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Forced Split Screen hook module (Settings side)
 *
 * Shares the same preference key name [Split_Screen_mandatory] with
 * systemframework.SplitScreenMandatory, ensuring both sides are enabled/disabled together.
 */
@SuppressLint("PrivateApi")
class SplitScreenMandatory : AppHookModule() {
    override fun getModuleName(): String = "Split_Screen_mandatory"

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SETTINGS.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        // Settings-side hook logic (no additional hooks currently; kept for future extension)
    }
}
