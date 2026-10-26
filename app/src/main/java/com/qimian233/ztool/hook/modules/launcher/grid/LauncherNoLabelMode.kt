package com.qimian233.ztool.hook.modules.launcher.grid

import android.annotation.SuppressLint
import android.os.Build
import android.util.FloatProperty
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Desktop no-label mode for ZUI Launcher.
 *
 * Hides BubbleTextView and ActiveIconView labels visually while keeping the
 * text intact for screen readers. Drawer callers and shortcut popups keep
 * their labels. See docs/research/launcher_no_label_mode.md.
 */
@SuppressLint("PrivateApi")
class LauncherNoLabelMode : AppHookModule() {

    override fun getModuleName(): String = "launcher_no_label_mode"

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        installBubbleTextViewVisibilityHook(param)
        installActiveIconViewVisibilityHook(param)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            installFolderNameHook(param)
            installFolderAnimationItemHook(param)
            installFloatingTextAlphaHook(param)
        }
    }

    /**
     * Whether the call comes from a path whose labels must stay visible
     * (shortcut popups, deep shortcuts, app drawer).
     */
    private fun isFromIgnoredPath(): Boolean {
        return Thread.currentThread().stackTrace.any { frame ->
            frame.className == "com.android.launcher3.popup.PopupContainerWithArrow" &&
                    frame.methodName == "initializeSystemShortcut" ||
                    frame.className == "com.android.launcher3.shortcuts.DeepShortcutTextView" ||
                    frame.className.startsWith("com.android.launcher3.allapps.") ||
                    frame.className.startsWith("com.android.launcher3.appprediction.")
        }
    }

    /** Forces BubbleTextView label visibility and alpha to stay hidden. */
    fun installBubbleTextViewVisibilityHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val bubbleTextViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.BubbleTextView")

            val setTextVisibilityMethod: Method = findMethod(
                bubbleTextViewClass, "setTextVisibility",
                Boolean::class.javaPrimitiveType
            )
            hookWithId(setTextVisibilityMethod, "set_text_visibility_1") {  chain ->
                if (isFromIgnoredPath()) {
                    chain.proceed()
                } else {
                    val args = arrayOf<Any?>(java.lang.Boolean.valueOf(false))
                    chain.proceed(args)
                }
            }

            val setTextAlphaMethod: Method = findMethod(
                bubbleTextViewClass, "setTextAlpha",
                Float::class.javaPrimitiveType
            )
            hookWithId(setTextAlphaMethod, "set_text_alpha_1") {  chain ->
                if (isFromIgnoredPath()) {
                    chain.proceed()
                } else {
                    val args = arrayOf<Any?>(java.lang.Float.valueOf(0.0f))
                    chain.proceed(args)
                }
            }

            logger.info("BubbleTextView visibility-block hook installed successfully!")
        } catch (e: Throwable) {
            logger.error("Exception caught in BubbleTextView visibility hook: ", e)
        }
    }

    /**
     * Forces ActiveIconView label visibility, alpha and the alpha-ignore flag
     * to stay hidden.
     */
    fun installActiveIconViewVisibilityHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val activeIconViewClass: Class<*> =
                loader.loadClass("com.zui.launcher.ActiveIconView")

            val setTextVisibilityMethod: Method = findMethod(
                activeIconViewClass, "setTextVisibility",
                Boolean::class.javaPrimitiveType
            )
            hookWithId(setTextVisibilityMethod, "set_text_visibility_2") {  chain ->
                if (isFromIgnoredPath()) {
                    chain.proceed()
                } else {
                    val args = arrayOf<Any?>(java.lang.Boolean.valueOf(false))
                    chain.proceed(args)
                }
            }

            val setTextAlphaMethod: Method = findMethod(
                activeIconViewClass, "setTextAlpha",
                Float::class.javaPrimitiveType
            )
            hookWithId(setTextAlphaMethod, "set_text_alpha_2") {  chain ->
                if (isFromIgnoredPath()) {
                    chain.proceed()
                } else {
                    val args = arrayOf<Any?>(java.lang.Float.valueOf(0.0f))
                    chain.proceed(args)
                }
            }

            val setIgnoreMethod: Method = findMethod(
                activeIconViewClass, "setIgnoreSetAlphaVisible",
                Boolean::class.javaPrimitiveType
            )
            hookWithId(setIgnoreMethod, "set_ignore") {  chain ->
                if (isFromIgnoredPath()) {
                    chain.proceed()
                } else {
                    val args = arrayOf<Any?>(java.lang.Boolean.valueOf(false))
                    chain.proceed(args)
                }
            }

            logger.info("ActiveIconView visibility-block hook installed successfully!")
        } catch (e: Throwable) {
            logger.error("Exception caught in ActiveIconView visibility hook: ", e)
        }
    }

    /**
     * Android 17+ only. Forces folder icon names to stay hidden by keeping the
     * name view invisible and its text alpha at zero.
     */
    fun installFolderNameHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val folderIconClass: Class<*> =
                loader.loadClass("com.android.launcher3.folder.FolderIcon")
            val bubbleTextViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.BubbleTextView")
            val setTextVisibleMethod: Method = findMethod(
                folderIconClass, "setTextVisible", Boolean::class.javaPrimitiveType
            )
            val getFolderNameMethod: Method = findMethod(folderIconClass, "getFolderName")
            val setTextVisibilityMethod: Method = findMethod(
                bubbleTextViewClass, "setTextVisibility", Boolean::class.javaPrimitiveType
            )

            hookWithId(setTextVisibleMethod, "folder_text_visible") { chain ->
                if (isFromIgnoredPath()) {
                    chain.proceed()
                } else {
                    chain.proceed(arrayOf<Any?>(java.lang.Boolean.valueOf(false)))
                    getFolderNameMethod.invoke(chain.thisObject)?.let { folderName ->
                        setTextVisibilityMethod.invoke(
                            folderName, java.lang.Boolean.valueOf(false)
                        )
                    }
                    null
                }
            }

            logger.info("Folder name hook installed successfully!")
        } catch (e: Throwable) {
            logger.error("Exception caught in folder name hook: ", e)
        }
    }

    /**
     * Android 17+ only. Hides labels on the throw-away item views created for
     * folder open/close animations.
     */
    fun installFolderAnimationItemHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val animationManagerClass: Class<*> =
                loader.loadClass("com.android.launcher3.folder.FolderAnimationManager")
            val bubbleTextViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.BubbleTextView")
            val activeIconViewClass: Class<*> =
                loader.loadClass("com.zui.launcher.ActiveIconView")
            val appPairIconClass: Class<*> =
                loader.loadClass("com.android.launcher3.apppairs.AppPairIcon")

            val createAnimItemMethod: Method = findMethod(
                animationManagerClass, "D",
                loader.loadClass("com.android.launcher3.model.data.ItemInfo")
            )
            val bubbleSetVisibility: Method = findMethod(
                bubbleTextViewClass, "setTextVisibility", Boolean::class.javaPrimitiveType
            )
            val activeSetVisibility: Method = findMethod(
                activeIconViewClass, "setTextVisibility", Boolean::class.javaPrimitiveType
            )
            val appPairTitleMethod: Method = findMethod(appPairIconClass, "getTitleTextView")
            val hidden = java.lang.Boolean.valueOf(false)

            hookWithId(createAnimItemMethod, "folder_anim_item_label") { chain ->
                val result = chain.proceed()
                when {
                    bubbleTextViewClass.isInstance(result) ->
                        bubbleSetVisibility.invoke(result, hidden)
                    activeIconViewClass.isInstance(result) ->
                        activeSetVisibility.invoke(result, hidden)
                    appPairIconClass.isInstance(result) ->
                        appPairTitleMethod.invoke(result)?.let {
                            bubbleSetVisibility.invoke(it, hidden)
                        }
                }
                result
            }

            logger.info("Folder animation item label hook installed successfully!")
        } catch (e: Throwable) {
            logger.error("Exception caught in folder animation item label hook: ", e)
        }
    }

    /**
     * Android 17+ only. Redirects the floating-view text-alpha channel to a
     * detached property, so folder animations cannot re-show a hidden label.
     */
    fun installFloatingTextAlphaHook(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val loader: ClassLoader = param.defaultClassLoader
            val bubbleTextViewClass: Class<*> =
                loader.loadClass("com.android.launcher3.BubbleTextView")
            val detachedAlphaSource = DetachedFloatingTextAlpha(loader)

            val getFloatingMethod: Method =
                findMethod(bubbleTextViewClass, "getFloatingViewTextAlpha")
            hookWithId(getFloatingMethod, "floating_text_alpha") { chain ->
                if (isFromIgnoredPath()) {
                    chain.proceed()
                } else {
                    detachedAlphaSource.create()
                }
            }

            logger.info("Floating text alpha channel hook installed successfully!")
        } catch (e: Throwable) {
            logger.error("Exception caught in floating text alpha hook: ", e)
        }
    }

    /**
     * Builds detached text-alpha MultiProperty instances with the same channel
     * layout as BubbleTextView: AnimatedFloat target, two channels, product.
     */
    private class DetachedFloatingTextAlpha(loader: ClassLoader) {

        private val animatedFloatConstructor: Constructor<*> =
            loader.loadClass("com.android.launcher3.anim.AnimatedFloat")
                .getConstructor(Runnable::class.java, Float::class.javaPrimitiveType)
        private val valueProperty: Any = loader.loadClass("com.android.launcher3.anim.AnimatedFloat")
            .getField("VALUE").get(null)!!
        private val multiPropertyFactoryConstructor: Constructor<*> =
            loader.loadClass("com.android.launcher3.util.MultiPropertyFactory")
                .getConstructor(
                    Any::class.java,
                    FloatProperty::class.java,
                    Int::class.javaPrimitiveType,
                    loader.loadClass("com.android.launcher3.util.MultiPropertyFactory\$FloatBiFunction"),
                    Float::class.javaPrimitiveType
                )
        private val getChannelMethod: Method =
            loader.loadClass("com.android.launcher3.util.MultiPropertyFactory")
                .getMethod("get", Int::class.javaPrimitiveType)
        private val productBiFunction: Any =
            Proxy.newProxyInstance(
                loader,
                arrayOf(loader.loadClass("com.android.launcher3.util.MultiPropertyFactory\$FloatBiFunction"))
            ) { proxy, method, args ->
                when (method.name) {
                    "apply" -> (args!![0] as Float) * (args[1] as Float)
                    "equals" -> proxy === args?.get(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "ZToolDetachedFloatingTextAlpha"
                    else -> null
                }
            }

        fun create(): Any {
            val target = animatedFloatConstructor.newInstance(Runnable {}, 1.0f)
            val factory = multiPropertyFactoryConstructor.newInstance(
                target, valueProperty, 2, productBiFunction, 1.0f
            )
            return getChannelMethod.invoke(factory, 1)!!
        }
    }

}

