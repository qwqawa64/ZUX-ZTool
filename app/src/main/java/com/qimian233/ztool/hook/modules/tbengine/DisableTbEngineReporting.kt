package com.qimian233.ztool.hook.modules.tbengine

import android.content.ContentResolver
import android.net.Uri
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 禁用 UDS 实时连接引擎 (com.lenovo.tbengine) 的数据上报。
 *
 * 覆盖三条上报通道（收口点）：
 * 1. UE 大数据埋点（PromptUtils.sendData / AvatarPlugin "big data"）：
 *    ContentResolver.insert → content://com.lenovo.ue.device.provider 下的各 URI
 *    （ZUN101/102/103、B104/B111/B112/B401/B403、ZUN401-405、ZWN103 等）
 * 2. UPS 推送回执（混淆类 a.a.a.b.b / "ReportUtil"）：
 *    ContentResolver.insert → content://my.upsreport/upsreport
 * 3. 推送注册上报（UpsApp.registToken）：设备型号 + SN 哈希 + token
 *    POST 到 tb-zui.lenovo.com/engine/push-message/register。
 *    与"禁用 UPS 推送"开关独立——只开本开关时注册信息同样不上送。
 *
 * 按一、二两条通道的 ContentProvider authority 过滤（跨版本稳定，对混淆免疫），
 * 其余 ContentResolver.insert 调用不受影响。
 */
class DisableTbEngineReporting : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.DISABLE_TB_ENGINE_REPORTING.name

    override fun getTargetPackages(): Array<out String?>? = arrayOf(ScopeKeys.TB_ENGINE.packageName)

    companion object {
        // UE 大数据埋点 + UPS 推送回执两个 ContentProvider 的 authority
        private val BLOCKED_AUTHORITIES = setOf(
            "com.lenovo.ue.device.provider",
            "my.upsreport"
        )
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        // 1. 收口拦截 ContentResolver.insert：命中上报 authority 直接吞掉
        try {
            val insert = findMethod(
                ContentResolver::class.java, "insert",
                Uri::class.java, android.content.ContentValues::class.java
            )
            hookWithId(insert, "tbengine_reporting_insert") { chain ->
                val uri = chain.args[0] as? Uri
                if (uri != null && uri.authority in BLOCKED_AUTHORITIES) {
                    logger.debug("Blocked reporting insert: $uri")
                    return@hookWithId null
                }
                chain.proceed()
            }
            logger.info("Reporting ContentResolver.insert hook installed")
        } catch (t: Throwable) {
            logger.error("Failed to hook ContentResolver.insert", t)
        }

        // 2. 推送注册上报：UpsApp.registToken 直连 OkHttp 上送设备信息
        try {
            val upsAppClass = classLoader.loadClass(
                "com.lenovo.tbengine.core.ups.UpsApp"
            )
            val registToken = findMethod(upsAppClass, "registToken", String::class.java)
            hookWithId(registToken, "tbengine_reporting_register_token") { _ ->
                logger.debug("Blocked push registration reporting (UpsApp.registToken)")
                // no-op：不上送设备信息
            }
            logger.info("Reporting UpsApp.registToken hook installed")
        } catch (t: Throwable) {
            logger.error("Failed to hook UpsApp.registToken", t)
        }
    }
}
