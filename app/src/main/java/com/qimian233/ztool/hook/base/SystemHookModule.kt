package com.qimian233.ztool.hook.base

import io.github.libxposed.api.XposedModuleInterface

/**
 * Base class for system framework Hook modules (Kotlin).
 * <p>
 * After extending this class the IDE will prompt to implement
 * [handleSystemServerStarting]; [handleLoadPackage] is irrelevant here.
 * </p>
 */
abstract class SystemHookModule : BaseHookModule() {

    /**
     * Performs Hook operations on system server start (**abstract**, for IDE autocompletion).
     * <p>Carries [@Throws](Throwable::class) so Java subclasses can keep declaring {@code throws Throwable}.</p>
     */
    @Throws(Throwable::class)
    abstract override fun handleSystemServerStarting(
            param: XposedModuleInterface.SystemServerStartingParam)
}
