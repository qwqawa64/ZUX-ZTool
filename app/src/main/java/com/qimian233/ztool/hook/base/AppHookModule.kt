package com.qimian233.ztool.hook.base

import io.github.libxposed.api.XposedModuleInterface

/**
 * Base class for normal app Hook modules (Kotlin).
 * <p>
 * After extending this class the IDE will prompt to implement
 * [handleLoadPackage]; [handleSystemServerStarting] is irrelevant here.
 * </p>
 */
abstract class AppHookModule : BaseHookModule() {

    /**
     * Performs Hook operations on app package load (**abstract**, for IDE autocompletion).
     * <p>Carries [@Throws](Throwable::class) so Java subclasses can keep declaring {@code throws Throwable}.</p>
     */
    @Throws(Throwable::class)
    abstract override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam)
}
