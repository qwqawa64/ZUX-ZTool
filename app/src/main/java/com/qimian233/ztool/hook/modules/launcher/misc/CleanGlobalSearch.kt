package com.qimian233.ztool.hook.modules.launcher.misc

import com.google.gson.JsonObject
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.dexindex.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore
import io.github.libxposed.api.XposedModuleInterface

class CleanGlobalSearch : AppHookModule() {

    companion object {
        private const val TARGET_CLASS = "com.zui.launcher.GlobalSearchView"
    }

    private var noHotWordView = false
    private var noSearchRecommend = false

    override fun getModuleName(): String = PreferenceKeys.CLEAN_GLOBAL_SEARCH.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        readPreferences()

        val idx = DexIndexStore.lookup(xposed, ScopeKeys.LAUNCHER.packageName)

        if (noHotWordView) {
            installHotWordRemoval(classLoader, idx)
        }

        if (noSearchRecommend) {
            installSearchRecommendRemoval(classLoader)
        }
    }

    private fun installHotWordRemoval(
        classLoader: ClassLoader,
        idx: JsonObject?
    ) {
        try {
            val globalSearchViewClass = classLoader.loadClass(TARGET_CLASS)

            val module = idx?.getAsJsonObject(DexIndexConstants.ModuleKeys.CLEAN_GLOBAL_SEARCH)

            // 候选列表：离线索引结果优先，缺失时回退硬编码（原 discoverInitMethods 语义）
            val methodNames = listOfNotNull(
                module?.get(DexIndexConstants.Keys.HOTWORD_INIT_METHOD)
                    ?.takeIf { !it.isJsonNull }?.asString,
                "K0",
                "T0"
            ).distinct()

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

            val e0Name = module?.get(DexIndexConstants.Keys.HOTWORD_DATA_METHOD)
                ?.takeIf { !it.isJsonNull }?.asString ?: "E0"
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
