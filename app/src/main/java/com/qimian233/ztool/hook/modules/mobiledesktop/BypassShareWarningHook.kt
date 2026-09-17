package com.qimian233.ztool.hook.modules.mobiledesktop

import android.content.Context
import android.content.SharedPreferences
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore
import io.github.libxposed.api.XposedModuleInterface
import androidx.core.content.edit

/**
 * Hook to bypass the share warning dialog of Super Interconnect (FileUnion).
 *
 * Uses DexKit to dynamically match the obfuscated method names by method
 * signature (parameter types + return type) instead of relying on hardcoded
 * single-letter names.
 */
class BypassShareWarningHook : AppHookModule() {

    companion object {
        private val TARGET_PACKAGE = arrayOf(ScopeKeys.MOBILE_DESKTOP.packageName)
        private const val TARGET_CLASS = "com.motorola.readyfor.tile.BaseFileUnionTile"
        private const val DIALOG_CLASS =
            "com.motorola.readyfor.common.dialog.ActionNoticeCommonDialogActivity"
        private const val MANAGER_PKG = "com.motorola.mobiledesktop.manager"
        private const val PREFS_NAME = "moto_ble_preference"
        private const val PREF_KEY1 = "file_union_transfer_switch"
        private const val PREF_KEY2 = "nearby_send_files"
    }

    override fun getModuleName(): String = "bypass_share_warning"

    override fun getTargetPackages(): Array<String> = TARGET_PACKAGE

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        // ── Read obfuscated class/method names from the offline index ────────
        val module = DexIndexStore.lookup(xposed, ScopeKeys.MOBILE_DESKTOP.packageName)
            ?.getAsJsonObject(DexIndexConstants.ModuleKeys.BYPASS_SHARE_WARNING)

        val managerClassName = module?.get(DexIndexConstants.Keys.MANAGER_CLASS)
            ?.takeIf { !it.isJsonNull }?.asString ?: "$MANAGER_PKG.c0"
        val managerFactoryMethodName = module?.get(DexIndexConstants.Keys.MANAGER_FACTORY_METHOD)
            ?.takeIf { !it.isJsonNull }?.asString ?: "l"   // static factory: (Context) → manager
        val managerSetMethodName = module?.get(DexIndexConstants.Keys.MANAGER_SET_METHOD)
            ?.takeIf { !it.isJsonNull }?.asString ?: "z"    // instance: (boolean) → void
        val tileRefreshMethod = module?.get(DexIndexConstants.Keys.TILE_REFRESH_METHOD)
            ?.takeIf { !it.isJsonNull }?.asString ?: "b"

        try {
            // ── Hook 1: tile click ───────────────────────────────────
            val baseFileUnionTileClass = classLoader.loadClass(TARGET_CLASS)
            val onClickMethod = baseFileUnionTileClass.getDeclaredMethod("onClick")
            hookWithId(onClickMethod, "on_click") { chain ->
                val tile = chain.thisObject
                val context = getContext(tile)
                if (context == null) {
                    chain.proceed()
                } else {
                    val enabled = isNearbyShareEnabled(context)
                    logger.debug("IsNearbyShareEnabled: $enabled")
                    if (enabled) {
                        logger.debug("Nearby share already enabled, keep original disable flow.")
                        chain.proceed()
                    } else {
                        setNearbyShareEnabled(
                            tile, context, classLoader,
                            managerClassName, managerFactoryMethodName, managerSetMethodName,
                            tileRefreshMethod
                        )
                        logger.debug("Bypassed warning and enabled nearby share directly.")
                        null
                    }
                }
            }
            logger.info("Installed hook for BaseFileUnionTile.onClick")
        } catch (t: Throwable) {
            logger.error("Failed to hook BaseFileUnionTile.onClick", t)
        }

