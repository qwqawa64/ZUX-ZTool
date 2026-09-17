package com.qimian233.ztool.hook.modules.pp

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Blocks cloud-side synchronization and application of power-saving policies in
 * the ZUI performance service (com.zui.pp).
 *
 * PolicySyncCmp.checkPolicySync loads the cached cloud policy
 * (policy_sync_cmp_data) into mCloudPolicy and applies it via PolicyParser;
 * syncFromCloud pulls new policy versions from the cloud via XUICloudApi.
 * Swallowing both keeps only the local policies bundled in assets as fallback;
 * the local whitelist initialization (initWhiteList) is unaffected.
 */
class BlockPowerPolicySync : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.PP_BLOCK_POWER_POLICY_SYNC.name

    override fun getTargetPackages(): Array<out String?>? = arrayOf(ScopeKeys.ZUI_PERFORMANCE.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        try {
            val policySyncCmpClass = classLoader.loadClass(
                "com.zui.power.policy.PolicySyncCmp"
            )
            val checkPolicySync = findMethod(policySyncCmpClass, "checkPolicySync")
            hookWithId(checkPolicySync, "pp_power_policy_check_sync") { _ ->
                logger.debug("Blocked PolicySyncCmp.checkPolicySync (cloud policy load).")
                // no-op: do not load the cached cloud policy into memory
            }
            logger.info("Hooked PolicySyncCmp.checkPolicySync")
        } catch (t: Throwable) {
            logger.error("Failed to hook PolicySyncCmp.checkPolicySync", t)
        }

        try {
            val policySyncCmpClass = classLoader.loadClass(
                "com.zui.power.policy.PolicySyncCmp"
            )
            val syncFromCloud = findMethod(
                policySyncCmpClass,
                "syncFromCloud",
                String::class.java,
                String::class.java,
                Long::class.javaPrimitiveType
            )
            hookWithId(syncFromCloud, "pp_power_policy_sync_from_cloud") { _ ->
                logger.debug("Blocked PolicySyncCmp.syncFromCloud.")
                // no-op: no longer request power policy updates from the cloud
            }
            logger.info("Hooked PolicySyncCmp.syncFromCloud")
        } catch (t: Throwable) {
            logger.error("Failed to hook PolicySyncCmp.syncFromCloud", t)
        }
    }
}
