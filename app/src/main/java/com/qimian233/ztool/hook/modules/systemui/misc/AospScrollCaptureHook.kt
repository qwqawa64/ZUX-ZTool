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
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Reroutes the SystemUI long-screenshot chip from the Moto/private chain to the
 * AOSP ScrollCapture pipeline, when that pipeline still exists in the ROM.
 *
 * ZUXOS 2.5 ships only empty scroll-capture stubs and no LongScreenshot class;
 * the capability probe then logs one warning and leaves the stock chip path.
 */
@SuppressLint("WrongConstant", "PrivateApi")
class AospScrollCaptureHook : AppHookModule() {

    companion object {
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName

        private const val LONG_SCREENSHOT_ACTIVITY =
            "com.android.systemui.screenshot.scroll.LongScreenshotActivity"
        private const val LONG_SCREENSHOT_CLASS =
            "com.android.systemui.screenshot.scroll.LongScreenshot"

        private const val ID_CAN_LONG_SCREENSHOT = "aosp_scroll_can_long_screenshot"
        private const val ID_INIT_APPLICATION_THREAD = "aosp_scroll_init_application_thread"
        private const val ID_CAPTURE_CONTROLLER_CTOR = "aosp_scroll_capture_controller_ctor"
        private const val ID_CAPTURE_CONTROLLER_HANDLE = "aosp_scroll_capture_controller_handle"
        private const val ID_CHIP_LISTENER = "aosp_scroll_chip_listener"
        private const val ID_ACTIVITY_LIFECYCLE = "aosp_scroll_activity_lifecycle"
        private const val ID_ACTIVITY_TRANSITION = "aosp_scroll_activity_transition"
        private const val ID_FATAL_TRACER = "aosp_scroll_fatal_tracer"

        private const val RESPONSE_TIMEOUT_MS = 10_000L
        private const val SESSION_START_TIMEOUT_MS = 10_000L
        private const val LONG_SCREENSHOT_TIMEOUT_MS = 120_000L
        private const val LISTENER_TAKEOVER_DELAY_MS = 250L

        /** Active ScreenshotController, captured on every screenshot request. */
        private val activeController = AtomicReference<WeakReference<Any>?>(null)
    }

