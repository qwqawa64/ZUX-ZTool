package com.qimian233.ztool.hook.modules.desktoplauncher

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger

/**
 * Read-only diagnostic probe for the ZUI work desktop (`com.zui.desktoplauncher`).
 *
 * Dumps the resolved work grid, the `DeviceProfile` every window bound is measured with, the PC
 * all-apps grid, and whether the bottom bar is the launcher taskbar or the hotseat. Gated by
 * [PreferenceKeys.TEST_HOOK] so it needs no frontend wiring; replace it with a real key when the
 * portrait feature lands.
 *
 * Findings: docs/research/zui_work_mode_desktop_hook_survey.md
 */
class WorkDesktopProbe : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.TEST_HOOK.name

    override fun getTargetPackages(): Array<String> =
        arrayOf(ScopeKeys.DESKTOP_LAUNCHER.packageName)

    private var systemPropertiesGet: Method? = null

    private val profileSequence = AtomicInteger(0)

    private val profileGate = Gate(MAX_PROFILE_LINES)

    private val environmentGate = Gate(MAX_ENVIRONMENT_LINES)

    private val displayInfoGate = Gate(MAX_DISPLAY_INFO_LINES)

    private val orientationGate = Gate(MAX_ORIENTATION_LINES)

    private val configGate = Gate(MAX_CONFIG_LINES)

    private val allAppsGate = Gate(MAX_ALL_APPS_LINES)

    private val taskbarGate = Gate(MAX_TASKBAR_LINES)

    private val hotseatGate = Gate(MAX_HOTSEAT_LINES)

    private val hierarchyDumped = java.util.concurrent.atomic.AtomicBoolean(false)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        logger.info("WorkDesktopProbe attaching to ${param.packageName}")
        systemPropertiesGet = runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
        }.getOrNull()
        probeDeviceProfile(classLoader)
        probeActivities(classLoader)
        probeActivityConfiguration(classLoader)
        probeRequestedOrientation(classLoader)
        probeAllApps(classLoader)
        probeTaskbar(classLoader)
        probeHotseat(classLoader)
    }

    /**
     * Hooks the widest `DeviceProfile` constructor and dumps every instance it produces.
     *
     * The constructor runs once per supported window bound on each profile rebuild, so the log
     * shows the portrait and landscape profiles of the same display side by side.
     */
    private fun probeDeviceProfile(classLoader: ClassLoader) {
        runCatching {
            val profileClass = classLoader.loadClass(PROFILE_CLASS)
            val constructor = profileClass.declaredConstructors
                .maxByOrNull { it.parameterCount }
                ?: error("no DeviceProfile constructor")
            constructor.isAccessible = true
            val reflector = Reflector(
                fields = resolveFields(profileClass, PROFILE_FIELDS),
                methods = resolveMethods(profileClass, PROFILE_METHODS),
                nestedFields = resolveFields(classLoader.loadClass(IDP_CLASS), IDP_FIELDS)
            )
            val displayInfo = DisplayInfoProbe(
                infoFields = resolveFields(classLoader.loadClass(DISPLAY_INFO_CLASS), INFO_FIELDS),
                boundsFields = resolveFields(
                    classLoader.loadClass(WINDOW_BOUNDS_CLASS), WINDOW_BOUNDS_FIELDS),
                isLandscape = runCatching {
                    findMethod(classLoader.loadClass(WINDOW_BOUNDS_CLASS), "isLandscape")
                }.getOrNull(),
                supportedBounds = runCatching {
                    findMethod(classLoader.loadClass(DISPLAY_INFO_CLASS), "getSupportedBounds")
                }.getOrNull()
            )
            val toSmallString = reflector.method("toSmallString")
            hookWithId(constructor, "probe_device_profile") { chain ->
                chain.proceed()
                runCatching {
                    val profile = chain.thisObject ?: return@runCatching
                    val sequence = profileSequence.incrementAndGet()
                    val line = "#$sequence " + describeProfile(reflector, profile, toSmallString)
                    if (profileGate.shouldLog(line)) logger.info("profile $line")
                    val context = chain.args[0] as? Context
                    if (context != null) {
                        val environment = describeEnvironment(context)
                        if (environmentGate.shouldLog(environment)) {
                            logger.info("environment $environment")
                        }
                    }
                    val info = reflector.call(profile, "getDisplayInfo")
                    if (info != null) {
                        val infoLine = "#$sequence " + displayInfo.describe(info)
                        if (displayInfoGate.shouldLog(infoLine)) {
                            logger.info("displayinfo $infoLine")
                        }
                    }
                }.onFailure { logger.error("probe: DeviceProfile dump failed", it) }
                null
            }
        }.onFailure { logger.error("probe: DeviceProfile hook not installed", it) }
    }

    /** Logs work-desktop and all-apps activity starts so every later line has a context. */
    private fun probeActivities(classLoader: ClassLoader) {
        listOf(WORK_LAUNCHER_CLASS, PC_ALL_APPS_ACTIVITY_CLASS).forEach { className ->
            runCatching {
                val activityClass = classLoader.loadClass(className)
                val onCreate = findMethod(activityClass, "onCreate", Bundle::class.java)
                hookWithId(onCreate, "probe_activity_${activityClass.simpleName}") { chain ->
                    chain.proceed()
                    runCatching {
                        val window = (chain.thisObject as? android.app.Activity)?.window
                        val params = window?.attributes
                        logger.info(
                            "activity ${activityClass.simpleName}.onCreate" +
                                " window=${params?.width}x${params?.height}" +
                                " gravity=${params?.gravity}"
                        )
                    }.onFailure { logger.error("probe: activity window dump failed", it) }
                    null
                }
            }.onFailure {
                logger.warn("probe: no onCreate on $className (${it.javaClass.simpleName})")
            }
        }
    }

    /** Logs configuration changes, so a rotation can be matched against profile rebuilds. */
    private fun probeActivityConfiguration(classLoader: ClassLoader) {
        runCatching {
            val activityClass = classLoader.loadClass(ACTIVITY_CLASS)
            val onConfigurationChanged = findMethod(
                activityClass, "onConfigurationChanged",
                android.content.res.Configuration::class.java)
            hookWithId(onConfigurationChanged, "probe_activity_config") { chain ->
                chain.proceed()
                runCatching {
                    val config = chain.args[0] as? android.content.res.Configuration
                    val line = "${chain.thisObject?.javaClass?.simpleName}" +
                        " orientation=${config?.orientation}" +
                        " size=${config?.screenWidthDp}x${config?.screenHeightDp}dp"
                    if (configGate.shouldLog(line)) logger.info("config $line")
                }.onFailure { logger.error("probe: config dump failed", it) }
                null
            }
        }.onFailure { logger.error("probe: onConfigurationChanged hook not installed", it) }
    }

    /** Logs every orientation request made by this process, to find who re-locks landscape. */
    private fun probeRequestedOrientation(classLoader: ClassLoader) {
        runCatching {
            val activityClass = classLoader.loadClass(ACTIVITY_CLASS)
            val setRequested = findMethod(
                activityClass, "setRequestedOrientation", Int::class.javaPrimitiveType)
            val getRequested = runCatching {
                findMethod(activityClass, "getRequestedOrientation")
            }.getOrNull()
            hookWithId(setRequested, "probe_requested_orientation") { chain ->
                runCatching {
                    val activity = chain.thisObject
                    val current = runCatching { getRequested?.invoke(activity) }.getOrNull()
                    val line = "${activity?.javaClass?.simpleName} requested=${chain.args[0]}" +
                        " (was $current)"
                    if (orientationGate.shouldLog(line)) logger.info("orientation $line")
                }.onFailure { logger.error("probe: orientation dump failed", it) }
                chain.proceed()
            }
        }.onFailure { logger.error("probe: setRequestedOrientation hook not installed", it) }
    }

    /** Dumps the PC all-apps grid decided by `PcAllAppsPagedView.setupContentDimensions`. */
    private fun probeAllApps(classLoader: ClassLoader) {
        runCatching {
            val pagedViewClass = classLoader.loadClass(PC_ALL_APPS_VIEW_CLASS)
            val setup = findMethod(
                pagedViewClass, "setupContentDimensions", Int::class.javaPrimitiveType)
            val reflector = Reflector(
                fields = emptyMap(),
                methods = resolveMethods(pagedViewClass, ALL_APPS_METHODS)
            )
            hookWithId(setup, "probe_pc_all_apps_grid") { chain ->
                chain.proceed()
                runCatching {
                    val view = chain.thisObject ?: return@runCatching
                    val measured = view as? View
                    val line = "cols=${reflector.call(view, "getCellCountX")}" +
                        " rows=${reflector.call(view, "getCellCountY")}" +
                        " perPage=${reflector.call(view, "itemsPerPage")}" +
                        " items=${reflector.call(view, "getItemCount")}" +
                        " count=${chain.args[0]}" +
                        " view=${measured?.width}x${measured?.height}"
                    if (allAppsGate.shouldLog(line)) logger.info("allapps $line")
                }.onFailure { logger.error("probe: all-apps dump failed", it) }
                null
            }
        }.onFailure { logger.error("probe: all-apps hook not installed", it) }
    }

    /**
     * Decides which component owns the bottom bar: logs the live taskbar context and the
     * `LayoutParams` the taskbar window is created with.
     */
    private fun probeTaskbar(classLoader: ClassLoader) {
        runCatching {
            val managerClass = classLoader.loadClass(TASKBAR_MANAGER_CLASS)
            val recreate = runCatching {
                findMethod(managerClass, "recreateTaskbar")
            }.getOrNull()
            val currentContext = runCatching {
                findMethod(managerClass, "getCurrentActivityContext")
            }.getOrNull()
            if (recreate != null) {
                hookWithId(recreate, "probe_taskbar_recreate") { chain ->
                    chain.proceed()
                    runCatching {
                        val context = currentContext?.invoke(chain.thisObject)
                        val line = "recreateTaskbar context=${context?.javaClass?.simpleName}"
                        if (taskbarGate.shouldLog(line)) logger.info("taskbar $line")
                    }.onFailure { logger.error("probe: taskbar context dump failed", it) }
                    null
                }
            }
        }.onFailure { logger.error("probe: TaskbarManager hook not installed", it) }

        runCatching {
            val contextClass = classLoader.loadClass(TASKBAR_CONTEXT_CLASS)
            val createParams = findMethod(
                contextClass, "createDefaultWindowLayoutParams",
                Int::class.javaPrimitiveType, String::class.java)
            val defaultHeight = runCatching {
                findMethod(contextClass, "getDefaultTaskbarWindowHeight")
            }.getOrNull()
            hookWithId(createParams, "probe_taskbar_layout_params") { chain ->
                val result = chain.proceed()
                runCatching {
                    val params = result as? WindowManager.LayoutParams
                    val height = defaultHeight?.let {
                        runCatching { it.invoke(chain.thisObject) }.getOrNull()
                    }
                    val line = "class=${chain.thisObject?.javaClass?.simpleName}" +
                        " gravity=${params?.gravity} width=${params?.width}" +
                        " height=${params?.height} defaultHeight=$height" +
                        " requestedHeight=${chain.args[0]}"
                    if (taskbarGate.shouldLog(line)) logger.info("taskbar $line")
                }.onFailure { logger.error("probe: taskbar params dump failed", it) }
                result
            }
        }.onFailure { logger.error("probe: TaskbarActivityContext hook not installed", it) }
    }

    /** Dumps the hotseat layout so it can be compared against the taskbar lines. */
    private fun probeHotseat(classLoader: ClassLoader) {
        runCatching {
            val hotseatClass = classLoader.loadClass(HOTSEAT_CLASS)
            val onLayout = findMethod(
                hotseatClass, "onLayout",
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType)
            val recentUsedLayout = findMethod(hotseatClass, "getRecentUsedLayout")
            hookWithId(onLayout, "probe_hotseat_layout") { chain ->
                chain.proceed()
                runCatching {
                    val hotseat = chain.thisObject as? View ?: return@runCatching
                    val recent = recentUsedLayout.invoke(hotseat) as? ViewGroup
                    val line = "${hotseat.width}x${hotseat.height}" +
                        " visibility=${hotseat.visibility}" +
                        " alpha=${hotseat.alpha}" +
                        " parent=${hotseat.parent?.javaClass?.simpleName}" +
                        " recentUsed=" + (recent?.let {
                            "${it.width}x${it.height} children=${it.childCount}" +
                                " visibility=${it.visibility}"
                        } ?: "null")
                    if (hotseatGate.shouldLog(line)) logger.info("hotseat $line")
                    if (hierarchyDumped.compareAndSet(false, true)) {
                        val tree = StringBuilder()
                        describeHierarchy(hotseat, 0, tree)
                        logger.info("hotseat hierarchy $tree")
                        val parentTree = StringBuilder()
                        (hotseat.parent as? View)?.let { describeHierarchy(it, 0, parentTree) }
                        logger.info("hotseat parent hierarchy $parentTree")
                    }
                }.onFailure { logger.error("probe: hotseat dump failed", it) }
                null
            }
            val setAlpha = runCatching {
                findMethod(hotseatClass, "setAlpha", Float::class.javaPrimitiveType)
            }.getOrNull()
            if (setAlpha != null) {
                hookWithId(setAlpha, "probe_hotseat_alpha") { chain ->
                    chain.proceed()
                    runCatching {
                        val line = "alpha=${chain.args[0]}" +
                            " visibility=${(chain.thisObject as? View)?.visibility}"
                        if (hotseatGate.shouldLog("set$line")) logger.info("hotseat set $line")
                    }.onFailure { logger.error("probe: hotseat alpha dump failed", it) }
                    null
                }
            }
        }.onFailure { logger.error("probe: ZuiHotseat hook not installed", it) }
    }

    private fun describeProfile(reflector: Reflector, profile: Any, toSmallString: Method?): String {
        val idp = reflector.get(profile, "inv")
        val bounds = "${reflector.get(profile, "widthPx")}x${reflector.get(profile, "heightPx")}"
        val grid = "${reflector.nested(idp, "numColumns")}x${reflector.nested(idp, "numRows")}"
        val folder = "${reflector.nested(idp, "numFolderColumns")}" +
            "x${reflector.nested(idp, "numFolderRows")}"
        val small = runCatching { toSmallString?.invoke(profile) }.getOrNull()
        return "landscape=${reflector.get(profile, "isLandscape")}" +
            " tablet=${reflector.get(profile, "isTablet")}" +
            " multiDisplay=${reflector.get(profile, "isMultiDisplay")}" +
            " twoPanels=${reflector.get(profile, "isTwoPanels")}" +
            " transpose=${reflector.get(profile, "transposeLayoutWithOrientation")}" +
            " taskbarPresent=${reflector.get(profile, "isTaskbarPresent")}" +
            " taskbarHeight=${reflector.get(profile, "taskbarHeight")}" +
            " stashedTaskbarHeight=${reflector.get(profile, "stashedTaskbarHeight")}" +
            " hotseatBarHeight=${reflector.get(profile, "hotseatBarSizePx")}" +
            " hotseatIcons=${reflector.get(profile, "numShownHotseatIcons")}" +
            " insets=${reflector.call(profile, "getInsets")}" +
            " bounds=$bounds" +
            " | grid=$grid folder=$folder" +
            " allAppsCols=${reflector.nested(idp, "numAllAppsColumns")}" +
            " idpHotseatIcons=${reflector.nested(idp, "numShownHotseatIcons")}" +
            " deviceType=${reflector.nested(idp, "deviceType")}" +
            " | $small"
    }

    /** Flattens a view tree into one log line: `Class#id(w x h,v…) ; child …`. */
    private fun describeHierarchy(view: View, depth: Int, out: StringBuilder) {
        if (depth > MAX_HIERARCHY_DEPTH || out.length > MAX_HIERARCHY_CHARS) return
        if (out.isNotEmpty()) out.append(" ; ")
        val id = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull() ?: "-"
        out.append(view.javaClass.simpleName).append('#').append(id)
            .append('(').append(view.width).append('x').append(view.height)
            .append(",v").append(view.visibility).append(')')
        if (view is ViewGroup) {
            val count = minOf(view.childCount, MAX_HIERARCHY_CHILDREN)
            for (index in 0 until count) {
                describeHierarchy(view.getChildAt(index), depth + 1, out)
            }
        }
    }

    private fun describeEnvironment(context: Context): String {
        val resolver = context.contentResolver
        val config = context.resources.configuration
        return "pkg=${context.packageName}" +
            " pcMode=${Settings.System.getInt(resolver, KEY_PC_MODE, -1)}" +
            " dpPcMode=${Settings.System.getInt(resolver, KEY_DP_PC_MODE, -1)}" +
            " pcProp=${systemProperty("persist.sys.zui.pcmode")}" +
            " orientation=${config.orientation}" +
            " size=${config.screenWidthDp}x${config.screenHeightDp}dp" +
            " density=${config.densityDpi}" +
            " layoutConfig=${Settings.System.getString(resolver, KEY_LAYOUT_CONFIG)}"
    }

    private fun systemProperty(key: String): String? =
        runCatching { systemPropertiesGet?.invoke(null, key) as? String }.getOrNull()

    private fun resolveFields(owner: Class<*>, names: List<String>): Map<String, Field> =
        names.mapNotNull { name ->
            runCatching { name to findField(owner, name) }.onFailure {
                logger.debug("probe: no field $name on ${owner.simpleName}")
            }.getOrNull()
        }.toMap()

    private fun resolveMethods(owner: Class<*>, names: List<String>): Map<String, Method> =
        names.mapNotNull { name ->
            runCatching { name to findMethod(owner, name) }.onFailure {
                logger.debug("probe: no method $name on ${owner.simpleName}")
            }.getOrNull()
        }.toMap()

    /** Reads the display bounds the launcher actually sees; `supportedBounds` is the key line. */
    private class DisplayInfoProbe(
        private val infoFields: Map<String, Field>,
        private val boundsFields: Map<String, Field>,
        private val isLandscape: Method?,
        private val supportedBounds: Method?
    ) {
        fun describe(info: Any): String {
            val supported = (read(infoFields, "supportedBounds", info)
                ?: runCatching { supportedBounds?.invoke(info) }.getOrNull()) as? Collection<*>
            val bounds = supported?.joinToString("; ") { describeBounds(it) } ?: "unresolved"
            return "currentSize=${read(infoFields, "currentSize", info)}" +
                " rotation=${read(infoFields, "rotation", info)}" +
                " realBounds=${describeBounds(read(infoFields, "realBounds", info))}" +
                " density=${read(infoFields, "systemDensityDpi", info)}" +
                " supportedCount=${supported?.size} [$bounds]"
        }

        private fun describeBounds(windowBounds: Any?): String {
            if (windowBounds == null) return "null"
            return "(" + read(boundsFields, "bounds", windowBounds) +
                ", available=" + read(boundsFields, "availableSize", windowBounds) +
                ", insets=" + read(boundsFields, "insets", windowBounds) +
                ", hint=" + read(boundsFields, "rotationHint", windowBounds) +
                ", landscape=" +
                runCatching { isLandscape?.invoke(windowBounds) }.getOrNull() + ")"
        }

        private fun read(fields: Map<String, Field>, name: String, owner: Any?): Any? =
            runCatching { fields[name]?.get(owner) }.getOrNull()
    }

    /** Resolves fields/methods once so hook bodies contain no reflective lookups. */
    private class Reflector(
        private val fields: Map<String, Field>,
        private val methods: Map<String, Method>,
        private val nestedFields: Map<String, Field> = emptyMap()
    ) {
        fun get(owner: Any?, name: String): Any? =
            runCatching { fields[name]?.get(owner) }.getOrNull()

        fun nested(owner: Any?, name: String): Any? =
            runCatching { nestedFields[name]?.get(owner) }.getOrNull()

        fun method(name: String): Method? = methods[name]

        fun call(owner: Any?, name: String, vararg args: Any?): Any? =
            runCatching { methods[name]?.invoke(owner, *args) }.getOrNull()
    }

    /** Accepts only lines not seen before, up to [maxLines] distinct lines per process. */
    private class Gate(private val maxLines: Int) {
        private val seen = LinkedHashSet<String>()

        fun shouldLog(line: String): Boolean = synchronized(seen) {
            if (seen.size >= maxLines) return false
            seen.add(line)
        }
    }

    private companion object {
        const val PROFILE_CLASS = "com.zui.desktoplauncher.DeviceProfile"
        const val IDP_CLASS = "com.zui.desktoplauncher.InvariantDeviceProfile"
        const val WORK_LAUNCHER_CLASS = "com.zui.desktoplauncher.workmode.WorkLauncher"
        const val PC_ALL_APPS_ACTIVITY_CLASS =
            "com.zui.desktoplauncher.workmode.pcallapps.PcAllAppsActivity"
        const val PC_ALL_APPS_VIEW_CLASS =
            "com.zui.desktoplauncher.workmode.pcallapps.PcAllAppsPagedView"
        const val TASKBAR_MANAGER_CLASS = "com.zui.desktoplauncher.taskbar.TaskbarManager"
        const val TASKBAR_CONTEXT_CLASS = "com.zui.desktoplauncher.taskbar.TaskbarActivityContext"
        const val HOTSEAT_CLASS = "com.zui.desktoplauncher.uiextend.ZuiHotseat"
        const val DISPLAY_INFO_CLASS = "com.zui.desktoplauncher.util.DisplayController\$Info"
        const val WINDOW_BOUNDS_CLASS = "com.zui.desktoplauncher.util.WindowBounds"
        const val ACTIVITY_CLASS = "android.app.Activity"

        val PROFILE_FIELDS = listOf(
            "isLandscape", "isTablet", "isMultiDisplay", "isTwoPanels",
            "transposeLayoutWithOrientation", "isTaskbarPresent", "taskbarHeight",
            "stashedTaskbarHeight", "hotseatBarSizePx", "numShownHotseatIcons",
            "widthPx", "heightPx", "inv"
        )
        val PROFILE_METHODS = listOf("toSmallString", "getInsets", "getDisplayInfo")
        val IDP_FIELDS = listOf(
            "numColumns", "numRows", "numFolderColumns", "numFolderRows",
            "numAllAppsColumns", "numShownHotseatIcons", "deviceType"
        )
        val INFO_FIELDS = listOf(
            "supportedBounds", "realBounds", "currentSize", "rotation",
            "systemDensityDpi", "normalizedDisplayInfo"
        )
        val WINDOW_BOUNDS_FIELDS = listOf("bounds", "availableSize", "insets", "rotationHint")
        val ALL_APPS_METHODS = listOf(
            "getCellCountX", "getCellCountY", "itemsPerPage", "getItemCount"
        )

        const val KEY_PC_MODE = "zui_pc_mode"
        const val KEY_DP_PC_MODE = "zui_dp_display_pc_mode"
        const val KEY_LAYOUT_CONFIG = "extra_new_layout_config"

        const val MAX_PROFILE_LINES = 24
        const val MAX_ENVIRONMENT_LINES = 2
        const val MAX_DISPLAY_INFO_LINES = 8
        const val MAX_ORIENTATION_LINES = 12
        const val MAX_CONFIG_LINES = 12
        const val MAX_ALL_APPS_LINES = 6
        const val MAX_TASKBAR_LINES = 8
        const val MAX_HOTSEAT_LINES = 12
        const val MAX_HIERARCHY_DEPTH = 3
        const val MAX_HIERARCHY_CHILDREN = 8
        const val MAX_HIERARCHY_CHARS = 900
    }
}
