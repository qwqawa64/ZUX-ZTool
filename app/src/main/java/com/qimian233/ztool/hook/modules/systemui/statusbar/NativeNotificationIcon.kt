package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.annotation.SuppressLint
import android.os.Build
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Objects

@SuppressLint("PrivateApi")
class NativeNotificationIcon : AppHookModule() {

    private val isCtsMode: ThreadLocal<Boolean?> = ThreadLocal.withInitial { null }

    override fun getModuleName(): String = PreferenceKeys.NATIVE_NOTIFICATION_ICON.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        logger.info("Loading module NativeNotificationIcon.")
        handleLoadSystemUi(classLoader)
    }

    private fun handleLoadSystemUi(classLoader: ClassLoader) {
        hookXSystemUtil(classLoader) // Hook 1
        hookNotificationShelf(classLoader) // Hook 2
        // Hook 3
        if (Build.VERSION.SDK_INT >= 36) {
            hookQSUtil(classLoader) // API 36+
        } else {
            hookNotificationListener(classLoader) // API 35-
        }
        // Hook 4
        if (Build.VERSION.SDK_INT >= 36) {
            hookNewPathClasses(classLoader) // API 36+
        } else {
            hookOldPathClasses(classLoader) // API 35-
        }
        hookNotificationHeaderView(classLoader) // Hook 5
        hookNotificationBuilder(classLoader) // Hook 6
    }

    private fun hookXSystemUtil(classLoader: ClassLoader) {
        try {
            logger.info("Hooking com.android.systemui.util.XSystemUtil...")
            val isCTSGTSTestMethod: Method = classLoader
                .loadClass("com.android.systemui.util.XSystemUtil")
                .getDeclaredMethod("isCTSGTSTest")
            hookWithId(isCTSGTSTestMethod, "is_ctsgts_test") { chain ->
                val mode = isCtsMode.get()
                if (mode != null) {
                    return@hookWithId mode
                }
                chain.proceed()
            }
            logger.info("Successfully hooked com.android.systemui.util.XSystemUtil. [1/6]")
        } catch (e: Exception) {
            logger.error("Failed to hook com.android.systemui.util.XSystemUtil.", e)
        }
    }

    private fun hookNotificationShelf(classLoader: ClassLoader) {
        try {
            logger.info("Hooking com.android.systemui.statusbar.NotificationShelf")
            val updateMethod: Method = classLoader
                .loadClass("com.android.systemui.statusbar.NotificationShelf")
                .getDeclaredMethod("updateResources\$5")
            hookWithId(updateMethod, "update") { chain ->
                isCtsMode.set(true)
                val result = chain.proceed()
                isCtsMode.remove()
                result
            }
            logger.info("Successfully hooked com.android.systemui.statusbar.NotificationShelf [2/6]")
        } catch (e: Exception) {
            logger.error("Failed to hook com.android.systemui.statusbar.NotificationShelf", e)
        }
    }

    private fun hookQSUtil(classLoader: ClassLoader) {
        try {
            logger.info("Hooking com.android.systemui.util.QSUtil")
            val qsUtilClass = classLoader.loadClass("com.android.systemui.util.QSUtil")
            var replaceMethod = findMethodByName(qsUtilClass, "replaceTheSmallIcon")
            if (replaceMethod != null) {
                hookWithId(replaceMethod, "replace_1") { null }
                logger.info("[NativeNotificationIcon] Successfully hooked com.android.systemui.util.QSUtil [3-1/6]")
                return
            }
            // Fallback: try NotificationListener
            val listenerClass = classLoader.loadClass("com.android.systemui.statusbar.NotificationListener")
            replaceMethod = findMethodByName(listenerClass, "replaceTheSmallIcon")
            if (replaceMethod != null) {
                hookWithId(replaceMethod, "replace_2") { null }
                logger.info("[NativeNotificationIcon] Successfully hooked NotificationListener.replaceTheSmallIcon [3-1/6]")
                return
            }
            logger.warn("replaceTheSmallIcon not found in QSUtil or NotificationListener, skipping hook.")
        } catch (e: ClassNotFoundException) {
            logger.error("Unable to find required class for hookQSUtil!", e)
        } catch (e: Exception) {
            logger.error("Failed to hook replaceTheSmallIcon (QSUtil path)", e)
        }
    }

    private fun hookNotificationListener(classLoader: ClassLoader) {
        try {
            val listenerClass = classLoader.loadClass("com.android.systemui.statusbar.NotificationListener")
            val replaceMethod = findMethodByName(listenerClass, "replaceTheSmallIcon")
            if (replaceMethod != null) {
                hookWithId(replaceMethod, "replace_3") { null }
                logger.info("[NativeNotificationIcon] Fallback: hooked NotificationListener.replaceTheSmallIcon [3-2/6]")
            } else {
                logger.warn("replaceTheSmallIcon not found in NotificationListener, skipping hook.")
            }
        } catch (e: Exception) {
            logger.error("Failed to hook replaceTheSmallIcon (NotificationListener path)", e)
        }
    }

    private fun hookNewPathClasses(classLoader: ClassLoader) {
        try {
            logger.info("Finding new path classes...")
            val newWrapperClass = classLoader.loadClass(
                "com.android.systemui.notificationlist.notification.wrapper.NotificationHeaderViewWrapper"
            )
            val newMIconField: Field = newWrapperClass.getDeclaredField("mIcon")
            newMIconField.isAccessible = true
            val newGetIcon: MethodHandle = MethodHandles.lookup().unreflectGetter(newMIconField)
            val newRowClass = classLoader.loadClass(
                "com.android.systemui.notificationlist.view.NotificationRowView"
            )
            logger.info("New path classes found. Hooking...")

            val onContentUpdatedMethod: Method =
                newWrapperClass.getDeclaredMethod("onContentUpdated", newRowClass)
            hookWithId(onContentUpdatedMethod, "on_content_updated_1") { chain ->
                val result = chain.proceed()
                val iconview = newGetIcon.invoke(chain.thisObject) as? ImageView
                if (iconview == null) return@hookWithId result

                val keySizeUnfucked = 1145141919
                if (Objects.equals(iconview.getTag(keySizeUnfucked), java.lang.Boolean.TRUE)) {
                    return@hookWithId result
                }

                val lp = iconview.layoutParams
                if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                    // AOSP notification_icon_circle_size: 24dp
                    val dm = iconview.context.resources.displayMetrics
                    val diameter = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, dm)
                    lp.width = Math.round(diameter)
                    lp.height = Math.round(diameter)
                    if (lp is ViewGroup.MarginLayoutParams) {
                        lp.marginStart = Math.round(
                            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, dm)
                        )
                    }
                    iconview.requestLayout()
                }
                iconview.setTag(keySizeUnfucked, java.lang.Boolean.TRUE)
                result
            }
            logger.info("Successfully hooked new path NotificationHeaderViewWrapper [4-1/6]")
        } catch (e: Exception) {
            logger.error("Failed to hook new path NotificationHeaderViewWrapper", e)
        }
    }

    private fun hookOldPathClasses(classLoader: ClassLoader) {
        try {
            logger.info("Finding old path classes...")
            val notificationHeaderViewWrapperClass = classLoader.loadClass(
                "com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper"
            )
            val notificationHeaderViewWrapperMIcon: Field =
                notificationHeaderViewWrapperClass.getDeclaredField("mIcon")
            notificationHeaderViewWrapperMIcon.isAccessible = true
            val getIcon: MethodHandle =
                MethodHandles.lookup().unreflectGetter(notificationHeaderViewWrapperMIcon)
            val expandableNotificationRowClass = classLoader.loadClass(
                "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow"
            )
            logger.info("Old path classes found. Hooking...")

            val onContentUpdatedMethod: Method =
                notificationHeaderViewWrapperClass.getDeclaredMethod(
                    "onContentUpdated", expandableNotificationRowClass
                )
            hookWithId(onContentUpdatedMethod, "on_content_updated_2") { chain ->
                val result = chain.proceed()
                val iconview = getIcon.invoke(chain.thisObject) as ImageView
                val keySizeUnfucked = 1145141919
                if (Objects.equals(iconview.getTag(keySizeUnfucked), java.lang.Boolean.TRUE)) {
                    return@hookWithId result
                }
                val lp = iconview.layoutParams
                if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                    // AOSP notification_icon_circle_size: 24dp
                    val dm = iconview.context.resources.displayMetrics
                    val diameter = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, dm)
                    lp.width = Math.round(diameter)
                    lp.height = Math.round(diameter)
                    if (lp is ViewGroup.MarginLayoutParams) {
                        lp.marginStart = Math.round(
                            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, dm)
                        )
                    }
                    iconview.requestLayout()
                }
                iconview.setTag(keySizeUnfucked, java.lang.Boolean.TRUE)
                result
            }
            logger.info("Successfully hooked NotificationHeaderViewWrapper [4-2/6]")
        } catch (e: Exception) {
            logger.error("Failed to hook NotificationHeaderViewWrapper", e)
        }
    }

    private fun hookNotificationHeaderView(classLoader: ClassLoader) {
        try {
            logger.info("Hooking NotificationHeaderView")
            val headerViewClass = classLoader.loadClass(
                "com.android.systemui.notificationlist.view.NotificationHeaderView"
            )
            // Old version (pre-inline): has shouldShowIconBackground
            val shouldShowMethod = findMethodByName(headerViewClass, "shouldShowIconBackground")
            if (shouldShowMethod != null) {
                hookWithId(shouldShowMethod, "should_show") { true }
                logger.info("Successfully hooked shouldShowIconBackground (old version) [5/6]")
                return
            }
            // New version: shouldShowIconBackground removed, logic inlined into updateIconBgVisibility.
            // Force the "show background" branch by clearing mIsChildInGroup and mShowLargeIconView
            // before the method runs, then restore them afterwards.
            val updateBgMethod = findMethodByName(headerViewClass, "updateIconBgVisibility")
            if (updateBgMethod != null) {
                val isChildField: Field = headerViewClass.getDeclaredField("mIsChildInGroup")
                isChildField.isAccessible = true
                val showLargeField: Field = headerViewClass.getDeclaredField("mShowLargeIconView")
                showLargeField.isAccessible = true
                hookWithId(updateBgMethod, "update_bg") { chain ->
                    val thiz = chain.thisObject
                    val origChild = isChildField.getBoolean(thiz)
                    val origLarge = showLargeField.getBoolean(thiz)
                    isChildField.setBoolean(thiz, false)
                    showLargeField.setBoolean(thiz, false)
                    try {
                        chain.proceed()
                    } finally {
                        isChildField.setBoolean(thiz, origChild)
                        showLargeField.setBoolean(thiz, origLarge)
                    }
                }
                logger.info("Successfully hooked updateIconBgVisibility (new version) [5/6]")
                return
            }
            logger.warn("Neither shouldShowIconBackground nor updateIconBgVisibility found, skipping hook.")
        } catch (e: Exception) {
            logger.error("Failed to hook NotificationHeaderView", e)
        }
    }

    private fun hookNotificationBuilder(classLoader: ClassLoader) {
        try {
            logger.info("Hooking android.app.Notification\$Builder")
            // always use circle template for android.app.Notification$Builder#get*Resource()
            val isCtsMethod: Method = classLoader
                .loadClass("android.app.Notification\$Builder")
                .getDeclaredMethod("isCtsGtsTest")
            hookWithId(isCtsMethod, "is_cts") { true }
            logger.info("Successfully hooked android.app.Notification\$Builder [6/6]")
        } catch (_: Exception) {
            try {
                logger.warn("Unable to hook method isCtsGtsTest! Try alternate way.")
                val methodToReplace: Method = classLoader.loadClass("com.android.systemui.util.XSystemUtil")
                    .getDeclaredMethod("isCTSGTSTest")
                hookWithId(methodToReplace, "method_to_replace") { true }
                logger.info("Successfully hooked android.app.Notification\$Builder with alternate way.[6/6]")
            } catch (e: Exception) {
                logger.error("Unable to hook isCTSGTSTest!", e)
            }
        }
    }

    /**
     * Find a declared method by name in a class, ignoring parameter types.
     * Returns the first match or null if not found.
     */
    private fun findMethodByName(clazz: Class<*>, methodName: String): Method? {
        for (m in clazz.declaredMethods) {
            if (m.name == methodName) {
                return m
            }
        }
        return null
    }
}
