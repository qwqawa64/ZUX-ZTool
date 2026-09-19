package com.qimian233.ztool.hook.modules.systemui.misc

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * TEST HOOK (module name "hook_test" — auto-enabled while installed, no
 * frontend switch). Reroutes the SystemUI screenshot overlay's long screenshot
 * chip (mScrollChip / share_long_screenshot) from the Moto private
 * IApplicationThread chain to the dormant AOSP ScrollCapture pipeline that is
 * fully present in this ROM:
 *
 *   WMS.requestScrollCapture -> ScrollCaptureResponse
 *     -> ScrollCaptureClient.SessionWrapper (startCapture / requestImage / endCapture)
 *     -> ScrollCaptureController stock tile loop, driven reflectively
 *     -> LongScreenshot -> toBitmap -> MediaStore
 *
 * Everything runs inside the com.android.systemui process against classes from
 * its own classloader. Risky points (reflection misses, binder failures, async
 * timeouts) log through both the module logger and android.util.Log with tag
 * [TAG] for on-device debugging via logcat.
 */
@SuppressLint("WrongConstant")
class AospScrollCaptureHook : AppHookModule() {

    companion object {
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val TAG = "ZTool.AospScroll"

        private const val ID_CAN_LONG_SCREENSHOT = "aosp_scroll_can_long_screenshot"
        private const val ID_CAPTURE_CONTROLLER_REF = "aosp_scroll_capture_controller"
        private const val ID_CHIP_LISTENER = "aosp_scroll_chip_listener"

        private const val RESPONSE_TIMEOUT_MS = 10_000L
        private const val SESSION_START_TIMEOUT_MS = 10_000L
        private const val LONG_SCREENSHOT_TIMEOUT_MS = 120_000L
        private const val LISTENER_TAKEOVER_DELAY_MS = 250L

        /** Active LegacyScreenshotController, captured on every screenshot request. */
        private val activeController = AtomicReference<WeakReference<Any>?>(null)
    }

    override fun getModuleName(): String = "hook_test"

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        logI("Loading AospScrollCaptureHook (test hook).")

        val cl = param.defaultClassLoader
        val screenshotViewClass = loadClass(cl, "com.android.systemui.screenshot.ScreenshotView") ?: return
        val legacyControllerClass =
            loadClass(cl, "com.android.systemui.screenshot.LegacyScreenshotController")
        val executorClass = loadClass(cl, "com.android.systemui.screenshot.scroll.ScrollCaptureExecutor")

