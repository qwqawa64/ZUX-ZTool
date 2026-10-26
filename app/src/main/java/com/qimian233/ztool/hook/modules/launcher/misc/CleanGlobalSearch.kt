package com.qimian233.ztool.hook.modules.launcher.misc

import android.view.View
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Field

/**
 * Removes the launcher global-search hot word card and the search box hint word.
 */
class CleanGlobalSearch : AppHookModule() {

    companion object {
        // Android 17 moved the class into globalsearch; the old FQN is the pre-17 fallback.
        private const val TARGET_CLASS = "com.zui.launcher.globalsearch.GlobalSearchView"
        private const val TARGET_CLASS_LEGACY = "com.zui.launcher.GlobalSearchView"

        // Android 17 names first; K0/T0 (no-arg init) and E0(List) are the pre-17 obfuscated names.
        private val HOT_WORD_INIT_FALLBACKS = listOf("hotWordView", "K0", "T0")
        private val HOT_WORD_DATA_FALLBACKS = listOf("handleWordView", "E0")
    }

    private var noHotWordView = false
    private var noSearchRecommend = false
    private val warnedKeys = mutableSetOf<String>()

    override fun getModuleName(): String = PreferenceKeys.CLEAN_GLOBAL_SEARCH.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        readPreferences()
        if (!noHotWordView && !noSearchRecommend) return

        val viewClass = loadGlobalSearchViewClass(param.defaultClassLoader)
        if (viewClass == null) {
            warnOnce("class", "GlobalSearchView not found; global search clean-up skipped")
            return
        }
        if (noHotWordView) {
            installHotWordRemoval(viewClass)
        }
        if (noSearchRecommend) {
            installSearchRecommendRemoval(viewClass)
        }
    }

    private fun loadGlobalSearchViewClass(classLoader: ClassLoader): Class<*>? {
        for (name in listOf(TARGET_CLASS, TARGET_CLASS_LEGACY)) {
            try {
                return classLoader.loadClass(name)
            } catch (_: Throwable) {
                logger.debug("GlobalSearchView candidate not found: $name")
            }
        }
        return null
    }

    private fun installHotWordRemoval(viewClass: Class<*>) {
        // Android 17's handleWordView() dereferences the hot word view without a null check,
        // so blocking the inflation leaves a null field. Hide the inflated view instead.
        val legacyClass = viewClass.name == TARGET_CLASS_LEGACY
        val hotWordField = if (legacyClass) null else findHotWordField(viewClass)
        if (!legacyClass && hotWordField == null) {
            logger.debug("hotWordView field not found; falling back to blocking hot word methods")
        }

        val initNames = hotWordCandidates(DexIndexConstants.Keys.HOTWORD_INIT_METHOD, HOT_WORD_INIT_FALLBACKS)
        val initName = hookFirstMatch(viewClass, initNames, "hotword_init", emptyArray(), hotWordField)
        if (initName == null) {
            warnOnce("hotword_init", "No hot word inflation method found; hot word card removal skipped")
        }

        val dataNames = hotWordCandidates(DexIndexConstants.Keys.HOTWORD_DATA_METHOD, HOT_WORD_DATA_FALLBACKS)
        val dataName = hookFirstMatch(viewClass, dataNames, "hotword_data", arrayOf(List::class.java), hotWordField)
        if (dataName == null) {
            warnOnce("hotword_data", "No hot word data method found; hot word card removal skipped")
        }
    }

    private fun findHotWordField(viewClass: Class<*>): Field? = try {
        findField(viewClass, "hotWordView")
    } catch (_: Throwable) {
        null
    }

    private fun hotWordCandidates(key: String, fallbacks: List<String>): List<String> {
        val indexed = DexIndexStore.string(
            xposed,
            ScopeKeys.LAUNCHER.packageName,
            DexIndexConstants.ModuleKeys.CLEAN_GLOBAL_SEARCH,
            key
        )
        return (listOfNotNull(indexed) + fallbacks).distinct()
    }

    private fun hookFirstMatch(
        viewClass: Class<*>,
        names: List<String>,
        id: String,
        paramTypes: Array<Class<*>>,
        hotWordField: Field?
    ): String? {
        for (name in names) {
            val method = try {
                viewClass.getDeclaredMethod(name, *paramTypes)
            } catch (_: Throwable) {
                logger.debug("Hot word method candidate not found: $name")
                continue
            }
            if (hotWordField != null) {
                hookWithId(method, id) { chain ->
                    val result = chain.proceed()
                    hideHotWordView(hotWordField, chain.thisObject)
                    result
                }
                logger.info("Hooked hot word method: $name (view kept hidden)")
            } else {
                hookWithId(method, id) { null }
                logger.info("Blocked hot word method: $name")
            }
            return name
        }
        return null
    }

    private fun hideHotWordView(field: Field, instance: Any?) {
        if (instance == null) return
        try {
            val view = field.get(instance) as? View ?: return
            view.visibility = View.GONE
        } catch (_: Throwable) {
            warnOnce("hotword_hide", "Failed to hide hot word view")
        }
    }

    private fun installSearchRecommendRemoval(viewClass: Class<*>) {
        // setHotWordHint only writes the EditText hint text, not the recommend panel.
        val method = try {
            viewClass.getDeclaredMethod("setHotWordHint")
        } catch (_: Throwable) {
            null
        }
        if (method == null) {
            warnOnce("hint", "GlobalSearchView#setHotWordHint not found; search box hint removal skipped")
            return
        }
        hookWithId(method, "set_hot_word_hint") { null }
        logger.info("Hooked setHotWordHint")
    }

    private fun warnOnce(key: String, message: String) {
        if (warnedKeys.add(key)) {
            logger.warn(message)
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
