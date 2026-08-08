package com.qimian233.ztool.hook.modules.setting

import android.annotation.SuppressLint
import com.qimian233.ztool.data.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Split Screen强制分屏功能Hook模块（设置端）
 *
 * 与 systemframework.SplitScreenMandatory 共享同一偏好键名 [Split_Screen_mandatory]，
 * 确保两端同时启用/禁用。
 */
@SuppressLint("PrivateApi")
class SplitScreenMandatory : AppHookModule() {
    override fun getModuleName(): String = "Split_Screen_mandatory"

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SETTINGS.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        // 设置端 Hook 逻辑（当前无额外 hook，保留用于未来扩展）
    }
}
