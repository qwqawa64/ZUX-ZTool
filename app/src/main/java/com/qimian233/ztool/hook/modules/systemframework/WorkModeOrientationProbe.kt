package com.qimian233.ztool.hook.modules.systemframework

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.util.concurrent.atomic.AtomicInteger

/**
 * Read-only probe for ZUI PC/work-mode orientation decisions.
 *
 * Logs the launch-param and window-container orientation choices plus the
 * display-rotation freezes so the caller that forces landscape can be found.
 * It never changes behaviour. The module reports as `test_hook`, so it is
 * always enabled while installed.
 */
class WorkModeOrientationProbe : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.TEST_HOOK.name

    override fun getTargetPackages(): Array<String> =
        arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    private val landscapeLogCount = AtomicInteger(0)

    private val launchLogCount = AtomicInteger(0)

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
                logger.info(
                    "freezeDisplayRotation(display=${chain.args[0]}, " +
                        "rotation=${rotationName(chain.args[1] as Int)}, " +
                        "reason=${chain.args[2]})\n" + callerStack()
                )
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
                logger.info(
                    "setUserRotation(mode=${chain.args[0]}, " +
                        "rotation=${rotationName(chain.args[1] as Int)}, " +
                        "reason=${chain.args[2]})"
                )
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
        const val MAX_LANDSCAPE_LOGS = 120
        const val MAX_LAUNCH_LOGS = 200
    }
}
