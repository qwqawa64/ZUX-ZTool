package com.qimian233.ztool

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.HookedTarget

/**
 * libxposed 服务桥接。
 * <p>
 * 持有当前 [XposedService] 实例（由 [ModuleActivationProbe] 在 binder 回调中更新），
 * 并将所有服务方法委托给该实例。若服务未连接则 getter 返回 null/零值，mutation 为 no-op。
 * </p>
 */
object XposedServiceBridge {

    /** 当前服务实例，仅在模块激活时非 null */
    @Volatile
    var currentService: XposedService? = null
        internal set

    // ---- 基础查询 ----

    /** 获取原始服务实例，未激活时返回 null */
    fun getService(): XposedService? = currentService

    /** 获取服务 API 版本，未激活返回 0 */
    fun getApiVersion(): Int = currentService?.apiVersion ?: 0

    /** 获取框架名称，未激活返回 null */
    fun getFrameworkName(): String? = currentService?.frameworkName

    /** 获取框架版本字符串，未激活返回 null */
    fun getFrameworkVersion(): String? = currentService?.frameworkVersion

    /** 获取框架版本号，未激活返回 0 */
    fun getFrameworkVersionCode(): Long = currentService?.frameworkVersionCode ?: 0L

    /** 获取框架属性标志，未激活返回 0 */
    fun getFrameworkProperties(): Long = currentService?.frameworkProperties ?: 0L

    // ---- 作用域 ----

    /** 获取当前作用域包名列表，未激活返回空列表 */
    fun getScope(): List<String> = currentService?.scope ?: emptyList()

    /** 申请作用域，未激活时为 no-op */
    fun requestScope(
        packages: List<String>,
        listener: XposedService.OnScopeEventListener
    ) {
        currentService?.requestScope(packages, listener)
    }

    /** 移除作用域，未激活时为 no-op */
    fun removeScope(packages: List<String>) {
        currentService?.removeScope(packages)
    }

    // ---- 运行目标 ----

    /** 获取当前运行中的 Hook 目标列表，未激活返回空列表 */
    fun getRunningTargets(): List<HookedTarget> {
        if (getApiVersion() >= 102) {
            return currentService?.runningTargets ?: emptyList()
        }
        return emptyList()
    }

    // ---- 热重载 ----

    /** 热重载模块，未激活时为 no-op */
    fun hotReloadModule(
        target: HookedTarget,
        extras: Bundle,
        callback: XposedService.HotReloadCallback
    ) {
        if (getApiVersion() >= 102) {
            currentService?.hotReloadModule(target, extras, callback)
        }
    }

    // ---- 远程文件/偏好 ----

    /** 获取远程 SharedPreferences，未激活返回 null */
    fun getRemotePreferences(name: String): SharedPreferences? =
        currentService?.getRemotePreferences(name)

    /** 删除远程 SharedPreferences，未激活时为 no-op */
    fun deleteRemotePreferences(name: String) {
        currentService?.deleteRemotePreferences(name)
    }

    /** 列出远程文件，未激活返回空数组 */
    fun listRemoteFiles(): Array<String> = currentService?.listRemoteFiles() ?: emptyArray()

    /** 打开远程文件，未激活返回 null */
    fun openRemoteFile(path: String): ParcelFileDescriptor? =
        currentService?.openRemoteFile(path)

    /** 删除远程文件，未激活返回 false */
    fun deleteRemoteFile(path: String): Boolean = currentService?.deleteRemoteFile(path) ?: false
}
