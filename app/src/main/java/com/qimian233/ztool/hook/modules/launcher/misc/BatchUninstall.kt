package com.qimian233.ztool.hook.modules.launcher.misc

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Process
import android.os.UserHandle
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Array as JvmArray
import java.util.Locale

/**
 * 桌面编辑模式（多选）批量卸载。
 *
 * 在 `ZuiEditModePanel.onFinishInflate` 之后向底栏克隆注入一个"卸载应用"按钮，
 * 并把按钮加入 `getEditModeTranslateAnimViews()` 返回的数组，使其与
 * 壁纸/小组件/设置等按钮一起参与编辑模式的进出场动画。
 *
 * 点击按钮时通过公开 API（getSelectedViews / View.tag）收集选中的 ItemInfo，
 * 过滤出主用户、itemType==APP_ITEM 且非系统应用（LauncherApps 解析 + FLAG_SYSTEM 判断，
 * 与启动器自身 `Utilities.getUninstallTarget` 语义一致）的包名，去重后
 * 以显式 Intent 交给 ZTool 的 BatchUninstallActivity 走 Root 静默卸载。
 *
 * Hook 的方法名均为未混淆的稳定名称（框架覆写或描述性 getter），
 * 因此不走 DexIndex，失败时直接降级为无此按钮并记录日志。
 */
@SuppressLint("PrivateApi", "DiscouragedApi", "UseCompatLoadingForDrawables")
class BatchUninstall : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.LAUNCHER_BATCH_UNINSTALL.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        try {
            val panelClass = classLoader.loadClass(EDIT_MODE_PANEL_CLASS)

            val onFinishInflate = panelClass.getDeclaredMethod("onFinishInflate")
            hookWithId(onFinishInflate, "batch_uninstall_on_finish_inflate") { chain ->
                try {
                    chain.proceed()
                    val panel = chain.thisObject as? ViewGroup
                    if (panel != null) {
                        installButton(panel)
                    }
                } catch (t: Throwable) {
                    logger.error("BatchUninstall: failed to inject edit mode button", t)
                }
                null
            }

            val translateAnimViews = panelClass.getDeclaredMethod("getEditModeTranslateAnimViews")
            hookWithId(translateAnimViews, "batch_uninstall_translate_anim_views") { chain ->
                val original = chain.proceed()
                try {
                    val views = original as? Array<*> ?: return@hookWithId original
                    val panel = chain.thisObject as? View ?: return@hookWithId original
                    val button = panel.findViewWithTag<View>(BUTTON_TAG)
                        ?: return@hookWithId original
                    if (views.contains(button)) return@hookWithId original
                    val componentType = views.javaClass.componentType
                        ?: return@hookWithId original
                    val merged = JvmArray.newInstance(componentType, views.size + 1)
                    System.arraycopy(views, 0, merged, 0, views.size)
                    JvmArray.set(merged, views.size, button)
                    logger.debug("BatchUninstall: button joined edit mode translate anim views")
                    merged
                } catch (t: Throwable) {
                    logger.error("BatchUninstall: failed to attach button to edit mode anim views", t)
                    original
                }
            }

            // 底栏是双态布局：未选中时显示 壁纸/小组件/设置 行，选中后切换为
            // 组成文件夹/移除图标 行。批量卸载只在有选中项时显示，跟随这三个
            // 公开的选中集变更方法同步可见性。
            // 注意：switchSelectedState 返回原始 boolean，hooker 必须透传
            // proceed() 的结果，返回 null 会在桥接层拆箱时 NPE。
            val switchSelectedState = panelClass.getDeclaredMethod("switchSelectedState", View::class.java)
            hookWithId(switchSelectedState, "batch_uninstall_switch_selected") { chain ->
                val result = chain.proceed()
                try {
                    syncButtonVisibility(chain.thisObject as? View)
                } catch (t: Throwable) {
                    logger.error("BatchUninstall: failed to sync after switchSelectedState", t)
                }
                result
            }

