package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.content.Context
import android.media.session.MediaSessionManager
import android.os.UserHandle
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Media output dialog centering + theme color fix: fixes two problems when
 * MediaOutputDialog is launched from the "media output" tile - the window sticks
 * to the left screen edge and the theme color is always the default yellow.
 *
 * Root cause: the tile path launches the dialog with a null package name, so
 * getGravity() keeps its hardcoded LEFT gravity and the dynamic-color chain never
 * starts (no MediaController binding means no album art), falling back to the
 * default legacy color scheme.
 *
 * Fix (three hooks, controlled by a single module switch):
 * - onReceive: set an intra-thread flag during LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG
 *   (used by the gravity hook);
 * - getGravity: return CENTER when the flag is set;
 * - createAndShow: when pkg == null, query the current active media session and
 *   inject the package name; if no session is found, keep the original arguments
 *   (empty-state dialog).
 */
@SuppressLint("PrivateApi")
class MediaOutputDialogCenterHook : AppHookModule() {

    // Intra-thread flag: only visible within the main-thread call stack, avoiding any cross-thread synchronization
    private val launchViaTileBroadcast = ThreadLocal.withInitial { false }

    override fun getModuleName(): String = PreferenceKeys.MEDIA_OUTPUT_DIALOG_CENTER.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        val receiverOk = hookReceiver(classLoader)
        val gravityOk = hookGetGravity(classLoader)
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
     * Maintain the flag around onReceive. onReceive is the standard BroadcastReceiver
     * override with a stable signature across versions; MediaOutputDialogReceiver is not obfuscated.
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
                try {
                    chain.proceed()
                } finally {
                    launchViaTileBroadcast.set(false)
                }
            }
            true
        } catch (t: Throwable) {
            logger.error("Hook MediaOutputDialogReceiver.onReceive failed", t)
            false
        }
    }

    /**
     * Override gravity. getGravity() is a virtual method of SystemUIDialog that is
     * final-overridden by MediaOutputBaseDialog; look it up on the subclass by explicit signature.
     */
    private fun hookGetGravity(classLoader: ClassLoader): Boolean {
        return try {
            val dialogClass = classLoader.loadClass(BASE_DIALOG_CLASS)
            val getGravity = findMethod(dialogClass, "getGravity")
            hookWithId(getGravity, "media_output_dialog_gravity") { chain ->
                val isLaunchViaTile: Boolean =
                    launchViaTileBroadcast.get() ?: return@hookWithId chain.proceed()
                if (isLaunchViaTile) {
                    android.view.Gravity.CENTER
                } else {
                    chain.proceed()
                }
            }
            true
        } catch (t: Throwable) {
            logger.error("Hook MediaOutputBaseDialog.getGravity failed", t)
            false
        }
    }

    /**
     * Tile path upgrade: when createAndShow(pkg, ...)'s pkg is null, query the active
     * media session to fill in the package name, and enable includePlaybackAndAppMetadata
     * (args[3]) to use the full metadata path.
     * The normal path (chip click carries its own package name) and the empty state
     * (no active session) keep the original arguments.
     */
    private fun hookCreateAndShow(classLoader: ClassLoader): Boolean {
        return try {
            val managerClass = classLoader.loadClass(MANAGER_CLASS)
            val createAndShow = findMethod(
                managerClass, "createAndShow",
                String::class.java,                    // packageName
                Boolean::class.javaPrimitiveType!!,    // aboveStatusBar
                classLoader.loadClass(CONTROLLER_CLASS), // DialogTransitionAnimator.Controller
                Boolean::class.javaPrimitiveType!!,    // includePlaybackAndAppMetadata
                UserHandle::class.java,                // userHandle
                android.media.session.MediaSession.Token::class.java // token
            )
            hookWithId(createAndShow, "media_output_create_and_show") { chain ->
                val args = chain.args
                if (args[0] != null) {
                    return@hookWithId chain.proceed()
                }
                val pkg =
                    findActiveMediaPackage(chain.thisObject) ?: return@hookWithId chain.proceed()
                logger.debug("Inject active media package: $pkg")
                chain.proceed(
                    arrayOf(
                        pkg,
                        args[1],
                        args[2],
                        true,   // includePlaybackAndAppMetadata: enable album-art coloring and playback metadata
                        args[4],
                        args[5]
                    )
                )
            }
            true
        } catch (t: Throwable) {
            logger.error("Hook MediaOutputDialogManager.createAndShow failed", t)
            false
        }
    }

    /**
     * Query the package name of the most recently active media session.
     * getActiveSessionsForUser is a hidden API (not in the public SDK layer; SystemUI
     * itself uses it for full queries, available within the privileged process), so it
     * is invoked via reflection.
     * No filtering by playback state: consistent with the normal path semantics - the
     * media card is shown and can open the dialog even when paused; simply take the
     * first session from the system list (list is sorted with media-button sessions first).
     *
     * @param manager MediaOutputDialogManager instance (its context is used to get the service)
     */
    private fun findActiveMediaPackage(manager: Any?): String? {
        if (manager == null) return null
        return try {
            val contextField = findField(manager.javaClass, "context")
            val context = contextField.get(manager) as Context
            val sm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val current = android.os.Process.myUserHandle()
            // hidden API: getActiveSessionsForUser(ComponentName, UserHandle)
            val method = MediaSessionManager::class.java.methods.firstOrNull {
                it.name == "getActiveSessionsForUser"
            } ?: run {
                logger.warn("getActiveSessionsForUser not found on this firmware")
                return null
            }
            @Suppress("UNCHECKED_CAST")
            val sessions = method.invoke(sm, null, current) as List<android.media.session.MediaController>
            sessions.firstOrNull()?.packageName
        } catch (t: Throwable) {
            logger.warn("findActiveMediaPackage failed: ${t.message}")
            null
        }
    }

    private companion object {
        const val RECEIVER_CLASS =
            "com.android.systemui.media.dialog.MediaOutputDialogReceiver"
        const val BASE_DIALOG_CLASS =
            "com.android.systemui.media.dialog.MediaOutputBaseDialog"
        const val MANAGER_CLASS =
            "com.android.systemui.media.dialog.MediaOutputDialogManager"
        const val CONTROLLER_CLASS =
            $$"com.android.systemui.animation.DialogTransitionAnimator$Controller"
    }
}
