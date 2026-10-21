package com.qimian233.ztool.hook.modules.desktoplauncher

import android.content.Context
import android.util.AttributeSet
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicInteger

/**
 * Turns the PC all-apps grid portrait-side-up.
 *
 * The host computes columns from the long edge and rows from the short edge, so portrait keeps the
 * landscape counts and the columns overflow. This module re-splits `itemsPerPage` into a portrait
 * `columns x rows` and rewrites the out-array of `calculateGridSize`, leaving paging consistent.
 *
 * Gated by [PreferenceKeys.WORK_MODE_PORTRAIT]; see docs/research/zui_work_mode_desktop_hook_survey.md
 */
class PcAllAppsPortraitGrid : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.WORK_MODE_PORTRAIT.name

    override fun getTargetPackages(): Array<String> =
        arrayOf(ScopeKeys.DESKTOP_LAUNCHER.packageName)

    @Volatile
    private var portrait = false

    @Volatile
    private var profileWidthPx = 0

    @Volatile
    private var cellWidthPx = 0

    private val logged = AtomicInteger(0)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val pagedViewClass = runCatching {
            classLoader.loadClass(PC_ALL_APPS_VIEW_CLASS)
        }.getOrElse {
            logger.error("PcAllAppsPortraitGrid: PcAllAppsPagedView missing", it)
            return
        }
        val profileReader = runCatching {
            ProfileReader(
                profileField = pagedViewClass.declaredFields
                    .firstOrNull { it.type.name == PROFILE_CLASS }
                    ?.apply { isAccessible = true } ?: error("no DeviceProfile field"),
                widthField = findField(classLoader.loadClass(PROFILE_CLASS), "widthPx"),
                heightField = findField(classLoader.loadClass(PROFILE_CLASS), "heightPx")
            )
        }.getOrElse {
            logger.error("PcAllAppsPortraitGrid: profile fields missing", it)
            return
        }
        hookConstructor(pagedViewClass, profileReader)
        hookGridCalculation(pagedViewClass)
    }

    /** Captures the profile of the live drawer, so [hookGridCalculation] needs no context. */
    private fun hookConstructor(pagedViewClass: Class<*>, profileReader: ProfileReader) {
        runCatching {
            val constructor = pagedViewClass.getDeclaredConstructor(
                Context::class.java, AttributeSet::class.java
            )
            constructor.isAccessible = true
            hookWithId(constructor, "pc_all_apps_portrait_profile") { chain ->
                chain.proceed()
                runCatching {
                    val view = chain.thisObject as? android.view.View ?: return@runCatching
                    val profile = profileReader.profileField.get(view) ?: return@runCatching
                    profileWidthPx = profileReader.widthField.getInt(profile)
                    portrait = profileReader.heightField.getInt(profile) > profileWidthPx
                    val resources = view.context.resources
                    val id = resources.getIdentifier(CELL_WIDTH_DIMEN, "dimen", HOST_PACKAGE)
                    if (id != 0) cellWidthPx = resources.getDimensionPixelSize(id)
                    logger.debug(
                        "PcAllAppsPortraitGrid: profile ${profileWidthPx}px" +
                            " portrait=$portrait cellWidth=${cellWidthPx}px"
                    )
                }.onFailure { logger.error("PcAllAppsPortraitGrid: profile capture failed", it) }
                null
            }
        }.onFailure { logger.error("PcAllAppsPortraitGrid: constructor hook not installed", it) }
    }

    /**
     * Rewrites `calculateGridSize`'s out array with a portrait factorisation of `itemsPerPage`.
     *
     * Keeping `columns * rows == itemsPerPage` leaves the host's page splitting, `rank`,
     * `getIconViewByItemInfo` and `itemsPerPage()` all consistent — no private final field is
     * touched.
     */
    private fun hookGridCalculation(pagedViewClass: Class<*>) {
        runCatching {
            val calculate = pagedViewClass.getMethod(
                "calculateGridSize",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                IntArray::class.java
            )
            hookWithId(calculate, "pc_all_apps_portrait_grid") { chain ->
                val result = chain.proceed()
                runCatching {
                    if (!portrait) return@runCatching
                    val out = chain.args[6] as? IntArray ?: return@runCatching
                    val count = chain.args[0] as Int
                    val perPage = chain.args[5] as Int
                    val columns = portraitColumns(perPage) ?: return@runCatching
                    val maxRows = perPage / columns
                    val rows = (count + columns - 1) / columns
                    out[0] = columns
                    out[1] = rows.coerceIn(1, maxRows)
                    if (logged.incrementAndGet() <= MAX_LOG_LINES) {
                        logger.info(
                            "PcAllAppsPortraitGrid: grid ${columns}x${out[1]}" +
                                " (count=$count perPage=$perPage)"
                        )
                    }
                }.onFailure { logger.error("PcAllAppsPortraitGrid: grid rewrite failed", it) }
                result
            }
        }.onFailure { logger.error("PcAllAppsPortraitGrid: grid hook not installed", it) }
    }

    /** Largest divisor of [perPage] that fits the panel width, or null when it cannot be sized. */
    private fun portraitColumns(perPage: Int): Int? {
        if (perPage <= 0) return null
        val target = if (cellWidthPx > 0) profileWidthPx / cellWidthPx else 0
        if (target <= 0) return null
        val divisor = (1..perPage).filter { perPage % it == 0 && it <= target }.maxOrNull()
        return divisor?.takeIf { it >= MIN_COLUMNS }
    }

    /** Resolves the profile fields of one paged view once. */
    private class ProfileReader(
        val profileField: Field,
        val widthField: Field,
        val heightField: Field
    )

    private companion object {
        const val HOST_PACKAGE = "com.zui.desktoplauncher"
        const val PC_ALL_APPS_VIEW_CLASS =
            "com.zui.desktoplauncher.workmode.pcallapps.PcAllAppsPagedView"
        const val PROFILE_CLASS = "com.zui.desktoplauncher.DeviceProfile"
        const val CELL_WIDTH_DIMEN = "pc_allapps_cellwidth"
        const val MIN_COLUMNS = 3
        const val MAX_LOG_LINES = 6
    }
}
