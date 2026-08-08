package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface

/**
 * Split Screen强制分屏功能Hook模块（系统框架端）
 * 通过Hook OneModeService清空分屏黑名单，实现强制分屏功能
 *
 * 与 setting.SplitScreenMandatory 共享同一偏好键名 [Split_Screen_mandatory]，
 * 确保两端同时启用/禁用。
 */
@SuppressLint("PrivateApi")
class SplitScreenMandatory : SystemHookModule() {
    override fun getModuleName(): String = "Split_Screen_mandatory"

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    override fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val classLoader = param.classLoader
        try {
            val m = classLoader
                .loadClass("com.android.server.wm.OneModeService")
                .getDeclaredMethod("initLocalBlackList")
            hookWithId(m, "hook_50") { chain: XposedInterface.Chain? ->
                // 运行时检查模块是否启用，支持动态开关
                if (!isEnabled()) {
                    return@hookWithId chain!!.proceed()
                }

                // 获取OneModeService实例
                val instance = chain!!.thisObject

                // 获取mLocalmap字段（存储分屏黑名单的HashMap）
                val field = instance.javaClass.getDeclaredField("mLocalmap")
                field.isAccessible = true
                val mLocalmap = field.get(instance) as HashMap<*, *>?

                // 清空mLocalmap，确保分屏黑名单为空
                if (mLocalmap != null) {
                    mLocalmap.clear()
                    logger.debug("Successfully cleared split screen blacklist")
                }
                null
            }

            logger.info("Successfully hooked OneModeService.initLocalBlackList")
        } catch (t: Throwable) {
            logger.error("Failed to hook OneModeService", t)
        }
    }
}
