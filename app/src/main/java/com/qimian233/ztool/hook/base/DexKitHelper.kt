package com.qimian233.ztool.hook.base

import com.qimian233.ztool.hook.base.DexKitHelper.closeBridge
import com.qimian233.ztool.hook.base.DexKitHelper.getBridge
import org.luckypray.dexkit.DexKitBridge

/**
 * DEXKit 辅助单例，为所有 Hook 模块提供统一的 DexKitBridge 管理。
 *
 * ## 使用方式
 * 1. 在 handleLoadPackage 中调用 [getBridge] 获取/创建桥梁实例。
 * 2. 使用 bridge.findMethod/findClass/findField 定义查询。
 * 3. 查询结果中包含实际的类/方法/字段描述符，用于后续 hook。
 * 4. 模块不再需要时调用 [closeBridge] 释放资源。
 *
 * **务必在不再需要时调用 [closeBridge]，否则会内存泄漏。**
 *
 * @see DexKitBridge
 */
object DexKitHelper {

    init {
        try {
            System.loadLibrary("dexkit")
        } catch (e: UnsatisfiedLinkError) {
            // dexkit native library not available — this is non-fatal;
            // individual hooks should fall back gracefully
            android.util.Log.e("DexKitHelper", "Failed to load dexkit native library", e)
        }
    }

    private val bridgeCache = java.util.concurrent.ConcurrentHashMap<String, DexKitBridge>()

    /**
     * 通过目标类的 ClassLoader 获取其所在 APK 的路径。
     */
    fun getApkPath(classLoader: ClassLoader, targetClassName: String): String? {
        return try {
            val cls = classLoader.loadClass(targetClassName)
            cls.protectionDomain?.codeSource?.location?.path
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取或创建指定 APK 路径的 [DexKitBridge]。
     * 桥梁实例会被缓存，多个 Hook 模块可共享同一个桥梁。
     */
    fun getBridge(apkPath: String): DexKitBridge? {
        if (!isAvailable()) return null
        return bridgeCache.getOrPut(apkPath) {
            DexKitBridge.create(apkPath)
        }
    }

    /**
     * 从 ClassLoader + 类名直接获取桥梁（便捷方法）。
     */
    fun getBridgeForClass(classLoader: ClassLoader, targetClassName: String): DexKitBridge? {
        val apkPath = getApkPath(classLoader, targetClassName)
            ?: return null
        return getBridge(apkPath)
    }

    /**
     * 关闭并移除指定 APK 路径的桥梁实例。
     */
    fun closeBridge(apkPath: String) {
        bridgeCache.remove(apkPath)?.close()
    }

    /**
     * 关闭所有缓存的桥梁实例。
     */
    @Suppress("unused")
    fun closeAll() {
        bridgeCache.values.forEach { it.close() }
        bridgeCache.clear()
    }

    /**
     * 检查 native 库是否加载成功。
     */
    fun isAvailable(): Boolean {
        return try {
            DexKitBridge::class.java
            true
        } catch (_: Throwable) {
            false
        }
    }
}
