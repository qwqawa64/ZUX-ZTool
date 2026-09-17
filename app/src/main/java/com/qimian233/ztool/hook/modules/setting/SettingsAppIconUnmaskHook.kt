package com.qimian233.ztool.hook.modules.setting

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * Unmask the Settings (com.android.settings) app icons.
 *
 * Mask location (confirmed via probe + decompilation): the app list icon convergence point
 * settingslib.Utils#getBadgedIcon(Context, ApplicationInfo) passes icons through the
 * Launcher3 icon factory (BaseIconFactory) packaged inside the Settings APK, flattening
 * them into FastBitmapDrawable (shape mask + IconNormalizer scaling), which crops
 * third-party icon pack content a second time.
 *
 * Handling: in the after-hook, if the return value is a FastBitmapDrawable, replace it
 * with the target package's raw resource icon loaded directly via
 * getResourcesForApplication(info) + info.icon, bypassing the PM icon pipeline; keep
 * the original value if the raw resource is missing. Results are cached per pkg#uid to
 * avoid repeated resource loading. Both AdaptiveIconDrawable and legacy PNG raw forms
 * are scaled by the list container itself.
 *
 * Known limitation: some pages go through the com.android.settings.Utils#getBadgedIcon(IconDrawableFactory,...)
 * bypass (e.g. the app info header) and are not covered by this hook point. Takes effect
 * after restarting Settings (AmStop).
 */
class SettingsAppIconUnmaskHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.SETTINGS_APP_ICON_UNMASK.name

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    /** pkg#uid -> raw icon cache; null results are also cached to avoid repeated resource lookups */
    private val iconCache = HashMap<String, Drawable?>()

    override fun handleLoadPackage(param: PackageLoadedParam) {
        if (TARGET_PACKAGE != param.packageName) {
            return
        }
        val cl = param.defaultClassLoader
        try {
            val utilsClass = cl.loadClass(CLASS_SETTINGSLIB_UTILS)
            val fastBitmapDrawableClass = try {
                cl.loadClass(CLASS_FAST_BITMAP_DRAWABLE)
            } catch (_: ClassNotFoundException) {
                logger.warn("FastBitmapDrawable not found in settings package, hook disabled.")
                return
            }
            val m: Method = utilsClass.getDeclaredMethod(
                "getBadgedIcon", Context::class.java, ApplicationInfo::class.java
            )
            hookWithId(m, "settings_icon_unmask_badged") { chain ->
                val result = chain.proceed()
                try {
                    val info = chain.args[1] as? ApplicationInfo
                    val ctx = chain.args[0] as? Context
                    if (info != null && ctx != null &&
                        fastBitmapDrawableClass.isInstance(result)
                    ) {
                        loadRawIcon(ctx, info)?.let { raw ->
                            return@hookWithId raw
                        }
                    }
                } catch (t: Throwable) {
                    logger.warn("[unmask] processing failed, keep original: " + t.message)
                }
                result
            }
            logger.info("Hooked settingslib.Utils#getBadgedIcon for icon unmask.")
        } catch (t: Throwable) {
            logger.warn("Failed to hook getBadgedIcon: " + t.message)
        }
    }

    /** Bypasses the PackageManager icon pipeline, loading the raw icon from the target package resources directly (with caching). */
    private fun loadRawIcon(context: Context, info: ApplicationInfo): Drawable? {
        val cacheKey = info.packageName + "#" + info.uid
        synchronized(iconCache) {
            if (iconCache.containsKey(cacheKey)) {
                return iconCache[cacheKey]
            }
        }
        val raw: Drawable? = try {
            val id = info.icon
            if (id == 0) {
                null
            } else {
                val res = context.packageManager.getResourcesForApplication(info)
                res.getDrawable(id, context.theme)
            }
        } catch (_: Throwable) {
            null
        }
        synchronized(iconCache) {
            iconCache[cacheKey] = raw
        }
        return raw
    }

    companion object {
        private val TARGET_PACKAGE = ScopeKeys.SETTINGS.packageName
        private const val CLASS_SETTINGSLIB_UTILS = "com.android.settingslib.Utils"
        private const val CLASS_FAST_BITMAP_DRAWABLE =
            "com.android.launcher3.icons.FastBitmapDrawable"
    }
}
