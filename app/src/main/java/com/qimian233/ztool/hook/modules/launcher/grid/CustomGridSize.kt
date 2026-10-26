package com.qimian233.ztool.hook.modules.launcher.grid

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor

@SuppressLint("PrivateApi")
class CustomGridSize : AppHookModule() {
    override fun getModuleName(): String = "CustomGridSize"

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.LAUNCHER.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        logger.info("Load CustomGridSize!")
        // We directly hook the constructor of GridOption class
        // But before hook, let us load custom grid size from shared prefs first
        val prefs = remotePreferences
        CUSTOM_ROWS =
            prefs.getInt(PreferenceKeys.CUSTOM_LAUNCHER_ROW.name, 4)
        CUSTOM_COLUMNS =
            prefs.getInt(PreferenceKeys.CUSTOM_LAUNCHER_COLUMN.name, 6)
        try {
            val gridOptionClass: Class<*> = classLoader.loadClass($$"com.android.launcher3.InvariantDeviceProfile$GridOption")

            val ctor: Constructor<*> = when (Build.VERSION.SDK_INT) {
                Build.VERSION_CODES.CINNAMON_BUN -> gridOptionClass.getDeclaredConstructor(
                    Context::class.java,
                    classLoader.loadClass("com.android.launcher3.util.XmlElement"),
                    classLoader.loadClass("com.android.launcher3.display.LauncherDisplayInfo")
                    )
                Build.VERSION_CODES.BAKLAVA -> gridOptionClass.getDeclaredConstructor(
                    Context::class.java,
                    classLoader.loadClass("android.util.AttributeSet"),
                    classLoader.loadClass($$"com.android.launcher3.util.DisplayController$Info")
                )
                else -> gridOptionClass.getDeclaredConstructor(
                    Context::class.java,
                    classLoader.loadClass("android.util.AttributeSet")
                )
            }
            // High priority: grid dimensions must be applied before other GridOption-
            // or DeviceProfile-dependent hooks (e.g. LauncherWideGridHook) recompute
            // layout values from them on the next setInsets pass.
            hookWithId(ctor, "ctor", XposedInterface.PRIORITY_HIGHEST) { chain ->
                chain.proceed()
                try {
                    val thisObject = chain.thisObject
                    // Directly set int fields
                    val numColsField = findField(gridOptionClass, "numColumns")
                    numColsField.set(thisObject, CUSTOM_COLUMNS)
                    val numRowsField = findField(gridOptionClass, "numRows")
                    numRowsField.set(thisObject, CUSTOM_ROWS)
                    logger.debug("GridOption config modded to " + CUSTOM_COLUMNS + "x" + CUSTOM_ROWS)
                } catch (e: Throwable) {
                    logger.error("No such method! Probably you are using a newer ZUXOS version!", e)
                }
                null
            }
        } catch (th: Throwable) {
            logger.error("Failed to hook GridOption!", th)
        }
    }

    companion object {
        private var CUSTOM_COLUMNS = 8
        private var CUSTOM_ROWS = 6
    }
}
