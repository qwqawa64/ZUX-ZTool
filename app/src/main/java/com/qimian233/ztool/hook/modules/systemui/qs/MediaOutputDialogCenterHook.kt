package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.content.Context
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.UserHandle
import android.view.Gravity
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Centers the media output dialog opened from the ZTool tile and restores its dynamic colors.
 *
 * The tile path passes a null package name, so no media session gets bound. While the receiver
 * flag is set, the active session package is injected and the album-art color chain re-enabled.
 * Legacy `MediaOutputBaseDialog.getGravity` and the current
 * `MediaOutputDialogDelegate.createDialog` gravity paths are both installed.
 */
@SuppressLint("PrivateApi")
class MediaOutputDialogCenterHook : AppHookModule() {

    // Intra-thread flag: only visible within the main-thread call stack, avoiding any cross-thread synchronization
    private val launchViaTileBroadcast = ThreadLocal.withInitial { false }

    private val receiverContext = ThreadLocal<Context?>()

    override fun getModuleName(): String = PreferenceKeys.MEDIA_OUTPUT_DIALOG_CENTER.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        val receiverOk = hookReceiver(classLoader)
        val gravityOk = hookGetGravity(classLoader) or hookCreateDialog(classLoader)
        val pkgOk = hookCreateAndShow(classLoader)

