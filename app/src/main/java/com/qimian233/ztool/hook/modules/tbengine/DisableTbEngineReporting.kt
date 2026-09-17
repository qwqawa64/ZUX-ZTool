package com.qimian233.ztool.hook.modules.tbengine

import android.content.ContentResolver
import android.net.Uri
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Disables data reporting of the UDS real-time connection engine
 * (com.lenovo.tbengine).
 *
 * Covers three reporting channels (choke points):
 * 1. UE big-data analytics events (PromptUtils.sendData / AvatarPlugin "big data"):
 *    ContentResolver.insert into URIs under content://com.lenovo.ue.device.provider
 *    (ZUN101/102/103, B104/B111/B112/B401/B403, ZUN401-405, ZWN103, etc.)
 * 2. UPS push receipts (obfuscated class a.a.a.b.b / "ReportUtil"):
 *    ContentResolver.insert into content://my.upsreport/upsreport
 * 3. Push registration reporting (UpsApp.registToken): device model + SN hash +
 *    token POSTed to tb-zui.lenovo.com/engine/push-message/register.
 *    Independent of the "disable UPS push" toggle — with only this toggle on,
 *    registration info is likewise not uploaded.
 *
 * Filtering is by ContentProvider authority of channels 1 and 2 (stable across
 * versions, immune to obfuscation); other ContentResolver.insert calls are
 * unaffected.
 */
class DisableTbEngineReporting : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.DISABLE_TB_ENGINE_REPORTING.name

    override fun getTargetPackages(): Array<out String?>? = arrayOf(ScopeKeys.TB_ENGINE.packageName)

    companion object {
        // Authorities of the two ContentProviders: UE big-data analytics + UPS push receipts
        private val BLOCKED_AUTHORITIES = setOf(
            "com.lenovo.ue.device.provider",
            "my.upsreport"
        )
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        // 1. Choke-point intercept of ContentResolver.insert: swallow reporting authorities directly
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

        // 2. Push registration reporting: UpsApp.registToken uploads device info directly via OkHttp
        try {
            val upsAppClass = classLoader.loadClass(
                "com.lenovo.tbengine.core.ups.UpsApp"
            )
            val registToken = findMethod(upsAppClass, "registToken", String::class.java)
            hookWithId(registToken, "tbengine_reporting_register_token") { _ ->
                logger.debug("Blocked push registration reporting (UpsApp.registToken)")
                // no-op: do not upload device info
            }
            logger.info("Reporting UpsApp.registToken hook installed")
        } catch (t: Throwable) {
            logger.error("Failed to hook UpsApp.registToken", t)
        }
    }
}
