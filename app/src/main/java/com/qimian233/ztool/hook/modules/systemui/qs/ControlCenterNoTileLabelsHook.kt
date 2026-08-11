package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class ControlCenterNoTileLabelsHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.CONTROL_CENTER_NO_TILE_LABELS.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val createAndAddLabelsMethod: Method = classLoader
            .loadClass("com.android.systemui.qs.tileimpl.CustomQSTileViewImpl")
            .getDeclaredMethod("createAndAddLabels")
        hookWithId(createAndAddLabelsMethod, "create_and_add_labels") { chain ->
            val result = chain.proceed()
            try {
                val cl = chain.thisObject.javaClass
                val labelContainer = findField(cl, "labelContainer")
                    .get(chain.thisObject) as? ViewGroup
                if (labelContainer != null) {
                    labelContainer.visibility = View.GONE
                    labelContainer.importantForAccessibility =
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
            } catch (e: Exception) {
                logger.error("Cannot apply no-label mode to tiles!", e)
            }
            result
        }
    }
}
