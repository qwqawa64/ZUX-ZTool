package com.qimian233.ztool.hook.base

import io.github.libxposed.api.XposedModuleInterface

/**
 * 普通 App Hook 模块基类（Kotlin）。
 * <p>
 * 继承此类后 IDE 会提示实现 [handleLoadPackage]，无需关心
 * [handleSystemServerStarting]。
 * </p>
 */
abstract class AppHookModule : BaseHookModule() {

    /**
     * 执行 App 包加载时的 Hook 操作（**abstract**，便于 IDE 自动补全）。
     * <p>带 [@Throws](Throwable::class) 以便 Java 子类继续声明 {@code throws Throwable}。</p>
     */
    @Throws(Throwable::class)
    abstract override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam)
}