        try {
            // ── Hook 2: generic dialog scenario ─────────────────────────
            val actionNoticeClass = classLoader.loadClass(DIALOG_CLASS)

            // The p() method name comes from the offline index — no-arg void + references the file_share_expose_title field
            val finalPMethodName = module?.get(DexIndexConstants.Keys.DIALOG_METHOD)
                ?.takeIf { !it.isJsonNull }?.asString ?: "t" // default fallback (current version)
            logger.debug("target method name of \"createAndStartExposureWarnDialog\": $finalPMethodName")

            val pMethod = actionNoticeClass.getDeclaredMethod(finalPMethodName)
            hookWithId(pMethod, "hook_162") {  chain ->
                val myObject = chain.thisObject
                val context = getContext(myObject)

                // Try enabling via the newer MotoDiscoveryManager
                try {
                    val qClass = classLoader.loadClass("com.motorola.motoaccount.sdk.gf.q")
                    val lMethod = qClass.getDeclaredMethod("l", Context::class.java)
                    val qInstance = lMethod.invoke(null, context)
                    qInstance.javaClass
                        .getDeclaredMethod("B", Boolean::class.javaPrimitiveType)
                        .invoke(qInstance, true)
                    logger.debug("dialog hook: enabled via MotoDiscoveryManager")
                } catch (_: ReflectiveOperationException) {
                    // Fall back to the legacy manager
                    val managerClass = classLoader.loadClass(managerClassName)
                    val lMethod =
                        managerClass.getDeclaredMethod(managerFactoryMethodName, Context::class.java)
                    val manager = lMethod.invoke(null, context)
                    if (manager == null) {
                        logger.warn("Unable to get manager!")
                    } else {
                        val zMethod = manager.javaClass.getDeclaredMethod(
                            managerSetMethodName, Boolean::class.javaPrimitiveType
                        )
                        zMethod.isAccessible = true
                        zMethod.invoke(manager, true)
                    }
                }
                null
            }
            logger.info("Installed hook for dialog activity method: $finalPMethodName")
        } catch (e: Exception) {
            logger.error("Failed to hook createAndStartExposureWarnIntent: ", e)
        }
    }

    private fun getContext(tile: Any?): Context? {
        return try {
            val getApplicationContextMethod = tile!!.javaClass.getMethod("getApplicationContext")
            val context = getApplicationContextMethod.invoke(tile)
            context as? Context
        } catch (_: Throwable) {
            null
        }
    }

    private fun isNearbyShareEnabled(context: Context): Boolean {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return try {
            prefs.getBoolean(PREF_KEY1, false) || prefs.getBoolean(PREF_KEY2, false)
        } catch (t: Throwable) {
            logger.error("Failed to read nearby share state", t)
            false
        }
    }

    private fun setNearbyShareEnabled(
        tile: Any?,
        context: Context,
        classLoader: ClassLoader,
        managerClass: String,
        factoryMethod: String,
        setMethod: String,
        tileRefreshMethod: String
    ) {
        // Try multiple enable strategies to support both old and new versions:
        //   Legacy: managerClass.l(context).z(true)  (c0)
        //   New:    q.l(context).B(true)             (MotoDiscoveryManager)
        //   Fallback: write SharedPreferences directly
        try {
            // ── Strategy 1: legacy manager class ──────────────────────────
            try {
                val mc = classLoader.loadClass(managerClass)
                val lMethod = mc.getDeclaredMethod(factoryMethod, Context::class.java)
                val manager = lMethod.invoke(null, context)
                if (manager != null) {
                    val zMethod = manager.javaClass.getDeclaredMethod(
                        setMethod, Boolean::class.javaPrimitiveType
                    )
                    zMethod.isAccessible = true
                    zMethod.invoke(manager, true)
                    logger.debug("enabled via legacy manager: $managerClass.$factoryMethod/$setMethod")
                }
            } catch (_: ReflectiveOperationException) {
                // ── Strategy 2: newer MotoDiscoveryManager ───────────────
                logger.warn("legacy manager not found, trying MotoDiscoveryManager")
                val qClass = classLoader.loadClass("com.motorola.motoaccount.sdk.gf.q")
                val lMethod = qClass.getDeclaredMethod("l", Context::class.java)
                val qInstance = lMethod.invoke(null, context)
                qInstance.javaClass
                    .getDeclaredMethod("B", Boolean::class.javaPrimitiveType)
                    .invoke(qInstance, true)
                logger.debug("enabled via MotoDiscoveryManager.q.l().B(true)")
            }

            // ── Fallback: write both SharedPreferences directly ───────────────
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit { putBoolean(PREF_KEY1, true) }
            context.getSharedPreferences("sp_file_ble", Context.MODE_PRIVATE)
                .edit { putBoolean("nearby_send_files", true) }

            // ── Refresh the tile via its b() method (resolved in handleLoadPackage) ────
            val bMethod = findMethod(tile!!.javaClass, tileRefreshMethod)
            bMethod.isAccessible = true
            bMethod.invoke(tile)

            logger.debug("successfully set share to enabled")
        } catch (e: Throwable) {
            logger.error("Failed to set nearby share to enable: ", e)
        }
    }
}
