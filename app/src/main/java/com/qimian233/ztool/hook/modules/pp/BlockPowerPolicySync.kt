package com.qimian233.ztool.hook.modules.pp

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 屏蔽 ZUI 性能服务 (com.zui.pp) 省电策略的云端同步与应用。
 *
 * PolicySyncCmp.checkPolicySync 会把缓存的云端策略 (policy_sync_cmp_data) 载入
 * mCloudPolicy 并由 PolicyParser 应用；syncFromCloud 则通过 XUICloudApi 向云端
 * 拉取新版本策略。两者均吞掉后仅保留 assets 内置的本地策略兜底，
 * 本地白名单初始化 (initWhiteList) 不受影响。
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
                // no-op：不把缓存的云端策略载入内存
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
                // no-op：不再向云端请求省电策略更新
            }
            logger.info("Hooked PolicySyncCmp.syncFromCloud")
        } catch (t: Throwable) {
            logger.error("Failed to hook PolicySyncCmp.syncFromCloud", t)
        }
    }
}
