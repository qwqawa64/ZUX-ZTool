package com.qimian233.ztool.hook.modules.safecenter

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field

class EnableAutorunByDefault : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.DEFAULT_ENABLE_AUTORUN.name

    override fun getTargetPackages(): Array<String> = arrayOf(
        ScopeKeys.LENOVO_SAFE_CENTER.packageName,
        ScopeKeys.ZUI_SAFE_CENTER.packageName
    )

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        if (ScopeKeys.ZUI_SAFE_CENTER.packageName == packageName
            || ScopeKeys.LENOVO_SAFE_CENTER.packageName == packageName
        ) {
            logger.info("Start hooking safecenter")
            try {
                val cls = classLoader.loadClass("com.lenovo.performance.autorun.beans.AutoRunDbItem")
                val fld: Field = cls.getDeclaredField("mAttrs")
                fld.isAccessible = true

                for (ctor in cls.declaredConstructors) {
                    hookWithId(ctor, "ctor") { chain ->
                        chain.proceed()
                        val obj = chain.thisObject
                        var attrs = fld.getInt(obj)
                        attrs = attrs or (ATTR_WHITELIST or ATTR_RELATIVE_WHITELIST)
                        fld.setInt(obj, attrs)
                        null
                    }
                }
                logger.info("Hooked safecenter [OK]")
            } catch (e: Exception) {
                logger.error("Failed hooking safecenter", e)
            }
        }
    }

    companion object {
        private const val ATTR_WHITELIST = 0x20000000
        private const val ATTR_RELATIVE_WHITELIST = 0x40000000
    }
}