        hookCanLongScreenshot(screenshotViewClass)
        if (legacyControllerClass != null) {
            hookCaptureControllerRef(legacyControllerClass)
        } else {
            logE("LegacyScreenshotController class not found — chip click will fall back to stock broadcast.")
        }
        if (executorClass != null && legacyControllerClass != null) {
            hookReplaceChipListener(screenshotViewClass)
        } else {
            logE("ScrollCaptureExecutor or controller missing — AOSP rerouting disabled.")
        }
    }

    private fun loadClass(cl: ClassLoader, name: String): Class<*>? = try {
        cl.loadClass(name)
    } catch (e: Throwable) {
        logE("Class not found: $name", e)
        null
    }

    /**
     * The stock gate keeps the chip disabled for apps that never report the Moto
     * ability. Force it open so the AOSP path can be exercised everywhere; the
     * ScrollCapture handshake itself decides per-window support.
     */
    private fun hookCanLongScreenshot(screenshotViewClass: Class<*>) {
        try {
            val method = findMethod(screenshotViewClass, "canLongScreenshot")
            hookWithId(method, ID_CAN_LONG_SCREENSHOT) { chain ->
                chain.proceed()
                true
            }
            logI("canLongScreenshot force-true installed.")
        } catch (e: Throwable) {
            logE("Failed to hook canLongScreenshot", e)
        }
    }

    /**
     * Grab the live LegacyScreenshotController on each screenshot request — it
     * carries the Dagger-injected ScrollCaptureExecutor and the overlay window
     * whose token WMS needs as the scroll capture host.
     */
    private fun hookCaptureControllerRef(legacyControllerClass: Class<*>) {
        try {
            val method = legacyControllerClass.declaredMethods
                .first { it.name == "handleScreenshot" }
            method.isAccessible = true
            hookWithId(method, ID_CAPTURE_CONTROLLER_REF) { chain ->
                chain.proceed()
                activeController.set(WeakReference(chain.thisObject))
                logI("Captured active LegacyScreenshotController: ${chain.thisObject}")
            }
            logI("LegacyScreenshotController.handleScreenshot capture hook installed.")
        } catch (e: Throwable) {
            logE("Failed to hook LegacyScreenshotController.handleScreenshot", e)
        }
    }

    /**
     * Override the scroll chip's OnClickListener shortly after the stock
     * animation controller binds its own (250ms > the stock 100ms rebinding in
     * ScreenshotView.AnonymousClass9), on every overlay show.
     */
    private fun hookReplaceChipListener(screenshotViewClass: Class<*>) {
        try {
            val method = findMethod(screenshotViewClass, "createScreenshotActionsShadeAnimation")
            val chipField = findField(screenshotViewClass, "mScrollChip")
            hookWithId(method, ID_CHIP_LISTENER) { chain ->
                // Must return the proceeded ValueAnimator: the hooker's return
                // value replaces the original method result, and the caller
                // does createScreenshotActionsShadeAnimation().start().
                val proceeded = chain.proceed()
                val view = chain.thisObject
                val chip = chipField.get(view)
                if (chip == null) {
                    logE("mScrollChip is null after createScreenshotActionsShadeAnimation; takeover skipped.")
                } else {
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            val chipNow = chipField.get(view)
                            if (chipNow == null) {
                                logE("mScrollChip became null before listener takeover.")
                            } else {
                                (chipNow as android.view.View).setOnClickListener { v ->
                                    logI("Scroll chip clicked — routing to AOSP ScrollCapture pipeline.")
                                    onChipClicked(view, v)
                                }
                                logI("AOSP scroll chip listener installed (+$LISTENER_TAKEOVER_DELAY_MS ms).")
                            }
                        } catch (e: Throwable) {
                            logE("Failed to install AOSP scroll chip listener", e)
                        }
                    }, LISTENER_TAKEOVER_DELAY_MS)
                }
                proceeded
            }
            logI("createScreenshotActionsShadeAnimation hook installed (chip listener takeover).")
        } catch (e: Throwable) {
            logE("Failed to hook createScreenshotActionsShadeAnimation", e)
        }
    }

    // ---------------------------------------------------------------------
    // AOSP ScrollCapture pipeline driver
    // ---------------------------------------------------------------------

    private fun onChipClicked(screenshotView: Any, chip: android.view.View) {
        val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ZTool-AospScroll") }
        worker.execute {
            var success = false
            try {
                success = runAospPipeline(screenshotView)
            } catch (e: Throwable) {
                logE("AOSP scroll capture pipeline crashed", e)
            } finally {
                worker.shutdown()
                if (!success) {
                    logE("AOSP scroll capture did not complete; falling back to stock broadcast.")
                    try {
                        chip.context.sendBroadcast(Intent("com.android.systemui.quickscreenshot_start"))
                    } catch (e: Throwable) {
                        logE("Stock fallback broadcast failed", e)
                    }
                }
            }
        }
    }

    private fun runAospPipeline(screenshotView: Any): Boolean {
        val controller = activeController.get()?.get()
        if (controller == null) {
            logE("No active LegacyScreenshotController — take a screenshot first, then click the chip.")
            return false
        }
        val controllerField = findFieldOrNull(controller.javaClass, "mScrollCaptureExecutor")
            ?: run { logE("Field mScrollCaptureExecutor missing on LegacyScreenshotController"); return false }
        val executor = controllerField.get(controller) ?: run {
            logE("mScrollCaptureExecutor is null"); return false
        }
        val executorClass = executor.javaClass
        val client = findFieldOrNull(executorClass, "scrollCaptureClient")?.get(executor) ?: run {
            logE("scrollCaptureClient is null"); return false
        }
        val captureController = findFieldOrNull(executorClass, "scrollCaptureController")?.get(executor) ?: run {
            logE("scrollCaptureController is null"); return false
        }
        val holder = findFieldOrNull(executorClass, "longScreenshotHolder")?.get(executor)
        val clientClass = client.javaClass
        val captureControllerClass = captureController.javaClass
        logI("Resolved executor=$executor")
        logI("Resolved client=$client")
        logI("Resolved controller=$captureController")

        // Host window token: the screenshot overlay's own decor view.
        val phoneWindow = findFieldOrNull(controller.javaClass, "mWindow")?.get(controller) ?: run {
            logE("mWindow is null on LegacyScreenshotController"); return false
        }
        val decorView = phoneWindow.javaClass.getMethod("getDecorView").invoke(phoneWindow) as android.view.View
        val windowToken = decorView.windowToken
        if (windowToken == null) {
            logE("Overlay decorView windowToken is null — window not attached?")
            return false
        }
        val displayId = findFieldOrNull(controller.javaClass, "mDisplayId")?.let {
            try { it.getInt(controller) } catch (e: Throwable) {
                logE("mDisplayId read failed, falling back to display 0", e); 0
            }
        } ?: 0
        logI("Host token=$windowToken displayId=$displayId")

        val wms = findFieldOrNull(clientClass, "mWindowManagerService")?.get(client) ?: run {
            logE("client.mWindowManagerService is null"); return false
        }
        findFieldOrNull(clientClass, "mHostWindowToken")?.set(client, windowToken)

        // --- Step 1: WMS requestScrollCapture --------------------------------
        val response = requestScrollCapture(controller.javaClass.classLoader, wms, displayId, windowToken)
            ?: return false
        val connected = invokeBool(response, "isConnected")
        if (!connected) {
            val desc = invokeObj(response, "getDescription")
            val title = invokeObj(response, "getWindowTitle")
            logE("ScrollCapture response NOT connected: desc=$desc window=$title")
            toastMain(screenshotView, "ScrollCapture unavailable: $desc")
            return false
        }
        val packageName = invokeObj(response, "getPackageName")?.toString() ?: "unknown"
        val windowBounds = invokeObj(response, "getWindowBounds") as? Rect
        val boundsInWindow = invokeObj(response, "getBoundsInWindow") as? Rect
        logI("Connected to window pkg=$packageName title=${invokeObj(response, "getWindowTitle")}")
        logI("windowBounds=$windowBounds boundsInWindow=$boundsInWindow")
        if (boundsInWindow == null || windowBounds == null) {
            logE("ScrollCaptureResponse rects are null — cannot build SessionWrapper.")
            return false
        }

        // --- Step 2: arm the stock ScrollCaptureController ---------------------
        val captureFuture = newSafeFuture(controller.javaClass.classLoader) { "ztool-capture" }
        val captureCompleter = extractCompleter(captureFuture)
            ?: run { logE("Cannot obtain capture Completer from SafeFuture"); return false }
        setField(captureControllerClass, captureController, "mCancelled", false)
        setField(captureControllerClass, captureController, "mWindowOwner", packageName)
        setField(captureControllerClass, captureController, "mCaptureCompleter", captureCompleter)
        logI("Stock controller armed: mCancelled=false mWindowOwner=$packageName")

        // --- Step 3: create session (SessionWrapper + ImageReader) -------------
        val cl = controller.javaClass.classLoader
        val connection = invokeObj(response, "getConnection") ?: run {
            logE("response.getConnection() is null"); return false
        }
        val wrapperClass = loadClass(cl, "com.android.systemui.screenshot.scroll.ScrollCaptureClient\$SessionWrapper")
            ?: return false
        val viewContext = (screenshotView as android.view.View).context
        val maxPages = Settings.Secure.getFloat(viewContext.contentResolver, "screenshot.scroll_max_pages", 3.0f)
        val bgExecutor = findFieldOrNull(clientClass, "mBgExecutor")?.get(client) as? java.util.concurrent.Executor
        val wrapper = wrapperClass.constructors.firstOrNull { it.parameterCount == 5 }?.newInstance(
            connection, windowBounds, boundsInWindow, maxPages, bgExecutor) ?: run {
            logE("SessionWrapper 5-arg constructor not found"); return false
        }
        val tileWidth = findField(wrapperClass, "mTileWidth").getInt(wrapper)
        val tileHeight = findField(wrapperClass, "mTileHeight").getInt(wrapper)
        logI("SessionWrapper created: tile=${tileWidth}x$tileHeight maxPages=$maxPages executor=$bgExecutor")

        val reader = ImageReader.newInstance(tileWidth, tileHeight, PixelFormat.RGBA_8888, 30, 0x100L)
        setField(wrapperClass, wrapper, "mReader", reader)
        // Public two-arg listener variant; SessionWrapper implements OnImageAvailableListener.
        reader.setOnImageAvailableListener(
            wrapper as android.media.ImageReader.OnImageAvailableListener,
            Handler(Looper.getMainLooper()))

        val surface = reader.surface
        logI("ImageReader ready surface=$surface; issuing connection.startCapture")
        val startFuture = newSafeFuture(cl) { completer ->
            try {
                setField(wrapperClass, wrapper, "mStartCompleter", completer)
                val callbacksClass = cl.loadClass("android.view.IScrollCaptureCallbacks")
                val startCapture = findMethodDeep(connection.javaClass, "startCapture",
                    android.view.Surface::class.java, callbacksClass)
                startCapture.invoke(connection, surface, wrapper)
                setField(wrapperClass, wrapper, "mStarted", true)
                logI("startCapture issued, mStarted=true")
            } catch (e: Throwable) {
                logE("startCapture failed", e)
                runCatching { completeException(cl, completer, e) }
                    .onFailure { logE("completer.setException failed", it) }
            }
            "ztool-start"
        }
        setField(captureControllerClass, captureController, "mSessionFuture", startFuture)

        val lambdaClass = loadClass(cl,
            "com.android.systemui.screenshot.scroll.ScrollCaptureController\$\$ExternalSyntheticLambda0")
        var usedStockLambda = false
        if (lambdaClass != null) {
            try {
                val onSessionReady = lambdaClass.constructors.first { it.parameterCount == 2 }
                    .newInstance(captureController, 3)
                val addListener = startFuture.javaClass.methods
                    .first { it.name == "addListener" && it.parameterCount == 2 }
                addListener.invoke(startFuture, onSessionReady, mainExecutor(controller))
                usedStockLambda = true
                logI("Stock onSessionReady lambda attached (case 3).")
            } catch (e: Throwable) {
                logE("Attaching stock session-ready lambda failed; manual fallback will be used.", e)
            }
        } else {
            logE("Controller lambda class missing; manual session start fallback will be used.")
        }

        if (awaitFuture(startFuture, SESSION_START_TIMEOUT_MS, "session start") == null) return false
        if (!usedStockLambda) {
            setField(captureControllerClass, captureController, "mSession", wrapper)
            val requestFirst = findMethodDeep(captureControllerClass, "requestNextTile", Int::class.javaPrimitiveType)
            requestFirst.invoke(captureController, 0)
            logI("Manual fallback: mSession=wrapper, requestNextTile(0) triggered.")
        } else {
            val session = findFieldOrNull(captureControllerClass, "mSession")?.get(captureController)
            logI("Session started via stock lambda: session=$session")
        }

        // Overlay no longer needed; dismiss exactly like the stock click path.
        dismissOverlay(screenshotView)

        // --- Step 4: stock tile loop runs (requestNextTile / onCaptureResult) --
        // Nothing to do here; stock lambdas drive the loop until finishCapture()
        // completes our captureFuture with a LongScreenshot.

        // --- Step 5: await LongScreenshot --------------------------------------
        val longScreenshot = awaitFuture(captureFuture, LONG_SCREENSHOT_TIMEOUT_MS, "long screenshot")
        if (longScreenshot == null) {
            logE("No LongScreenshot produced.")
            return false
        }
        val tileSet = runCatching {
            findFieldOrNull(longScreenshot.javaClass, "mImageTileSet")?.get(longScreenshot)
        }.getOrNull()
        var tileInfo = "tileSet=null"
        if (tileSet != null) {
            val width = runCatching { tileSet.javaClass.getMethod("getWidth").invoke(tileSet) as Int }.getOrDefault(-1)
            val height = runCatching { tileSet.javaClass.getMethod("getHeight").invoke(tileSet) as Int }.getOrDefault(-1)
            tileInfo = "tileSet=${width}x$height"
        }
        logI("LongScreenshot produced: $tileInfo")
        if (tileSet == null || tileInfo.contains("x0")) {
            logE("LongScreenshot tile set empty or unreadable ($tileInfo).")
            return false
        }

        // --- Step 6: export -----------------------------------------------------
        if (holder != null) {
            try {
                val holderRef = findField(holder.javaClass, "mLongScreenshot").get(holder)
                    as java.util.concurrent.atomic.AtomicReference<Any>
                @Suppress("UNCHECKED_CAST")
                holderRef.set(longScreenshot as Any)
                logI("Stored LongScreenshot into longScreenshotHolder.")
            } catch (e: Throwable) {
                logE("Storing LongScreenshot into holder failed (non-fatal)", e)
            }
        }
        val bitmap = runCatching {
            longScreenshot.javaClass.getMethod("toBitmap").invoke(longScreenshot) as? android.graphics.Bitmap
        }.getOrNull()
        if (bitmap == null || bitmap.height <= 0) {
            logE("toBitmap() returned $bitmap")
            return false
        }
        val uri = saveBitmap(viewContext, bitmap)
        if (uri == null) {
            logE("Bitmap save failed (${bitmap.width}x${bitmap.height}).")
            return false
        }
        logI("Long screenshot saved: $uri (${bitmap.width}x${bitmap.height})")
        toastMain(screenshotView, "长截屏已保存: ${bitmap.width}x${bitmap.height}")
        return true
    }

    private fun requestScrollCapture(
        cl: ClassLoader,
        wms: Any,
        displayId: Int,
        windowToken: android.os.IBinder
    ): Any? {
        val listenerClass = try {
            cl.loadClass("android.view.IScrollCaptureResponseListener")
        } catch (e: Throwable) {
            logE("IScrollCaptureResponseListener not loadable", e); return null
        }
        val future = newSafeFuture(cl) { completer ->
            try {
                val clientListenerClass =
                    cl.loadClass("com.android.systemui.screenshot.scroll.ScrollCaptureClient\$1")
                val listener = clientListenerClass.constructors
                    .first { it.parameterCount == 1 }
                    .newInstance(completer)
                val request = findMethodDeep(wms.javaClass, "requestScrollCapture",
                    Int::class.javaPrimitiveType, android.os.IBinder::class.java,
                    Int::class.javaPrimitiveType, listenerClass)
                logI("Calling WMS.requestScrollCapture(displayId=$displayId, token=$windowToken, taskId=-1)")
                request.invoke(wms, displayId, windowToken, -1, listener)
            } catch (e: Throwable) {
                logE("requestScrollCapture invocation failed", e)
                runCatching { completeException(cl, completer, e) }
                    .onFailure { logE("completer.setException failed", it) }
            }
            "ztool-request"
        }
        return awaitFuture(future, RESPONSE_TIMEOUT_MS, "scroll capture response")
    }

    /**
     * Creates a real SafeFuture via the ROM-bundled CallbackToFutureAdapter so
     * stock code can complete it; [resolverBody] runs inside attachCompleter.
     */
    private fun newSafeFuture(cl: ClassLoader, resolverBody: (Any) -> Unit): Any {
        val adapterClass = cl.loadClass("androidx.concurrent.futures.CallbackToFutureAdapter")
        val resolverClass = cl.loadClass("androidx.concurrent.futures.CallbackToFutureAdapter\$Resolver")
        val resolver = Proxy.newProxyInstance(cl, arrayOf(resolverClass), InvocationHandler { _, method, args ->
            if (method.name == "attachCompleter" && args != null && args.isNotEmpty()) {
                resolverBody(args[0])
                return@InvocationHandler "ZTool AospScrollCapture future"
            }
            null
        })
        val getFuture = adapterClass.methods.first { it.name == "getFuture" }
        return getFuture.invoke(null, resolver)
    }

    /** SafeFuture holds its Completer only through completerWeakReference. */
    private fun extractCompleter(safeFuture: Any): Any? = try {
        val weakRef = safeFuture.javaClass.getDeclaredField("completerWeakReference")
            .apply { isAccessible = true }.get(safeFuture) as java.lang.ref.WeakReference<*>
        weakRef.get()
    } catch (e: Throwable) {
        logE("Cannot extract completer from SafeFuture", e)
        null
    }

    private fun awaitFuture(safeFuture: Any, timeoutMs: Long, what: String): Any? = try {
        val get = safeFuture.javaClass.methods
            .first { it.name == "get" && it.parameterCount == 2 }
        val result = get.invoke(safeFuture, timeoutMs, TimeUnit.MILLISECONDS)
        logI("$what future completed: $result")
        result
    } catch (e: java.lang.reflect.InvocationTargetException) {
        val cause = e.cause
        when (cause) {
            is java.util.concurrent.TimeoutException ->
                logE("$what future timed out after ${timeoutMs}ms", cause)
            is java.util.concurrent.ExecutionException ->
                logE("$what future failed", cause.cause ?: cause)
            else -> logE("$what future failed", cause ?: e)
        }
        null
    } catch (e: Throwable) {
        logE("$what future await failed", e)
        null
    }

    private fun completeException(cl: ClassLoader, completer: Any, e: Throwable) {
        val completerClass = cl.loadClass("androidx.concurrent.futures.CallbackToFutureAdapter\$Completer")
        completerClass.getMethod("setException", Throwable::class.java).invoke(completer, e)
    }

    private fun dismissOverlay(screenshotView: Any) {
        try {
            val callback = findField(screenshotView.javaClass, "mControlCallBack").get(screenshotView)
            if (callback == null) {
                logE("mControlCallBack is null; overlay window stays on screen.")
                return
            }
            callback.javaClass.getMethod("dismissWin").invoke(callback)
            logI("Overlay window dismissed via mControlCallBack.dismissWin().")
        } catch (e: Throwable) {
            logE("dismissWin invocation failed; overlay window may remain visible", e)
        }
    }

    private fun saveBitmap(context: android.content.Context, bitmap: android.graphics.Bitmap): Uri? {
        return try {
            val name = "ZTool_longshot_${SystemClock.elapsedRealtime()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ZTool")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                logE("MediaStore.insert returned null")
                return null
            }
            val out = resolver.openOutputStream(uri)
            if (out == null) {
                logE("openOutputStream returned null for $uri")
                return null
            }
            out.use {
                if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) {
                    logE("Bitmap.compress returned false")
                    return null
                }
            }
            uri
        } catch (e: Throwable) {
            logE("Saving bitmap failed", e)
            null
        }
    }

    // ---------------------------------------------------------------------
    // Helpers & logging
    // ---------------------------------------------------------------------

    private fun mainExecutor(controller: Any): java.util.concurrent.Executor {
        val context = findField(controller.javaClass, "mContext").get(controller) as android.content.Context
        return context.mainExecutor
    }

    private fun findFieldOrNull(origin: Class<*>, name: String) = try {
        findField(origin, name)
    } catch (e: Throwable) {
        logE("Field not found: ${origin.simpleName}.$name", e)
        null
    }

    private fun setField(origin: Class<*>, target: Any, name: String, value: Any?) {
        findField(origin, name).set(target, value)
    }

    /** Name-based lookup across class hierarchy and interfaces (parameter-count matched). */
    private fun findMethodDeep(target: Class<*>, name: String, vararg params: Class<*>?): Method {
        val wanted = params.size
        var c: Class<*>? = target
        while (c != null) {
            findMethodIn(c, name, wanted, params)?.let { return it }
            for (iface in c.interfaces) {
                findMethodIn(iface, name, wanted, params)?.let { return it }
            }
            c = c.superclass
        }
        throw NoSuchMethodException("$name/($wanted params) on $target")
    }

    private fun findMethodIn(c: Class<*>, name: String, count: Int, params: Array<out Class<*>?>): Method? {
        for (m in c.declaredMethods) {
            if (m.name != name || m.parameterTypes.size != count) continue
            var match = true
            for (i in params.indices) {
                val want = params[i]
                if (want != null && m.parameterTypes[i] != want) { match = false; break }
            }
            if (match) { m.isAccessible = true; return m }
        }
        return null
    }

    private fun invokeBool(target: Any, name: String): Boolean =
        target.javaClass.methods.first { it.name == name && it.parameterCount == 0 }
            .invoke(target) as Boolean

    private fun invokeObj(target: Any, name: String): Any? =
        target.javaClass.methods.first { it.name == name && it.parameterCount == 0 }
            .invoke(target)

    private fun toastMain(screenshotView: Any, message: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText((screenshotView as android.view.View).context,
                    message, Toast.LENGTH_LONG).show()
            } catch (e: Throwable) {
                logE("Toast failed: $message", e)
            }
        }
    }

    private fun logI(message: String) {
        Log.i(TAG, message)
        logger.info("AospScroll: $message")
    }

    private fun logE(message: String, e: Throwable? = null) {
        if (e != null) Log.e(TAG, message, e) else Log.e(TAG, message)
        if (e != null) logger.error("AospScroll: $message", e) else logger.error("AospScroll: $message")
    }
}
