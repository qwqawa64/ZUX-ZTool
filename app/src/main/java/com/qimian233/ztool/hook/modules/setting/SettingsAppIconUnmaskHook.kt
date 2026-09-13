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
 * 设定（com.android.settings）应用图标去蒙版。
 *
 * 蒙版位置（探针 + 反编译确认）：应用列表图标汇聚点
 * settingslib.Utils#getBadgedIcon(Context, ApplicationInfo) 内部经由打包进
 * Settings APK 的 Launcher3 图标工厂（BaseIconFactory）展平为
 * FastBitmapDrawable（形状蒙版 + IconNormalizer 缩放），第三方图标包内容被
 * 二次裁切。
 *
 * 处理：after-hook 中若返回值是 FastBitmapDrawable，则用
 * getResourcesForApplication(info) + info.icon 直载目标包原始资源图标替换，
 * 绕过 PM 图标管线；原始资源缺失时保持原值。结果按 pkg#uid 缓存避免重复
 * 资源加载。AdaptiveIconDrawable 与传统 PNG 的原始形态均由列表容器自行缩放。
 *
 * 已知边界：个别页面走 com.android.settings.Utils#getBadgedIcon(IconDrawableFactory,...)
 * 旁路（如应用信息头部），不在此钩点覆盖。生效需重启设定（AmStop）。
 */
class SettingsAppIconUnmaskHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.SETTINGS_APP_ICON_UNMASK.name

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    /** pkg#uid -> 原始图标缓存；命中失败也缓存 null，避免反复资源查找 */
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

    /** 绕过 PackageManager 图标管线，直接从目标包资源加载原始图标（带缓存）。 */
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
