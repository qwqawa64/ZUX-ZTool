package com.qimian233.ztool.hook.modules.setting

import android.content.ContentResolver
import android.provider.Settings.Secure
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * Hides the red OTA update hint in Settings while keeping the OTA entry usable.
 */
class HideOtaUpdateHint : AppHookModule() {
    override fun getModuleName(): String = "hide_ota_update_hint"

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        handlePreferenceRead()
    }

    fun handlePreferenceRead() {
        try {
            // getInt(ContentResolver, String, int)
            val getInt3 : Method = Secure::class.java.getDeclaredMethod(
                "getInt",
                ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            hookWithId(getInt3, "get_int3") { chain ->
                if (OTA_NEW_VERSION_FOUND == chain.getArg(1)) 0
                else chain.proceed()
            }

            // getInt(ContentResolver, String)
            val getInt2 = Secure::class.java.getDeclaredMethod(
                "getInt", ContentResolver::class.java, String::class.java
            )
            hookWithId(getInt2, "get_int2") { chain ->
                if (OTA_NEW_VERSION_FOUND == chain.getArg(1)) 0
                else chain.proceed()
            }

            logger.info("Hooked Settings OTA new-version flag reads")
        } catch (t: Throwable) {
            logger.error("Failed to hook Settings OTA new-version flag reads", t)
        }
    }

    companion object {
        private val TARGET_PACKAGE = ScopeKeys.SETTINGS.packageName
        private const val OTA_NEW_VERSION_FOUND = "lenovo_ota_new_version_found"
    }
}
