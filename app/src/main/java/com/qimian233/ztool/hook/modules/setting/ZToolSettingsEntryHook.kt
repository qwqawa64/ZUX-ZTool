package com.qimian233.ztool.hook.modules.setting

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import com.qimian233.ztool.data.ScopeKeys
import com.qimian233.ztool.MainActivity
import com.qimian233.ztool.data.PreferenceKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor

@SuppressLint("PrivateApi")
class ZToolSettingsEntryHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.ZTOOL_SETTINGS_ENTRY.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SETTINGS.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        try {
            logger.info("Installing hook.")
            val m = findMethod(
                classLoader.loadClass("com.android.settings.homepage.TopLevelSettings"),
                "onCreatePreferences",
                Bundle::class.java, String::class.java)
            hookWithId(m, "hook_44") { chain: XposedInterface.Chain? ->
                val result = chain!!.proceed()
                try {
                    val getPrefScreen =
                        findMethod(chain.thisObject.javaClass, "getPreferenceScreen")
                    val screen = getPrefScreen.invoke(chain.thisObject) ?: return@hookWithId result

                    val getContext = findMethod(screen.javaClass, "getContext")
                    val context = getContext.invoke(screen) as? Context ?: return@hookWithId result

                    val findPreference = findMethod(
                        screen.javaClass,
                        "findPreference", CharSequence::class.java
                    )
                    if (findPreference.invoke(screen, ENTRY_KEY) != null) {
                        return@hookWithId result
                    }

                    val preferenceCategoryClass = classLoader
                        .loadClass("androidx.preference.PreferenceCategory")
                    val preferenceClass = classLoader
                        .loadClass("androidx.preference.Preference")

                    val categoryCtor: Constructor<*> = preferenceCategoryClass
                        .getDeclaredConstructor(Context::class.java)
                    val category: Any = categoryCtor.newInstance(context)
                    val setKey = findMethod(preferenceCategoryClass, "setKey", String::class.java)
                    setKey.invoke(category, CATEGORY_KEY)
                    val setOrder = findMethod(
                        preferenceCategoryClass,
                        "setOrder",
                        Int::class.javaPrimitiveType
                    )
                    setOrder.invoke(category, -90)

                    val prefCtor: Constructor<*> =
                        preferenceClass.getDeclaredConstructor(Context::class.java)
                    val entry: Any = prefCtor.newInstance(context)
                    setKey.invoke(entry, ENTRY_KEY)
                    val setTitle = findMethod(preferenceClass, "setTitle", CharSequence::class.java)
                    setTitle.invoke(entry, ENTRY_TITLE)
                    setOrder.invoke(entry, Int.MIN_VALUE + 1)

                    val intent = Intent()
                    intent.component = ComponentName(
                        APP_PACKAGE,
                        MainActivity::class.java.name
                    )
                    val setIntent =
                        preferenceClass.getDeclaredMethod("setIntent", Intent::class.java)
                    setIntent.invoke(entry, intent)
                    val setIcon = preferenceClass.getDeclaredMethod("setIcon", Drawable::class.java)
                    setIcon.invoke(
                        entry,
                        context.packageManager.getApplicationIcon(APP_PACKAGE)
                    )

                    val addPreference = findMethod(
                        screen.javaClass, "addPreference",
                        classLoader.loadClass("androidx.preference.Preference")
                    )
                    addPreference.invoke(screen, category)
                    addPreference.invoke(category, entry)

                    logger.debug("Injected ZTool entry into TopLevelSettings")
                } catch (t: Throwable) {
                    logger.error("Failed to inject ZTool settings entry", t)
                }
                result
            }
            logger.info("Successfully installed hook.")
        } catch (t: Throwable) {
            logger.error("Failed to hook TopLevelSettings.onCreatePreferences", t)
        }
    }

    companion object {
        private const val ENTRY_KEY = "ztool_settings_entry"
        private const val CATEGORY_KEY = "ztool_settings_category"
        private const val APP_PACKAGE = "com.qimian233.ztool"
        private const val ENTRY_TITLE = "ZTool"
    }
}
