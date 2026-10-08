package com.qimian233.ztool.hook.modules.launcher.grid

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Big-folder blur guard (com.zui.launcher), independent of the grid features.
 *
 * FolderIcon.A() dereferences DragController.getDragObject().dragView whenever the icon
 * drag flag is set, but the controller holds no drag object while the workspace builds the
 * long-press drag preview, so long-pressing a big folder with dynamic blur enabled crashes
 * the launcher. Disable only if the host is fixed.
 */
@SuppressLint("PrivateApi")
class LauncherBigFolderBlurGuardHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.LAUNCHER_BIG_FOLDER_BLUR_GUARD.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    @Volatile
    private var reported = false

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val refs = try {
            val folderIcon = classLoader.loadClass("com.android.launcher3.folder.FolderIcon")
            val launcher = classLoader.loadClass("com.android.launcher3.Launcher")
            Refs(
                blurData = findMethod(folderIcon, "A"),
                dragFlag = findField(folderIcon, "S"),
                activityContext = findField(folderIcon, "b"),
                launcherGetDragController = findMethod(launcher, "getDragController"),
                dragControllerGetDragObject = findMethod(
                    classLoader.loadClass("com.android.launcher3.dragndrop.DragController"),
                    "getDragObject"
                )
            )
        } catch (t: Throwable) {
            logger.error("BigFolderBlurGuard: resolve failed, not installed", t)
            return
        }

        hookWithId(refs.blurData, "big_folder_blur_guard") { chain ->
            val icon = chain.thisObject
            var flipped = false
            try {
                if (refs.dragFlag.getBoolean(icon)) {
                    val context = refs.activityContext.get(icon)
                    val dragController = context?.let { refs.launcherGetDragController.invoke(it) }
                    val dragObject = dragController?.let { refs.dragControllerGetDragObject.invoke(it) }
                    if (dragObject == null) {
                        // No drag is in flight, so the idle branch is the correct one; the host
                        // would otherwise read through the null drag object.
                        refs.dragFlag.setBoolean(icon, false)
                        flipped = true
                    }
                }
            } catch (t: Throwable) {
                logger.debug("BigFolderBlurGuard: state check failed: $t")
            }
            try {
                chain.proceed()
            } catch (t: Throwable) {
                // This is a cosmetic blur-rect update; a null dereference must not kill the UI.
                if (!isNullPointer(t)) throw t
                if (!reported) {
                    reported = true
                    logger.warn("BigFolderBlurGuard: null drag object suppressed: $t")
                }
            } finally {
                if (flipped) {
                    try {
                        refs.dragFlag.setBoolean(icon, true)
                    } catch (_: Throwable) {
                    }
                }
            }
            null
        }
        logger.debug("BigFolderBlurGuard installed")
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

    /** Reflection handles of the guarded call. */
    private class Refs(
        val blurData: Method,
        val dragFlag: Field,
        val activityContext: Field,
        val launcherGetDragController: Method,
        val dragControllerGetDragObject: Method
    )
}