            val clearSelectedItems = panelClass.getDeclaredMethod("clearSelectedItems")
            hookWithId(clearSelectedItems, "batch_uninstall_clear_selected") { chain ->
                val result = chain.proceed()
                try {
                    syncButtonVisibility(chain.thisObject as? View)
                } catch (t: Throwable) {
                    logger.error("BatchUninstall: failed to sync after clearSelectedItems", t)
                }
                result
            }

            val initSelectedItemByIds = panelClass.getDeclaredMethod(
                "initSelectedItemByIds",
                ArrayList::class.java
            )
            hookWithId(initSelectedItemByIds, "batch_uninstall_init_selected") { chain ->
                val result = chain.proceed()
                try {
                    syncButtonVisibility(chain.thisObject as? View)
                } catch (t: Throwable) {
                    logger.error("BatchUninstall: failed to sync after initSelectedItemByIds", t)
                }
                result
            }

            logger.info("BatchUninstall hooks installed")
        } catch (t: Throwable) {
            logger.error("Failed to install batch uninstall hooks", t)
        }
    }

    // ── 按钮注入 ────────────────────────────────────────────────

    private fun installButton(panel: ViewGroup) {
        if (panel.findViewWithTag<View>(BUTTON_TAG) != null) return
        val context = panel.context
        val resources = context.resources
        val packageName = context.packageName

        // 实测 ZUI 平板 18.1.9：bottom_panel 下是两个 RelativeLayout 容器，
        // drop_combine_folder（图标+文字，静止可见）与 drop_remove_icon_container
        // （静止时 GONE，拖拽时才出现"移除"目标）。按钮必须挂在 bottom_panel 上、
        // 插在两个容器之间，绝不能进 remove 容器内部，否则与拖拽态重叠。
        val combineContainer = findViewByIdOrNull(panel, resources, packageName, "drop_combine_folder_container")
        val combineText = findViewByIdOrNull(panel, resources, packageName, "drop_combine_folder") as? TextView
        val removeTarget = findViewByIdOrNull(panel, resources, packageName, "drop_remove_icon")
        val removeContainer = findViewByIdOrNull(panel, resources, packageName, "drop_remove_icon_container")
            ?: (removeTarget?.parent as? View)

        val styleSource = combineText ?: (removeTarget as? TextView)
        val anchor = removeContainer ?: removeTarget
        if (styleSource == null || anchor == null) {
            logger.warn("BatchUninstall: edit mode bottom anchors not found, button skipped")
            return
        }

        val button = TextView(context)
        button.tag = BUTTON_TAG
        button.id = View.generateViewId()
        button.text = moduleString(context, STRING_BUTTON, FALLBACK_BUTTON)
        button.isAllCaps = false
        copyStyle(button, styleSource)
        applyIcon(button, styleSource, removeTarget, resources, packageName)

        val layoutParams = buildLayoutParams(button.id, anchor, combineContainer)
        if (layoutParams == null) {
            logger.warn("BatchUninstall: cannot build layout params, button skipped")
            return
        }
        // 编辑模式的显隐由面板 alpha 与 translationY 动画驱动，行内视图从不调
        // setVisibility，因此可见性只在 VISIBLE/GONE 之间切换（跟随选中数）
        button.visibility = View.GONE
        syncButtonVisibility(panel)

        (anchor.parent as? ViewGroup ?: panel).addView(button, layoutParams)
        button.setOnClickListener { handleClicked(panel) }
        logger.info(
            "BatchUninstall: edit mode button injected into " +
                anchor.parent.javaClass.name
        )
    }

    private fun copyStyle(button: TextView, styleSource: TextView) {
        runCatching {
            button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, styleSource.textSize)
            styleSource.textColors?.let { button.setTextColor(it) }
            button.gravity = styleSource.gravity
            button.setPadding(
                styleSource.paddingLeft,
                styleSource.paddingTop,
                styleSource.paddingRight,
                styleSource.paddingBottom
            )
            styleSource.background?.constantState?.newDrawable()?.let { button.background = it }
            button.compoundDrawablePadding = styleSource.compoundDrawablePadding
            button.typeface = styleSource.typeface
            button.includeFontPadding = styleSource.includeFontPadding
        }.onFailure { logger.warn("BatchUninstall: failed to copy button style: " + it.message) }
    }

    /**
     * 按样式模板的 drawable 方位放置图标，素材按优先级：
     * 1. drop_remove_icon 的复合图标——编辑模式设计体系里的删除图标
     *    （圆形底 + glyph、与组成文件夹同规格）；该视图静止时 GONE，
     *    但 XML 设置的 drawable 依然存在；
     * 2. 自绘复合图标：半透明圆形底 + ic_delete_zui 居中缩放，尺寸参照
     *    drop_combine_folder 的实测 intrinsic；
     * 3. 最后回退 ic_delete_zui intrinsic（旧的小图标行为）。
     */
    private fun applyIcon(
        button: TextView,
        styleSource: TextView,
        removeTarget: View?,
        resources: Resources,
        packageName: String
    ) {
        val templates = styleSource.compoundDrawablesRelative
        var index = templates.indexOfFirst { it != null }
        if (index < 0) index = 1 // 模板无图标时默认放顶部，与底栏图标按钮一致

        var icon: Drawable? = null
        var source = "none"
        val removeDrawable = (removeTarget as? TextView)
            ?.compoundDrawablesRelative
            ?.getOrNull(index)
        if (removeDrawable != null) {
            icon = removeDrawable.constantState?.newDrawable()
            source = "remove_target"
        }
        if (icon == null) {
            icon = buildCompositeIcon(button.context, templates.getOrNull(index), resources, packageName)
            if (icon != null) source = "composite"
        }
        if (icon == null) {
            val deleteId = resources.getIdentifier("ic_delete_zui", "drawable", packageName)
            if (deleteId != 0) {
                icon = runCatching { resources.getDrawable(deleteId, button.context.theme) }.getOrNull()
                source = "ic_delete_zui"
            }
        }
        if (icon == null) {
            logger.warn("BatchUninstall: no icon drawable available")
            return
        }
        val arranged = arrayOfNulls<Drawable>(4)
        arranged[index] = icon
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(
            arranged[0], arranged[1], arranged[2], arranged[3]
        )
        logger.info(
            "BatchUninstall: icon from $source " +
                "intrinsic=${icon.intrinsicWidth}x${icon.intrinsicHeight} " +
                "(template=${templates.getOrNull(index)?.intrinsicWidth}x" +
                "${templates.getOrNull(index)?.intrinsicHeight})"
        )
    }

    /** 兜底：半透明圆形底 + 居中缩放的删除 glyph，尺寸参照模板图标的 intrinsic。 */
    private fun buildCompositeIcon(
        context: Context,
        sizeReference: Drawable?,
        resources: Resources,
        packageName: String
    ): Drawable? {
        val deleteId = resources.getIdentifier("ic_delete_zui", "drawable", packageName)
        val glyph = if (deleteId != 0) {
            runCatching { resources.getDrawable(deleteId, context.theme) }.getOrNull()
        } else null
        val scrim = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x66000000)
        }
        val size = sizeReference?.intrinsicWidth?.takeIf { it > 0 }
            ?: android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP,
                60f,
                resources.displayMetrics
            ).toInt()
        val inset = (size * 0.28f).toInt()
        return if (glyph != null) {
            object : LayerDrawable(arrayOf(scrim, glyph)) {
                override fun getIntrinsicWidth(): Int = size
                override fun getIntrinsicHeight(): Int = size
            }.apply { setLayerInset(1, inset, inset, inset, inset) }
        } else {
            object : LayerDrawable(arrayOf(scrim)) {
                override fun getIntrinsicWidth(): Int = size
                override fun getIntrinsicHeight(): Int = size
            }
        }
    }

    private fun findViewByIdOrNull(panel: View, resources: Resources, packageName: String, name: String): View? {
        val id = resources.getIdentifier(name, "id", packageName)
        return if (id != 0) panel.findViewById(id) else null
    }

    /**
     * 有选中项才显示按钮（仅在 VISIBLE/GONE 间切换，不参与面板显隐动画），
     * 并把"组成文件夹"容器与按钮摆成关于屏幕中轴线对称的双槽布局。
     *
     * bottom_panel 是自定义 ViewGroup（子项 LayoutParams 为私有类型，无法
     * 用规则/锚点定位），因此采用类型无关的平移方案：读取两者当前布局
     * 中心，各自 translationX 到 1600±(面板宽度*0.086)（本机 ±275px，
     * 对应三槽布局的半槽距）。translationX 与进出场动画驱动的
     * translationY 互不干扰；未选中态复位容器偏移。
     */
    private fun syncButtonVisibility(panel: View?) {
        val button = panel?.findViewWithTag<View>(BUTTON_TAG) ?: return
        try {
            val count = panel.javaClass.getMethod("getSelectedCount").invoke(panel) as? Int ?: 0
            val visible = count > 0
            if (button.visibility != (if (visible) View.VISIBLE else View.GONE)) {
                button.visibility = if (visible) View.VISIBLE else View.GONE
                logger.debug("BatchUninstall: button visibility -> $visible (selected=$count)")
            }
            val context = panel.context
            val combine = findViewByIdOrNull(
                panel, context.resources, context.packageName, "drop_combine_folder_container"
            ) ?: return
            if (panel.width == 0 || combine.width == 0) return
            if (visible) {
                val centerX = panel.width / 2f
                val slotOffset = panel.width * SLOT_OFFSET_RATIO
                // 组成文件夹容器原生居中于中央槽
                combine.translationX =
                    (centerX - slotOffset) - (combine.left + combine.width / 2f)
                // 按钮布局位置由克隆参数决定（右侧槽），置零后等布局完成再对称偏移
                button.translationX = 0f
                button.post {
                    button.translationX =
                        (centerX + slotOffset) - (button.left + button.width / 2f)
                }
            } else {
                combine.translationX = 0f
                button.translationX = 0f
            }
        } catch (t: Throwable) {
            logger.warn("BatchUninstall: failed to sync button visibility: " + t.message)
        }
    }

    private fun cloneLayoutParams(source: ViewGroup.LayoutParams?): ViewGroup.LayoutParams? {
        if (source == null) return null
        return try {
            val constructor = source.javaClass.getConstructor(source.javaClass)
            constructor.newInstance(source) as ViewGroup.LayoutParams
        } catch (t: Throwable) {
            logger.warn("BatchUninstall: no copy constructor for ${source.javaClass.name}: " + t.message)
            null
        }
    }

    /**
     * 构建按钮布局参数：ConstraintLayout 宿主锚在合并容器与移除锚点之间；
     * RelativeLayout 宿主（实测 ZUI 平板 bottom_panel）垂直居中并置于移除
     * 容器左侧；其他布局保持克隆值。
     */
    private fun buildLayoutParams(
        buttonId: Int,
        anchor: View,
        combineContainer: View?
    ): ViewGroup.LayoutParams? {
        val source = anchor.layoutParams ?: return null
        if (source.javaClass.name == CONSTRAINT_LAYOUT_LP) {
            val layoutParams = cloneLayoutParams(source) ?: return null
            adjustConstraintLayoutParams(layoutParams, anchor, combineContainer, buttonId)
            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            return layoutParams
        }
        if (source is RelativeLayout.LayoutParams) {
            // 布局位置取中央槽，视觉对称由 syncButtonVisibility 的 translationX 完成
            val layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
            return layoutParams
        }
        return cloneLayoutParams(source)
    }

    private fun adjustConstraintLayoutParams(
        layoutParams: ViewGroup.LayoutParams,
        anchor: View,
        combineContainer: View?,
        buttonId: Int
    ) {
        try {
            val lpClass = layoutParams.javaClass
            val unset = lpClass.getField("UNSET").getInt(null)
            val source = anchor.layoutParams
            val sourceClass = source.javaClass

            for (field in listOf("topToTop", "topToBottom", "bottomToTop", "bottomToBottom")) {
                lpClass.getField(field).setInt(layoutParams, sourceClass.getField(field).getInt(source))
            }
            lpClass.getField("verticalBias").setFloat(
                layoutParams,
                sourceClass.getField("verticalBias").getFloat(source)
            )
            lpClass.getField("startToStart").setInt(layoutParams, unset)
            lpClass.getField("endToEnd").setInt(layoutParams, unset)

            if (combineContainer != null && combineContainer !== anchor) {
                lpClass.getField("startToEnd").setInt(layoutParams, combineContainer.id)
                lpClass.getField("endToStart").setInt(layoutParams, anchor.id)
                val combineLp = combineContainer.layoutParams
                if (combineLp.javaClass == lpClass &&
                    combineLp.javaClass.getField("endToStart").getInt(combineLp) == anchor.id
                ) {
                    combineLp.javaClass.getField("endToStart").setInt(combineLp, buttonId)
                    combineContainer.layoutParams = combineLp
                }
            } else {
                lpClass.getField("endToStart").setInt(layoutParams, anchor.id)
            }
        } catch (t: Throwable) {
            logger.warn("BatchUninstall: failed to adjust constraint params: " + t.message)
        }
    }

    // ── 点击分发 ────────────────────────────────────────────────

    /** 一个待卸载候选：展示用应用名 + 执行用包名。 */
    private data class UninstallCandidate(val label: String, val packageName: String)

    private fun handleClicked(panel: ViewGroup) {
        try {
            val context = panel.context
            val candidates = collectUninstallableCandidates(panel, context)
            if (candidates.isEmpty()) {
                Toast.makeText(
                    context,
                    moduleString(context, STRING_NO_APPS, FALLBACK_NO_APPS),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            if (!showConfirmDialog(panel, context, candidates)) {
                logger.warn("BatchUninstall: no confirm dialog available, aborted")
                return
            }
        } catch (t: Throwable) {
            logger.error("BatchUninstall: failed to show confirm dialog", t)
        }
    }

    /**
     * 弹 ZUI 风格确认框；MessageDialog 反射失败时回退标准 AlertDialog，
     * 两者都失败则返回 false（宁可中止也绝不无确认卸载）。
     */
    private fun showConfirmDialog(
        panel: ViewGroup,
        context: Context,
        candidates: List<UninstallCandidate>
    ): Boolean {
        // 展示应用名，缺失时回退包名
        val labelList = candidates.joinToString(separator = "\n") {
            "- " + it.label.ifBlank { it.packageName }
        }
        val message = String.format(
            Locale.getDefault(),
            moduleString(context, STRING_DIALOG_MESSAGE, FALLBACK_DIALOG_MESSAGE),
            candidates.size,
            labelList
        )
        val title = moduleString(context, STRING_DIALOG_TITLE, FALLBACK_DIALOG_TITLE)
        val packages = candidates.map { it.packageName }
        val onConfirm = Runnable { dispatchBatchUninstall(panel, context, packages) }
        if (showZuiConfirmDialog(context, title, message, onConfirm)) {
            logger.debug("BatchUninstall: ZUI MessageDialog shown")
            return true
        }
        if (showFallbackConfirmDialog(context, title, message, onConfirm)) {
            logger.info("BatchUninstall: fallback AlertDialog shown")
            return true
        }
        return false
    }

    /**
     * 反射调用启动器自带的 zui.app.MessageDialog，用法对照
     * EditModeRemoveDropTarget.m()（Builder 链 + 窗口 2038）。
     */
    private fun showZuiConfirmDialog(
        launcher: Context,
        title: CharSequence,
        message: CharSequence,
        onConfirm: Runnable
    ): Boolean {
        return try {
            val loader = launcher.classLoader
            val dialogClass = Class.forName("zui.app.MessageDialog", true, loader)
            val builderClass = Class.forName($$"zui.app.MessageDialog$Builder", true, loader)
            val builder = builderClass.getConstructor(Context::class.java).newInstance(launcher)
            val resources = launcher.resources
            val packageName = launcher.packageName

            val cancelListener = DialogInterface.OnCancelListener { }
            // 取消与确认必须是两个监听器：共用会使得点取消也触发卸载
            val negativeListener = DialogInterface.OnClickListener { dialog, _ -> dialog.dismiss() }
            val positiveListener = DialogInterface.OnClickListener { _, _ -> onConfirm.run() }

            builderClass.getMethod("setCancelable", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
            builderClass.getMethod("setOnCancelListener", DialogInterface.OnCancelListener::class.java)
                .invoke(builder, cancelListener)
            try {
                builderClass.getMethod("setTitle", CharSequence::class.java).invoke(builder, title)
            } catch (_: Throwable) {
                val resId = resources.getIdentifier("uninstall_item_title", "string", packageName)
                builderClass.getMethod("setTitle", Int::class.javaPrimitiveType).invoke(builder, resId)
            }
            try {
                builderClass.getMethod("setMessageDialogType", Int::class.javaPrimitiveType)
                    .invoke(builder, 0)
            } catch (_: Throwable) {
                // 旧版本可能没有该选项，忽略
            }
            setDialogButton(builderClass, builder, "setNegativeButton", resources, packageName,
                "cancel_action", android.R.string.cancel, negativeListener)
            setDialogButton(builderClass, builder, "setPositiveButton", resources, packageName,
                "uninstall_item_title", 0, positiveListener)

            val dialog = builderClass.getMethod("create").invoke(builder) as Dialog
            // Builder 没有 setMessage（参考 EditModeRemoveDropTarget 的用法），
            // 必须在 create 之后调 MessageDialog.setMessage，标题会吞掉换行
            dialogClass.getMethod("setMessage", CharSequence::class.java).invoke(dialog, message)
            runCatching {
                // 长列表（最多 24 项）需要放开高度限制
                dialogClass.getMethod("disableHeightRestrictions", Boolean::class.javaPrimitiveType)
                    .invoke(dialog, true)
            }
            dialog.setCanceledOnTouchOutside(true)
            runCatching { dialog.window?.setType(DIALOG_WINDOW_TYPE) }
            dialog.show()
            true
        } catch (t: Throwable) {
            logger.warn("BatchUninstall: MessageDialog reflection failed: " + t.message)
            false
        }
    }

    /** 优先 (int resId, listener) 形参，失败再试 (CharSequence, listener)。 */
    private fun setDialogButton(
        builderClass: Class<*>,
        builder: Any,
        methodName: String,
        resources: Resources,
        packageName: String,
        resName: String,
        fallbackResId: Int,
        clickListener: DialogInterface.OnClickListener
    ) {
        val resId = resources.getIdentifier(resName, "string", packageName)
            .takeIf { it != 0 } ?: fallbackResId
        try {
            builderClass.getMethod(
                methodName,
                Int::class.javaPrimitiveType,
                DialogInterface.OnClickListener::class.java
            ).invoke(builder, resId, clickListener)
        } catch (_: Throwable) {
            val text = if (resId != 0) {
                runCatching { resources.getString(resId) }.getOrDefault("")
            } else ""
            builderClass.getMethod(
                methodName,
                CharSequence::class.java,
                DialogInterface.OnClickListener::class.java
            ).invoke(builder, text, clickListener)
        }
    }

    private fun showFallbackConfirmDialog(
        context: Context,
        title: CharSequence,
        message: CharSequence,
        onConfirm: Runnable
    ): Boolean {
        return try {
            val dialog = AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(true)
                .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
                .setPositiveButton(android.R.string.ok) { d, _ ->
                    d.dismiss()
                    onConfirm.run()
                }
                .create()
            dialog.setCanceledOnTouchOutside(true)
            runCatching { dialog.window?.setType(DIALOG_WINDOW_TYPE) }
            dialog.show()
            true
        } catch (t: Throwable) {
            logger.warn("BatchUninstall: AlertDialog fallback failed: " + t.message)
            false
        }
    }

    private fun dispatchBatchUninstall(panel: ViewGroup, context: Context, packages: List<String>) {
        try {
            val intent = Intent()
            intent.setClassName(MODULE_PACKAGE, ACTIVITY_CLASS)
            intent.putStringArrayListExtra(EXTRA_PACKAGES, ArrayList(packages))
            // 鉴权凭据：用启动器身份创建 PendingIntent 随 Intent 携带。创建者由系统
            // 绑定（creatorPackage=启动器），第三方既无法伪造创建者、也无法拿到
            // 启动器创建的实例。callingPackage 在部分 ROM 上跨任务启动会返回 null，
            // referrer extra 又可伪造，PendingIntent 是可靠且不可伪造的凭据。
            val proof = PendingIntent.getActivity(
                context,
                0,
                Intent(AUTH_PROOF_ACTION).setPackage(MODULE_PACKAGE),
                PendingIntent.FLAG_IMMUTABLE
            )
            intent.putExtra(EXTRA_PROOF, proof)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            val activity = context as? Activity
            if (activity == null) {
                logger.error("BatchUninstall: context is not an activity, dispatch aborted")
                Toast.makeText(
                    context,
                    moduleString(context, STRING_DISPATCH_FAILED, FALLBACK_DISPATCH_FAILED),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            activity.startActivityForResult(intent, DISPATCH_REQUEST_CODE)
            // 分发后清空选择，避免卸载完成后编辑模式残留失效的选中项
            runCatching {
                panel.javaClass.getMethod("clearSelectedItems").invoke(panel)
            }.onFailure { logger.warn("BatchUninstall: clearSelectedItems failed: " + it.message) }
        } catch (t: Throwable) {
            logger.error("BatchUninstall: failed to dispatch batch uninstall", t)
            Toast.makeText(
                context,
                moduleString(context, STRING_DISPATCH_FAILED, FALLBACK_DISPATCH_FAILED),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 从选中视图收集可卸载候选。仅保留：
     * itemInfo.itemType == 0（应用图标）、主用户、LauncherApps 可解析且非系统应用；
     * 按包名去重，应用名取 ItemInfo.title，缺失时回退包名。
     */
    private fun collectUninstallableCandidates(panel: View, context: Context): List<UninstallCandidate> {
        val selectedViews = try {
            val method = panel.javaClass.getMethod("getSelectedViews")
            @Suppress("UNCHECKED_CAST")
            method.invoke(panel) as? List<View>
        } catch (t: Throwable) {
            logger.error("BatchUninstall: getSelectedViews failed", t)
            null
        } ?: return emptyList()
        if (selectedViews.isEmpty()) return emptyList()

        val classLoader = panel.javaClass.classLoader ?: return emptyList()
        val itemInfoClass = try {
            Class.forName("com.android.launcher3.model.data.ItemInfo", true, classLoader)
        } catch (t: Throwable) {
            logger.error("BatchUninstall: ItemInfo class not found", t)
            return emptyList()
        }
        val itemTypeField = itemInfoClass.getField("itemType")
        val userField = itemInfoClass.getField("user")
        val titleField = itemInfoClass.getField("title")
        val targetComponentMethod = itemInfoClass.getMethod("getTargetComponent")
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val myUser = Process.myUserHandle()

        val candidates = LinkedHashMap<String, UninstallCandidate>()
        for (view in selectedViews) {
            try {
                val info = view.tag ?: continue
                if (!itemInfoClass.isInstance(info)) continue
                if (itemTypeField.getInt(info) != ITEM_TYPE_APPLICATION) continue
                val user = userField.get(info) as? UserHandle ?: continue
                if (user != myUser) continue
                val component = targetComponentMethod.invoke(info) as? ComponentName ?: continue
                val packageName = component.packageName
                if (packageName.isEmpty()) continue
                if (!isUninstallable(launcherApps, component, user)) continue
                if (candidates.containsKey(packageName)) continue
                val label = (titleField.get(info) as? CharSequence)?.toString().orEmpty()
                candidates[packageName] = UninstallCandidate(label, packageName)
            } catch (t: Throwable) {
                logger.warn("BatchUninstall: failed to inspect selected view: " + t.message)
            }
        }
        logger.info("BatchUninstall: ${candidates.size} uninstallable package(s) collected")
        return candidates.values.toList()
    }

    private fun isUninstallable(launcherApps: LauncherApps?, component: ComponentName, user: UserHandle): Boolean {
        if (launcherApps == null) return true
        return try {
            val activity = launcherApps.resolveActivity(Intent().setComponent(component), user)
            val flags = activity?.applicationInfo?.flags ?: return false
            flags and ApplicationInfo.FLAG_SYSTEM == 0
        } catch (_: Throwable) {
            false
        }
    }

    // ── 模块资源（复用 RecentTaskMemoryViewHook 的 i18n 方案） ──

    private fun moduleString(hostContext: Context, resourceName: String, fallback: String): String {
        return try {
            val moduleContext = hostContext.createPackageContext(
                MODULE_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY
            )
            val resId = moduleContext.resources.getIdentifier(resourceName, "string", MODULE_PACKAGE)
            if (resId != 0) moduleContext.resources.getString(resId) else fallback
        } catch (t: Throwable) {
            logger.warn("BatchUninstall: failed to load module string $resourceName: " + t.message)
            fallback
        }
    }

    companion object {
        private const val MODULE_PACKAGE = "com.qimian233.ztool"
        private const val EDIT_MODE_PANEL_CLASS = "com.zui.launcher.uiextend.ZuiEditModePanel"
        private const val ACTIVITY_CLASS = "com.qimian233.ztool.uninstall.BatchUninstallActivity"
        private const val EXTRA_PACKAGES = "ztool_extra_batch_uninstall_packages"
        private const val EXTRA_PROOF = "ztool_extra_batch_uninstall_proof"
        private const val AUTH_PROOF_ACTION = "com.qimian233.ztool.action.BATCH_UNINSTALL_PROOF"
        private const val DISPATCH_REQUEST_CODE = 21001
        private const val BUTTON_TAG = "ztool_edit_mode_batch_uninstall"
        private const val CONSTRAINT_LAYOUT_LP =
            $$"androidx.constraintlayout.widget.ConstraintLayout$LayoutParams"
        private const val ITEM_TYPE_APPLICATION = 0

        /** 底栏三槽布局的槽距占面板宽度的比例（实测 550/3200）。 */
        private const val SLOT_OFFSET_RATIO = 0.086f

        private const val STRING_BUTTON = "ztool_batch_uninstall_button"
        private const val STRING_NO_APPS = "ztool_batch_uninstall_no_apps"
        private const val STRING_DIALOG_TITLE = "ztool_batch_uninstall_dialog_title"
        private const val STRING_DIALOG_MESSAGE = "ztool_batch_uninstall_dialog_message"
        private const val STRING_DISPATCH_FAILED = "ztool_batch_uninstall_dispatch_failed"

        /** 与 EditModeRemoveDropTarget 的确认框一致：TYPE_APPLICATION_OVERLAY。 */
        private const val DIALOG_WINDOW_TYPE = 2038

        private val FALLBACK_BUTTON: String
            get() = if (Locale.getDefault().language == "zh") "卸载应用" else "Uninstall"
        private val FALLBACK_NO_APPS: String
            get() = if (Locale.getDefault().language == "zh") "所选中内容没有可卸载的应用" else "No uninstallable apps in the selection"
        private val FALLBACK_DIALOG_TITLE: String
            get() = if (Locale.getDefault().language == "zh") "批量卸载" else "Batch Uninstall"
        private val FALLBACK_DIALOG_MESSAGE: String
            get() = if (Locale.getDefault().language == "zh") {
                $$"将通过 Root 权限静默卸载以下 %1$d 个应用，桌面图标会在卸载后自动移除：\n\n%2$s"
            } else {
                $$"The following %1$d app(s) will be silently uninstalled with Root permission. Their home screen icons will be removed automatically:\n\n%2$s"
            }
        private val FALLBACK_DISPATCH_FAILED: String
            get() = if (Locale.getDefault().language == "zh") "无法打开 ZTool 执行卸载" else "Failed to open ZTool for uninstall"
    }
}
