package com.qimian233.ztool.hook.modules.systemframework

import android.content.pm.ActivityInfo
import android.os.SystemClock
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lets ZUI's PC/work desktop follow the device orientation.
 *
 * System server pins it to landscape through the PC-mode rotation lock, the
 * WindowContainer/ActivityRecord orientation overrides (including the desktop
 * AppCompat policy), the launch-bounds force orientation, and the DisplayContent
 * shortcut. Each is bypassed while enabled; apps with a fixed orientation keep it.
 */
class WorkModePortrait : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.WORK_MODE_PORTRAIT.name

    override fun getTargetPackages(): Array<String> =
        arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    @Volatile
    private var featureEnabled = false

    @Volatile
    private var featureCheckedAt = 0L

    @Volatile
    private var pcMode = false

    @Volatile
    private var pcModeCheckedAt = 0L

    private val probeLogs = AtomicInteger(0)

    @Volatile
    private var lastFeatureState: Boolean? = null

    private var infoField: Field? = null

    private var componentField: Field? = null

    private var pcLauncherPackages: Set<String> = emptySet()

    private val systemPropertiesGet: Method? by lazy {
        runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
        }.getOrNull()
    }

    @Throws(Throwable::class)
    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        logger.info("WorkModePortrait installing")
        try {
            val recordClass = classLoader.loadClass("com.android.server.wm.ActivityRecord")
            infoField = findField(recordClass, "info")
            componentField = findField(recordClass, "mActivityComponent")
            pcLauncherPackages = loadPcLauncherPackages(classLoader)
        } catch (t: Throwable) {
            logger.error("WorkModePortrait: ActivityRecord lookup failed", t)
        }
        hookUserRotation(classLoader)
        hookWindowOrientation(classLoader)
        hookDisplayOrientation(classLoader)
        hookPcModeForceOrientation(classLoader)
        logger.info("WorkModePortrait installed, launcher=$pcLauncherPackages")
    }

    private fun hookPcModeForceOrientation(classLoader: ClassLoader) {
        try {
            val internalClass = classLoader.loadClass(
                "com.android.server.wm.OVDesktopController\$OVDesktopControllerInternal")
            val getForceOrientation = findMethod(
                internalClass, "getPCModeAppForceOrientation", String::class.java)
            hookWithId(
                getForceOrientation,
                "work_mode_portrait_force_orientation"
            ) { chain ->
                val result = chain.proceed()
                try {
                    val packageName = chain.args[0] as? String
                    if (isFeatureEnabled() && packageName != null &&
                        packageName in pcLauncherPackages
                    ) {
                        if (probeLogs.incrementAndGet() <= MAX_PROBE_LOGS) {
                            logger.debug(
                                "suppressed PC force orientation " +
                                    "$result for $packageName"
                            )
                        }
                        return@hookWithId UNSPECIFIED
                    }
                } catch (t: Throwable) {
                    logger.error(
                        "WorkModePortrait: force orientation hook failed", t)
                }
                result
            }
        } catch (t: Throwable) {
            logger.error(
                "WorkModePortrait: force orientation hook not installed", t)
        }
    }

    private fun hookUserRotation(classLoader: ClassLoader) {
        try {
            val displayRotationClass = classLoader.loadClass(
                "com.android.server.wm.DisplayRotation")
            val setUserRotation = findMethod(
                displayRotationClass, "setUserRotation",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java)
            hookWithId(setUserRotation, "work_mode_portrait_user_rotation") { chain ->
                val mode = chain.args[0] as Int
                val rotation = chain.args[1] as Int
                val reason = chain.args[2] as? String
                val pcEntry = reason?.contains("PcMode") == true
                if (isFeatureEnabled() && mode == USER_ROTATION_LOCKED &&
                    rotation == ROTATION_90 && (pcEntry || isPcMode())
                ) {
                    logger.debug("Releasing PC-mode rotation lock (reason=$reason)")
                    return@hookWithId chain.proceed(
                        arrayOf<Any?>(USER_ROTATION_FREE, rotation, reason))
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("WorkModePortrait: rotation lock hook not installed", t)
        }
    }

    private fun hookWindowOrientation(classLoader: ClassLoader) {
        try {
            val containerClass = classLoader.loadClass(
                "com.android.server.wm.WindowContainer")
            val recordClass = classLoader.loadClass(
                "com.android.server.wm.ActivityRecord")
            val getOverrideOrientation = findMethod(
                containerClass, "getOverrideOrientation")
            val activityGetOverride = findMethod(
                recordClass, "getOverrideOrientation")
            val setOverrideOrientation = findMethod(
                containerClass, "setOverrideOrientation", Int::class.javaPrimitiveType)
            val overrideOrientationField = findField(
                containerClass, "mOverrideOrientation")

            hookWithId(
                getOverrideOrientation,
                "work_mode_portrait_window_orientation"
            ) { chain ->
                try {
                    val self = chain.thisObject
                    if (self != null && isFeatureEnabled()) {
                        if (isPcLauncherRecord(self)) {
                            // Clear the record's own sensorLandscape so launch bounds and
                            // size-compat paths see an unspecified orientation too.
                            if (overrideOrientationField.getInt(self) != UNSPECIFIED) {
                                overrideOrientationField.setInt(self, UNSPECIFIED)
                                logger.debug(
                                    "launcher orientation forced unspecified: " +
                                        recordInfo(self)?.name
                                )
                            }
                            return@hookWithId UNSPECIFIED
                        }
                        if (isPcMode()) {
                            return@hookWithId overrideOrientationField.getInt(self)
                        }
                    }
                } catch (t: Throwable) {
                    logger.error(
                        "WorkModePortrait: getOverrideOrientation hook failed", t)
                }
                chain.proceed()
            }

            hookWithId(
                activityGetOverride,
                "work_mode_portrait_activity_orientation"
            ) { chain ->
                val result = chain.proceed()
                try {
                    val self = chain.thisObject
                    if (self != null && isFeatureEnabled() && isPcMode()) {
                        val info = recordInfo(self)
                        val manifest = info?.screenOrientation ?: UNSPECIFIED
                        if (manifest < 0 || isPcLauncherRecord(self)) {
                            if (probeLogs.incrementAndGet() <= MAX_PROBE_LOGS) {
                                logger.debug(
                                    "override -> UNSPECIFIED for " +
                                        "${info?.name} (was $result)"
                                )
                            }
                            return@hookWithId UNSPECIFIED
                        }
                    }
                } catch (t: Throwable) {
                    logger.error(
                        "WorkModePortrait: activity orientation hook failed", t)
                }
                result
            }

            hookWithId(
                setOverrideOrientation,
                "work_mode_portrait_set_override"
            ) { chain ->
                val value = chain.args[0] as? Int
                try {
                    val self = chain.thisObject
                    if (self != null && isFeatureEnabled() && value != null &&
                        value != UNSPECIFIED && isPcLauncherRecord(self)
                    ) {
                        overrideOrientationField.setInt(self, UNSPECIFIED)
                        logger.debug(
                            "cleared initial orientation $value for " +
                                (recordInfo(self)?.name ?: recordPackage(self))
                        )
                        return@hookWithId null
                    }
                } catch (t: Throwable) {
                    logger.error("WorkModePortrait: set override hook failed", t)
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("WorkModePortrait: window orientation hook not installed", t)
        }
    }

    private fun hookDisplayOrientation(classLoader: ClassLoader) {
        try {
            val displayContentClass = classLoader.loadClass(
                "com.android.server.wm.DisplayContent")
            val getOrientation = findMethod(displayContentClass, "getOrientation")
            val topRunningActivity = findMethod(
                displayContentClass, "topRunningActivity",
                Boolean::class.javaPrimitiveType)

            hookWithId(
                getOrientation,
                "work_mode_portrait_display_orientation"
            ) { chain ->
                val result = chain.proceed()
                if (!isFeatureEnabled() || !isPcMode() ||
                    (result as? Int) != LANDSCAPE
                ) {
                    return@hookWithId result
                }
                val top = chain.thisObject?.let { display ->
                    runCatching {
                        topRunningActivity.invoke(display, true)
                    }.getOrNull()
                }
                val info = top?.let { record ->
                    runCatching {
                        infoField?.get(record) as? ActivityInfo
                    }.getOrNull()
                }
                val manifestOrientation = info?.screenOrientation ?: UNSPECIFIED
                val topPackage = info?.packageName
                if (manifestOrientation < 0 ||
                    (topPackage != null && topPackage in pcLauncherPackages)
                ) {
                    return@hookWithId UNSPECIFIED
                }
                result
            }
        } catch (t: Throwable) {
            logger.error("WorkModePortrait: display orientation hook not installed", t)
        }
    }

    private fun recordInfo(record: Any): ActivityInfo? {
        val field = infoField ?: return null
        return runCatching { field.get(record) as? ActivityInfo }.getOrNull()
    }

    private fun recordPackage(record: Any): String? {
        recordInfo(record)?.packageName?.let { return it }
        val field = componentField ?: return null
        return runCatching {
            (field.get(record) as? android.content.ComponentName)?.packageName
        }.getOrNull()
    }

    private fun isPcLauncherRecord(record: Any): Boolean {
        val packageName = recordPackage(record) ?: return false
        return packageName in pcLauncherPackages
    }

    private fun loadPcLauncherPackages(classLoader: ClassLoader): Set<String> {
        return runCatching {
            val controllerClass = classLoader.loadClass(
                "com.android.server.wm.OVDesktopController")
            listOf(
                "ZUI_DESKTOP_LAUNCHER_PACKAGE_NAME",
                "ZUI_EXTEND_DESKTOP_LAUNCHER_PACKAGE_NAME"
            ).mapNotNull { name ->
                runCatching {
                    findField(controllerClass, name).get(null) as? String
                }.getOrNull()
            }.filter { it.isNotEmpty() }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun isFeatureEnabled(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - featureCheckedAt > CACHE_TTL_MS) {
            featureEnabled = runCatching {
                remotePreferences.getBoolean(
                    PreferenceKeys.WORK_MODE_PORTRAIT.name,
                    PreferenceKeys.WORK_MODE_PORTRAIT.default
                )
            }.getOrDefault(false)
            if (lastFeatureState != featureEnabled) {
                lastFeatureState = featureEnabled
                logger.debug("feature enabled=$featureEnabled")
            }
            featureCheckedAt = now
        }
        return featureEnabled
    }

    private fun isPcMode(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - pcModeCheckedAt > CACHE_TTL_MS) {
            pcMode = systemProperty("persist.sys.zui.pcmode") == "1"
            pcModeCheckedAt = now
        }
        return pcMode
    }

    private fun systemProperty(key: String): String? =
        runCatching { systemPropertiesGet?.invoke(null, key) as? String }.getOrNull()

    private companion object {
        const val LANDSCAPE = 0
        const val UNSPECIFIED = -1
        const val ROTATION_90 = 1
        const val USER_ROTATION_LOCKED = 1
        const val USER_ROTATION_FREE = 0
        const val MAX_PROBE_LOGS = 40
        const val CACHE_TTL_MS = 2000L
    }
}