        if (receiverOk && gravityOk && pkgOk) {
            logger.info("MediaOutputDialogCenterHook installed")
        } else {
            logger.warn(
                "MediaOutputDialogCenterHook partial: receiver=$receiverOk " +
                    "gravity=$gravityOk createAndShow=$pkgOk"
            )
        }
    }

    /**
     * Maintain the flag and the receiver context around onReceive; both are consumed by the
     * createAndShow hook, which runs synchronously inside the same broadcast dispatch.
     */
    private fun hookReceiver(classLoader: ClassLoader): Boolean {
        return try {
            val receiverClass = classLoader.loadClass(RECEIVER_CLASS)
            val onReceive = findMethod(
                receiverClass, "onReceive",
                Context::class.java, android.content.Intent::class.java
            )
            hookWithId(onReceive, "media_output_dialog_receiver") { chain ->
                launchViaTileBroadcast.set(true)
                receiverContext.set(chain.args.getOrNull(0) as? Context)
                try {
                    chain.proceed()
                } finally {
                    launchViaTileBroadcast.set(false)
                    receiverContext.remove()
                }
            }
            true
        } catch (t: Throwable) {
            logger.error("Hook MediaOutputDialogReceiver.onReceive failed", t)
            false
        }
    }

    /**
     * Legacy firmware kept the gravity in MediaOutputBaseDialog.getGravity; keep this path as a
     * fallback because the class was removed on Android 17 (MediaOutputDialogDelegate instead).
     */
    private fun hookGetGravity(classLoader: ClassLoader): Boolean {
        return try {
            val dialogClass = classLoader.loadClass(BASE_DIALOG_CLASS)
            val getGravity = findMethod(dialogClass, "getGravity")
            hookWithId(getGravity, "media_output_dialog_get_gravity") { chain ->
                if (launchViaTileBroadcast.get() == true) {
                    Gravity.CENTER
                } else {
                    chain.proceed()
                }
            }
            true
        } catch (t: Throwable) {
            logger.debug("MediaOutputBaseDialog.getGravity absent: ${t.message}")
            false
        }
    }

    /**
     * Current path: SystemUIDialog.updateWindowSize applies mCustomGravity when the dialog is
     * shown. The tile launch has no transition controller, so forcing CENTER on the dialog
     * returned by the delegate covers firmware that keeps a non-centered default.
     */
    private fun hookCreateDialog(classLoader: ClassLoader): Boolean {
        return try {
            val delegateClass = classLoader.loadClass(DELEGATE_CLASS)
            val createDialog = findMethod(delegateClass, "createDialog")
            hookWithId(createDialog, "media_output_dialog_create_dialog") { chain ->
                val dialog = chain.proceed()
                if (launchViaTileBroadcast.get() == true && dialog != null) {
                    try {
                        findField(dialog.javaClass, "mCustomGravity")
                            .setInt(dialog, Gravity.CENTER)
                    } catch (t: Throwable) {
                        logger.warn("SystemUIDialog.mCustomGravity unavailable: ${t.message}")
                    }
                }
                dialog
            }
            true
        } catch (t: Throwable) {
            logger.debug("MediaOutputDialogDelegate.createDialog absent: ${t.message}")
            false
        }
    }

    /**
     * Fills the package name when the caller passed none (tile and volume long-press paths).
     * Android 17 moved the package to argument 1; the legacy six-argument signature remains
     * supported as a fallback.
     */
    private fun hookCreateAndShow(classLoader: ClassLoader): Boolean {
        val managerClass = try {
            classLoader.loadClass(MANAGER_CLASS)
        } catch (t: Throwable) {
            logger.error("$MANAGER_CLASS not found", t)
            return false
        }
        val layout = findCreateAndShow(classLoader, managerClass) ?: return false
        hookWithId(layout.method, "media_output_create_and_show") { chain ->
            val args = chain.args
            if (args.getOrNull(layout.pkgIndex) != null) {
                return@hookWithId chain.proceed()
            }
            val pkg = findActiveMediaPackage(chain.thisObject)
                ?: return@hookWithId chain.proceed()
            val updated = args.toMutableList()
            updated[layout.pkgIndex] = pkg
            // Enable album-art coloring and turn off the system-color shortcut so the bound
            // MediaController actually drives the color scheme.
            layout.metadataIndex?.let { updated[it] = true }
            layout.systemColorsIndex?.let { updated[it] = false }
            logger.debug("Inject active media package: $pkg")
            chain.proceed(updated.toTypedArray())
        }
        return true
    }

    /**
     * Resolves createAndShow for the current firmware: eight arguments with UserHandle first on
     * Android 17, six arguments with the package name first on older builds.
     */
    private fun findCreateAndShow(
        classLoader: ClassLoader,
        managerClass: Class<*>
    ): CreateAndShowLayout? {
        val controllerClass = try {
            classLoader.loadClass(CONTROLLER_CLASS)
        } catch (t: Throwable) {
            logger.error("$CONTROLLER_CLASS not found", t)
            return null
        }
        val switchingTypeClass = runCatching {
            classLoader.loadClass(SWITCHING_TYPE_CLASS)
        }.getOrNull()
        if (switchingTypeClass != null) {
            val method = runCatching {
                findMethod(
                    managerClass, "createAndShow",
                    UserHandle::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType!!,
                    controllerClass,
                    Boolean::class.javaPrimitiveType!!,
                    MediaSession.Token::class.java,
                    switchingTypeClass,
                    Boolean::class.javaPrimitiveType!!
                )
            }.getOrNull()
            if (method != null) return CreateAndShowLayout(method, 1, 4, 7)
        }
        val legacy = runCatching {
            findMethod(
                managerClass, "createAndShow",
                String::class.java,
                Boolean::class.javaPrimitiveType!!,
                controllerClass,
                Boolean::class.javaPrimitiveType!!,
                UserHandle::class.java,
                MediaSession.Token::class.java
            )
        }.getOrNull() ?: run {
            logger.error("MediaOutputDialogManager.createAndShow not found")
            return null
        }
        return CreateAndShowLayout(legacy, 0, 3, null)
    }

    /**
     * Package name of the most recently active media session, or null when unavailable.
     * Uses the hidden getActiveSessionsForUser (SystemUI holds MEDIA_CONTENT_CONTROL).
     */
    private fun findActiveMediaPackage(manager: Any?): String? {
        val context = resolveContext(manager) ?: run {
            logger.warn("Media output dialog context unavailable; keeping original arguments")
            return null
        }
        return try {
            val sm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val user = android.os.Process.myUserHandle()
            val method = MediaSessionManager::class.java.methods.firstOrNull {
                it.name == "getActiveSessionsForUser"
            } ?: run {
                logger.warn("getActiveSessionsForUser not found on this firmware")
                return null
            }
            @Suppress("UNCHECKED_CAST")
            val sessions = method.invoke(sm, null, user) as List<android.media.session.MediaController>
            sessions.firstOrNull()?.packageName
        } catch (t: Throwable) {
            logger.warn("findActiveMediaPackage failed: ${t.message}")
            null
        }
    }

    /**
     * Context for the session query: the receiver's own context on the broadcast path, then the
     * manager context (legacy layout) or the last delegate's context (Android 17 layout).
     */
    private fun resolveContext(manager: Any?): Context? {
        receiverContext.get()?.let { return it }
        if (manager == null) return null
        contextFieldOf(manager.javaClass)?.let { field ->
            (runCatching { field.get(manager) }.getOrNull() as? Context)?.let { return it }
        }
        val delegate = runCatching {
            findField(manager.javaClass, "mediaOutputDialogDelegate").get(manager)
        }.getOrNull()
        if (delegate != null) {
            contextFieldOf(delegate.javaClass)?.let { field ->
                (runCatching { field.get(delegate) }.getOrNull() as? Context)?.let { return it }
            }
        }
        return null
    }

    private fun contextFieldOf(owner: Class<*>): Field? {
        var type: Class<*>? = owner
        while (type != null) {
            type.declaredFields.firstOrNull {
                Context::class.java.isAssignableFrom(it.type)
            }?.let {
                it.isAccessible = true
                return it
            }
            type = type.superclass
        }
        return null
    }

    /** Argument layout of the resolved MediaOutputDialogManager.createAndShow. */
    private class CreateAndShowLayout(
        val method: Method,
        val pkgIndex: Int,
        val metadataIndex: Int?,
        val systemColorsIndex: Int?
    )

    private companion object {
        const val RECEIVER_CLASS =
            "com.android.systemui.media.dialog.MediaOutputDialogReceiver"
        const val BASE_DIALOG_CLASS =
            "com.android.systemui.media.dialog.MediaOutputBaseDialog"
        const val DELEGATE_CLASS =
            "com.android.systemui.media.dialog.MediaOutputDialogDelegate"
        const val MANAGER_CLASS =
            "com.android.systemui.media.dialog.MediaOutputDialogManager"
        const val SWITCHING_TYPE_CLASS =
            "com.android.systemui.media.dialog.MediaSwitchingType"
        const val CONTROLLER_CLASS =
            $$"com.android.systemui.animation.DialogTransitionAnimator$Controller"
    }
}