    override fun getModuleName(): String = PreferenceKeys.FORCE_LONG_SCREENSHOT_AOSP.name

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != SYSTEMUI_PACKAGE) return
        logI("Loading AospScrollCaptureHook.")

        val cl = param.defaultClassLoader
        val screenshotViewClass = loadClass(cl, "com.android.systemui.screenshot.ScreenshotView") ?: return

        hookLongScreenshotActivityTracing(cl)
        hookPatchEnterTransition(cl)
        hookFatalExceptionTracing()

        val controllerClass = resolveControllerClass(cl)
        if (controllerClass == null) {
            logger.warn("AospScroll: no ScreenshotController class found; AOSP reroute skipped, chip stays stock.")
            return
        }
        if (!hasAospScrollPipeline(cl, controllerClass)) {
            logger.warn(
                "AospScroll: AOSP ScrollCapture pipeline is absent in this build " +
                    "(${controllerClass.simpleName} has no usable scroll-capture executor); " +
                    "chip stays on the stock ZUX long-screenshot path."
            )
            return
        }

        hookCanLongScreenshot(screenshotViewClass)
        hookInitApplicationThread(screenshotViewClass)
        hookCaptureControllerRef(controllerClass)
        hookReplaceChipListener(screenshotViewClass)
    }

    private fun loadClass(cl: ClassLoader, name: String, logMissing: Boolean = true): Class<*>? = try {
        cl.loadClass(name)
    } catch (e: Throwable) {
        if (logMissing) logE("Class not found: $name", e)
        else logger.debug("AospScroll: class absent: $name")
        null
    }

    private fun tryLoadClass(cl: ClassLoader, name: String): Class<*>? = try {
        cl.loadClass(name)
    } catch (_: Throwable) {
        null
    }

    /** Legacy builds use LegacyScreenshotController; current ZUXOS uses the merged ScreenshotController. */
    private fun resolveControllerClass(cl: ClassLoader): Class<*>? {
        tryLoadClass(cl, "com.android.systemui.screenshot.LegacyScreenshotController")?.let {
            logger.debug("AospScroll: controller=LegacyScreenshotController.")
            return it
        }
        val merged = tryLoadClass(cl, "com.android.systemui.screenshot.ScreenshotController")
        if (merged != null) {
            logger.debug("AospScroll: controller=ScreenshotController (merged replacement).")
        }
        return merged
    }

    /**
     * The AOSP driver needs a real ScrollCaptureExecutor (client/controller
     * fields) plus LongScreenshot. ZUXOS 2.5 keeps only empty stubs.
     */
    private fun hasAospScrollPipeline(cl: ClassLoader, controllerClass: Class<*>): Boolean {
        val executorField = findFieldOrNull(controllerClass, "mScrollCaptureExecutor", quiet = true)
            ?: findFieldOrNull(controllerClass, "scrollCaptureExecutor", quiet = true)
            ?: return false
        val executorClass = executorField.type
        if (findFieldOrNull(executorClass, "scrollCaptureClient", quiet = true) == null) return false
        if (findFieldOrNull(executorClass, "scrollCaptureController", quiet = true) == null) return false
        return tryLoadClass(cl, LONG_SCREENSHOT_CLASS) != null
    }

    /**
     * LongScreenshotActivity has no module-side crash evidence, so trace its
     * lifecycle (onCreate/onStart/onStop). Logging only; proceed() rethrows.
     */
    private fun hookLongScreenshotActivityTracing(cl: ClassLoader) {
        val activityClass = loadClass(
            cl, LONG_SCREENSHOT_ACTIVITY, logMissing = false
        ) ?: return

        try {
            val onCreate = activityClass.declaredMethods
                .first { it.name == "onCreate" && it.parameterCount == 1 }
            onCreate.isAccessible = true
            hookWithId(onCreate, ID_ACTIVITY_LIFECYCLE + "_create") { chain ->
                logI("LongScreenshotActivity.onCreate entered")
                val result = chain.proceed()
                try {
                    val activity = chain.thisObject as android.app.Activity
                    val intent = activity.intent
                    logI("onCreate intent component=${intent?.component} " +
                        "hasCaptureResponse=${intent?.hasExtra("capture-response")} " +
                        "hasUserHandle=${intent?.hasExtra("screenshot-userhandle")}")
                } catch (e: Throwable) {
                    logE("onCreate intent inspection failed", e)
                }
                logI("LongScreenshotActivity.onCreate exited normally")
                result
            }
            logI("LongScreenshotActivity.onCreate tracing installed.")
        } catch (e: Throwable) {
            logE("Failed to hook LongScreenshotActivity.onCreate", e)
        }

        try {
            val onStart = activityClass.declaredMethods
                .first { it.name == "onStart" && it.parameterCount == 0 }
            onStart.isAccessible = true
            hookWithId(onStart, ID_ACTIVITY_LIFECYCLE + "_start") { chain ->
                logI("LongScreenshotActivity.onStart entered")
                try {
                    val result = chain.proceed()
                    logI("LongScreenshotActivity.onStart exited normally")
                    result
                } catch (e: Throwable) {
                    logE("LongScreenshotActivity.onStart THREW: ${e.javaClass.name}: ${e.message}", e)
                    throw e
                }
            }
            logI("LongScreenshotActivity.onStart tracing installed.")
        } catch (e: Throwable) {
            logE("Failed to hook LongScreenshotActivity.onStart", e)
        }

        try {
            val onStop = activityClass.declaredMethods
                .first { it.name == "onStop" && it.parameterCount == 0 }
            onStop.isAccessible = true
            hookWithId(onStop, ID_ACTIVITY_LIFECYCLE + "_stop") { chain ->
                logI("LongScreenshotActivity.onStop entered")
                chain.proceed()
            }
            logI("LongScreenshotActivity.onStop tracing installed.")
        } catch (e: Throwable) {
            logE("Failed to hook LongScreenshotActivity.onStop", e)
        }
    }

    /**
     * Old builds decode the enter-transition lambda's $r8$classId (case 1 =
     * shelf transition). Patch only when that exact lambda shape exists; 2.5
     * removed it and leaves crop entrance to stock code.
     */
    private fun hookPatchEnterTransition(cl: ClassLoader) {
        val lambdaClass = findEnterTransitionLambda(cl)
        if (lambdaClass == null) {
            logger.warn(
                "AospScroll: no enter-transition lambda in this build; crop-entrance patch skipped."
            )
            return
        }
        try {
            val runMethod = lambdaClass.declaredMethods.first { it.name == "run" }
            runMethod.isAccessible = true
            val classIdField = findField(lambdaClass, $$"$r8$classId")
            val activityField = findField(lambdaClass, $$"f$0")
            val topFractionField = findField(lambdaClass, $$"f$1")
            val bottomFractionField = findField(lambdaClass, $$"f$2")
            hookWithId(runMethod, ID_ACTIVITY_TRANSITION) { chain ->
                val self = chain.thisObject ?: return@hookWithId chain.proceed()
                val classId = classIdField.getInt(self)
                if (classId == 1) {
                    val activity = activityField.get(self)
                        ?: return@hookWithId chain.proceed()
                    val holder = findField(activity.javaClass, "mLongScreenshotHolder").get(activity)
                    val callbackRef = holder?.let {
                        findFieldOrNull(it.javaClass, "mTransitionDestinationCallback")?.get(it)
                            as? AtomicReference<*>
                    }
                    if (callbackRef == null || callbackRef.get() == null) {
                        logI("Shelf transition callback missing; applying direct crop entrance.")
                        applyDirectCropEntrance(activity,
                            topFractionField.getFloat(self), bottomFractionField.getFloat(self))
                    } else {
                        logI("Shelf transition callback present; running native transition.")
                        chain.proceed()
                    }
                } else {
                    chain.proceed()
                }
            }
            logI("Enter-transition patch hook installed (LongScreenshotActivity lambda).")
        } catch (e: Throwable) {
            logE("Failed to hook the enter-transition lambda", e)
        }
    }

    /**
     * Matches only the lambda carrying $r8$classId plus two float fractions;
     * current export/view lambdas (e.g. Lambda4/Lambda5) do not qualify.
     */
    private fun findEnterTransitionLambda(cl: ClassLoader): Class<*>? {
        for (index in 0..15) {
            val name = $$$"com.android.systemui.screenshot.scroll.LongScreenshotActivity$$ExternalSyntheticLambda" +
                index
            val candidate = tryLoadClass(cl, name) ?: continue
            val fields = candidate.declaredFields
            if (fields.none { it.name == $$"$r8$classId" && it.type == Int::class.javaPrimitiveType }) continue
            val hasActivity = fields.any { it.name == $$"f$0" && it.type.name == LONG_SCREENSHOT_ACTIVITY }
            if (!hasActivity) continue
            if (fields.none { it.name == $$"f$1" && it.type == Float::class.javaPrimitiveType }) continue
            if (fields.none { it.name == $$"f$2" && it.type == Float::class.javaPrimitiveType }) continue
            if (candidate.declaredMethods.none { it.name == "run" && it.parameterCount == 0 }) continue
            return candidate
        }
        return null
    }

    /**
     * Direct equivalent of the transition end handler: reveal the preview,
     * position the crop boundaries and enable the buttons — without the shelf
     * preview animation that requires the dormant ScreenshotController.
     */
    private fun applyDirectCropEntrance(activity: Any, topFraction: Float, bottomFraction: Float) {
        try {
            val preview = findField(activity.javaClass, "mPreview").get(activity) as android.widget.ImageView
            preview.animate().alpha(1.0f)

            val cropView = findField(activity.javaClass, "mCropView").get(activity)
            val cropViewClass = cropView.javaClass
            val boundaryClass = cropViewClass.classLoader!!
                .loadClass($$"com.android.systemui.screenshot.scroll.CropView$CropBoundary")
            val topBoundary = boundaryClass.getField("TOP").get(null)
            val bottomBoundary = boundaryClass.getField("BOTTOM").get(null)
            val setBoundary = findMethodDeep(cropViewClass, "setBoundaryPosition",
                Float::class.javaPrimitiveType, boundaryClass)
            setBoundary.invoke(cropView, topFraction, topBoundary)
            setBoundary.invoke(cropView, bottomFraction, bottomBoundary)
            logI("Crop boundaries applied: top=$topFraction bottom=$bottomFraction")

            findField(cropViewClass, "mEntranceInterpolation").setFloat(cropView, 1.0f)
            cropViewClass.getMethod("setVisibility", Int::class.javaPrimitiveType)
                .invoke(cropView, android.view.View.VISIBLE)
            val invalidate = cropViewClass.getMethod("invalidate")
            invalidate.invoke(cropView)

            val setButtonsEnabled = try {
                findMethodDeep(activity.javaClass, "setButtonsEnabled", Boolean::class.javaPrimitiveType)
            } catch (_: Throwable) {
                null
            }
            if (setButtonsEnabled != null) {
                setButtonsEnabled.invoke(activity, true)
            } else {
                enableCropButtons(activity)
            }
            logI("Direct crop entrance applied: crop view visible, buttons enabled.")
        } catch (e: Throwable) {
            logE("Direct crop entrance failed — crop view/buttons may stay unusable", e)
        }
    }

    /** Current LongScreenshotActivity enables mSave/mEdit/mShare directly. */
    private fun enableCropButtons(activity: Any) {
        for (name in arrayOf("mSave", "mEdit", "mShare")) {
            val button = findFieldOrNull(activity.javaClass, name, quiet = true)?.get(activity) as? android.view.View
            button?.isEnabled = true
        }
    }

    /**
     * Log every fatal uncaught exception in this process (full stack) before
     * the framework kills it, so any LongScreenshotActivity crash is captured
     * in LSPosed logs even without a logcat capture.
     */
    private fun hookFatalExceptionTracing() {
        try {
            val handlerClass = Class.forName(
                $$"com.android.internal.os.RuntimeInit$KillApplicationHandler"
            )
            val method = handlerClass.declaredMethods
                .first { it.name == "uncaughtException" && it.parameterCount == 2 }
            method.isAccessible = true
            hookWithId(method, ID_FATAL_TRACER) { chain ->
                val throwable = chain.args.getOrNull(1) as? Throwable
                logE("FATAL uncaught exception in SystemUI process:\n" +
                    Log.getStackTraceString(throwable ?: Throwable("unknown")))
                chain.proceed()
            }
            logI($$"Fatal exception tracer installed (RuntimeInit$KillApplicationHandler).")
        } catch (e: Throwable) {
            logE($$"Failed to hook RuntimeInit$KillApplicationHandler", e)
        }
    }

    /**
     * The takeover owns the chip click, so the stock gate must not disable it.
     * Try the current canLongScreenshot(Context) first, then the legacy no-arg.
     */
    private fun hookCanLongScreenshot(screenshotViewClass: Class<*>) {
        val contextClass = android.content.Context::class.java
        val method = runCatching {
            findMethod(screenshotViewClass, "canLongScreenshot", contextClass)
        }.getOrNull() ?: runCatching {
            findMethod(screenshotViewClass, "canLongScreenshot")
        }.getOrNull()
        if (method == null) {
            logE("canLongScreenshot(Context) / canLongScreenshot() not found; chip gate left stock.")
            return
        }
        try {
            hookWithId(method, ID_CAN_LONG_SCREENSHOT) { _ -> true }
            logI("canLongScreenshot short-circuit true installed (params=${method.parameterCount}).")
        } catch (e: Throwable) {
            logE("Failed to hook canLongScreenshot", e)
        }
    }

    /**
     * ForceLongScreenshot also patched initApplicationThread: the flag
     * mCanBeLongScreenshotMotoFlag is normally written asynchronously by the
     * app-reported ILongScreenshotListener callback, which can arrive after
     * this method and flip it back to false. Re-assert true after every run
     * so click handlers and the scroll-capture path that re-read the field
     * stay consistent.
     */
    private fun hookInitApplicationThread(screenshotViewClass: Class<*>) {
        try {
            val method = findMethod(screenshotViewClass, "initApplicationThread")
            val flagField = findField(screenshotViewClass, "mCanBeLongScreenshotMotoFlag")
            hookWithId(method, ID_INIT_APPLICATION_THREAD) { chain ->
                chain.proceed()
                try {
                    flagField.set(chain.thisObject, true)
                    logger.debug("AospScroll: mCanBeLongScreenshotMotoFlag forced true")
                } catch (e: Throwable) {
                    logger.error("AospScroll: failed to force ability flag", e)
                }
            }
            logI("initApplicationThread flag re-assert hook installed.")
        } catch (e: Throwable) {
            logE("Failed to hook initApplicationThread", e)
        }
    }

    /**
     * Captures the live controller: constructor when declared, then the stock
     * entry points (takeScreenshotFullscreen on legacy, handleScreenshot on both).
     */
    private fun hookCaptureControllerRef(controllerClass: Class<*>) {
        val ctor = controllerClass.declaredConstructors
            .firstOrNull { it.parameterCount > 0 }
            ?: controllerClass.declaredConstructors.firstOrNull()
        if (ctor == null) {
            logger.debug("AospScroll: no declared constructor on ${controllerClass.simpleName}.")
        } else {
            try {
                ctor.isAccessible = true
                hookWithId(ctor, ID_CAPTURE_CONTROLLER_CTOR) { chain ->
                    chain.proceed()
                    activeController.set(WeakReference(chain.thisObject))
                    logI("Captured ${controllerClass.simpleName} via constructor.")
                }
                logI("Controller constructor capture installed (${ctor.parameterTypes.size} params).")
            } catch (e: Throwable) {
                logE("Failed to hook controller constructor", e)
            }
        }
        hookControllerEntry(controllerClass, "takeScreenshotFullscreen", ID_CAPTURE_CONTROLLER_HANDLE)
        hookControllerEntry(controllerClass, "handleScreenshot", ID_CAPTURE_CONTROLLER_HANDLE + "_hs")
    }

    private fun hookControllerEntry(controllerClass: Class<*>, name: String, id: String) {
        val method = controllerClass.declaredMethods.firstOrNull { it.name == name }
        if (method == null) {
            logger.debug("AospScroll: $name absent on ${controllerClass.simpleName}.")
            return
        }
        try {
            method.isAccessible = true
            hookWithId(method, id) { chain ->
                chain.proceed()
                activeController.set(WeakReference(chain.thisObject))
                logI("Captured controller via $name.")
            }
            logI("$name capture hook installed.")
        } catch (e: Throwable) {
            logE("Failed to hook $name", e)
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
                            captureControllerFromView(view)
                            val chipNow = chipField.get(view)
                            if (chipNow == null) {
                                logE("mScrollChip became null before listener takeover.")
                            } else {
                                val chipView = chipNow as android.view.View
                                logI("Chip state before takeover: enabled=${chipView.isEnabled} " +
                                    "clickable=${chipView.isClickable} visibility=${chipView.visibility}")
                                chipView.isEnabled = true
                                chipView.isClickable = true
                                chipView.visibility = android.view.View.VISIBLE
                                restoreChipIcon(chipView)
                                chipView.setOnClickListener { v ->
                                    logI("Scroll chip clicked — routing to AOSP ScrollCapture pipeline.")
                                    onChipClicked(view, v)
                                }
                                logI("AOSP scroll chip listener installed (+$LISTENER_TAKEOVER_DELAY_MS ms); " +
                                    "state after: enabled=${chipView.isEnabled} clickable=${chipView.isClickable}")
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

    /** Fallback capture: mControlCallBack is an anonymous class holding the controller in this$0. */
    private fun captureControllerFromView(view: Any?) {
        if (view == null) return
        try {
            val callback = findFieldOrNull(view.javaClass, "mControlCallBack", quiet = true)
                ?.get(view) ?: return
            val controller = findFieldOrNull(callback.javaClass, $$"this$0", quiet = true)
                ?.get(callback) ?: return
            activeController.set(WeakReference(controller))
        } catch (_: Throwable) {
            // Fallback only; constructor/entry hooks remain the primary capture path.
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
            logE("No active ScreenshotController — take a screenshot first, then click the chip.")
            return false
        }
        val executor = resolveScrollCaptureExecutor(controller) ?: run {
            logE("scroll-capture executor is null on ${controller.javaClass.simpleName}"); return false
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
        val phoneWindow = resolveControllerWindow(controller) ?: run {
            logE("Overlay window is null on ${controller.javaClass.simpleName}"); return false
        }
        val decorView = phoneWindow.javaClass.getMethod("getDecorView").invoke(phoneWindow) as android.view.View
        val windowToken = decorView.windowToken
        if (windowToken == null) {
            logE("Overlay decorView windowToken is null — window not attached?")
            return false
        }
        val displayId = resolveControllerDisplayId(controller)
        logI("Host token=$windowToken displayId=$displayId")

        val wms = findFieldOrNull(clientClass, "mWindowManagerService")?.get(client) ?: run {
            logE("client.mWindowManagerService is null"); return false
        }
        findFieldOrNull(clientClass, "mHostWindowToken")?.set(client, windowToken)

        // --- Step 1: WMS requestScrollCapture --------------------------------
        val response = requestScrollCapture(controller.javaClass.classLoader!!, wms, displayId, windowToken)
            ?: return false
        val connected = isResponseConnected(response)
        if (!connected) {
            val desc = invokeObj(response, "getDescription")
            val title = invokeObj(response, "getWindowTitle")
            logE("ScrollCapture response NOT connected: desc=$desc window=$title")
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
        val captureFuture = newSafeFuture(controller.javaClass.classLoader!!) { _ -> }
        val captureCompleter = extractCompleter(captureFuture)
            ?: run { logE("Cannot obtain capture Completer from SafeFuture"); return false }
        setField(captureControllerClass, captureController, "mCancelled", false)
        setField(captureControllerClass, captureController, "mWindowOwner", packageName)
        setField(captureControllerClass, captureController, "mCaptureCompleter", captureCompleter)
        logI("Stock controller armed: mCancelled=false mWindowOwner=$packageName")

        // --- Step 3: create session (SessionWrapper + ImageReader) -------------
        val cl = controller.javaClass.classLoader!!
        val connection = invokeObj(response, "getConnection") ?: run {
            logE("response.getConnection() is null"); return false
        }
        val wrapperClass = loadClass(cl,
            $$"com.android.systemui.screenshot.scroll.ScrollCaptureClient$SessionWrapper"
        )
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
            wrapper as ImageReader.OnImageAvailableListener,
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
        }
        setField(captureControllerClass, captureController, "mSessionFuture", startFuture)

        val lambdaClass = loadClass(cl,
            $$$"com.android.systemui.screenshot.scroll.ScrollCaptureController$$ExternalSyntheticLambda0"
        )
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

        // --- Step 6: hand off to the stock LongScreenshotActivity --------------
        if (holder != null) {
            try {
                val holderRef = findField(holder.javaClass, "mLongScreenshot").get(holder)
                @Suppress("UNCHECKED_CAST")
                holderRef as AtomicReference<Any>
                holderRef.set(longScreenshot)
                logI("Stored LongScreenshot into longScreenshotHolder.")
            } catch (e: Throwable) {
                logE("Storing LongScreenshot into holder failed (non-fatal)", e)
            }
        }

        // The stock activity consumes holder.mLongScreenshot (getAndSet(null))
        // in onStart and offers the native crop/save/share UI.
        if (launchLongScreenshotActivity(screenshotView, controller, response)) {
            logI("Handed off to LongScreenshotActivity.")
            return true
        }

        // Fallback: export directly if the activity could not be launched.
        logE("LongScreenshotActivity launch failed; falling back to direct MediaStore export.")
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
        return true
    }

    /**
     * Launch the ROM's stock LongScreenshotActivity with the same extras the
     * native transition uses (capture-response + screenshot-userhandle); the
     * long image itself is passed through longScreenshotHolder.
     */
    private fun launchLongScreenshotActivity(
        screenshotView: Any,
        controller: Any,
        response: Any
    ): Boolean {
        val cl = controller.javaClass.classLoader!!
        if (loadClass(cl, LONG_SCREENSHOT_ACTIVITY) == null) {
            logE("LongScreenshotActivity class not found.")
            return false
        }
        if (response !is android.os.Parcelable) {
            logE("ScrollCaptureResponse is not Parcelable (${response.javaClass}); cannot attach to intent.")
            return false
        }
        val viewContext = (screenshotView as android.view.View).context
        val appContext = viewContext.applicationContext
        val intent = Intent()
        intent.setClassName(appContext.packageName, LONG_SCREENSHOT_ACTIVITY)
        intent.putExtra("capture-response", response)
        intent.putExtra("screenshot-userhandle", android.os.Process.myUserHandle())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try {
            appContext.startActivity(intent)
            logI("startActivity(LongScreenshotActivity) ok from app context.")
            true
        } catch (e: Throwable) {
            logE("startActivity from app context failed; retrying with controller window context.", e)
            val controllerContext = resolveControllerContext(controller)
            if (controllerContext == null) {
                logE("No controller context available for LongScreenshotActivity launch.")
                return false
            }
            try {
                controllerContext.startActivity(intent)
                logI("startActivity(LongScreenshotActivity) ok from controller context.")
                true
            } catch (e2: Throwable) {
                logE("startActivity from controller context also failed.", e2)
                false
            }
        }
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
                    cl.loadClass("com.android.systemui.screenshot.scroll.ScrollCaptureClient$1")
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
        }
        return awaitFuture(future, RESPONSE_TIMEOUT_MS, "scroll capture response")
    }

    /**
     * Creates a real SafeFuture via the ROM-bundled CallbackToFutureAdapter so
     * stock code can complete it; [resolverBody] runs inside attachCompleter.
     */
    private fun newSafeFuture(cl: ClassLoader, resolverBody: (Any) -> Unit): Any {
        val adapterClass = cl.loadClass("androidx.concurrent.futures.CallbackToFutureAdapter")
        val resolverClass = cl.loadClass($$"androidx.concurrent.futures.CallbackToFutureAdapter$Resolver")
        val resolver = Proxy.newProxyInstance(cl, arrayOf(resolverClass), InvocationHandler { _, method, args ->
            if (method.name == "attachCompleter" && args != null && args.isNotEmpty()) {
                val completer = args[0] ?: return@InvocationHandler null
                resolverBody(completer)
                return@InvocationHandler "ZTool AospScrollCapture future"
            }
            null
        })
        val getFuture = adapterClass.methods.first { it.name == "getFuture" }
        // Static method: the receiver must be null. SDK 37 stubs annotate the
        // invoke() return as nullable, hence the non-null assertion.
        return getFuture.invoke(null as Any?, resolver as Any) ?: error("getFuture returned null")
    }

    /** SafeFuture holds its Completer only through completerWeakReference. */
    private fun extractCompleter(safeFuture: Any): Any? = try {
        val weakRef = safeFuture.javaClass.getDeclaredField("completerWeakReference")
            .apply { isAccessible = true }.get(safeFuture) as WeakReference<*>
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
        when (val cause = e.cause) {
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
        val completerClass = cl.loadClass($$"androidx.concurrent.futures.CallbackToFutureAdapter$Completer")
        completerClass.getMethod("setException", Throwable::class.java).invoke(completer, e)
    }

    private fun dismissOverlay(screenshotView: Any) {
        try {
            val callback = findField(screenshotView.javaClass, "mControlCallBack").get(screenshotView)
            if (callback == null) {
                logE("mControlCallBack is null; overlay window stays on screen.")
                return
            }
            // dismissWin -> requestDismissal -> animateDismissal starts a
            // ValueAnimator, which is only legal on Looper threads.
            Handler(Looper.getMainLooper()).post {
                try {
                    callback.javaClass.getMethod("dismissWin").invoke(callback)
                    logI("Overlay window dismissed via mControlCallBack.dismissWin().")
                } catch (e: Throwable) {
                    logE("dismissWin invocation failed on main looper; overlay window may remain visible", e)
                }
            }
            logI("dismissWin posted to main looper.")
        } catch (e: Throwable) {
            logE("dismissOverlay preparation failed", e)
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

    /**
     * Swap the disabled long-press icon back to the normal one. The drawables
     * live in SystemUI's own resources, so resolve by identifier.
     */
    @SuppressLint("DiscouragedApi")
    private fun restoreChipIcon(chipView: android.view.View) {
        try {
            val resId = chipView.context.resources.getIdentifier(
                "screenshot_long_press", "drawable", chipView.context.packageName)
            if (resId == 0) {
                logE("screenshot_long_press drawable not found; icon left unchanged.")
                return
            }
            chipView.javaClass.getMethod("setIcon", Int::class.javaPrimitiveType)
                .invoke(chipView, resId)
            logI("Chip icon restored to screenshot_long_press.")
        } catch (e: Throwable) {
            logE("Restoring chip icon failed (non-fatal)", e)
        }
    }

    private fun mainExecutor(controller: Any): java.util.concurrent.Executor {
        val context = resolveControllerContext(controller)
            ?: error("controller context missing")
        return context.mainExecutor
    }

    private fun findFieldOrNull(origin: Class<*>, name: String, quiet: Boolean = false): Field? = try {
        findField(origin, name)
    } catch (e: Throwable) {
        if (quiet) logger.debug("AospScroll: no field ${origin.simpleName}.$name")
        else logE("Field not found: ${origin.simpleName}.$name", e)
        null
    }

    private fun resolveScrollCaptureExecutor(controller: Any): Any? =
        findFieldOrNull(controller.javaClass, "mScrollCaptureExecutor", quiet = true)?.get(controller)
            ?: findFieldOrNull(controller.javaClass, "scrollCaptureExecutor", quiet = true)?.get(controller)

    private fun resolveControllerWindow(controller: Any): Any? {
        findFieldOrNull(controller.javaClass, "mWindow", quiet = true)?.get(controller)?.let { return it }
        val holder = findFieldOrNull(controller.javaClass, "window", quiet = true)?.get(controller)
            ?: return null
        return findFieldOrNull(holder.javaClass, "window", quiet = true)?.get(holder)
    }

    private fun resolveControllerDisplayId(controller: Any): Int {
        findFieldOrNull(controller.javaClass, "mDisplayId", quiet = true)?.let { field ->
            return runCatching { field.getInt(controller) }.getOrDefault(0)
        }
        val display = findFieldOrNull(controller.javaClass, "display", quiet = true)?.get(controller)
            ?: return 0
        return runCatching {
            display.javaClass.getMethod("getDisplayId").invoke(display) as Int
        }.getOrDefault(0)
    }

    private fun resolveControllerContext(controller: Any): android.content.Context? {
        for (name in arrayOf("mContext", "context", "appContext")) {
            val field = findFieldOrNull(controller.javaClass, name, quiet = true) ?: continue
            (field.get(controller) as? android.content.Context)?.let { return it }
        }
        return null
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

    private fun isResponseConnected(target: Any): Boolean =
        target.javaClass.methods.first { it.name == "isConnected" && it.parameterCount == 0 }
            .invoke(target) as Boolean

    private fun invokeObj(target: Any, name: String): Any? =
        target.javaClass.methods.first { it.name == name && it.parameterCount == 0 }
            .invoke(target)

    private fun logI(message: String) {
        logger.info("AospScroll: $message")
    }

    private fun logE(message: String, e: Throwable? = null) {
        if (e != null) logger.error("AospScroll: $message", e) else logger.error("AospScroll: $message")
    }
}
