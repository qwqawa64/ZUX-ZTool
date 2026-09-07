package com.qimian233.ztool.hook.modules.launcher.grid

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.View
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 大文件夹水平几何对齐试验 Hook（常开测试通道, getModuleName() = "hook_test"）。
 *
 * 背景：ZUI 大文件夹背景矩形由 com.android.launcher3.folder.PreviewBackground 计算：
 *   宽度  = B * spanX - widgetPadding.left * 2，其中 B = (cellLayoutWidth - 2*cellLayoutPaddingPx.left) / numColumns
 *   offsetX = widgetPadding.left
 * 而邻居单格图标的视觉边缘内缩是 (cellWidth - iconSizePx)/2（应用图标）或
 * (cellWidth - folderIconSizePx)/2（小文件夹），两套体系互不换算，且名义格宽 B
 * 未扣除 CellLayout 的 borderSpace，因此大文件夹背景总是过宽、边缘无法对齐。
 *
 * 本 Hook 验证四个切入点：
 *  1. PreviewBackground.setup(...) 尾部改写 [宽=o 字段][offsetX=p 字段]（主修复点）
 *  2. PreviewBackground.computeBigFolderAvaliableWh(...) 同步宽度公式（同源公式）
 *  3. PreviewBackground.isUpdatePreviewSize(...) 只读观测（检测重设循环）
 *  4. DeviceProfile.updateIconSize(...) 只读遥测（图标/内缩/网格派生量）
 *
 * PreviewBackground 的关键字段在宿主内是混淆短名（JADX 因与根包冲突显示为 f4283o 等）：
 *   o = 背景宽度, p = offsetX, q = offsetY；spanX/spanY/previewSizeY 为原名。
 * 若宿主版本字段名变化，安装期即会记录错误并跳过对应改写，不会崩溃宿主。
 */
@SuppressLint("PrivateApi")
class BigFolderAlignTestHook : AppHookModule() {

    override fun getModuleName(): String = "hook_test"

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    /** 对齐目标：true = 小文件夹背景圆（folderIconSizePx）；false = 应用图标（iconSizePx）。 */
    private val alignToSmallFolder: Boolean = true

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        logger.info("BigFolderAlignTestHook installing for launcher")

        val pbClass = try {
            classLoader.loadClass("com.android.launcher3.folder.PreviewBackground")
        } catch (t: Throwable) {
            logger.error("PreviewBackground not found, abort", t)
            return
        }
        val activityContextClass = try {
            classLoader.loadClass("com.android.launcher3.views.ActivityContext")
        } catch (t: Throwable) {
            logger.error("ActivityContext not found, abort", t)
            return
        }

        resolveStaticRefs(pbClass)

        // Hook 点 4：DeviceProfile 派生量遥测（只读）
        hookDeviceProfileTelemetry(classLoader)

