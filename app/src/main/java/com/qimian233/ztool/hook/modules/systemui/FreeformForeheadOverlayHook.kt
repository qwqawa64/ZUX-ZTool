package com.qimian233.ztool.hook.modules.systemui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.Region
import android.os.Binder
import android.view.SurfaceControl
import android.view.View
import android.view.WindowInsets
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Drops the forced freeform caption inset so the app fills the window top, and limits the
 * caption touch region to the handle pill. PC mode keeps its full caption touch region.
 *
 * Android 17 moved both code paths from WindowDecoration into CaptionController; the legacy
 * WindowDecoration-based path stays installed as a fallback.
 */
@SuppressLint("PrivateApi", "NewApi")
class FreeformForeheadOverlayHook : AppHookModule() {
    @Volatile
    private var insetRemovedLogged = false

    @Volatile
    private var regionAppliedLogged = false

    override fun getModuleName(): String =
        PreferenceKeys.SYSTEM_UI_FREEFORM_NO_FOREHEAD.name

    override fun getTargetPackages(): Array<out String> =
        arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        try {
            install(param)
        } catch (t: Throwable) {
            logger.error("Freeform forehead overlay install failed", t)
        }
    }

    private fun install(param: PackageLoadedParam) {
        val loader = param.defaultClassLoader
        val pcModeClass = loadClass(loader, PC_MODE_CLASS) ?: return
        val wctClass = loadClass(loader, WCT_CLASS) ?: return

        val taskTokenField = fieldByWalk(ActivityManager.RunningTaskInfo::class.java, "token")
        val getWindowingMode = methodByWalk(
            ActivityManager.RunningTaskInfo::class.java,
            "getWindowingMode"
        ) ?: run {
            logger.error("RunningTaskInfo.getWindowingMode not found")
            return
        }
        val isZuiDesktopMode = staticMethod(pcModeClass, "isInZuiDesktopModeStatus") ?: return
        val isPcMode = staticMethod(pcModeClass, "isUnderPcMode") ?: return

        val removeInsetsSource = wctClass.methods.firstOrNull {
            it.name == "removeInsetsSource" &&
                it.parameterTypes.size == 4 &&
                it.parameterTypes[1].isAssignableFrom(Binder::class.java) &&
                it.parameterTypes[2] == Int::class.javaPrimitiveType &&
                it.parameterTypes[3] == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true } ?: run {
            logger.error("WindowContainerTransaction.removeInsetsSource not found")
            return
        }
        val setAppBounds = wctClass.methods.firstOrNull {
            it.name == "setAppBounds" &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[1] == Rect::class.java
        }?.apply { isAccessible = true } ?: run {
            logger.warn("WindowContainerTransaction.setAppBounds not found")
            null
        }

        val ops = InsetOps(
            wctClass = wctClass,
            captionBarType = WindowInsets.Type.captionBar(),
            gesturesType = WindowInsets.Type.mandatorySystemGestures(),
            removeInsetsSource = removeInsetsSource,
            setAppBounds = setAppBounds,
            taskTokenField = taskTokenField,
            getWindowingMode = getWindowingMode,
            isPcMode = isPcMode,
            isZuiDesktopMode = isZuiDesktopMode
        )

        if (installCaptionController(loader, ops)) return
        installLegacyDecoration(loader, ops)
    }

    /** Android 17 path: CaptionController owns the caption insets and the caption touch region. */
    private fun installCaptionController(loader: ClassLoader, ops: InsetOps): Boolean {
        val controllerClass = runCatching {
            loader.loadClass(CAPTION_CONTROLLER_CLASS)
        }.getOrNull() ?: return false
        val relayoutParamsClass = runCatching {
            loader.loadClass(RELAYOUT_PARAMS_CLASS)
        }.getOrNull() ?: return false
        val viewHostClass = runCatching {
            loader.loadClass(VIEW_HOST_CLASS)
        }.getOrNull() ?: return false
        val taskInfoField = field(controllerClass, "taskInfo") ?: return false
        val ownerField = field(controllerClass, "insetsOwner") ?: return false
        val insetsField = field(controllerClass, "captionInsets") ?: return false
        val captionVisibleField = field(controllerClass, "isCaptionVisible")
        val updateCaptionInsets = runCatching {
            findMethod(
                controllerClass,
                "updateCaptionInsets",
                relayoutParamsClass,
                List::class.java,
                Context::class.java,
                ops.wctClass,
                Int::class.javaPrimitiveType,
                Rect::class.java,
                Rect::class.java
            )
        }.getOrNull() ?: run {
            logger.warn("CaptionController.updateCaptionInsets not found")
            return false
        }
        val updateViewHierarchy = runCatching {
            findMethod(
                controllerClass,
                "updateViewHierarchy",
                relayoutParamsClass,
                viewHostClass,
                View::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                SurfaceControl.Transaction::class.java,
                Region::class.java
            )
        }.getOrNull() ?: run {
            logger.warn("CaptionController.updateViewHierarchy not found")
            return false
        }
        val appBoundsField = runCatching {
            findField(loader.loadClass(INSETS_CLASS), "appBoundsExclusion")
                .apply { isAccessible = true }
        }.getOrNull()

        hookWithId(updateCaptionInsets, "freeform_forehead_overlay") { chain ->
            try {
                val controller = chain.thisObject ?: return@hookWithId chain.proceed()
                val wct = chain.args.getOrNull(3)
                if (wct == null || !ops.wctClass.isInstance(wct)) {
                    return@hookWithId chain.proceed()
                }
                val task = ops.targetTask(taskInfoField.get(controller))
                    ?: return@hookWithId chain.proceed()
                val owner = ownerField.get(controller) as? Binder
                    ?: return@hookWithId chain.proceed()
                val existing = insetsField.get(controller)
                val excluded = existing != null &&
                    runCatching { appBoundsField?.get(existing) != null }.getOrDefault(false)
                ops.dropInset(wct, task, owner, controller, insetsField, excluded)
                null
            } catch (t: Throwable) {
                logger.error("Freeform caption inset drop failed", t)
                chain.proceed()
            }
        }

        val addCaptionInset = runCatching {
            findMethod(controllerClass, "addCaptionInset", ops.wctClass)
        }.getOrNull()
        addCaptionInset?.let { addMethod ->
            hookWithId(addMethod, "freeform_forehead_skip_add") { chain ->
                try {
                    val controller = chain.thisObject ?: return@hookWithId chain.proceed()
                    val wct = chain.args.getOrNull(0)
                    if (wct == null || !ops.wctClass.isInstance(wct)) {
                        return@hookWithId chain.proceed()
                    }
                    val task = ops.targetTask(taskInfoField.get(controller))
                        ?: return@hookWithId chain.proceed()
                    val owner = ownerField.get(controller) as? Binder
                        ?: return@hookWithId chain.proceed()
                    val existing = insetsField.get(controller)
                    val excluded = existing != null &&
                        runCatching { appBoundsField?.get(existing) != null }.getOrDefault(false)
                    ops.dropInset(wct, task, owner, controller, insetsField, excluded)
                    null
                } catch (t: Throwable) {
                    logger.error("Freeform caption inset re-add block failed", t)
                    chain.proceed()
                }
            }
        }

        hookWithId(updateViewHierarchy, "freeform_forehead_touch_region") { chain ->
            try {
                val controller = chain.thisObject ?: return@hookWithId chain.proceed()
                val existingRegion = chain.args.getOrNull(6) as? Region
                val captionVisible = captionVisibleField?.getBoolean(controller) != false
                if (existingRegion != null || !captionVisible || ops.pcModeNow() ||
                    ops.targetTask(taskInfoField.get(controller)) == null
                ) {
                    return@hookWithId chain.proceed()
                }
                val width = (chain.args.getOrNull(3) as? Int) ?: 0
                val height = (chain.args.getOrNull(4) as? Int) ?: 0
                if (width <= 0 || height <= 0) return@hookWithId chain.proceed()

                val resources = (chain.args.getOrNull(2) as? View)?.context?.resources
                val pillWidth = captionPillWidth(resources)
                val left = (width - pillWidth) / 2
                val args = chain.args.toMutableList()
                args[6] = Region(left, 0, left + pillWidth, height)
                if (!regionAppliedLogged) {
                    regionAppliedLogged = true
                    logger.info(
                        "Freeform caption touch region: caption=${width}x$height, " +
                            "pill=${pillWidth}px"
                    )
                }
                chain.proceed(args.toTypedArray())
            } catch (t: Throwable) {
                logger.error("Freeform caption touch region failed", t)
                chain.proceed()
            }
        }

        logger.info("Freeform forehead overlay installed (CaptionController path)")
        return true
    }

    /** Legacy path: WindowDecoration owned the caption insets and touch region directly. */
    private fun installLegacyDecoration(loader: ClassLoader, ops: InsetOps) {
        val decorClass = loadClass(loader, LEGACY_DECOR_CLASS) ?: return
        val paramsClass = loadClass(loader, LEGACY_PARAMS_CLASS) ?: return
        val resultClass = loadClass(loader, LEGACY_RESULT_CLASS) ?: return
        val insetsClass = INSETS_CLASSES.firstNotNullOfOrNull {
            runCatching { loader.loadClass(it) }.getOrNull()
        } ?: run {
            logger.error("WindowDecorationInsets not found")
            return
        }

        val taskInfoField = field(decorClass, "mTaskInfo") ?: return
        val insetsField = field(decorClass, "mWindowDecorationInsets") ?: return
        val ownerField = field(decorClass, "mOwner") ?: return
        val captionVisibleField = field(decorClass, "mIsCaptionVisible")
        val excludedField = field(insetsClass, "mExcludedFromAppBounds")
        val captionWidthField = field(resultClass, "mCaptionWidth")
        val captionHeightField = field(resultClass, "mCaptionHeight")
        val displayResources = method(decorClass, "ovGetDisplayResources")

        val updateCaptionInsets = runCatching {
            findMethod(
                decorClass,
                "updateCaptionInsets",
                paramsClass,
                ops.wctClass,
                resultClass,
                Rect::class.java
            )
        }.getOrNull() ?: run {
            logger.debug("WindowDecoration.updateCaptionInsets absent (CaptionController build)")
            return
        }
        val updateViewHierarchy = runCatching {
            findMethod(
                decorClass,
                "updateViewHierarchy",
                paramsClass,
                resultClass,
                SurfaceControl.Transaction::class.java,
                Region::class.java
            )
        }.getOrNull() ?: run {
            logger.debug("WindowDecoration.updateViewHierarchy absent (CaptionController build)")
            return
        }
        val addCaptionInset = runCatching {
            findMethod(decorClass, "addCaptionInset", ops.wctClass)
        }.getOrNull()

        hookWithId(updateCaptionInsets, "freeform_forehead_overlay") { chain ->
            try {
                val decor = chain.thisObject ?: return@hookWithId chain.proceed()
                val wct = chain.args.getOrNull(1)
                if (wct == null || !ops.wctClass.isInstance(wct)) {
                    return@hookWithId chain.proceed()
                }
                val task = ops.targetTask(taskInfoField.get(decor))
                    ?: return@hookWithId chain.proceed()
                val owner = ownerField.get(decor) as? Binder
                    ?: return@hookWithId chain.proceed()
                val existing = insetsField.get(decor)
                val excluded = existing != null && excludedField != null &&
                    runCatching { excludedField.getBoolean(existing) }.getOrDefault(false)
                ops.dropInset(wct, task, owner, decor, insetsField, excluded)
                null
            } catch (t: Throwable) {
                logger.error("Freeform caption inset drop failed", t)
                chain.proceed()
            }
        }

        addCaptionInset?.let { addMethod ->
            hookWithId(addMethod, "freeform_forehead_skip_add") { chain ->
                try {
                    val decor = chain.thisObject ?: return@hookWithId chain.proceed()
                    val wct = chain.args.getOrNull(0)
                    if (wct == null || !ops.wctClass.isInstance(wct)) {
                        return@hookWithId chain.proceed()
                    }
                    val task = ops.targetTask(taskInfoField.get(decor))
                        ?: return@hookWithId chain.proceed()
                    val owner = ownerField.get(decor) as? Binder
                        ?: return@hookWithId chain.proceed()
                    val existing = insetsField.get(decor)
                    val excluded = existing != null && excludedField != null &&
                        runCatching { excludedField.getBoolean(existing) }.getOrDefault(false)
                    ops.dropInset(wct, task, owner, decor, insetsField, excluded)
                    null
                } catch (t: Throwable) {
                    logger.error("Freeform caption inset re-add block failed", t)
                    chain.proceed()
                }
            }
        }

        hookWithId(updateViewHierarchy, "freeform_forehead_touch_region") { chain ->
            try {
                val decor = chain.thisObject ?: return@hookWithId chain.proceed()
                val existingRegion = chain.args.getOrNull(3) as? Region
                val captionVisible = captionVisibleField?.getBoolean(decor) != false
                if (existingRegion != null || !captionVisible || ops.pcModeNow() ||
                    ops.targetTask(taskInfoField.get(decor)) == null
                ) {
                    return@hookWithId chain.proceed()
                }
                val relayoutResult = chain.args.getOrNull(1) ?: return@hookWithId chain.proceed()
                val width = captionWidthField?.getInt(relayoutResult) ?: 0
                val height = captionHeightField?.getInt(relayoutResult) ?: 0
                if (width <= 0 || height <= 0) return@hookWithId chain.proceed()

                val resources = runCatching { displayResources?.invoke(decor) as? Resources }
                    .getOrNull()
                val pillWidth = captionPillWidth(resources)
                val left = (width - pillWidth) / 2
                val args = chain.args.toMutableList()
                args[3] = Region(left, 0, left + pillWidth, height)
                if (!regionAppliedLogged) {
                    regionAppliedLogged = true
                    logger.info(
                        "Freeform caption touch region: caption=${width}x$height, " +
                            "pill=${pillWidth}px"
                    )
                }
                chain.proceed(args.toTypedArray())
            } catch (t: Throwable) {
                logger.error("Freeform caption touch region failed", t)
                chain.proceed()
            }
        }

        logger.info("Freeform forehead overlay installed (legacy WindowDecoration path)")
    }

    @Suppress("DEPRECATION")
    private fun captionPillWidth(resources: Resources?): Int {
        if (resources != null) {
            val id = resources.getIdentifier(CAPTION_WIDTH_RES, "dimen", SYSTEMUI_PACKAGE)
            if (id != 0) return resources.getDimensionPixelSize(id)
        }
        val density = resources?.displayMetrics?.density
            ?: Resources.getSystem().displayMetrics.density
        return (CAPTION_WIDTH_DP * density).toInt()
    }

    private fun loadClass(loader: ClassLoader, name: String): Class<*>? =
        try {
            loader.loadClass(name)
        } catch (t: Throwable) {
            logger.error("$name not found", t)
            null
        }

    private fun field(owner: Class<*>, name: String): Field? =
        try {
            findField(owner, name).apply { isAccessible = true }
        } catch (t: Throwable) {
            logger.error("Field $name not found on ${owner.name}", t)
            null
        }

    private fun fieldByWalk(owner: Class<*>, name: String): Field? {
        var type: Class<*>? = owner
        while (type != null) {
            try {
                return type.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            } catch (t: Throwable) {
                logger.error("Field $name lookup failed on ${owner.name}", t)
                return null
            }
        }
        logger.error("Field $name not found on ${owner.name}")
        return null
    }

    private fun methodByWalk(owner: Class<*>, name: String, vararg types: Class<*>): Method? {
        var type: Class<*>? = owner
        while (type != null) {
            try {
                return type.getDeclaredMethod(name, *types).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                type = type.superclass
            } catch (t: Throwable) {
                logger.error("Method $name lookup failed on ${owner.name}", t)
                return null
            }
        }
        logger.error("Method $name not found on ${owner.name}")
        return null
    }

    private fun method(owner: Class<*>, name: String): Method? =
        try {
            findMethod(owner, name).apply { isAccessible = true }
        } catch (t: Throwable) {
            logger.warn("Method $name not found on ${owner.name}: ${t.message}")
            null
        }

    private fun staticMethod(owner: Class<*>, name: String): Method? =
        try {
            owner.getDeclaredMethod(name).apply { isAccessible = true }
        } catch (t: Throwable) {
            logger.error("Static method $name not found on ${owner.name}", t)
            null
        }

    /** Handles shared by both install paths, resolved once per package load. */
    private inner class InsetOps(
        val wctClass: Class<*>,
        private val captionBarType: Int,
        private val gesturesType: Int,
        private val removeInsetsSource: Method,
        private val setAppBounds: Method?,
        private val taskTokenField: Field?,
        private val getWindowingMode: Method,
        private val isPcMode: Method,
        private val isZuiDesktopMode: Method
    ) {
        fun pcModeNow(): Boolean =
            runCatching { isPcMode.invoke(null) as? Boolean == true }.getOrDefault(false)

        fun targetTask(taskInfo: Any?): ActivityManager.RunningTaskInfo? {
            val task = taskInfo as? ActivityManager.RunningTaskInfo ?: return null
            val windowingMode = runCatching { getWindowingMode.invoke(task) as? Int }
                .getOrNull() ?: return null
            return if (windowingMode == WINDOWING_MODE_FREEFORM) task else null
        }

        // WindowDecorationInsets.remove() skips captionBar outside PC/OV-default mode, so drop
        // both inset sources directly and clear the cached instance.
        fun dropInset(
            wct: Any,
            task: ActivityManager.RunningTaskInfo,
            owner: Binder,
            insetsOwner: Any,
            insetsField: Field,
            excludedFromAppBounds: Boolean
        ) {
            val token = taskTokenField?.get(task) ?: return
            removeInsetsSource.invoke(wct, token, owner, 0, captionBarType)
            removeInsetsSource.invoke(wct, token, owner, 0, gesturesType)
            if (excludedFromAppBounds) {
                setAppBounds?.invoke(wct, token, Rect())
            }
            insetsField.set(insetsOwner, null)
            if (!insetRemovedLogged) {
                insetRemovedLogged = true
                val desktopMode = runCatching {
                    isZuiDesktopMode.invoke(null) as? Boolean == true
                }.getOrDefault(false)
                logger.info(
                    "Freeform caption inset dropped: task=${task.taskId}, " +
                        "pcMode=${pcModeNow()}, zuiDesktopMode=$desktopMode"
                )
            }
        }
    }

    private companion object {
        const val CAPTION_CONTROLLER_CLASS =
            "com.android.wm.shell.windowdecor.caption.CaptionController"
        const val RELAYOUT_PARAMS_CLASS =
            $$"com.android.wm.shell.windowdecor.WindowDecoration2$RelayoutParams"
        const val VIEW_HOST_CLASS =
            "com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost"
        const val INSETS_CLASS = "com.android.wm.shell.windowdecor.WindowDecorationInsets"
        const val LEGACY_DECOR_CLASS = "com.android.wm.shell.windowdecor.WindowDecoration"
        const val LEGACY_PARAMS_CLASS =
            $$"com.android.wm.shell.windowdecor.WindowDecoration$RelayoutParams"
        const val LEGACY_RESULT_CLASS =
            $$"com.android.wm.shell.windowdecor.WindowDecoration$RelayoutResult"
        const val PC_MODE_CLASS = "com.android.wm.shell.ov.utils.OvcPcModeUtils"
        const val WCT_CLASS = "android.window.WindowContainerTransaction"
        const val WINDOWING_MODE_FREEFORM = 5
        const val CAPTION_WIDTH_RES = "ov_window_decor_caption_bar_width_pad"
        const val CAPTION_WIDTH_DP = 50f
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
        val INSETS_CLASSES = listOf(
            INSETS_CLASS,
            $$"com.android.wm.shell.windowdecor.WindowDecoration$WindowDecorationInsets"
        )
    }
}
