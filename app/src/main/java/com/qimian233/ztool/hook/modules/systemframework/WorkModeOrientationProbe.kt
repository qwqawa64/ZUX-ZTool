package com.qimian233.ztool.hook.modules.systemframework

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger

/**
 * Probe and opt-in experiment for ZUI PC/work-mode orientation.
 *
 * Logs PC-mode force-orientation, launch-param and display-rotation decisions.
 * When `debug.ztool.pc_portrait` or `persist.sys.ztool.pc_portrait` is `1`,
 * rotation-90 locks are rewritten to FREE and the PC-mode landscape override is
 * bypassed, so the work desktop can follow the sensor.
 * The module reports as `test_hook`, so it is always enabled while installed.
 */
class WorkModeOrientationProbe : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.TEST_HOOK.name

    override fun getTargetPackages(): Array<String> =
        arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    private val landscapeLogCount = AtomicInteger(0)

    private val launchLogCount = AtomicInteger(0)

    private val overrideLogCount = AtomicInteger(0)

    private val overrideShortCircuitLogs = AtomicInteger(0)

    private val systemPropertiesGet: Method? by lazy {
        runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
        }.getOrNull()
    }

    @Volatile
    private var pcMode = false

    @Volatile
    private var pcModeCheckedAt = 0L

    @Volatile
    private var portraitOverrideActive = false

    @Volatile
    private var portraitOverrideCheckedAt = 0L

    @Throws(Throwable::class)
    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        logger.info("WorkModeOrientationProbe installing")
        hookPcModeForceOrientation(classLoader)
        hookWindowContainerOrientation(classLoader)
        hookDisplayRotation(classLoader)
        logger.info("WorkModeOrientationProbe installed")
    }

    private fun hookPcModeForceOrientation(classLoader: ClassLoader) {
        try {
            val recordClass = classLoader.loadClass("com.android.server.wm.ActivityRecord")
            val controllerClass = classLoader.loadClass("com.android.server.wm.OVDesktopController")
            val internalClass = classLoader.loadClass(
                "com.android.server.wm.OVDesktopController\$OVDesktopControllerInternal")

            val byRecord = findMethod(
                controllerClass, "getPCModeAppForceOrientation", recordClass)
            hookWithId(byRecord, "probe_pc_force_orientation_record") { chain ->
                val result = chain.proceed()
                logger.info(
                    "PCModeForceOrientation(${describe(chain.args[0])}) -> " +
                        orientationName((result as? Int) ?: UNSPECIFIED) + "\n" + callerStack()
                )
                result
            }

            val byPackage = findMethod(
                internalClass, "getPCModeAppForceOrientation", String::class.java)
            hookWithId(byPackage, "probe_pc_force_orientation_package") { chain ->
                val result = chain.proceed()
                logger.info(
                    "PCModeForceOrientation(pkg=${chain.args[0]}) -> " +
                        orientationName((result as? Int) ?: UNSPECIFIED) + "\n" + callerStack()
                )
                result
            }

            val modifierClass = classLoader.loadClass(
                "com.android.server.wm.TaskLaunchParamsModifier")
            val resolveOrientation = findMethod(
                modifierClass, "resolveOrientation", recordClass)
            hookWithId(resolveOrientation, "probe_resolve_orientation") { chain ->
                val result = chain.proceed()
                val orientation = result as? Int
                if (orientation == LANDSCAPE) {
                    val count = launchLogCount.incrementAndGet()
                    if (count <= MAX_LAUNCH_LOGS) {
                        logger.info(
                            "resolveOrientation(${describe(chain.args[0])}) -> LANDSCAPE(0)\n" +
                                callerStack()
                        )
                    } else if (count == MAX_LAUNCH_LOGS + 1) {
                        logger.warn(
                            "resolveOrientation probe limit ($MAX_LAUNCH_LOGS) reached, " +
                                "further logs suppressed"
                        )
                    }
                }
                result
            }
        } catch (t: Throwable) {
            logger.error("Probe: PC-mode orientation hooks not installed", t)
        }
    }

    private fun hookWindowContainerOrientation(classLoader: ClassLoader) {
        try {
            val containerClass = classLoader.loadClass("com.android.server.wm.WindowContainer")
            val setOrientation = findMethod(
                containerClass, "setOrientation",
                Int::class.javaPrimitiveType, containerClass)
            val getOverrideOrientation = findMethod(
                containerClass, "getOverrideOrientation")
            val overrideOrientationField = findField(
                containerClass, "mOverrideOrientation")
            val setOverrideOrientation = findMethod(
                containerClass, "setOverrideOrientation", Int::class.javaPrimitiveType)

            hookWithId(getOverrideOrientation, "probe_get_override_orientation") { chain ->
                if (isPortraitOverrideActive()) {
                    val self = chain.thisObject
                    if (self == null) {
                        return@hookWithId chain.proceed()
                    }
                    val raw = overrideOrientationField.getInt(self)
                    if (raw != LANDSCAPE &&
                        overrideShortCircuitLogs.incrementAndGet() <= 5
                    ) {
                        logger.info(
                            "Portrait experiment: getOverrideOrientation on " +
                                "${describe(self)} -> ${orientationName(raw)}"
                        )
                    }
                    return@hookWithId raw
                }
                chain.proceed()
            }

            hookWithId(setOverrideOrientation, "probe_set_override_orientation") { chain ->
                val orientation = chain.args[0] as Int
                if (orientation == LANDSCAPE && isPcMode()) {
                    val count = overrideLogCount.incrementAndGet()
                    if (count <= MAX_OVERRIDE_LOGS) {
                        logger.info(
                            "setOverrideOrientation(LANDSCAPE) on " +
                                "${describe(chain.thisObject)}\n" + callerStack()
                        )
                    } else if (count == MAX_OVERRIDE_LOGS + 1) {
                        logger.warn(
                            "setOverrideOrientation probe limit ($MAX_OVERRIDE_LOGS) reached, " +
                                "further logs suppressed"
                        )
                    }
                }
                chain.proceed()
            }

            hookWithId(setOrientation, "probe_window_set_orientation") { chain ->
                val orientation = chain.args[0] as Int
                val source = chain.args[1]
                val self = chain.thisObject
                if (orientation == LANDSCAPE) {
                    val current = runCatching {
                        getOverrideOrientation.invoke(self) as Int
                    }.getOrDefault(UNSPECIFIED)
                    if (current != LANDSCAPE) {
                        val count = landscapeLogCount.incrementAndGet()
                        if (count <= MAX_LANDSCAPE_LOGS) {
                            logger.info(
                                "setOrientation(LANDSCAPE) on ${describe(self)}, " +
                                    "source=${describe(source)}\n" + callerStack()
                            )
                        } else if (count == MAX_LANDSCAPE_LOGS + 1) {
                            logger.warn(
                                "setOrientation probe limit ($MAX_LANDSCAPE_LOGS) reached, " +
                                    "further landscape logs suppressed"
                            )
                        }
                    }
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("Probe: WindowContainer.setOrientation hook not installed", t)
        }
    }

    private fun hookDisplayRotation(classLoader: ClassLoader) {
        try {
            val wmServiceClass = classLoader.loadClass(
                "com.android.server.wm.WindowManagerService")
            val freeze = findMethod(
                wmServiceClass, "freezeDisplayRotation",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java)
            hookWithId(freeze, "probe_freeze_display_rotation") { chain ->
                val rotation = chain.args[1] as Int
                logger.info(
                    "freezeDisplayRotation(display=${chain.args[0]}, " +
                        "rotation=${rotationName(rotation)}, " +
                        "reason=${chain.args[2]})\n" + callerStack()
                )
                if (isPortraitExperimentEnabled() && rotation == ROTATION_90) {
                    logger.warn(
                        "Portrait experiment: freeze at ROTATION_90, will rewrite the " +
                            "resulting LOCKED user rotation to FREE " +
                            "(reason=${chain.args[2]})"
                    )
                }
                chain.proceed()
            }

            val thaw = findMethod(
                wmServiceClass, "thawDisplayRotation",
                Int::class.javaPrimitiveType, String::class.java)
            hookWithId(thaw, "probe_thaw_display_rotation") { chain ->
                logger.info(
                    "thawDisplayRotation(display=${chain.args[0]}, " +
                        "reason=${chain.args[1]})"
                )
                chain.proceed()
            }

            val displayRotationClass = classLoader.loadClass(
                "com.android.server.wm.DisplayRotation")
            val setUserRotation = findMethod(
                displayRotationClass, "setUserRotation",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java)
            hookWithId(setUserRotation, "probe_set_user_rotation") { chain ->
                val mode = chain.args[0] as Int
                val rotation = chain.args[1] as Int
                logger.info(
                    "setUserRotation(mode=$mode, " +
                        "rotation=${rotationName(rotation)}, " +
                        "reason=${chain.args[2]})"
                )
                if (isPortraitExperimentEnabled() &&
                    mode == USER_ROTATION_LOCKED && rotation == ROTATION_90
                ) {
                    logger.warn(
                        "Portrait experiment: rewrote setUserRotation(LOCKED, ROTATION_90) " +
                            "to FREE"
                    )
                    return@hookWithId chain.proceed(
                        arrayOf(USER_ROTATION_FREE, rotation, chain.args[2])
                    )
                }
                chain.proceed()
            }

            val animationClass = classLoader.loadClass(
                "com.android.server.wm.PcModeBlackAnimation")
            val fixOrientation = findMethod(
                animationClass, "fixOrientation", Boolean::class.javaPrimitiveType)
            hookWithId(fixOrientation, "probe_fix_orientation") { chain ->
                logger.info("PcModeBlackAnimation.fixOrientation(entering=${chain.args[0]})")
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("Probe: display-rotation hooks not installed", t)
        }
    }

    private fun systemProperty(key: String): String? =
        runCatching { systemPropertiesGet?.invoke(null, key) as? String }.getOrNull()

    private fun isPcMode(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - pcModeCheckedAt > 2000L) {
            pcMode = systemProperty("persist.sys.zui.pcmode") == "1"
            pcModeCheckedAt = now
        }
        return pcMode
    }

    private fun isPortraitExperimentEnabled(): Boolean =
        systemProperty("debug.ztool.pc_portrait") == "1" ||
            systemProperty("persist.sys.ztool.pc_portrait") == "1"

    private fun isPortraitOverrideActive(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - portraitOverrideCheckedAt > 2000L) {
            portraitOverrideActive = isPortraitExperimentEnabled()
            portraitOverrideCheckedAt = now
        }
        return portraitOverrideActive
    }

    private fun describe(target: Any?): String {
        if (target == null) return "null"
        val text = runCatching { target.toString() }.getOrDefault("")
        val trimmed = if (text.length > 160) text.take(160) + "…" else text
        return "${target.javaClass.simpleName}($trimmed)"
    }

    private fun callerStack(): String {
        val frames = Throwable().stackTrace
            .filter { frame ->
                val name = frame.className
                (name.startsWith("com.android.server.wm") || name.startsWith("com.zui.")) &&
                    !name.contains("WorkModeOrientationProbe")
            }
            .take(8)
            .joinToString("\n        ") {
                "at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})"
            }
        return if (frames.isEmpty()) "        <no framework frame>" else "    $frames"
    }

    private fun orientationName(value: Int): String = when (value) {
        -2 -> "UNSET(-2)"
        UNSPECIFIED -> "UNSPECIFIED(-1)"
        LANDSCAPE -> "LANDSCAPE(0)"
        1 -> "PORTRAIT(1)"
        2 -> "USER(2)"
        3 -> "BEHIND(3)"
        4 -> "SENSOR(4)"
        5 -> "NOSENSOR(5)"
        6 -> "SENSOR_LANDSCAPE(6)"
        7 -> "SENSOR_PORTRAIT(7)"
        8 -> "REVERSE_LANDSCAPE(8)"
        9 -> "REVERSE_PORTRAIT(9)"
        10 -> "FULL_SENSOR(10)"
        11 -> "USER_LANDSCAPE(11)"
        12 -> "USER_PORTRAIT(12)"
        13 -> "FULL_USER(13)"
        14 -> "LOCKED(14)"
        else -> "orientation($value)"
    }

    private fun rotationName(value: Int): String = when (value) {
        0 -> "ROTATION_0"
        1 -> "ROTATION_90"
        2 -> "ROTATION_180"
        3 -> "ROTATION_270"
        else -> "rotation($value)"
    }

    private companion object {
        const val LANDSCAPE = 0
        const val UNSPECIFIED = -1
        const val ROTATION_90 = 1
        const val USER_ROTATION_LOCKED = 1
        const val USER_ROTATION_FREE = 0
        const val MAX_LANDSCAPE_LOGS = 120
        const val MAX_LAUNCH_LOGS = 200
        const val MAX_OVERRIDE_LOGS = 100
    }
}