        // Hook 点 1：setup 尾部改写大文件夹背景几何（主修复点）
        try {
            val setup = findMethod(
                pbClass, "setup",
                Context::class.java, activityContextClass, View::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            hookWithId(setup, "big_folder_align_setup") { chain ->
                chain.proceed()
                try {
                    applyAlignedGeometry(chain.thisObject, chain.args.getOrNull(1))
                } catch (t: Throwable) {
                    logger.error("applyAlignedGeometry failed", t)
                }
                null
            }
            logger.info("hooked PreviewBackground.setup")
        } catch (t: Throwable) {
            logger.error("hook PreviewBackground.setup failed", t)
        }

        // Hook 点 2：computeBigFolderAvaliableWh 宽度同步
        try {
            val computeWh = findMethod(
                pbClass, "computeBigFolderAvaliableWh",
                activityContextClass,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, IntArray::class.java
            )
            hookWithId(computeWh, "big_folder_align_available_wh") { chain ->
                chain.proceed()
                try {
                    val args = chain.args
                    val wh = args[3] as IntArray
                    val metrics = readGridMetrics(args[0] ?: return@hookWithId null)
                    val spanX = args[1] as Int
                    if (metrics != null && spanX > 1) {
                        val inset = if (alignToSmallFolder) metrics.insetFolder else metrics.insetIcon
                        val old = wh[0]
                        wh[0] = spanX * metrics.cellWidth + (spanX - 1) * metrics.gapX - 2 * inset
                        logger.debug(
                            "computeBigFolderAvaliableWh spanX=$spanX width $old -> ${wh[0]} (h=${wh[1]})"
                        )
                    }
                } catch (t: Throwable) {
                    logger.error("computeBigFolderAvaliableWh adjust failed", t)
                }
                null
            }
            logger.info("hooked PreviewBackground.computeBigFolderAvaliableWh")
        } catch (t: Throwable) {
            logger.error("hook computeBigFolderAvaliableWh failed", t)
        }

        // Hook 点 3：isUpdatePreviewSize 只读观测
        try {
            val isUpdate = findMethod(
                pbClass, "isUpdatePreviewSize", activityContextClass
            )
            hookWithId(isUpdate, "big_folder_align_is_update") { chain ->
                val result = chain.proceed()
                try {
                    val pb = chain.thisObject
                    logger.debug(
                        "isUpdatePreviewSize -> $result span=${spanXField.getInt(pb)}x${spanYField.getInt(pb)}" +
                            " width=${widthField.getInt(pb)} offsetX=${offsetXField.getInt(pb)}"
                    )
                } catch (_: Throwable) {
                }
                result
            }
            logger.info("hooked PreviewBackground.isUpdatePreviewSize")
        } catch (t: Throwable) {
            logger.error("hook isUpdatePreviewSize failed", t)
        }
    }

    // ── 反射句柄（进程内唯一 launcher classLoader，安装期解析一次） ──

    private lateinit var spanXField: Field
    private lateinit var spanYField: Field
    private lateinit var widthField: Field
    private lateinit var offsetXField: Field
    private lateinit var offsetYField: Field
    private lateinit var previewSizeYField: Field
    private var staticRefsReady = false

    private fun resolveStaticRefs(pbClass: Class<*>) {
        try {
            spanXField = declaredIntField(pbClass, "spanX")
            spanYField = declaredIntField(pbClass, "spanY")
            // 混淆短名：o = 背景宽, p = offsetX, q = offsetY
            widthField = declaredIntField(pbClass, "o")
            offsetXField = declaredIntField(pbClass, "p")
            offsetYField = declaredIntField(pbClass, "q")
            previewSizeYField = declaredIntField(pbClass, "previewSizeY")
            staticRefsReady = true
        } catch (t: Throwable) {
            logger.error("resolve PreviewBackground fields failed (obfuscated names changed?)", t)
        }
    }

    private fun declaredIntField(clazz: Class<*>, name: String): Field =
        clazz.getDeclaredField(name).apply { isAccessible = true }

    // ── 核心：setup 尾部几何改写 ──

    private fun applyAlignedGeometry(pb: Any, activityContext: Any?) {
        if (!staticRefsReady || activityContext == null) return
        val spanX = spanXField.getInt(pb)
        val spanY = spanYField.getInt(pb)
        // 与宿主 BigFolderConfig.isBigFolder 一致：(spanX > 1 || spanY > 1) && feature
        if (spanX <= 1 && spanY <= 1) return

        val metrics = readGridMetrics(activityContext) ?: return
        val inset = if (alignToSmallFolder) metrics.insetFolder else metrics.insetIcon
        val newWidth = spanX * metrics.cellWidth + (spanX - 1) * metrics.gapX - 2 * inset

        val oldWidth = widthField.getInt(pb)
        val oldOffsetX = offsetXField.getInt(pb)
        widthField.setInt(pb, newWidth)
        offsetXField.setInt(pb, inset)
        logger.info(
            "BigFolderAlign span=${spanX}x${spanY}" +
                " width $oldWidth->$newWidth offsetX $oldOffsetX->$inset" +
                " cellW=${metrics.cellWidth} nominalW=${metrics.nominalCellWidth} gap=${metrics.gapX}" +
                " folderIcon=${metrics.folderIconSizePx} icon=${metrics.iconSizePx}" +
                " widgetPadL=${metrics.widgetPaddingLeft} previewY=${previewSizeYField.getInt(pb)}"
        )
    }

