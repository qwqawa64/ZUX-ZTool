package com.qimian233.ztool.hook.modules.launcher

import android.annotation.SuppressLint
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * Test hook (launcher side): forces ZUI Launcher's freeform (small window) entry
 * checks to always pass, and logs the original results.
 *
 * The recents long-press menu entry is gated by
 * com.zui.launcher.utils.FreeformUtilities.isPackageSupportZuiFreeform(...), which
 * requires ENABLE_ZUI_FREEFORM, CommercialManager.isOVFeatureEnable(),
 * zui.permission.OVFREEFORM_CLIENT and default display before asking the
 * "ovcommon" binder service. Other entries (sidebar/freeform bar) go through
 * isTaskSupportSmallWindow(...), which additionally consults the client-side static
 * deny set inStaticDenyOvcDcvSet(...) read from framework-res
 * config_ovcDcv_StaticDenySet. All three are bypassed here.
 *
 * Module name is "hook_test" so it always runs without a frontend switch.
 */
@SuppressLint("PrivateApi")
class LauncherFreeformEntryHook : AppHookModule() {
    override fun getModuleName(): String = "hook_test"

    override fun getTargetPackages(): Array<out String> =
        arrayOf(ScopeKeys.LAUNCHER.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val utilsClass: Class<*> = classLoader.loadClass(
            "com.zui.launcher.utils.FreeformUtilities")
        val taskInfoClass: Class<*> = classLoader.loadClass("android.app.TaskInfo")
        val componentNameClass: Class<*> = classLoader.loadClass("android.content.ComponentName")

        val isPkgSupport: Method = findMethod(
            utilsClass, "isPackageSupportZuiFreeform",
            android.content.Context::class.java,
            Int::class.javaPrimitiveType,
            String::class.java,
            Int::class.javaPrimitiveType,
            String::class.java
        )
        hookWithId(isPkgSupport, "launcher_freeform_pkg_support") { chain ->
            val original = chain.proceed() as Boolean
            logger.debug(
                "isPackageSupportZuiFreeform(${chain.getArg(2)}, u${chain.getArg(3)}, " +
                    "display=${chain.getArg(1)}) original=$original -> true")
            true
        }

        val isTaskSupport: Method = findMethod(
            utilsClass, "isTaskSupportSmallWindow",
            android.content.Context::class.java,
            taskInfoClass
        )
        hookWithId(isTaskSupport, "launcher_freeform_task_support") { chain ->
            val original = chain.proceed() as Boolean
            logger.debug("isTaskSupportSmallWindow original=$original -> true")
            true
        }

        val inDenySet: Method = findMethod(
            utilsClass, "inStaticDenyOvcDcvSet",
            android.content.Context::class.java,
            componentNameClass
        )
        hookWithId(inDenySet, "launcher_freeform_deny_set") { chain ->
            val original = chain.proceed() as Boolean
            logger.debug(
                "inStaticDenyOvcDcvSet(${chain.getArg(1)}) original=$original -> false")
            false
        }
    }
}
