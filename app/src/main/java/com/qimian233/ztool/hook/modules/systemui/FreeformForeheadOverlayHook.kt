package com.qimian233.ztool.hook.modules.systemui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.Region
import android.os.Binder
import android.view.SurfaceControl
import android.view.WindowInsets
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Drops the forced freeform caption inset so the app fills the window top, then
 * limits the caption touch region to the handle pill. ZUI desktop mode only.
 */
@SuppressLint("PrivateApi", "NewApi")
class FreeformForeheadOverlayHook : AppHookModule() {
    @Volatile
    private var insetRemovedLogged = false

    @Volatile
    private var regionAppliedLogged = false

    override fun getModuleName(): String = "hook_test"

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
        val decorClass = loadClass(loader, DECOR_CLASS) ?: return
        val paramsClass = loadClass(loader, PARAMS_CLASS) ?: return
        val resultClass = loadClass(loader, RESULT_CLASS) ?: return
        val insetsClass = loadClass(loader, INSETS_CLASS) ?: return
        val pcModeClass = loadClass(loader, PC_MODE_CLASS) ?: return
        val wctClass = loadClass(loader, WCT_CLASS) ?: return

        val taskInfoField = field(decorClass, "mTaskInfo") ?: return
        val insetsField = field(decorClass, "mWindowDecorationInsets") ?: return
        val ownerField = field(decorClass, "mOwner") ?: return
        val captionVisibleField = field(decorClass, "mIsCaptionVisible")
        val excludedField = field(insetsClass, "mExcludedFromAppBounds")
        val captionWidthField = field(resultClass, "mCaptionWidth")
        val captionHeightField = field(resultClass, "mCaptionHeight")
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
        val displayResources = method(decorClass, "ovGetDisplayResources")

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

        val updateCaptionInsets = try {
            findMethod(
                decorClass,
                "updateCaptionInsets",
                paramsClass,
                wctClass,
                resultClass,
                Rect::class.java
            )
        } catch (t: Throwable) {
            logger.error("WindowDecoration.updateCaptionInsets not found", t)
            return
        }
        val updateViewHierarchy = try {
            findMethod(
                decorClass,
                "updateViewHierarchy",
                paramsClass,
                resultClass,
                SurfaceControl.Transaction::class.java,
                Region::class.java
            )
        } catch (t: Throwable) {
            logger.error("WindowDecoration.updateViewHierarchy not found", t)
            return
        }
        val addCaptionInset = try {
            findMethod(decorClass, "addCaptionInset", wctClass)
        } catch (t: Throwable) {
            logger.warn("WindowDecoration.addCaptionInset not found: ${t.message}")
            null
        }

        val captionBarType = WindowInsets.Type.captionBar()
        val gesturesType = WindowInsets.Type.mandatorySystemGestures()

        fun pcModeNow(): Boolean =
            runCatching { isPcMode.invoke(null) as? Boolean == true }.getOrDefault(false)

        fun targetTask(decor: Any): ActivityManager.RunningTaskInfo? {
            val task = taskInfoField.get(decor) as? ActivityManager.RunningTaskInfo ?: return null
            val windowingMode = runCatching { getWindowingMode.invoke(task) as? Int }
                .getOrNull() ?: return null
            return if (windowingMode == WINDOWING_MODE_FREEFORM) task else null
        }

        // WindowDecorationInsets.remove() skips captionBar outside PC/OV-default mode,
        // so drop both sources directly.
        fun dropInset(decor: Any, wct: Any) {
            val task = targetTask(decor) ?: return
            val token = taskTokenField?.get(task) ?: return
            val owner = ownerField.get(decor) as? Binder ?: return
            val existing = insetsField.get(decor)
            removeInsetsSource.invoke(wct, token, owner, 0, captionBarType)
            removeInsetsSource.invoke(wct, token, owner, 0, gesturesType)
            if (existing != null && excludedField?.getBoolean(existing) == true) {
                setAppBounds?.invoke(wct, token, Rect())
            }
            insetsField.set(decor, null)
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

        hookWithId(updateCaptionInsets, "freeform_forehead_overlay") { chain ->
            try {
                val decor = chain.thisObject
                val wct = chain.args.getOrNull(1)
                if (decor == null || wct == null || !wctClass.isInstance(wct) ||
                    targetTask(decor) == null
                ) {
                    return@hookWithId chain.proceed()
                }
                dropInset(decor, wct)
                null
            } catch (t: Throwable) {
                logger.error("Freeform caption inset drop failed", t)
                chain.proceed()
            }
        }

        addCaptionInset?.let { addMethod ->
            hookWithId(addMethod, "freeform_forehead_skip_add") { chain ->
                try {
                    val decor = chain.thisObject
                    val wct = chain.args.getOrNull(0)
                    if (decor == null || wct == null || !wctClass.isInstance(wct) ||
                        targetTask(decor) == null
                    ) {
                        return@hookWithId chain.proceed()
                    }
                    dropInset(decor, wct)
                    null
                } catch (t: Throwable) {
                    logger.error("Freeform caption inset re-add block failed", t)
                    chain.proceed()
                }
            }
        }

        hookWithId(updateViewHierarchy, "freeform_forehead_touch_region") { chain ->
            try {
                val decor = chain.thisObject
                val existingRegion = chain.args.getOrNull(3) as? Region
                if (decor == null || existingRegion != null || targetTask(decor) == null ||
                    captionVisibleField?.getBoolean(decor) == false || pcModeNow()
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

        logger.info("Freeform forehead overlay installed (freeform, PC mode included)")
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

    private companion object {
        const val DECOR_CLASS = "com.android.wm.shell.windowdecor.WindowDecoration"
        const val PARAMS_CLASS =
            "com.android.wm.shell.windowdecor.WindowDecoration\$RelayoutParams"
        const val RESULT_CLASS =
            "com.android.wm.shell.windowdecor.WindowDecoration\$RelayoutResult"
        const val INSETS_CLASS =
            "com.android.wm.shell.windowdecor.WindowDecoration\$WindowDecorationInsets"
        const val PC_MODE_CLASS = "com.android.wm.shell.ov.utils.OvcPcModeUtils"
        const val WCT_CLASS = "android.window.WindowContainerTransaction"
        const val WINDOWING_MODE_FREEFORM = 5
        const val CAPTION_WIDTH_RES = "ov_window_decor_caption_bar_width_pad"
        const val CAPTION_WIDTH_DP = 50f
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
    }
}