    // ── DeviceProfile 派生量 ──

    private class GridMetrics(
        val columns: Int,
        val gapX: Int,
        val nominalCellWidth: Int,
        val cellWidth: Int,
        val insetFolder: Int,
        val insetIcon: Int,
        val iconSizePx: Int,
        val folderIconSizePx: Int,
        val widgetPaddingLeft: Int
    )

    private fun readGridMetrics(activityContext: Any): GridMetrics? {
        try {
            val dp = activityContext.javaClass
                .getMethod("getDeviceProfile")
                .invoke(activityContext) ?: return null
            val dpClass = dp.javaClass
            val inv = dpClass.getField("inv").get(dp)
            val columns = inv.javaClass.getField("numColumns").getInt(inv)
            val padding = dpClass.getField("cellLayoutPaddingPx").get(dp) as Rect
            val borderSpace = dpClass.getField("cellLayoutBorderSpacePx").get(dp) as Point
            val widgetPadding = dpClass.getField("widgetPadding").get(dp) as Rect
            val iconSizePx = dpClass.getField("iconSizePx").getInt(dp)
            val folderIconSizePx = dpClass.getField("folderIconSizePx").getInt(dp)
            val cellLayoutWidth =
                dpClass.getMethod("getCellLayoutWidth").invoke(dp) as Int

            val gridWidth = cellLayoutWidth - 2 * padding.left
            val nominalCellWidth = gridWidth / columns
            val gapX = borderSpace.x
            val cellWidth = (gridWidth - (columns - 1) * gapX) / columns
            return GridMetrics(
                columns = columns,
                gapX = gapX,
                nominalCellWidth = nominalCellWidth,
                cellWidth = cellWidth,
                insetFolder = (cellWidth - folderIconSizePx) / 2,
                insetIcon = (cellWidth - iconSizePx) / 2,
                iconSizePx = iconSizePx,
                folderIconSizePx = folderIconSizePx,
                widgetPaddingLeft = widgetPadding.left
            )
        } catch (t: Throwable) {
            logger.error("readGridMetrics failed", t)
            return null
        }
    }

    // ── Hook 点 4：DeviceProfile.updateIconSize 遥测（只读） ──

    private fun hookDeviceProfileTelemetry(classLoader: ClassLoader) {
        try {
            val dpClass = classLoader.loadClass("com.android.launcher3.DeviceProfile")
            val updateIconSize = findMethod(
                dpClass, "updateIconSize",
                Float::class.javaPrimitiveType, Context::class.java
            )
            hookWithId(updateIconSize, "big_folder_align_dp_log") { chain ->
                chain.proceed()
                try {
                    val dp = chain.thisObject
                    val dpClass = dp.javaClass
                    val inv = dpClass.getField("inv").get(dp)
                    val padding = dpClass.getField("cellLayoutPaddingPx").get(dp) as Rect
                    val borderSpace = dpClass.getField("cellLayoutBorderSpacePx").get(dp) as Point
                    val widgetPadding = dpClass.getField("widgetPadding").get(dp) as Rect
                    val cellLayoutWidth =
                        dpClass.getMethod("getCellLayoutWidth").invoke(dp) as Int
                    logger.info(
                        "DeviceProfile cols=${inv.javaClass.getField("numColumns").getInt(inv)}" +
                            " cellLayoutW=$cellLayoutWidth pad=${padding.left}x${padding.top}" +
                            " gap=${borderSpace.x}x${borderSpace.y}" +
                            " iconSize=${dpClass.getField("iconSizePx").getInt(dp)}" +
                            " folderIcon=${dpClass.getField("folderIconSizePx").getInt(dp)}" +
                            " folderOffsetY=${dpClass.getField("folderIconOffsetYPx").getInt(dp)}" +
                            " widgetPad=${widgetPadding.left},${widgetPadding.top}"
                    )
                } catch (t: Throwable) {
                    logger.debug("DeviceProfile telemetry failed: $t")
                }
                null
            }
            logger.info("hooked DeviceProfile.updateIconSize (telemetry)")
        } catch (t: Throwable) {
            logger.error("hook DeviceProfile.updateIconSize failed", t)
        }
    }
}
