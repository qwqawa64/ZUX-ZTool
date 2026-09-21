package com.qimian233.ztool.hook.modules.launcher.grid

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Overrides the launcher's icon scale source [com.android.launcher3.Utilities]
 * #getCustomedIconScale(Context), which normally reads the ZUI persistent
 * LAUNCHER_ICON_SIZE_SCALE from Settings.System and clamps it to 0.6~1.3.
 *
 * The returned value is stored into InvariantDeviceProfile#customIconScale and
 * consumed across DeviceProfile construction/updateIconSize, all-apps sizing,
 * IconCache/LauncherIcons bitmap sizing, theme-change and config-change rebuilds
 * — so hooking the static method once covers every consumer and survives all IDP
 * rebuilds (unlike patching the field, which the next rebuild would overwrite).
 *
 * The hook replaces the method entirely: when enabled it returns the user value
 * from xposed_module_config (bypassing the 0.6~1.3 clamp), otherwise the original
 * result. Called repeatedly at runtime — the preference read in the callback is
 * intentional so slider changes apply on the next IDP rebuild; a launcher restart
 * forces an immediate rebuild.
 */
class IconScaleOverrideHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.LAUNCHER_ICON_SCALE_OVERRIDE.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val utilitiesClass = try {
            param.defaultClassLoader.loadClass("com.android.launcher3.Utilities")
        } catch (th: Throwable) {
            logger.error("IconScale: Utilities class not found, aborting", th)
            return
        }
        val getCustomedIconScale = try {
            findMethod(utilitiesClass, "getCustomedIconScale", android.content.Context::class.java)
        } catch (th: Throwable) {
            logger.error("IconScale: Utilities#getCustomedIconScale not found, aborting", th)
            return
        }

        hookWithId(getCustomedIconScale, "launcher_icon_scale_override") { chain ->
            val prefs = remotePreferences
            val enabled = prefs.getBoolean(
                PreferenceKeys.LAUNCHER_ICON_SCALE_OVERRIDE.name,
                PreferenceKeys.LAUNCHER_ICON_SCALE_OVERRIDE.default
            )
            if (!enabled) return@hookWithId chain.proceed()
            val scale = prefs.getFloat(
                PreferenceKeys.LAUNCHER_ICON_SCALE_VALUE.name,
                PreferenceKeys.LAUNCHER_ICON_SCALE_VALUE.default
            )
            if (scale <= 0f) return@hookWithId chain.proceed()
            logger.debug("IconScale: overriding customIconScale -> $scale")
            scale
        }
        logger.info("IconScaleOverrideHook installed (hooking Utilities#getCustomedIconScale)")
    }
}
