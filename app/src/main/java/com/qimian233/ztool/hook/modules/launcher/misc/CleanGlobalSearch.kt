package com.qimian233.ztool.hook.modules.launcher.misc

import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexKitHelper
import io.github.libxposed.api.XposedModuleInterface

/**
 * 清理全局搜索 — 移除热词视图和搜索推荐。
 *
 * ## 策略
 *
 * **K0（无参 void，HotWordView 初始化方法）：before-hook replace null**
 *
 * 在 `onFinishInflate` → `m21544K0()` 执行前拦截，阻止 `hot_word_view`
 * 布局被 inflate 和 addView 到 `hot_word_container`。
 *
 * 为什么可以安全地用 before-hook？
 * - `m21538E0(List)` 是唯一会调用 `f29571G.setVisibility()` 的方法，
 *   但它已被 replace-null 阻断，不会访问空字段。
 * - `m21534A0(int,int,int)` → `if (f29571G == null) return;`
 * - `removeFromLayer()` → `if (f29571G != null) { ... }`
 * - 所有其他访问 `f29571G` 的代码路径都有空值保护。
 *
 * 这样 HotWordView 从未被创建，`hot_word_container` 保持空状态，
 * 不会有"空容器壳"残留。比 after-hook + setVisibility/removeView
 * 更彻底。
 *
 * **E0（List → void，数据填充与可见性控制）：before-hook replace null**
 *
 * 拦截热词数据填充和 `setVisibility(0)` 调用。
 *
 * **setHotWordHint：before-hook replace null**
 *
 * 拦截搜索框 hint 文字更新。
 */
class CleanGlobalSearch : AppHookModule() {

    companion object {
        private const val TARGET_CLASS = "com.zui.launcher.GlobalSearchView"
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

    // ── 热词视图移除（before-hook，阻止 inflate） ──────────────

    private fun installHotWordRemoval(
        classLoader: ClassLoader,
        bridge: org.luckypray.dexkit.DexKitBridge?
    ) {
        try {
            val globalSearchViewClass = classLoader.loadClass(TARGET_CLASS)

            // 1. DEXKit DSL：查找无参 void 初始化方法（混淆后的 K0/T0）
            val methodNames = discoverInitMethods(bridge)

            // 2. Before-hook：阻止方法执行 → 不 inflate → 不 addView
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

            // 3. DEXKit DSL：查找 E0(List) → void，拦截数据填充
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
            } catch (_: Throwable) { /* fall through to fallback */ }
        }
        return "E0"
    }

    // ── 搜索框推荐移除 ──────────────────────────────────────────

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

    // ── 偏好读取 ────────────────────────────────────────────────

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
