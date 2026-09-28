package com.qimian233.ztool.hook.modules.systemframework

import android.graphics.Rect
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Freeform edge bubble: system_server side of the "shrink a freeform window into an
 * edge bubble" replication (see docs/research/oplus-float-handle-gesture-spec.md).
 *
 * Responsibilities:
 * 1. Publish a raw-Parcel Binder service "ztool.freeform_bubble" (the only
 *    app→system_server channel; ZTool app talks to it through reflection/HiddenApiBypass).
 * 2. Execute task minimize/restore inside the WMS global lock via ATMS reflection.
 * 3. Pass ZTool's uid through [com.android.server.wm.OvFreeformService.checkPermission]
 *    so the app can use IOvFreeformService helpers without privileged permissions.
 * 4. Watch freeform task bounds ([android.view.WindowContainer.setBounds]) to detect a
 *    window dragged to the screen edge and notify the app overlay via callback binder.
 *
 * The feature switch is [PreferenceKeys.FREEFORM_EDGE_BUBBLE] (read by
 * [com.qimian233.ztool.hook.base.BaseHookModule.isEnabled]). Toggling it requires a
 * system restart (scope HowToRestart.Reboot) because the binder service is registered
 * once at boot.
 */
@Suppress("PrivateApi", "SdkLintPrivateApi")
class FreeformEdgeBubbleHook : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.FREEFORM_EDGE_BUBBLE.name

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    companion object {
        private const val BRIDGE_DESCRIPTOR = "com.qimian233.ztool.hook.IFreeformBubbleBridge"
        private const val BRIDGE_SERVICE_NAME = "ztool.freeform_bubble"

        // Transaction codes (app→server), mirrored in app-side FreeformBubbleBridge.
        private const val CODE_MINIMIZE_TASK = 1
        private const val CODE_RESTORE_TASK = 2
        private const val CODE_LIST_MINIMIZED = 3
        private const val CODE_GET_PACKAGE = 4
        private const val CODE_REGISTER_CALLBACK = 5
        // Transaction codes (server→app callback).
        private const val CB_BUBBLE_ADDED = 11
        private const val CB_BUBBLE_REMOVED = 12

        private const val WINDOWING_MODE_FREEFORM = 5
        private const val MATCH_TASK_MODE_ANY = 0
        /** Fully-offscreen placement leaves this much extra gap beyond the window edge. */
        private const val OFFSCREEN_GAP_PX = 32
        /** Fraction of task width allowed to hang offscreen before the bubble triggers. */
        private const val EDGE_TRIGGER_FRACTION = 0.5f
        private const val PUBLISH_MAX_ATTEMPTS = 120
        private const val PUBLISH_RETRY_INTERVAL_MS = 500L

        @Volatile private var ztoolUid = -1

        /** taskId → restore bounds, server-side session state (lost on reboot is fine). */
        private val restoreRects = ConcurrentHashMap<Int, Rect>()
        /** taskId → side (0 left / 1 right) of currently minimized tasks. */
        private val minimizedSides = ConcurrentHashMap<Int, Int>()
        /** taskId → package name, cached for getPackageForTask and events. */
        private val taskPackages = ConcurrentHashMap<Int, String>()
        /** Guards against re-entering the setBounds hook from our own moves. */
        private val internalMove = ThreadLocal.withInitial { false }

        @Volatile private var eventCallback: IBinder? = null
    }

    // region server-start wiring

    @Throws(Throwable::class)
    override fun handleSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val classLoader = param.classLoader

        installCheckPermissionPassthrough(classLoader)
        installEdgeDetectionHook(classLoader)
        // Publish immediately: task-handle resolution inside the bridge is lazy per
        // call, so there is no need to wait for a boot phase (and ZUX renames
        // SystemServiceManager.startBootPhase, which broke the deferred approach).
        // Retry on a background thread: at the very start of startBootstrapServices the
        // binder host may not accept addService yet, and the true cause is wrapped in
        // InvocationTargetException. Late publication is harmless — callers resolve the
        // service lazily.
        Thread({
            var lastError: Throwable? = null
            for (attempt in 1..PUBLISH_MAX_ATTEMPTS) {
                try {
                    if (publishBridgeService(classLoader)) return@Thread
                } catch (t: Throwable) {
                    lastError = (t as? java.lang.reflect.InvocationTargetException)?.cause ?: t
                }
                try {
                    Thread.sleep(PUBLISH_RETRY_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
            logger.error(
                "publish bridge service failed after $PUBLISH_MAX_ATTEMPTS attempts", lastError)
        }, "ztool-bubble-publish").start()
    }

    /**
     * @return true when the service is registered (or already present from a previous
     * generation during hot reload).
     */
    private fun publishBridgeService(classLoader: ClassLoader): Boolean {
        val smClass = classLoader.loadClass("android.os.ServiceManager")
        val existing = try {
            val getService = smClass.getDeclaredMethod("getService", String::class.java)
            getService.invoke(null, BRIDGE_SERVICE_NAME)
        } catch (_: Throwable) {
            null
        }
        if (existing != null) {
            logger.debug("bridge service already present, skip publish")
            return true
        }
        val addService = smClass.getDeclaredMethod(
            "addService", String::class.java, IBinder::class.java)
        addService.invoke(null, BRIDGE_SERVICE_NAME, BridgeBinder(classLoader))
        logger.info("bridge service published: $BRIDGE_SERVICE_NAME")
        return true
    }

    // endregion

    // region task operations (called from BridgeBinder, system uid, WMS lock held)

    private fun resolveAtms(classLoader: ClassLoader): Any {
        val atmsClass = classLoader.loadClass("com.android.server.wm.ActivityTaskManagerService")
        return try {
            val getInstance = atmsClass.getDeclaredMethod("getInstance")
            getInstance.isAccessible = true
            getInstance.invoke(null)
        } catch (_: Throwable) {
            // Fallback: LocalServices registered ActivityTaskManagerInternal impl
            // carrying an mService field back to ATMS.
            val localServicesClass = classLoader.loadClass("com.android.server.LocalServices")
            val internalClass = classLoader.loadClass("com.android.server.wm.ActivityTaskManagerInternal")
            val getService = localServicesClass.getDeclaredMethod("getService", Class::class.java)
            val internal = getService.invoke(null, internalClass)
                ?: error("ActivityTaskManagerInternal not in LocalServices")
            val serviceField = internal.javaClass.getDeclaredField("mService")
            serviceField.isAccessible = true
            serviceField.get(internal)
        }
    }

    private class AtmsHandles(
        val atms: Any,
        val globalLock: Any,
        val anyTaskForId: Method,
        val taskClass: Class<*>,
        val getBounds: Method,
        val setBounds: Method,
        val moveToFront: Method?,
        val setLastNonFullscreenBounds: Method?,
        val getBaseIntent: Method?,
        val getWindowingMode: Method?
    )

    private fun resolveAtmsHandles(classLoader: ClassLoader): AtmsHandles {
        val atms = resolveAtms(classLoader)
        val atmsClass = atms.javaClass
        val globalLock = findField(atmsClass, "mGlobalLock").get(atms)
            ?: error("ATMS.mGlobalLock is null")
        val rwc = findField(atmsClass, "mRootWindowContainer").get(atms)
            ?: error("ATMS.mRootWindowContainer is null")
        val rwcClass = classLoader.loadClass("com.android.server.wm.RootWindowContainer")
        val anyTaskForId = rwcClass.getMethod(
            "anyTaskForId", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val taskClass = classLoader.loadClass("com.android.server.wm.Task")
        return AtmsHandles(
            atms, globalLock, anyTaskForId, taskClass,
            taskClass.getMethod("getBounds"),
            taskClass.getMethod("setBounds", Rect::class.java),
            try { taskClass.getMethod("moveToFront", String::class.java) } catch (_: Throwable) { null },
            try { taskClass.getMethod("setLastNonFullscreenBounds", Rect::class.java) } catch (_: Throwable) { null },
            try { taskClass.getMethod("getBaseIntent") } catch (_: Throwable) { null },
            try { taskClass.getMethod("getWindowingMode") } catch (_: Throwable) { null }
        )
    }

    private fun findTask(handles: AtmsHandles, taskId: Int): Any? =
        handles.anyTaskForId.invoke(handles.atms, taskId, MATCH_TASK_MODE_ANY)

    private fun doMinimize(handles: AtmsHandles, taskId: Int, side: Int): Boolean {
        val task = findTask(handles, taskId) ?: run {
            logger.warn("minimize: task $taskId not found")
            return false
        }
        val bounds = handles.getBounds.invoke(task) as Rect
        if (bounds.isEmpty) return false
        restoreRects[taskId] = Rect(bounds)
        minimizedSides[taskId] = side
        try {
            handles.setLastNonFullscreenBounds?.invoke(task, bounds)
        } catch (t: Throwable) {
            logger.debug("setLastNonFullscreenBounds failed: ${t.message}")
        }
        val offscreen = Rect(bounds)
        offscreen.offset(if (side == 0) -(bounds.width() + OFFSCREEN_GAP_PX) else bounds.width() + OFFSCREEN_GAP_PX, 0)
        internalMove.set(true)
        try {
            handles.setBounds.invoke(task, offscreen)
        } finally {
            internalMove.set(false)
        }
        logger.info("minimize task=$taskId side=$side bounds=$bounds -> $offscreen")
        return true
    }

    private fun doRestore(handles: AtmsHandles, taskId: Int): Boolean {
        val task = findTask(handles, taskId) ?: run {
            logger.warn("restore: task $taskId not found")
            restoreRects.remove(taskId)
            minimizedSides.remove(taskId)
            return false
        }
        val target = restoreRects.remove(taskId) ?: run {
            logger.warn("restore: no stored bounds for task $taskId")
            return false
        }
        minimizedSides.remove(taskId)
        internalMove.set(true)
        try {
            handles.setBounds.invoke(task, target)
            try {
                handles.moveToFront?.invoke(task, "ztool_freeform_bubble")
            } catch (t: Throwable) {
                logger.debug("moveToFront failed: ${t.message}")
            }
        } finally {
            internalMove.set(false)
        }
        // internalMove suppressed the setBounds hook, so the removal event must be
        // sent here explicitly.
        notifyBubbleRemoved(taskId)
        logger.info("restore task=$taskId -> $target")
        return true
    }

    private fun resolveZtoolUid(handles: AtmsHandles): Int {
        ztoolUid.let { if (it != -1) return it }
        synchronized(this) {
            if (ztoolUid != -1) return ztoolUid
            val uid = try {
                val contextField = findField(handles.atms.javaClass, "mContext")
                val context = contextField.get(handles.atms) as android.content.Context
                context.packageManager.getPackageUid("com.qimian233.ztool", 0)
            } catch (t: Throwable) {
                logger.warn("resolve ztool uid failed: ${t.message}")
                -2
            }
            ztoolUid = uid
            return uid
        }
    }

    // endregion

    // region hooks

    /**
     * Lets ZTool call IOvFreeformService helpers (setLastNonFullscreenBounds, bounds
     * queries) without the signature|privileged zui.permission.OVFREEFORM_* grants.
     * Only the calling uid of the ZTool app bypasses; everyone else proceeds unchanged.
     */
    @Throws(Throwable::class)
    private fun installCheckPermissionPassthrough(classLoader: ClassLoader) {
        val serviceClass = try {
            classLoader.loadClass("com.android.server.wm.OvFreeformService")
        } catch (_: Throwable) {
            logger.debug("OvFreeformService not present on this build, skip passthrough")
            return
        }
        val checkPermission: Method = try {
            findMethod(serviceClass, "checkPermission", String::class.java)
        } catch (_: Throwable) {
            logger.debug("OvFreeformService.checkPermission not found, skip passthrough")
            return
        }
        hookWithId(checkPermission, "freeform_edge_bubble_ovf_perm") { chain ->
            val callingUid = Binder.getCallingUid()
            val handles = try { resolveAtmsHandles(chain.thisObject.javaClass.classLoader) } catch (_: Throwable) { null }
            val zuid = handles?.let { resolveZtoolUid(it) } ?: -2
            if (callingUid == zuid) {
                logger.debug("checkPermission passthrough for ztool uid=$callingUid")
                return@hookWithId null
            }
            chain.proceed()
        }
    }

    /**
     * Detects freeform tasks dragged to the screen edge. When a freeform task's new
     * bounds hang offscreen by more than [EDGE_TRIGGER_FRACTION] of its width, the app
     * overlay is told to show a bubble; when a tracked task comes back mostly onscreen,
     * the bubble is dismissed.
     */
    @Throws(Throwable::class)
    private fun installEdgeDetectionHook(classLoader: ClassLoader) {
        val windowContainerClass = classLoader.loadClass("com.android.server.wm.WindowContainer")
        val setBounds: Method = findMethod(windowContainerClass, "setBounds", Rect::class.java)
        val taskClass = classLoader.loadClass("com.android.server.wm.Task")
        hookWithId(setBounds, "freeform_edge_bubble_detect") { chain ->
            val task = chain.thisObject
            if (internalMove.get() || !taskClass.isInstance(task)) {
                return@hookWithId chain.proceed()
            }
            val newBounds = chain.getArg(0) as Rect
            val windowingMode = try {
                taskClass.getMethod("getWindowingMode").invoke(task) as Int
            } catch (_: Throwable) { -1 }
            if (windowingMode != WINDOWING_MODE_FREEFORM || newBounds.isEmpty) {
                return@hookWithId chain.proceed()
            }
            val displayBounds = try {
                val dc = taskClass.getMethod("getDisplayContent").invoke(task)
                dc?.javaClass?.getMethod("getBounds")?.invoke(dc) as? Rect
            } catch (_: Throwable) { null }
            val width = newBounds.width().coerceAtLeast(1)
            val offLeft = displayBounds?.left?.let { (it - newBounds.left).coerceAtLeast(0) } ?: 0
            val offRight = displayBounds?.right?.let { (newBounds.right - it).coerceAtLeast(0) } ?: 0
            val wasTracked = minimizedSides.containsKey(task.taskIdCompat())
            val side = when {
                offLeft >= width * EDGE_TRIGGER_FRACTION -> 0
                offRight >= width * EDGE_TRIGGER_FRACTION -> 1
                else -> -1
            }
            chain.proceed()
            if (side >= 0 && !wasTracked) {
                // First time reaching the edge: record the onscreen bounds the user had,
                // then fully dock the task offscreen.
                restoreRects[task.taskIdCompat()] = Rect(newBounds)
                minimizedSides[task.taskIdCompat()] = side
                notifyBubbleAdded(task, taskClass, side)
            } else if (side < 0 && wasTracked) {
                minimizedSides.remove(task.taskIdCompat())
                restoreRects.remove(task.taskIdCompat())
                notifyBubbleRemoved(task.taskIdCompat())
            }
        }
    }

    private fun Any.taskIdCompat(): Int {
        return try {
            this.javaClass.getMethod("getTaskId").invoke(this) as Int
        } catch (_: Throwable) {
            val f = this.javaClass.getField("mTaskId")
            f.getInt(this)
        }
    }

    private fun notifyBubbleAdded(task: Any, taskClass: Class<*>, side: Int) {
        val taskId = task.taskIdCompat()
        val pkg = try {
            val intent = taskClass.getMethod("getBaseIntent").invoke(task) as? android.content.Intent
            intent?.component?.packageName ?: intent?.`package`
        } catch (_: Throwable) { null }
        if (pkg != null) taskPackages[taskId] = pkg
        minimizedSides[taskId] = side
        sendEvent(CB_BUBBLE_ADDED) { data ->
            data.writeInt(taskId)
            data.writeInt(side)
            data.writeString(pkg)
        }
        logger.info("bubble add task=$taskId side=$side pkg=$pkg")
    }

    private fun notifyBubbleRemoved(taskId: Int) {
        sendEvent(CB_BUBBLE_REMOVED) { data ->
            data.writeInt(taskId)
        }
        logger.info("bubble remove task=$taskId")
    }

    private fun sendEvent(code: Int, payload: (Parcel) -> Unit) {
        val callback = eventCallback ?: return
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(BRIDGE_DESCRIPTOR)
            payload(data)
            try {
                callback.transact(code, data, reply, Binder.FLAG_ONEWAY)
            } catch (t: Throwable) {
                logger.warn("sendEvent code=$code failed: ${t.message}")
            }
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    // endregion

    // region binder bridge

    /**
     * Raw-Parcel binder published as "ztool.freeform_bubble". All task mutations run
     * under the WMS global lock; reflection handles are re-resolved per call so hot
     * reload and ATMS replacement stay safe.
     */
    private inner class BridgeBinder(private val classLoader: ClassLoader) : Binder() {

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != INTERFACE_TRANSACTION && !isAllowedCaller()) {
                logger.warn("bridge call from untrusted uid=" + Binder.getCallingUid())
                return false
            }
            when (code) {
                CODE_MINIMIZE_TASK -> {
                    val taskId = data.readInt()
                    val side = data.readInt()
                    val ok = withTaskHandles { doMinimize(it, taskId, side) }
                    reply?.writeNoException()
                    reply?.writeInt(if (ok) 1 else 0)
                    return true
                }
                CODE_RESTORE_TASK -> {
                    val taskId = data.readInt()
                    val ok = withTaskHandles { doRestore(it, taskId) }
                    reply?.writeNoException()
                    reply?.writeInt(if (ok) 1 else 0)
                    return true
                }
                CODE_LIST_MINIMIZED -> {
                    reply?.writeNoException()
                    val flat = ArrayList<Int>(minimizedSides.size * 2)
                    for ((taskId, side) in minimizedSides) {
                        flat.add(taskId)
                        flat.add(side)
                    }
                    reply?.writeInt(flat.size)
                    for (v in flat) reply?.writeInt(v)
                    return true
                }
                CODE_GET_PACKAGE -> {
                    val taskId = data.readInt()
                    reply?.writeNoException()
                    reply?.writeString(taskPackages[taskId])
                    return true
                }
                CODE_REGISTER_CALLBACK -> {
                    val callback = data.readStrongBinder()
                    eventCallback = callback
                    try {
                        val deathRecipient = IBinder.DeathRecipient {
                            logger.info("app callback died, clearing event callback")
                            eventCallback = null
                        }
                        callback.linkToDeath(deathRecipient, 0)
                    } catch (t: Throwable) {
                        logger.warn("linkToDeath failed: ${t.message}")
                    }
                    reply?.writeNoException()
                    logger.info("app event callback registered")
                    return true
                }
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(BRIDGE_DESCRIPTOR)
                    return true
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
        }

        /**
         * Root, adb shell, and the ZTool app may call the bridge. The interface-token
         * check is intentionally omitted so `adb shell service call` can drive the
         * bridge directly during testing.
         */
        private fun isAllowedCaller(): Boolean {
            val uid = Binder.getCallingUid()
            if (uid == 0 || uid == 2000) return true
            return try {
                uid == resolveZtoolUid(resolveAtmsHandles(classLoader))
            } catch (_: Throwable) {
                false
            }
        }

        private inline fun withTaskHandles(block: (AtmsHandles) -> Boolean): Boolean {
            return try {
                val handles = resolveAtmsHandles(classLoader)
                val lock = handles.globalLock
                synchronized(lock) { block(handles) }
            } catch (t: Throwable) {
                logger.error("bridge operation failed", t)
                false
            }
        }
    }

    // endregion
}
