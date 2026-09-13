package com.qimian233.ztool.hook.modules.setting

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger

/**
 * 测试性探针 Hook：确定 ZUXOS 对第三方图标的蒙版出现在管线哪一层。
 *
 * 三个探测点（自上游到下游）：
 *  1. android.content.pm.ApplicationInfo#loadUnbadgedIcon —— 框架 PM 层的原始返回
 *  2. com.android.settingslib.Utils#getBadgedIcon(Context, ApplicationInfo) ——
 *     Settings 应用列表图标的汇聚点，同时尝试用包资源直载还原无蒙版图标
 *  3. com.android.settings.applications.manageapplications.ApplicationViewHolder#setIcon
 *     —— 列表显示点
 *
 * 日志按采样上限输出，避免刷爆 Audit 日志。getModuleName() 返回 "hook_test"，
 * 无需前端开关即可启用；确认蒙版位置后再转为正式功能。
 */
class SettingsAppIconUnmaskProbe : AppHookModule() {

    override fun getModuleName(): String = "hook_test"

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        if (TARGET_PACKAGE != param.packageName) {
            return
        }
        val cl = param.defaultClassLoader

        hookLoadUnbadgedIcon(cl)
        hookSettingslibGetBadgedIcon(cl)
        hookViewHolderSetIcon(cl)
    }

    /**
     * 探针 1：框架层 ApplicationInfo.loadUnbadgedIcon 的返回类型。
     * 若这里已是 BitmapDrawable/圆角位图，则蒙版在 PackageManager/主题引擎层；
     * 若是 AdaptiveIconDrawable，则蒙版发生在 Settings 侧更下游。
     */
    private fun hookLoadUnbadgedIcon(cl: ClassLoader) {
        try {
            val appInfoClass = cl.loadClass("android.content.pm.ApplicationInfo")
            val m: Method = appInfoClass.getMethod(
                "loadUnbadgedIcon", PackageManager::class.java
            )
            hookWithId(m, "settings_icon_probe_load_unbadged") { chain ->
                val result = chain.proceed()
                try {
                    if (counter1.getAndIncrement() < LIMIT_LOAD_UNBADGED) {
                        val info = chain.thisObject as? ApplicationInfo
                        logger.info(
                            "[probe1 loadUnbadgedIcon] pkg=" + (info?.packageName ?: "?")
                                + " -> " + describe(result as? Drawable)
                        )
                    }
                } catch (t: Throwable) {
                    logger.warn("[probe1] logging failed: " + t.message)
                }
                result
            }
            logger.info("Probe1 hooked ApplicationInfo#loadUnbadgedIcon.")
        } catch (t: Throwable) {
            logger.warn("Probe1 failed to hook loadUnbadgedIcon: " + t.message)
        }
    }

    /**
     * 探针 2：settingslib.Utils.getBadgedIcon(Context, ApplicationInfo)。
     * 记录汇聚点返回值类型；若返回已被展平成位图（蒙版征兆），尝试用
     * 包资源直载（getResourcesForApplication + info.icon）取回原始图标替换。
     */
    private fun hookSettingslibGetBadgedIcon(cl: ClassLoader) {
        try {
            val utilsClass = cl.loadClass(CLASS_SETTINGSLIB_UTILS)
            val m: Method = utilsClass.getDeclaredMethod(
                "getBadgedIcon", Context::class.java, ApplicationInfo::class.java
            )
            hookWithId(m, "settings_icon_probe_get_badged") { chain ->
                val result = chain.proceed()
                try {
                    val info = chain.args[1] as? ApplicationInfo ?: return@hookWithId result
                    val ctx = chain.args[0] as? Context ?: return@hookWithId result
                    val shouldLog =
                        counter2.getAndIncrement() < LIMIT_GET_BADGED
                    if (shouldLog) {
                        logger.info(
                            "[probe2 getBadgedIcon] pkg=" + info.packageName
                                + " -> " + describe(result as? Drawable)
                        )
                    }
                    if (result is AdaptiveIconDrawable) {
                        return@hookWithId result
                    }
                    val raw = loadRawIcon(ctx, info)
                    if (raw != null && raw.javaClass != result?.javaClass) {
                        if (shouldLog) {
                            logger.info(
                                "[probe2] replaced with raw icon for " + info.packageName
                                    + ": " + describe(raw)
                            )
                        }
                        return@hookWithId raw
                    }
                } catch (t: Throwable) {
                    logger.warn("[probe2] processing failed: " + t.message)
                }
                result
            }
            logger.info("Probe2 hooked settingslib.Utils#getBadgedIcon(Context, ApplicationInfo).")
        } catch (t: Throwable) {
            logger.warn("Probe2 failed to hook getBadgedIcon: " + t.message)
        }
    }

    /**
     * 探针 3：显示点 ApplicationViewHolder.setIcon(Drawable)。
     * 记录最终进入列表图标 ImageView 的 Drawable 类型，用于对照蒙版是否仍存在。
     */
    private fun hookViewHolderSetIcon(cl: ClassLoader) {
        try {
            val holderClass = cl.loadClass(
                "com.android.settings.applications.manageapplications.ApplicationViewHolder"
            )
            val m: Method = holderClass.getDeclaredMethod("setIcon", Drawable::class.java)
            hookWithId(m, "settings_icon_probe_viewholder") { chain ->
                try {
                    if (counter3.getAndIncrement() < LIMIT_VIEWHOLDER) {
                        logger.info("[probe3 setIcon] -> " + describe(chain.args[0] as? Drawable))
                    }
                } catch (t: Throwable) {
                    logger.warn("[probe3] logging failed: " + t.message)
                }
                chain.proceed()
            }
            logger.info("Probe3 hooked ApplicationViewHolder#setIcon.")
        } catch (t: Throwable) {
            logger.warn("Probe3 failed to hook setIcon: " + t.message)
        }
    }

    /** 绕过 PackageManager 图标管线，直接从目标包资源加载原始图标资源。 */
    private fun loadRawIcon(context: Context, info: ApplicationInfo): Drawable? {
        return try {
            val id = info.icon
            if (id == 0) {
                return null
            }
            val res = context.packageManager.getResourcesForApplication(info)
            res.getDrawable(id, context.theme)
        } catch (_: Throwable) {
            null
        }
    }

    /** 输出 Drawable 的关键形态信息：类名 + 分层结构 + 位图四角 alpha（判断圆角蒙版）。 */
    private fun describe(d: Drawable?): String {
        if (d == null) {
            return "null"
        }
        val sb = StringBuilder(d.javaClass.name)
        when (d) {
            is AdaptiveIconDrawable -> sb.append(" [adaptive fg=").append(d.foreground?.javaClass?.simpleName)
                .append(", bg=").append(d.background?.javaClass?.simpleName).append("]")
            is LayerDrawable -> {
                sb.append(" [layers=").append(d.getNumberOfLayers())
                for (i in 0 until d.getNumberOfLayers()) {
                    sb.append(", #").append(i).append('=')
                        .append(d.getDrawable(i)?.javaClass?.simpleName)
                }
                sb.append("]")
            }
            is BitmapDrawable -> sb.append(' ').append(cornerAlphaInfo(d))
            else -> sb.append(" [type=").append(d.javaClass.simpleName).append("]")
        }
        return sb.toString()
    }

    /**
     * 采样位图四角的不透明度：圆角/形状蒙版会把四角 alpha 裁到 0，
     * 原始方形图标四角应为不透明。Hardware Bitmap 无法读取像素，直接标注。
     */
    private fun cornerAlphaInfo(d: BitmapDrawable): String {
        return try {
            val bmp = d.bitmap ?: return "[bitmap=null]"
            if (bmp.isRecycled || bmp.width <= 0 || bmp.height <= 0) {
                return "[bitmap invalid]"
            }
            if (bmp.config == null) {
                return "[hardware bitmap, alpha unreadable]"
            }
            val w = bmp.width
            val h = bmp.height
            val tl = bmp.getPixel(0, 0) ushr 24
            val tr = bmp.getPixel(w - 1, 0) ushr 24
            val bl = bmp.getPixel(0, h - 1) ushr 24
            val br = bmp.getPixel(w - 1, h - 1) ushr 24
            return "[size=" + w + "x" + h + " cornerAlpha=(" + tl + "," + tr + "," + bl + "," + br + ")]"
        } catch (_: Throwable) {
            "[alpha read failed]"
        }
    }

    companion object {
        private val TARGET_PACKAGE = ScopeKeys.SETTINGS.packageName
        private const val CLASS_SETTINGSLIB_UTILS = "com.android.settingslib.Utils"

        private const val LIMIT_LOAD_UNBADGED = 20
        private const val LIMIT_GET_BADGED = 20
        private const val LIMIT_VIEWHOLDER = 40

        private val counter1 = AtomicInteger(0)
        private val counter2 = AtomicInteger(0)
        private val counter3 = AtomicInteger(0)
    }
}
