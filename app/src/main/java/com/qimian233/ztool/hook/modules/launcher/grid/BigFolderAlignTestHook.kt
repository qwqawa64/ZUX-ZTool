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
 * 第二轮试验：
 *  5. BigFolderConfig.getBigFolderIconChildCount(...) 把 (2,2) span 的子网格改成
 *     [CHILD_COLS, CHILD_ROWS]（每行 3 个, 共 6 个）; maxNumItemsInPreview 由
 *     ClippedFolderIconLayoutRule.e() 自动跟随为 cols*rows。
 *  6. ClippedFolderIconLayoutRule.c(...) 手机分支是硬编码 2 列定位, 统一改写为
 *     rule 自身的 getOffsetX/getOffsetY 通用网格（与平板分支同源, cols/rows/gap 全部生效）。
 *  7. setup(...) 垂直改写(第四轮修正, 基于截屏实测): 背景对齐参考图标的"图形"边缘
 *     而非图标盒子。实测: 图标盒 190px 内四周透明边距约 21px(iconSize*0.11),
 *     图形边缘 [252..750], stock 背景按盒子对齐 [228..770], 上下各多出一个边距。
 *     bgTop    = rowInset + artInset
 *     bgBottom = (spanY-1)*(cellH+gapY) + rowInset + iconSize - artInset
 *     rowInset = (cellHeight - cellHeightPx)/2（实测 348/260 → 44;
 *     DeviceProfile.cellYPaddingPx 实测为 -1 未初始化, 不可用）。
 *  8. FolderIcon.z() 整体替换(第三轮): 小文件夹分支复刻 stock 公式, 大文件夹分支
 *     topMargin = (spanY-1)*(cellH+gapY) + rowInset + iconSize + drawablePadding,
 *     与最后一行邻居图标的标签完全同高。实测设备上 folderBubbleTextView 字段名不符
 *     （NoSuchFieldException）, 改为候选名 + 按 BubbleTextView 类型扫描解析。
 *
 * PreviewBackground 的关键字段在宿主内是混淆短名（JADX 因与根包冲突显示为 f4283o 等）：
 *   o = 背景宽度, p = offsetX, q = offsetY；spanX/spanY/previewSizeY 为原名。
 * ClippedFolderIconLayoutRule 同理: b=背景宽, i=背景高, d=图标尺寸, j=列数, g=行数。
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
                    applyAlignedGeometry(chain.thisObject, chain.args.getOrNull(1), chain.args.getOrNull(2))
                } catch (t: Throwable) {
                    logger.error("applyAlignedGeometry failed", t)
                }
                null
            }
            logger.info("hooked PreviewBackground.setup")
        } catch (t: Throwable) {
            logger.error("hook PreviewBackground.setup failed", t)
        }

        // 需求1：大文件夹子图标网格（2x2 → 每行 3 个）
        hookChildGridLayout(classLoader)

        // 需求3：文件夹标签与邻居图标标签同高（替换 FolderIcon.z()）
        labelField = try {
            resolveLabelField(classLoader)
        } catch (t: Throwable) {
            logger.error("resolve FolderIcon label field failed", t)
            null
        }
        hookFolderLabel(classLoader)

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
                    val holder = args[0] ?: return@hookWithId null
                    val metrics =
                        readGridMetrics(getDeviceProfileOf(holder) ?: return@hookWithId null)
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

    /** FolderIcon 上的标签字段（候选名/类型扫描解析, 解析失败时为 null） */
    private var labelField: Field? = null
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

    private fun applyAlignedGeometry(pb: Any, activityContext: Any?, folderIconView: Any?) {
        if (!staticRefsReady || activityContext == null) return
        val spanX = spanXField.getInt(pb)
        val spanY = spanYField.getInt(pb)
        // 与宿主 BigFolderConfig.isBigFolder 一致：(spanX > 1 || spanY > 1) && feature
        if (spanX <= 1 && spanY <= 1) return

        val dp = getDeviceProfileOf(activityContext) ?: return
        val metrics = readGridMetrics(dp) ?: return
        val inset = if (alignToSmallFolder) metrics.insetFolder else metrics.insetIcon
        val newWidth = spanX * metrics.cellWidth + (spanX - 1) * metrics.gapX - 2 * inset

        val oldWidth = widthField.getInt(pb)
        val oldOffsetX = offsetXField.getInt(pb)
        widthField.setInt(pb, newWidth)
        offsetXField.setInt(pb, inset)

        // 需求2(第四轮修正)：对齐参考图标的"图形"边缘而非 190px 图标盒子。
        // 实测(uiautomator 视图边界 + 截屏 2px 逐行扫描): 图标盒四周约有
        // iconSize*ART_INSET_RATIO ≈ 21px 的透明边距(图形≈0.78x盒子), 图形边缘
        // [252..750], stock 背景按盒子对齐 [228..770], 故上下各多出约一个边距。
        // bgTop    = rowInset + artInset                                      (对齐上排图形顶边)
        // bgBottom = (spanY-1)*pitch + rowInset + iconSize - artInset         (对齐下排图形底边)
        val artInset = Math.round(metrics.iconSizePx * ART_INSET_RATIO)
        val newOffsetY = metrics.rowInset + artInset
        val newPreviewSizeY = (spanY - 1) * (metrics.cellHeight + metrics.gapY) +
            metrics.rowInset + metrics.iconSizePx - artInset - newOffsetY
        val oldOffsetY = offsetYField.getInt(pb)
        val oldPreviewSizeY = previewSizeYField.getInt(pb)
        if (newPreviewSizeY > 0) {
            offsetYField.setInt(pb, newOffsetY)
            previewSizeYField.setInt(pb, newPreviewSizeY)
        }

        // 观测: 标签 topMargin（z() 已被本 Hook 替换时即为写入值）与 FolderIcon 自身 paddingTop,
        // 用于核对标签视觉位置 = paddingTop + topMargin 是否与邻居标签一致
        var labelTopMargin = -1
        var iconPaddingTop = -1
        try {
            val f = labelField
            if (folderIconView != null && f != null) {
                val label = f.get(folderIconView)
                labelTopMargin = (label.javaClass.getMethod("getLayoutParams").invoke(label)
                        as android.view.ViewGroup.MarginLayoutParams).topMargin
            }
            iconPaddingTop = (folderIconView as? View)?.paddingTop ?: -1
        } catch (_: Throwable) {
        }
        val bgBottom = offsetYField.getInt(pb) + previewSizeYField.getInt(pb)

        logger.info(
            "BigFolderAlign span=${spanX}x${spanY}" +
                " width $oldWidth->$newWidth offsetX $oldOffsetX->$inset" +
                " offsetY $oldOffsetY->$newOffsetY previewY $oldPreviewSizeY->${previewSizeYField.getInt(pb)}" +
                " artInset=$artInset" +
                " cellW=${metrics.cellWidth} nominalW=${metrics.nominalCellWidth} gap=${metrics.gapX}" +
                " cellH=${metrics.cellHeight} gapY=${metrics.gapY} rowInset=${metrics.rowInset}" +
                " cellHeightPx=${metrics.cellHeightPx}" +
                " folderIcon=${metrics.folderIconSizePx} icon=${metrics.iconSizePx}" +
                " widgetPadL=${metrics.widgetPaddingLeft} widgetPadT=${metrics.widgetPaddingTop}" +
                " bgBottom=$bgBottom labelTop=$labelTopMargin iconPadT=$iconPaddingTop"
        )
    }

    // ── DeviceProfile 派生量 ──

    private class GridMetrics(
        val columns: Int,
        val rows: Int,
        val gapX: Int,
        val gapY: Int,
        val nominalCellWidth: Int,
        val cellWidth: Int,
        val cellHeight: Int,
        val cellHeightPx: Int,
        val rowInset: Int,
        val insetFolder: Int,
        val insetIcon: Int,
        val iconSizePx: Int,
        val folderIconSizePx: Int,
        val widgetPaddingLeft: Int,
        val widgetPaddingTop: Int
    )

    // ── 反射缓存（宿主公开字段/方法, 进程内首次访问解析, 之后走缓存） ──

    private val fieldCache = java.util.concurrent.ConcurrentHashMap<String, Field>()
    private val methodCache = java.util.concurrent.ConcurrentHashMap<String, Method>()

    private fun publicField(clazz: Class<*>, name: String): Field =
        fieldCache.computeIfAbsent("${clazz.name}#$name") { clazz.getField(name) }

    private fun publicMethod(clazz: Class<*>, name: String, vararg params: Class<*>): Method =
        methodCache.computeIfAbsent("${clazz.name}#${name}#${params.size}") { clazz.getMethod(name, *params) }

    private fun getDeviceProfileOf(holder: Any): Any? =
        publicMethod(holder.javaClass, "getDeviceProfile").invoke(holder)

    private fun readGridMetrics(dp: Any): GridMetrics? {
        try {
            val dpClass = dp.javaClass
            val inv = publicField(dpClass, "inv").get(dp)
            val invClass = inv.javaClass
            val columns = publicField(invClass, "numColumns").getInt(inv)
            val rows = publicField(invClass, "numRows").getInt(inv)
            val padding = publicField(dpClass, "cellLayoutPaddingPx").get(dp) as Rect
            val borderSpace = publicField(dpClass, "cellLayoutBorderSpacePx").get(dp) as Point
            val widgetPadding = publicField(dpClass, "widgetPadding").get(dp) as Rect
            val iconSizePx = publicField(dpClass, "iconSizePx").getInt(dp)
            val folderIconSizePx = publicField(dpClass, "folderIconSizePx").getInt(dp)
            val cellLayoutWidth = publicMethod(dpClass, "getCellLayoutWidth").invoke(dp) as Int
            val cellLayoutHeight = publicMethod(dpClass, "getCellLayoutHeight").invoke(dp) as Int
            // DeviceProfile.cellHeightPx = 行内容高（图标+间距+文字）, 实测≈260 而行距≈348
            val cellHeightPx = publicField(dpClass, "cellHeightPx").getInt(dp)

            val gridWidth = cellLayoutWidth - 2 * padding.left
            val nominalCellWidth = gridWidth / columns
            val gapX = borderSpace.x
            val cellWidth = (gridWidth - (columns - 1) * gapX) / columns
            val gridHeight = cellLayoutHeight - padding.top - padding.bottom
            val gapY = borderSpace.y
            val cellHeight = (gridHeight - (rows - 1) * gapY) / rows
            // 图标在行内的真实垂直内缩, 与宿主公式 max(0,(C-cellHeightPx)/2) 同源。
            // （cellYPaddingPx 实测为 -1 未初始化, 不可用）
            val rowInset = maxOf(0, (cellHeight - cellHeightPx) / 2)
            return GridMetrics(
                columns = columns,
                rows = rows,
                gapX = gapX,
                gapY = gapY,
                nominalCellWidth = nominalCellWidth,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                cellHeightPx = cellHeightPx,
                rowInset = rowInset,
                insetFolder = (cellWidth - folderIconSizePx) / 2,
                insetIcon = (cellWidth - iconSizePx) / 2,
                iconSizePx = iconSizePx,
                folderIconSizePx = folderIconSizePx,
                widgetPaddingLeft = widgetPadding.left,
                widgetPaddingTop = widgetPadding.top
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

    // ── 需求1：2x2 大文件夹子图标改为每行 CHILD_COLS 个 ──

    /**
     * 两个配合的 Hook 点：
     *  - BigFolderConfig.getBigFolderIconChildCount(spanX, spanY, out) 是样式表汇聚点：
     *    out[0]=子网格列数, out[1]=子网格行数, 返回值=总数。ClippedFolderIconLayoutRule.e()
     *    用它设置列/行/maxNumItemsInPreview, PreviewItemManager/FolderAnimationManager/
     *    CellLayout(投放容量) 也都从它取值, 一处改写全链路生效。
     *  - ClippedFolderIconLayoutRule.c(...) 手机分支是硬编码 2 列定位, 与样式表列数不匹配,
     *    统一改写为 rule 自身的 getOffsetX/getOffsetY 通用网格（平板分支本来就走这条,
     *    改写为等值覆盖, 两条分支行为一致）。
     */
    private fun hookChildGridLayout(classLoader: ClassLoader) {
        // 5. 样式表汇聚点：(2,2) → [CHILD_COLS, CHILD_ROWS], 返回值改为总数
        try {
            val bfcClass = classLoader.loadClass("com.zui.launcher.folder.bigfolder.BigFolderConfig")
            val getChildCount = findMethod(
                bfcClass, "getBigFolderIconChildCount",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, IntArray::class.java
            )
            hookWithId(getChildCount, "big_folder_align_child_count") { chain ->
                val result = chain.proceed()
                try {
                    val spanX = chain.args[0] as Int
                    val spanY = chain.args[1] as Int
                    if (spanX == TARGET_SPAN_X && spanY == TARGET_SPAN_Y) {
                        (chain.args[2] as IntArray?).let { out ->
                            out?.set(0, CHILD_COLS)
                            out?.set(1, CHILD_ROWS)
                        }
                        val newCount = CHILD_COLS * CHILD_ROWS
                        if ((result as Int) != newCount &&
                            loggedChildCountSpans.add("${spanX}x$spanY")
                        ) {
                            logger.info(
                                "childCount($spanX,$spanY) $result -> $newCount" +
                                    " (cols=$CHILD_COLS rows=$CHILD_ROWS), rewriting every call"
                            )
                        }
                        return@hookWithId newCount
                    }
                } catch (t: Throwable) {
                    logger.error("childCount rewrite failed", t)
                }
                result
            }
            logger.info("hooked BigFolderConfig.getBigFolderIconChildCount")
        } catch (t: Throwable) {
            logger.error("hook getBigFolderIconChildCount failed", t)
        }

        // 6. 布局规则 c() 归一化为通用网格定位
        try {
            val ruleClass = classLoader.loadClass("com.android.launcher3.folder.ClippedFolderIconLayoutRule")
            val cMethod = findMethod(
                ruleClass, "c",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, FloatArray::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            // 混淆短名: b=背景宽, i=背景高, d=图标尺寸, j=列数, g=行数
            val colsField = ruleClass.getDeclaredField("j").apply { isAccessible = true }
            val rowsField = ruleClass.getDeclaredField("g").apply { isAccessible = true }
            val iconSizeField = ruleClass.getDeclaredField("d").apply { isAccessible = true }
            val bgWidthField = ruleClass.getDeclaredField("b").apply { isAccessible = true }
            val bgHeightField = ruleClass.getDeclaredField("i").apply { isAccessible = true }
            val primitiveInt = Int::class.javaPrimitiveType
            val primitiveFloat = Float::class.javaPrimitiveType
            val getOffsetX = ruleClass.getMethod(
                "getOffsetX", primitiveInt, primitiveInt, primitiveInt,
                primitiveFloat, primitiveFloat, primitiveInt, primitiveInt
            )
            val getOffsetY = ruleClass.getMethod(
                "getOffsetY", primitiveInt, primitiveInt, primitiveInt,
                primitiveFloat, primitiveFloat, primitiveInt, primitiveInt
            )
            val scaleForItem = ruleClass.getMethod(
                "scaleForItem", primitiveInt, Boolean::class.java
            )
            hookWithId(cMethod, "big_folder_align_child_grid") { chain ->
                chain.proceed()
                try {
                    val args = chain.args
                    val index = args[0] as Int
                    val spanX = args[3] as Int
                    val spanY = args[4] as Int
                    // ENTER/EXIT(-2/-3) 走动画专用路径, 非大文件夹不动
                    if (index < 0 || (spanX <= 1 && spanY <= 1)) return@hookWithId null
                    val rule = chain.thisObject
                    val cols = colsField.getInt(rule)
                    val rows = rowsField.getInt(rule)
                    val iconSize = iconSizeField.getFloat(rule)
                    val numItems = args[1] as Int
                    val halfIcon =
                        iconSize * (scaleForItem.invoke(rule, maxOf(numItems, 3), true) as Float) / 2f
                    val centerX =
                        if ((args[5] as Int) == -1) bgWidthField.getFloat(rule) / 2f
                        else (args[5] as Int) / 2f
                    val centerY =
                        (if ((args[6] as Int) == -1) bgHeightField.getFloat(rule) / 2f
                        else (args[6] as Int) / 2f) + (args[7] as Int)
                    val out = args[2] as FloatArray
                    out[0] = getOffsetX.invoke(rule, index, cols, rows, centerX, halfIcon, spanX, spanY) as Float
                    out[1] = getOffsetY.invoke(rule, index, cols, rows, centerY, halfIcon, spanX, spanY) as Float
                    logger.debug(
                        "childGrid idx=$index cols=$cols rows=$rows bgW=${bgWidthField.getFloat(rule)}" +
                            " -> (${out[0]}, ${out[1]})"
                    )
                } catch (t: Throwable) {
                    logger.error("child grid rewrite failed", t)
                }
                null
            }
            logger.info("hooked ClippedFolderIconLayoutRule.c (generic grid)")
        } catch (t: Throwable) {
            logger.error("hook ClippedFolderIconLayoutRule.c failed", t)
        }
    }

    // ── 需求3：文件夹标签与邻居图标标签同高（整体替换 FolderIcon.z()） ──

    /**
     * FolderIcon 标签字段名跨版本漂移（实测设备上 "folderBubbleTextView" 不存在,
     * JADX 注释显示原名可能为 "e"）。先按候选名解析, 再按类型兜底:
     * FolderIcon 中唯一的 BubbleTextView 类型字段即标签。
     */
    private fun resolveLabelField(classLoader: ClassLoader): Field? {
        val folderIconClass = classLoader.loadClass("com.android.launcher3.folder.FolderIcon")
        for (name in listOf("folderBubbleTextView", "e")) {
            try {
                return folderIconClass.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: Throwable) {
            }
        }
        val bubbleClass = classLoader.loadClass("com.android.launcher3.BubbleTextView")
        return folderIconClass.declaredFields
            .firstOrNull { bubbleClass.isAssignableFrom(it.type) }
            ?.apply { isAccessible = true }
    }

    private fun hookFolderLabel(classLoader: ClassLoader) {
        val resolvedLabelField = labelField
        if (resolvedLabelField == null) {
            logger.error("FolderIcon label field unresolved, z() hook skipped")
            return
        }
        try {
            val folderIconClass = classLoader.loadClass("com.android.launcher3.folder.FolderIcon")
            val zMethod = findMethod(folderIconClass, "z")
            // FolderIcon.b = ActivityContext（混淆短名）; mInfo 为原名
            val activityContextField = folderIconClass.getDeclaredField("b").apply { isAccessible = true }
            val infoField = folderIconClass.getField("mInfo")
            hookWithId(zMethod, "big_folder_align_label") { chain ->
                try {
                    val icon = chain.thisObject
                    val info = infoField.get(icon)
                        ?: return@hookWithId chain.proceed()
                    val spanX = publicField(info.javaClass, "spanX").getInt(info)
                    val spanY = publicField(info.javaClass, "spanY").getInt(info)
                    val activityContext = activityContextField.get(icon)
                        ?: return@hookWithId chain.proceed()
                    val dp = getDeviceProfileOf(activityContext)
                        ?: return@hookWithId chain.proceed()
                    val dpClass = dp.javaClass
                    val iconSizePx = publicField(dpClass, "iconSizePx").getInt(dp)
                    val drawablePadding = publicField(dpClass, "iconDrawablePaddingPx").getInt(dp)
                    val topMargin: Int
                    if (spanX <= 1 && spanY <= 1) {
                        topMargin = iconSizePx + drawablePadding
                    } else {
                        val metrics = readGridMetrics(dp)
                            ?: return@hookWithId chain.proceed()
                        topMargin = (spanY - 1) * (metrics.cellHeight + metrics.gapY) +
                            metrics.rowInset + iconSizePx + drawablePadding
                    }
                    val label = resolvedLabelField.get(icon) as View
                    val lp = label.layoutParams as android.view.ViewGroup.MarginLayoutParams
                    if (lp.topMargin != topMargin) {
                        lp.topMargin = topMargin
                        label.requestLayout()
                        logger.debug("label topMargin -> $topMargin (span=${spanX}x${spanY})")
                    }
                    null // 已完整替代宿主 z()
                } catch (t: Throwable) {
                    logger.error("label align failed, fallback to stock z()", t)
                    chain.proceed()
                }
            }
            logger.info("hooked FolderIcon.z (label alignment, field=${resolvedLabelField.name})")
        } catch (t: Throwable) {
            logger.error("hook FolderIcon.z failed", t)
        }
    }

    companion object {
        /** 需求1：该 span 的大文件夹子图标网格改为每行 CHILD_COLS 个、共 CHILD_ROWS 行。 */
        private const val TARGET_SPAN_X = 2
        private const val TARGET_SPAN_Y = 2
        private const val CHILD_COLS = 3
        private const val CHILD_ROWS = 2

        /**
         * 需求2：图标盒内图形的透明边距比例（实测 190px 盒 → 约 21px 边距, 图形≈0.78x盒子,
         * 截屏 2px 逐行扫描测得）。背景矩形按图形边缘对齐时, 上下各收进一个该边距。
         */
        private const val ART_INSET_RATIO = 0.11f

        /** childCount 改写日志去重（每个 span 组合只记一次, 该调用非常频繁）。 */
        private val loggedChildCountSpans: MutableSet<String> =
            java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())
    }
}
