package com.qimian233.ztool.hook.modules.launcher.misc

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.res.Resources
import android.os.Process
import android.os.UserHandle
import android.view.View
import android.view.ViewGroup
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
@SuppressLint("PrivateApi", "DiscouragedApi")
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
                    merged
                } catch (t: Throwable) {
                    logger.error("BatchUninstall: failed to attach button to edit mode anim views", t)
                    original
                }
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

        val removeId = resources.getIdentifier("drop_remove_icon", "id", packageName)
        val removeTarget = if (removeId != 0) panel.findViewById<View>(removeId) else null
        if (removeTarget == null) {
            logger.warn("BatchUninstall: drop_remove_icon not found, button skipped")
            return
        }

        val button = TextView(context)
        button.tag = BUTTON_TAG
        button.id = View.generateViewId()
        button.text = moduleString(context, STRING_BUTTON, FALLBACK_BUTTON)
        button.isAllCaps = false
        (removeTarget as? TextView)?.let { styleSource ->
            runCatching {
                button.setTextSize(
                    android.util.TypedValue.COMPLEX_UNIT_PX,
                    styleSource.textSize
                )
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

        val layoutParams = cloneLayoutParams(removeTarget.layoutParams)
        if (layoutParams == null) {
            logger.warn("BatchUninstall: cannot clone layout params, button skipped")
            return
        }
        adjustLayoutParams(layoutParams, removeTarget, button.id, resources, packageName)
        button.visibility = View.INVISIBLE

        (removeTarget.parent as? ViewGroup)?.addView(button, layoutParams)
            ?: run { panel.addView(button, layoutParams) }
        button.setOnClickListener { handleClicked(panel, button) }
        logger.info("BatchUninstall: edit mode button injected")
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
     * 调整克隆出的布局参数：垂直锚点与"移除"按钮一致，水平上插在
     * "合并文件夹"容器与"移除"按钮之间（ConstraintLayout），其他布局
     * 类型退化为 LEFT_OF 规则；不识别的布局直接保持克隆值。
     */
    private fun adjustLayoutParams(
        layoutParams: ViewGroup.LayoutParams,
        removeTarget: View,
        buttonId: Int,
        resources: Resources,
        packageName: String
    ) {
        if (layoutParams.javaClass.name != CONSTRAINT_LAYOUT_LP) return
        try {
            val lpClass = layoutParams.javaClass
            val unset = lpClass.getField("UNSET").getInt(null)
            val source = removeTarget.layoutParams
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

            val combineId = resources.getIdentifier("drop_combine_folder_container", "id", packageName)
            val combine = if (combineId != 0) removeTarget.rootView.findViewById<View>(combineId) else null
            if (combine != null && combine !== removeTarget) {
                lpClass.getField("startToEnd").setInt(layoutParams, combine.id)
                lpClass.getField("endToStart").setInt(layoutParams, removeTarget.id)
                val combineLp = combine.layoutParams
                if (combineLp.javaClass == lpClass &&
                    combineLp.javaClass.getField("endToStart").getInt(combineLp) == removeTarget.id
                ) {
                    combineLp.javaClass.getField("endToStart").setInt(combineLp, buttonId)
                    combine.layoutParams = combineLp
                }
            } else {
                lpClass.getField("endToStart").setInt(layoutParams, removeTarget.id)
            }
            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        } catch (t: Throwable) {
            logger.warn("BatchUninstall: failed to adjust constraint params: " + t.message)
        }
    }

    // ── 点击分发 ────────────────────────────────────────────────

    private fun handleClicked(panel: ViewGroup, button: View) {
        try {
            val context = panel.context
            val packages = collectUninstallablePackages(panel, context)
            if (packages.isEmpty()) {
                Toast.makeText(
                    context,
                    moduleString(context, STRING_NO_APPS, FALLBACK_NO_APPS),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            val intent = Intent()
            intent.setClassName(MODULE_PACKAGE, ACTIVITY_CLASS)
            intent.putStringArrayListExtra(EXTRA_PACKAGES, ArrayList(packages))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            // 分发后清空选择，避免卸载完成后编辑模式残留失效的选中项
            button.post {
                runCatching {
                    panel.javaClass.getMethod("clearSelectedItems").invoke(panel)
                }.onFailure { logger.warn("BatchUninstall: clearSelectedItems failed: " + it.message) }
            }
        } catch (t: Throwable) {
            logger.error("BatchUninstall: failed to dispatch batch uninstall", t)
        }
    }

    /**
     * 从选中视图收集可卸载的包名。仅保留：
     * itemInfo.itemType == 0（应用图标）、主用户、LauncherApps 可解析且非系统应用。
     */
    private fun collectUninstallablePackages(panel: View, context: Context): List<String> {
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
        val targetComponentMethod = itemInfoClass.getMethod("getTargetComponent")
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val myUser = Process.myUserHandle()

        val packages = LinkedHashSet<String>()
        for (view in selectedViews) {
            try {
                val info = view.tag ?: continue
                if (!itemInfoClass.isInstance(info)) continue
                if (itemTypeField.getInt(info) != ITEM_TYPE_APPLICATION) continue
                val user = userField.get(info) as? UserHandle ?: continue
                if (user != myUser) continue
                val component = targetComponentMethod.invoke(info) as? ComponentName ?: continue
                val packageName = component.packageName
                if (packageName.isNullOrEmpty()) continue
                if (!isUninstallable(launcherApps, component, user)) continue
                packages.add(packageName)
            } catch (t: Throwable) {
                logger.warn("BatchUninstall: failed to inspect selected view: " + t.message)
            }
        }
        logger.info("BatchUninstall: ${packages.size} uninstallable package(s) collected")
        return packages.toList()
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
        private const val ACTIVITY_CLASS = "com.qimian233.ztool.settingactivity.launcher.BatchUninstallActivity"
        private const val EXTRA_PACKAGES = "ztool_extra_batch_uninstall_packages"
        private const val BUTTON_TAG = "ztool_edit_mode_batch_uninstall"
        private const val CONSTRAINT_LAYOUT_LP = "androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams"
        private const val ITEM_TYPE_APPLICATION = 0

        private const val STRING_BUTTON = "ztool_batch_uninstall_button"
        private const val STRING_NO_APPS = "ztool_batch_uninstall_no_apps"

        private val FALLBACK_BUTTON: String
            get() = if (Locale.getDefault().language == "zh") "卸载应用" else "Uninstall"
        private val FALLBACK_NO_APPS: String
            get() = if (Locale.getDefault().language == "zh") "所选中内容没有可卸载的应用" else "No uninstallable apps in the selection"
    }
}
