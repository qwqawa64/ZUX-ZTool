package com.qimian233.ztool.hook.modules.launcher.misc

import android.view.View
import org.luckypray.dexkit.DexKitBridge
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexKitHelper
import io.github.libxposed.api.XposedModuleInterface

/**
 * 清理全局搜索 — 移除热词视图和搜索推荐。
 *
 * **方案 A：After-hook + DEXKit FindField → setVisibility(GONE)**
 *
 * 热词视图通过 after-hook 实现：先让初始化方法正常执行（避免 NPE），
 * 再用 DEXKit FindField 动态定位 HotWordView 字段名（绕过 R8 混淆），
 * 最后反射调用 [View.setVisibility] 隐藏视图。
 *
 * 搜索推荐数据方法（E0/List）和 setHotWordHint 保持拦截返回 null。
 */
class CleanGlobalSearch : AppHookModule() {

    companion object {
        private const val TARGET_CLASS = "com.zui.launcher.GlobalSearchView"
        private const val HOTWORD_VIEW_CLASS = "com.zui.launcher.globalsearch.HotWordView"
        private const val SEARCH_PACKAGE = "com.zui.launcher"
    }

    private var noHotWordView = false
    private var noSearchRecommend = false

    override fun getModuleName(): String = PreferenceKeys.CLEAN_GLOBAL_SEARCH.name

    override fun getTargetPackages(): Array<String> = arrayOf("com.zui.launcher")

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
        bridge: DexKitBridge?
    ) {
        try {
            val globalSearchViewClass = classLoader.loadClass(TARGET_CLASS)

            val hotWordFieldNames = discoverHotwordFields(bridge)

            val methodNames = discoverInitMethods(bridge)

            var hooked = false
            for (methodName in methodNames) {
                try {
                    val method = globalSearchViewClass.getDeclaredMethod(methodName)
                    hookWithId(method, "method") { chain ->
                        chain.proceed()
                        hideHotwordFields(chain.thisObject, hotWordFieldNames)
                        null
                    }
                    logger.debug("Hooked hotword inflation method: $methodName")
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
                logger.info("Hooked hot word data method: $e0Name")
            } catch (_: Throwable) {
                logger.error("Unable to find GlobalSearchView hot word data method")
            }
        } catch (t: Throwable) {
            logger.error("Failed to install hot word removal hooks", t)
        }
    }

    /**
     * DEXKit DSL：在 TARGET_CLASS 中查找类型为 HOTWORD_VIEW_CLASS 的字段。
     * 返回混淆后的字段名列表（通常只有一个）。
     */
    private fun discoverHotwordFields(
        bridge: DexKitBridge?
    ): List<String> {
        if (bridge == null) return emptyList()
        return try {
            bridge.findField {
                searchPackages(SEARCH_PACKAGE)
                matcher {
                    declaredClass = TARGET_CLASS
                    type = HOTWORD_VIEW_CLASS
                }
            }.map { it.name }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * DEXKit DSL：查找无参 void 方法（HotWordView 初始化方法）。
     * 失败时回退硬编码名称列表。
     */
    private fun discoverInitMethods(
        bridge: DexKitBridge?
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
            } catch (_: Throwable) { /* fall through to fallback */ }
        }
        logger.warn("unable to locate hot word view initialization method, using fallback!")
        return listOf("K0", "T0")
    }

    /**
     * DEXKit DSL：查找 (List) → void 方法（热词数据填充方法 E0）。
     * 失败时回退硬编码 "E0"。
     */
    private fun discoverE0Method(
        bridge: DexKitBridge?
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
            } catch (_: Throwable) { /* fall through to fallback */ }
        }
        return "E0"
    }

    /**
     * 从父布局中移除 HotWordView。
     *
     * K0/T0 已将 HotWordView addView 到 hot_word_container，仅 setVisibility(GONE)
     * 无法真正拦截——View 仍驻留在 View 树中。这里通过 [ViewGroup.removeView]
     * 将其彻底移除，等效于从未被 add。
     */
    private fun hideHotwordFields(target: Any, fieldNames: List<String>) {
        if (fieldNames.isEmpty()) return
        try {
            for (fieldName in fieldNames) {
                try {
                    val field = findField(target.javaClass, fieldName)
                    field.isAccessible = true
                    val view = field.get(target) as? View ?: continue
                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                } catch (_: Throwable) { /* field not found on this version, skip */ }
            }
        } catch (t: Throwable) {
            logger.error("Failed to remove hotword view from container", t)
        }
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
