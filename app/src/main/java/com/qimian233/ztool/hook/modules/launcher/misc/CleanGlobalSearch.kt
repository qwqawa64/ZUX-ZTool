package com.qimian233.ztool.hook.modules.launcher.misc

import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexKitHelper
import io.github.libxposed.api.XposedModuleInterface

class CleanGlobalSearch : AppHookModule() {

    companion object {
        private const val TARGET_CLASS = "com.zui.launcher.GlobalSearchView"
        private val SEARCH_PACKAGE = ScopeKeys.LAUNCHER.packageName
    }

    private var noHotWordView = false
    private var noSearchRecommend = false

    override fun getModuleName(): String = PreferenceKeys.CLEAN_GLOBAL_SEARCH.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        readPreferences()

        val bridge = DexKitHelper.getBridgeForClass(classLoader, TARGET_CLASS)

        if (noHotWordView) {
            installHotWordRemoval(classLoader, bridge)
        }

        if (noSearchRecommend) {
            installSearchRecommendRemoval(classLoader)
        }
    }

    private fun installHotWordRemoval(
        classLoader: ClassLoader,
        bridge: org.luckypray.dexkit.DexKitBridge?
    ) {
        try {
            val globalSearchViewClass = classLoader.loadClass(TARGET_CLASS)

            val methodNames = discoverInitMethods(bridge)

            var hooked = false
            for (methodName in methodNames) {
                try {
                    val method = globalSearchViewClass.getDeclaredMethod(methodName)
                    hookWithId(method, "method") { null }
                    logger.info("Blocked hotword init method: $methodName")
                    hooked = true
                    break
                } catch (_: Throwable) {
                    logger.debug("Method $methodName not found, trying next...")
                }
            }
            if (!hooked) {
                logger.error("Unable to find any hot word inflation method")
            }

            val e0Name = discoverE0Method(bridge)
            try {
                val e0Method = globalSearchViewClass.getDeclaredMethod(e0Name, List::class.java)
                hookWithId(e0Method, "hook_115") { null }
                logger.info("Blocked hotword data method: $e0Name")
            } catch (_: Throwable) {
                logger.error("Unable to find GlobalSearchView hotword data method")
            }
        } catch (t: Throwable) {
            logger.error("Failed to install hotword removal hooks", t)
        }
    }

    /**
     * DEXKit DSL：查找无参 void 方法（HotWordView 初始化方法）。
     * 失败时回退硬编码名称列表。
     */
    private fun discoverInitMethods(
        bridge: org.luckypray.dexkit.DexKitBridge?
    ): List<String> {
        if (bridge != null) {
            try {
                val methods = bridge.findMethod {
                    searchPackages(SEARCH_PACKAGE)
                    matcher {
                        paramTypes()
                        returnType = "void"
                        declaredClass = TARGET_CLASS
                    }
                }
                if (methods.isNotEmpty()) {
                    return methods.map { it.name }
                }
            } catch (_: Throwable) {}
        }
        logger.warn("unable to locate hot word view initialization method, using fallback!")
        return listOf("K0", "T0")
    }

    /**
     * DEXKit DSL：查找 (List) → void 方法（热词数据填充方法 E0）。
     * 失败时回退硬编码 "E0"。
     */
    private fun discoverE0Method(
        bridge: org.luckypray.dexkit.DexKitBridge?
    ): String {
        if (bridge != null) {
            try {
                val result = bridge.findMethod {
                    searchPackages(SEARCH_PACKAGE)
                    matcher {
                        paramTypes("java.util.List")
                        returnType = "void"
                        declaredClass = TARGET_CLASS
                    }
                }.singleOrNull()
                if (result != null) return result.name
            } catch (_: Throwable) {}
        }
        return "E0"
    }

    private fun installSearchRecommendRemoval(classLoader: ClassLoader) {
        try {
            val globalSearchViewClass = classLoader.loadClass(TARGET_CLASS)
            val setHotWordHintMethod = globalSearchViewClass.getDeclaredMethod("setHotWordHint")
            hookWithId(setHotWordHintMethod, "set_hot_word_hint") { null }
            logger.info("Hooked setHotWordHint")
        } catch (_: Throwable) {
            logger.error("Unable to find GlobalSearchView#setHotWordHint.")
        }
    }

    private fun readPreferences() {
        noHotWordView = try {
            remotePreferences
                .getBoolean(PreferenceKeys.REMOVE_HOT_WORD_VIEW.name, false)
        } catch (_: Throwable) {
            false
        }
        noSearchRecommend = try {
            remotePreferences
                .getBoolean(PreferenceKeys.REMOVE_SEARCH_RECOMMEND.name, false)
        } catch (_: Throwable) {
            false
        }
    }
}
