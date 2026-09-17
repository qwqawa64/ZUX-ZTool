package com.qimian233.ztool.hook.modules.systemui.keyguard

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor

@SuppressLint("PrivateApi")
class ForceLenovoAOD : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.FORCE_LENOVO_AOD.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)


    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        // Directly set the mIsGoingToStartAOD field
        hookZuiDozeTriggers(classLoader)
        // Additionally ensure AOD-related checks pass
        hookAODChecks()
    }

    private fun hookZuiDozeTriggers(classLoader: ClassLoader) {
        try {
            // Hook the ZuiDozeTriggers constructor to set the flag immediately after instance creation
            val ctor: Constructor<*> = classLoader.loadClass(ZUI_DOZE_TRIGGERS_CLASS)
                .getDeclaredConstructor(
                    classLoader.loadClass("com.android.systemui.doze.DozeTriggers"),
                    Context::class.java
                )
            hookWithId(ctor, "ctor") { chain ->
                chain.proceed()
                // Set the AOD start flag immediately after the constructor runs
                chain.thisObject.javaClass.getDeclaredField("mIsGoingToStartAOD")
                    .setBoolean(chain.thisObject, true)
                logger.debug("ZuiDozeTriggers constructed, forced mIsGoingToStartAOD = true")
                null
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook ZuiDozeTriggers: ", t)
        }
    }

    private fun hookAODChecks() {
        try {
            // Hook the SystemProperties check
            val getIntMethod = Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
            hookWithId(getIntMethod, "get_int") { chain ->
                val key = chain.args[0] as String
                if ("ro.config.aod.support" == key) {
                    logger.debug("Bypassed ro.config.aod.support check")
                    return@hookWithId 1 // Force AOD support
                }
                chain.proceed()
            }

            // Hook the AOD setting check
            @SuppressLint("DiscouragedPrivateApi") val getIntForUserMethod =
                Settings.System::class.java
                    .getDeclaredMethod(
                        "getIntForUser",
                        ContentResolver::class.java,
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
            hookWithId(
                getIntForUserMethod,
                "get_int_for_user"
            ) { chain ->
                val setting = chain.args[1] as String
                if ("always_on_display" == setting) {
                    logger.debug("Bypassed always_on_display setting check")
                    return@hookWithId 1
                }
                chain.proceed()
            }
        } catch (t: Exception) {
            logger.error("Failed to hook AOD checks: ", t)
        }
    }

    companion object {
        private const val ZUI_DOZE_TRIGGERS_CLASS = "com.android.systemui.doze.ZuiDozeTriggers"
    }
}
