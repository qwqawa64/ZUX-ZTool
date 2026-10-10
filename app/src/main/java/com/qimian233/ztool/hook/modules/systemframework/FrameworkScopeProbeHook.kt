package com.qimian233.ztool.hook.modules.systemframework

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.BaseHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.File

/**
 * Temporary probe: does an `android` / `system` scope entry deliver *app* package loads
 * to this module, or only the system server?
 *
 * Extends [BaseHookModule] directly and accepts every package on purpose: a module that
 * filtered by package could never observe what the framework actually delivers, so the
 * usual App/SystemHookModule split is bypassed here. Delete after the measurement.
 */
class FrameworkScopeProbeHook : BaseHookModule() {

    override fun getModuleName(): String = PreferenceKeys.TEST_HOOK.name

    override fun getTargetPackages(): Array<String> = arrayOf(
        ScopeKeys.ANDROID_SYSTEM.packageName,
        ScopeKeys.SYSTEM_SERVER.packageName
    )

    /** Probe only: the answer is meaningless if the probe filters anything out. */
    override fun supportsPackage(packageName: String?): Boolean = true

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        logger.info(
            MARKER + " packageLoaded pkg=" + param.packageName
                    + " first=" + param.isFirstPackage
                    + " pid=" + android.os.Process.myPid()
                    + " proc=" + currentProcessName()
        )
    }

    @Throws(Throwable::class)
    override fun handleSystemServerStarting(
        param: XposedModuleInterface.SystemServerStartingParam
    ) {
        logger.info(
            MARKER + " systemServerStarting pid=" + android.os.Process.myPid()
                    + " proc=" + currentProcessName()
        )
    }

    private fun currentProcessName(): String = runCatching {
        val bytes = File("/proc/self/cmdline").readBytes()
        val end = bytes.indexOf(0.toByte())
        String(if (end >= 0) bytes.copyOfRange(0, end) else bytes, Charsets.UTF_8)
    }.getOrDefault("unknown")

    private companion object {
        /** Grep token: shared test_hook module name makes the prefix necessary. */
        const val MARKER = "PROBE-ANDROID-SCOPE:"
    }
}
