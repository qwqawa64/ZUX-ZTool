package com.qimian233.ztool.hook.modules.launcher.grid

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.roundToInt

/**
 * Big folder icon and background geometric alignment (com.zui.launcher).
 *
 * Fixes the background/grid mismatch, the edge-hugging child grid and the label offset.
 * See docs/research/zui_vs_oplus_launcher_grid_and_big_folder.md (section 4) and
 * PLAN_launcher_grid_convergence.md for the host behaviour and the hook strategy.
 */
@SuppressLint("PrivateApi")
class BigFolderAlignHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.LAUNCHER_BIG_FOLDER_ALIGN.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    /** Horizontal alignment target: true = small folder background circle (folderIconSizePx), false = app icon (iconSizePx). */
    private val alignToSmallFolder = true

    private var coreReady = false
    private var ruleReady = false
    private var bfcReady = false
    private var folderIconReady = false
    private var blurGuardReady = false

    // PreviewBackground: o=bg width p=offsetX q=offsetY
    private lateinit var pbWidth: Field
    private lateinit var pbOffsetX: Field
    private lateinit var pbOffsetY: Field
    private lateinit var pbPreviewSizeY: Field
    private lateinit var pbSpanX: Field
    private lateinit var pbSpanY: Field
    private lateinit var pbSetup: Method
    private lateinit var pbComputeWh: Method
    private lateinit var pbIsUpdate: Method

    // ActivityContext / DeviceProfile / InvariantDeviceProfile
    private lateinit var activityContextGetDeviceProfile: Method
    private lateinit var dpInv: Field
    private lateinit var invNumColumns: Field
    private lateinit var dpPadding: Field
    private lateinit var dpBorderSpace: Field
    private lateinit var dpWidgetPadding: Field
    private lateinit var dpIconSize: Field
    private lateinit var dpFolderIconSize: Field
    private lateinit var dpIconDrawablePadding: Field
    private lateinit var dpFolderIconOffsetY: Field
    private lateinit var dpGetCellLayoutWidth: Method
    private lateinit var dpUpdateIconSize: Method

    // BigFolderConfig
    private lateinit var bfcChildIconScale: Field
    private lateinit var bfcGetChildCount: Method
    private lateinit var bfcGetHGap: Method
    private lateinit var bfcGetVGap: Method

    // ClippedFolderIconLayoutRule: b=bg width i=bg height d=icon size j=columns g=rows
    private lateinit var ruleCols: Field
    private lateinit var ruleRows: Field
    private lateinit var ruleIconSize: Field
    private lateinit var ruleBgWidth: Field
    private lateinit var ruleBgHeight: Field
    private lateinit var ruleC: Method
    private lateinit var ruleGetOffsetX: Method
    private lateinit var ruleGetOffsetY: Method
    private lateinit var ruleScaleForItem: Method

    // FolderIcon: b=ActivityContext; label field name drifts, resolved by candidate name + type scan
    private var folderLabel: Field? = null
    private lateinit var fiActivityContext: Field
    private lateinit var fiInfo: Field
    private lateinit var fiSpanX: Field
    private lateinit var fiSpanY: Field
    private lateinit var fiZ: Method

    // FolderIcon blur: A() reads getDragObject().dragView whenever the S drag flag is set
    private lateinit var fiBlurData: Method
    private lateinit var fiDragFlag: Field
    private lateinit var launcherGetDragController: Method
    private lateinit var dragControllerGetDragObject: Method

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        logger.debug("BigFolderAlign installing")
        if (!resolveCoreRefs(classLoader)) return
        if (!LauncherGridMetrics.install(classLoader, logger)) {
            logger.error("BigFolderAlign: shared grid metrics unavailable, disabled")
            return
        }
        ruleReady = resolveRuleRefs(classLoader)
        bfcReady = resolveBfcRefs(classLoader)
        folderIconReady = resolveFolderIconRefs(classLoader)
        blurGuardReady = resolveBlurGuardRefs(classLoader)

        hookSetupGeometry()
        hookChildCountRewrite()
        if (ruleReady) hookChildGridRule()
        if (bfcReady) hookFolderGaps()
        if (folderIconReady) hookFolderLabel()
        if (blurGuardReady) hookBlurGuard()
        hookAvailableWh()
        hookIsUpdatePreviewSize()
        hookDeviceProfileTelemetry()
    }

    /** Core group: PreviewBackground / ActivityContext / DeviceProfile / InvariantDeviceProfile. */
    private fun resolveCoreRefs(classLoader: ClassLoader): Boolean {
        return try {
            val pb = classLoader.loadClass("com.android.launcher3.folder.PreviewBackground")
            val activityContextClass = classLoader.loadClass("com.android.launcher3.views.ActivityContext")
            pbSpanX = findField(pb, "spanX")
            pbSpanY = findField(pb, "spanY")
            pbWidth = findField(pb, "o")     // obfuscated short name: bg width
            pbOffsetX = findField(pb, "p")   // obfuscated short name: offsetX
            pbOffsetY = findField(pb, "q")   // obfuscated short name: offsetY
            pbPreviewSizeY = findField(pb, "previewSizeY")
            pbSetup = findMethod(
                pb, "setup",
                Context::class.java, activityContextClass, View::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            pbComputeWh = findMethod(
                pb, "computeBigFolderAvaliableWh",
                activityContextClass, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, IntArray::class.java
            )
            pbIsUpdate = findMethod(pb, "isUpdatePreviewSize", activityContextClass)

            activityContextGetDeviceProfile = findMethod(activityContextClass, "getDeviceProfile")

            val dp = classLoader.loadClass("com.android.launcher3.DeviceProfile")
            dpInv = findField(dp, "inv")
            invNumColumns = findField(dpInv.type, "numColumns")
            dpPadding = findField(dp, "cellLayoutPaddingPx")
            dpBorderSpace = findField(dp, "cellLayoutBorderSpacePx")
            dpWidgetPadding = findField(dp, "widgetPadding")
            dpIconSize = findField(dp, "iconSizePx")
            dpFolderIconSize = findField(dp, "folderIconSizePx")
            dpIconDrawablePadding = findField(dp, "iconDrawablePaddingPx")
            dpFolderIconOffsetY = findField(dp, "folderIconOffsetYPx")
            dpGetCellLayoutWidth = findMethod(dp, "getCellLayoutWidth")
            dpUpdateIconSize = findMethod(dp, "updateIconSize", Float::class.javaPrimitiveType, Context::class.java)
            coreReady = true
            true
        } catch (t: Throwable) {
            logger.error("resolve core refs failed, BigFolderAlign disabled", t)
            false
        }
    }

    /** Rule group: ClippedFolderIconLayoutRule (child icon grid positioning). */
    private fun resolveRuleRefs(classLoader: ClassLoader): Boolean {
        return try {
            val rule = classLoader.loadClass("com.android.launcher3.folder.ClippedFolderIconLayoutRule")
            ruleCols = findField(rule, "j")          // obfuscated short name: columns
            ruleRows = findField(rule, "g")          // obfuscated short name: rows
            ruleIconSize = findField(rule, "d")      // obfuscated short name: icon size
            ruleBgWidth = findField(rule, "b")       // obfuscated short name: bg width
            ruleBgHeight = findField(rule, "i")      // obfuscated short name: bg height
            val i = Int::class.javaPrimitiveType
            val f = Float::class.javaPrimitiveType
            ruleC = findMethod(
                rule, "c",
                i, i, FloatArray::class.java, i, i, i, i, i
            )
            ruleGetOffsetX = findMethod(rule, "getOffsetX", i, i, i, f, f, i, i)
            ruleGetOffsetY = findMethod(rule, "getOffsetY", i, i, i, f, f, i, i)
            ruleScaleForItem = findMethod(rule, "scaleForItem", i, Boolean::class.java)
            true
        } catch (t: Throwable) {
            logger.error("resolve rule refs failed, child grid hook skipped", t)
            false
        }
    }

    /** Style sheet group: BigFolderConfig (child grid/gap convergence point). */
    private fun resolveBfcRefs(classLoader: ClassLoader): Boolean {
        return try {
            val bfc = classLoader.loadClass("com.zui.launcher.folder.bigfolder.BigFolderConfig")
            bfcChildIconScale = findField(bfc, "CHILD_ICON_SCALE")
            bfcGetChildCount = findMethod(
                bfc, "getBigFolderIconChildCount",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, IntArray::class.java
            )
            bfcGetHGap = findMethod(bfc, "getBigFolderIconHGap", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            bfcGetVGap = findMethod(bfc, "getBigFolderIconVGap", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            true
        } catch (t: Throwable) {
            logger.error("resolve BigFolderConfig refs failed, child grid/gap hooks skipped", t)
            false
        }
    }

    /** FolderIcon group: label field resolved by candidate names, falling back to a BubbleTextView type scan. */
    private fun resolveFolderIconRefs(classLoader: ClassLoader): Boolean {
        return try {
            val fi = classLoader.loadClass("com.android.launcher3.folder.FolderIcon")
            fiActivityContext = findField(fi, "b")   // obfuscated short name: ActivityContext
            fiInfo = findField(fi, "mInfo")
            fiSpanX = findField(fiInfo.type, "spanX")
            fiSpanY = findField(fiInfo.type, "spanY")
            folderLabel = listOf("folderBubbleTextView", "e").firstNotNullOfOrNull { name ->
                try {
                    findField(fi, name)
                } catch (_: Throwable) {
                    null
                }
            }
            if (folderLabel == null) {
                val bubbleClass = classLoader.loadClass("com.android.launcher3.BubbleTextView")
                folderLabel = fi.declaredFields
                    .firstOrNull { bubbleClass.isAssignableFrom(it.type) }
                    ?.apply { isAccessible = true }
            }
            if (folderLabel == null) {
                logger.error("FolderIcon label field unresolved, label hook skipped")
                return false
            }
            fiZ = findMethod(fi, "z")
            true
        } catch (t: Throwable) {
            logger.error("resolve FolderIcon refs failed, label hook skipped", t)
            false
        }
    }

    /** Blur group: FolderIcon.A plus the drag state it dereferences. */
    private fun resolveBlurGuardRefs(classLoader: ClassLoader): Boolean {
        return try {
            val fi = classLoader.loadClass("com.android.launcher3.folder.FolderIcon")
            fiActivityContext = findField(fi, "b")
            fiDragFlag = findField(fi, "S")
            fiBlurData = findMethod(fi, "A")
            val launcher = classLoader.loadClass("com.android.launcher3.Launcher")
            launcherGetDragController = findMethod(launcher, "getDragController")
            dragControllerGetDragObject = findMethod(
                classLoader.loadClass("com.android.launcher3.dragndrop.DragController"),
                "getDragObject"
            )
            true
        } catch (t: Throwable) {
            logger.error("resolve blur guard refs failed, blur guard skipped", t)
            false
        }
    }

    /**
     * Runs the host's idle blur branch when its drag flag is set but the controller has no
     * drag object — the state long-pressing a big folder crashes in.
     */
    private fun hookBlurGuard() {
        hookWithId(fiBlurData, "big_folder_blur_guard") { chain ->
            val icon = chain.thisObject
            var flipped = false
            try {
                if (fiDragFlag.getBoolean(icon)) {
                    val context = fiActivityContext.get(icon)
                    val dragController = context?.let { launcherGetDragController.invoke(it) }
                    val dragObject = dragController?.let { dragControllerGetDragObject.invoke(it) }
                    if (dragObject == null) {
                        fiDragFlag.setBoolean(icon, false)
                        flipped = true
                        if (loggedSpans.add("blurGuardFlip")) {
                            logger.debug("blur guard: drag flag set with no drag object, idle branch used")
                        }
                    }
                }
            } catch (t: Throwable) {
                logger.debug("blur guard check failed: $t")
            }
            try {
                chain.proceed()
            } catch (t: Throwable) {
                // Last resort: a null drag object must never kill the launcher; anything else
                // still surfaces to the framework.
                if (!isNullPointer(t)) throw t
                if (loggedSpans.add("blurGuardNpe")) {
                    logger.warn("big folder blur update hit a null drag object, suppressed: $t")
                }
            } finally {
                if (flipped) {
                    try {
                        fiDragFlag.setBoolean(icon, true)
                    } catch (_: Throwable) {
                    }
                }
            }
            null
        }
        logger.debug("hooked FolderIcon.A (big folder blur guard)")
    }

    /** True when [t] or its cause chain is a NullPointerException. */
    private fun isNullPointer(t: Throwable): Boolean {
        var cause: Throwable? = t
        var depth = 0
        while (cause != null && depth < 4) {
            if (cause is NullPointerException) return true
            cause = cause.cause
            depth++
        }
        return false
    }

    private fun hookSetupGeometry() {
        hookWithId(pbSetup, "big_folder_align_setup") { chain ->
            chain.proceed()
            try {
                applyAlignedGeometry(chain.thisObject, chain.args.getOrNull(1), chain.args.getOrNull(2))
            } catch (t: Throwable) {
                logger.error("applyAlignedGeometry failed", t)
            }
            null
        }
        logger.debug("hooked PreviewBackground.setup")
    }

    private fun applyAlignedGeometry(pb: Any, activityContext: Any?, folderIconView: Any?) {
        if (!coreReady || activityContext == null) return
        val spanX = pbSpanX.getInt(pb)
        val spanY = pbSpanY.getInt(pb)
        // Same semantics as the host BigFolderConfig.isBigFolder
        if (spanX <= 1 && spanY <= 1) return

        val dp = activityContextGetDeviceProfile.invoke(activityContext) ?: return
        // The live page carries the measured cell; the profile values are the fallback.
        val page = LauncherGridMetrics.pageOf(folderIconView as? View)
        val metrics = LauncherGridMetrics.fromPage(page, dp) ?: return
        // Static host methods (gap queries) have no page reference; they read this snapshot.
        LauncherGridMetrics.publish(metrics)

        val inset = if (alignToSmallFolder) metrics.insetToSmallFolder else metrics.insetToIcon
        val newWidth = spanX * metrics.cellWidth + (spanX - 1) * metrics.borderX - 2 * inset
        val oldWidth = pbWidth.getInt(pb)
        val oldOffsetX = pbOffsetX.getInt(pb)
        pbWidth.setInt(pb, newWidth)
        pbOffsetX.setInt(pb, inset)

        // Background top/bottom edges align with the graphic edges of the top/bottom icon rows.
        val artInset = artInsetPx(metrics)
        val newOffsetY = metrics.rowInset + artInset
        val newPreviewSizeY = (spanY - 1) * metrics.cellPitchY +
            metrics.rowInset + metrics.iconSizePx - artInset - newOffsetY
        val oldOffsetY = pbOffsetY.getInt(pb)
        val oldPreviewSizeY = pbPreviewSizeY.getInt(pb)
        if (newPreviewSizeY > 0) {
            pbOffsetY.setInt(pb, newOffsetY)
            pbPreviewSizeY.setInt(pb, newPreviewSizeY)
        }

        // Telemetry: label topMargin vs FolderIcon's own paddingTop (visual label position = their sum)
        var labelTopMargin = -1
        var iconPaddingTop = -1
        try {
            val f = folderLabel
            if (folderIconView != null && f != null) {
                labelTopMargin = ((f.get(folderIconView) as View).layoutParams
                        as ViewGroup.MarginLayoutParams).topMargin
            }
            iconPaddingTop = (folderIconView as? View)?.paddingTop ?: -1
        } catch (_: Throwable) {
        }

        logger.debug(
            "BigFolderAlign span=${spanX}x${spanY}" +
                " width $oldWidth->$newWidth offsetX $oldOffsetX->$inset" +
                " offsetY $oldOffsetY->$newOffsetY previewY $oldPreviewSizeY->${pbPreviewSizeY.getInt(pb)}" +
                " artInset=$artInset" +
                " cellW=${metrics.cellWidth} nominalW=${metrics.nominalCellWidth}" +
                " border=${metrics.borderX},${metrics.borderY} measured=${metrics.measured}" +
                " cellH=${metrics.cellHeight} rowInset=${metrics.rowInset}" +
                " profileContent=${metrics.profileContentHeightPx}" +
                " folderIcon=${metrics.folderIconSizePx} icon=${metrics.iconSizePx}" +
                " widgetPadL=${metrics.widgetPaddingLeft} widgetPadT=${metrics.widgetPaddingTop}" +
                " bgBottom=${pbOffsetY.getInt(pb) + pbPreviewSizeY.getInt(pb)}" +
                " labelTop=$labelTopMargin iconPadT=$iconPaddingTop"
        )
    }

    /**
     * Transparent margin around the icon artwork inside the icon box.
     *
     * DeviceProfile.widgetPadding is the host's own inset for the big-folder background;
     * [ART_INSET_RATIO] only covers platforms that report no widget padding.
     */
    private fun artInsetPx(metrics: LauncherGridMetrics.Metrics): Int =
        if (metrics.widgetPaddingTop > 0) metrics.widgetPaddingTop
        else (metrics.iconSizePx * ART_INSET_RATIO).roundToInt()

    /** Style sheet convergence rewrite: one rewrite makes layout/preview count/click hit-testing/drop capacity all take effect. */
    private fun hookChildCountRewrite() {
        hookWithId(bfcGetChildCount, "big_folder_align_child_count") { chain ->
            val result = chain.proceed()
            try {
                val spanX = chain.args[0] as Int
                val spanY = chain.args[1] as Int
                val out = chain.args[2] as IntArray?
                val key = "${spanX}x$spanY"
                if (out != null) {
                    stockGrids[key] = intArrayOf(out[0], out[1])
                }
                val rewrite: IntArray? = when {
                    spanX == TARGET_SPAN_X && spanY == TARGET_SPAN_Y ->
                        intArrayOf(CHILD_COLS, CHILD_ROWS)
                    spanY == TARGET_SPAN_Y && spanX >= 2 -> {
                        val stock = stockGrids[key]
                        if (stock != null && stock[1] == 2) intArrayOf(stock[0], CHILD_ROWS)
                        else null
                    }
                    else -> null
                }
                if (rewrite != null) {
                    out?.set(0, rewrite[0])
                    out?.set(1, rewrite[1])
                    rewrittenGrids[key] = rewrite
                    val newCount = rewrite[0] * rewrite[1]
                    if ((result as Int) != newCount && loggedSpans.add(key)) {
                        logger.debug(
                            "childCount($key) $result -> $newCount (cols=${rewrite[0]} rows=${rewrite[1]})"
                        )
                    }
                    return@hookWithId newCount
                }
            } catch (t: Throwable) {
                logger.error("childCount rewrite failed", t)
            }
            result
        }
        logger.debug("hooked getBigFolderIconChildCount")
    }

    /** The c() phone branch is hardcoded 2-column positioning; uniformly rewritten to the rule's own getOffsetX/getOffsetY generic grid. */
    private fun hookChildGridRule() {
        hookWithId(ruleC, "big_folder_align_child_grid") { chain ->
            chain.proceed()
            try {
                val args = chain.args
                val index = args[0] as Int
                val spanX = args[3] as Int
                val spanY = args[4] as Int
                // ENTER/EXIT(-2/-3) go through the animation-specific path; leave non-big-folders untouched
                if (index < 0 || (spanX <= 1 && spanY <= 1)) return@hookWithId null
                val rule = chain.thisObject
                val cols = ruleCols.getInt(rule)
                val rows = ruleRows.getInt(rule)
                val iconSize = ruleIconSize.getFloat(rule)
                val halfIcon =
                    iconSize * (ruleScaleForItem.invoke(rule, maxOf(args[1] as Int, 3), true) as Float) / 2f
                val centerX =
                    if ((args[5] as Int) == -1) ruleBgWidth.getFloat(rule) / 2f
                    else (args[5] as Int) / 2f
                val centerY =
                    (if ((args[6] as Int) == -1) ruleBgHeight.getFloat(rule) / 2f
                    else (args[6] as Int) / 2f) + (args[7] as Int)
                val out = args[2] as FloatArray
                out[0] = ruleGetOffsetX.invoke(rule, index, cols, rows, centerX, halfIcon, spanX, spanY) as Float
                out[1] = ruleGetOffsetY.invoke(rule, index, cols, rows, centerY, halfIcon, spanX, spanY) as Float
                logger.debug("childGrid idx=$index -> (${out[0]}, ${out[1]})")
            } catch (t: Throwable) {
                logger.error("child grid rewrite failed", t)
            }
            null
        }
        logger.debug("hooked ClippedFolderIconLayoutRule.c")
    }

    /**
     * Stock gaps are tuned for the stock grid; for every big-folder span they are solved
     * from the background box instead, so the child grid fills the same box whose insets
     * the background uses. The centered layout turns the leftover into outer margins.
     */
    private fun hookFolderGaps() {
        for ((method, isH) in listOf(bfcGetHGap to true, bfcGetVGap to false)) {
            val id = "big_folder_align_${if (isH) "h" else "v"}gap"
            hookWithId(method, id) { chain ->
                val result = chain.proceed()
                try {
                    val spanX = chain.args[0] as Int
                    val spanY = chain.args[1] as Int
                    if (spanX <= 1 && spanY <= 1) return@hookWithId result
                    val spanKey = "${spanX}x$spanY"
                    val grid = rewrittenGrids[spanKey] ?: stockGrids[spanKey]
                        ?: return@hookWithId result
                    val metrics = LauncherGridMetrics.cached ?: return@hookWithId result
                    if (metrics.cellWidth <= 0) return@hookWithId result
                    val n = if (isH) grid[0] else grid[1]
                    if (n <= 1) return@hookWithId 0f
                    val bgAxis = if (isH) {
                        (spanX - 1) * metrics.cellPitchX + metrics.folderIconSizePx
                    } else {
                        (spanY - 1) * metrics.cellPitchY + metrics.iconSizePx - 2 * artInsetPx(metrics)
                    }
                    val childSize = metrics.folderIconSizePx * bfcChildIconScale.getFloat(null)
                    // (n + 1) splits the free space over the n-1 inner gaps and the two outer
                    // margins equally, so the grid never touches the background edge.
                    val newGap = maxOf(0f, (bgAxis - n * childSize) / (n + 1))
                    val logKey = "${if (isH) "h" else "v"}Gap$spanKey"
                    if (loggedSpans.add(logKey) && newGap != (result as Float)) {
                        logger.debug(
                            "${if (isH) "hGap" else "vGap"}($spanX,$spanY) $result -> $newGap " +
                                "(n=$n bg=$bgAxis child=$childSize)"
                        )
                    }
                    return@hookWithId newGap
                } catch (t: Throwable) {
                    logger.error("gap rewrite failed", t)
                    result
                }
            }
        }
        logger.debug("hooked getBigFolderIconHGap/VGap")
    }

    private fun hookFolderLabel() {
        val labelField = folderLabel ?: return
        hookWithId(fiZ, "big_folder_align_label") { chain ->
            try {
                val icon = chain.thisObject
                val info = fiInfo.get(icon)
                    ?: return@hookWithId chain.proceed()
                val spanX = fiSpanX.getInt(info)
                val spanY = fiSpanY.getInt(info)
                val activityContext = fiActivityContext.get(icon)
                    ?: return@hookWithId chain.proceed()
                val dp = activityContextGetDeviceProfile.invoke(activityContext)
                    ?: return@hookWithId chain.proceed()
                val iconSizePx = dpIconSize.getInt(dp)
                val drawablePadding = dpIconDrawablePadding.getInt(dp)
                val topMargin: Int
                if (spanX <= 1 && spanY <= 1) {
                    // Small folder branch: replicate the stock z() formula
                    topMargin = iconSizePx + drawablePadding
                } else {
                    val metrics = LauncherGridMetrics.fromPage(
                        LauncherGridMetrics.pageOf(icon as? View), dp
                    ) ?: return@hookWithId chain.proceed()
                    topMargin = (spanY - 1) * metrics.cellPitchY +
                        metrics.rowInset + iconSizePx + drawablePadding
                }
                val label = labelField.get(icon) as View
                val lp = label.layoutParams as ViewGroup.MarginLayoutParams
                if (lp.topMargin != topMargin) {
                    lp.topMargin = topMargin
                    label.requestLayout()
                    logger.debug("label topMargin -> $topMargin (span=${spanX}x${spanY})")
                }
                null // Fully replaces the host z(); on reflection failure the catch falls back to stock
            } catch (t: Throwable) {
                logger.error("label align failed, fallback to stock z()", t)
                chain.proceed()
            }
        }
        logger.debug("hooked FolderIcon.z (label alignment)")
    }

    private fun hookAvailableWh() {
        hookWithId(pbComputeWh, "big_folder_align_available_wh") { chain ->
            chain.proceed()
            try {
                val args = chain.args
                val wh = args[3] as IntArray
                val holder = args[0] ?: return@hookWithId null
                val dp = activityContextGetDeviceProfile.invoke(holder) ?: return@hookWithId null
                // This call is reached from resize flows; the last setup already published
                // the measured page geometry for the same folder.
                val metrics = LauncherGridMetrics.cached
                    ?: LauncherGridMetrics.fromPage(null, dp)
                    ?: return@hookWithId null
                val spanX = args[1] as Int
                if (spanX > 1) {
                    val inset = if (alignToSmallFolder) metrics.insetToSmallFolder else metrics.insetToIcon
                    val old = wh[0]
                    wh[0] = spanX * metrics.cellWidth + (spanX - 1) * metrics.borderX - 2 * inset
                    logger.debug("availableWh spanX=$spanX width $old -> ${wh[0]} (h=${wh[1]})")
                }
            } catch (t: Throwable) {
                logger.error("availableWh adjust failed", t)
            }
            null
        }
        logger.debug("hooked computeBigFolderAvaliableWh")
    }

    private fun hookIsUpdatePreviewSize() {
        hookWithId(pbIsUpdate, "big_folder_align_is_update") { chain ->
            val result = chain.proceed()
            try {
                val pb = chain.thisObject
                logger.debug(
                    "isUpdatePreviewSize -> $result span=${pbSpanX.getInt(pb)}x${pbSpanY.getInt(pb)}" +
                        " width=${pbWidth.getInt(pb)} offsetX=${pbOffsetX.getInt(pb)}"
                )
            } catch (_: Throwable) {
            }
            result
        }
        logger.debug("hooked isUpdatePreviewSize")
    }

    private fun hookDeviceProfileTelemetry() {
        hookWithId(dpUpdateIconSize, "big_folder_align_dp_log") { chain ->
            chain.proceed()
            try {
                val dp = chain.thisObject
                val inv = dpInv.get(dp)
                val padding = dpPadding.get(dp) as Rect
                val borderSpace = dpBorderSpace.get(dp) as Point
                val widgetPadding = dpWidgetPadding.get(dp) as Rect
                // Snapshot the same numbers the geometry hooks consume, so a grid change is
                // verifiable from the log alone.
                val metrics = LauncherGridMetrics.fromPage(null, dp)
                logger.debug(
                    "DeviceProfile cols=${invNumColumns.getInt(inv)}" +
                        " cellLayoutW=${dpGetCellLayoutWidth.invoke(dp)}" +
                        " pad=${padding.left}x${padding.top}" +
                        " gap=${borderSpace.x}x${borderSpace.y}" +
                        " iconSize=${dpIconSize.getInt(dp)}" +
                        " folderIcon=${dpFolderIconSize.getInt(dp)}" +
                        " folderOffsetY=${dpFolderIconOffsetY.getInt(dp)}" +
                        " widgetPad=${widgetPadding.left},${widgetPadding.top}" +
                        " cellW=${metrics?.cellWidth} cellH=${metrics?.cellHeight}" +
                        " nominalW=${metrics?.nominalCellWidth}" +
                        " content=${metrics?.contentHeightPx}" +
                        " profileContent=${metrics?.profileContentHeightPx}"
                )
            } catch (t: Throwable) {
                logger.debug("DeviceProfile telemetry failed: $t")
            }
            null
        }
        logger.debug("hooked DeviceProfile.updateIconSize (telemetry)")
    }

    companion object {
        /** 2x2 big folder child icon grid = CHILD_COLS per row x CHILD_ROWS rows (3x3=9). */
        private const val TARGET_SPAN_X = 2
        private const val TARGET_SPAN_Y = 2
        private const val CHILD_COLS = 3
        private const val CHILD_ROWS = 3

        /** Transparent margin ratio around the artwork inside the icon box; fallback only (measured ~21px in a 190px box). */
        private const val ART_INSET_RATIO = 0.11f

        /** One-shot log dedup (childCount/gap calls are very frequent; each key is logged once). */
        private val loggedSpans: MutableSet<String> =
            java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

        /** Learned stock child grids (span -> [cols, rows]), consulted by calls with out=null. */
        private val stockGrids = java.util.concurrent.ConcurrentHashMap<String, IntArray>()

        /** Spans with rewritten child grids (span -> [cols, rows]); gap recomputation prefers them. */
        private val rewrittenGrids = java.util.concurrent.ConcurrentHashMap<String, IntArray>()
    }
}
