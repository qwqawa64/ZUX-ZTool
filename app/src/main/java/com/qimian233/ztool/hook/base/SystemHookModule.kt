package com.qimian233.ztool.hook.base

import io.github.libxposed.api.XposedModuleInterface

/**
 * 系统框架 Hook 模块基类（Kotlin）。
 * <p>
 * 继承此类后 IDE 会提示实现 [handleSystemServerStarting]，无需关心
 * [handleLoadPackage]。
 * </p>
 */
abstract class SystemHookModule : BaseHookModule() {

    /**
     * 执行系统服务器启动时的 Hook 操作（**abstract**，便于 IDE 自动补全）。
     * <p>带 [@Throws](Throwable::class) 以便 Java 子类继续声明 {@code throws Throwable}。</p>
     */
    @Throws(Throwable::class)
    abstract override fun handleSystemServerStarting(
            param: XposedModuleInterface.SystemServerStartingParam)
}
