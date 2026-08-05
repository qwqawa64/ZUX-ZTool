package com.qimian233.ztool.hook.modules.setting

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.Boolean
import kotlin.Any
import kotlin.Array
import kotlin.CharSequence
import kotlin.String
import kotlin.Throwable
import kotlin.Throws
import kotlin.arrayOf

/**
 * 允许显示杜比音效Hook模块
 * 功能：绕过耳机检测，使杜比音效在非耳机状态下可用
 */
@SuppressLint("PrivateApi")
class AllowDisplayDolbyHook : AppHookModule() {
    override fun getModuleName(): String = "allow_display_dolby"

    override fun getTargetPackages(): Array<String> = arrayOf(
            "com.android.settings",
            "com.android.systemui",
        )

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        when (packageName) {
            "com.android.settings" -> hookSettingsPackage(classLoader)
            "com.android.systemui" -> hookSystemUIPackage(classLoader)
        }
    }

    /**
     * Hook设置应用中的杜比音效相关功能
     */
    private fun hookSettingsPackage(classLoader: ClassLoader) {
        try {
            // Android 13 (SDK 33)
            if (Build.VERSION.SDK_INT == 33) {
                val m = classLoader
                    .loadClass("com.android.settings.dolby.DolbyAtmosPreferenceFragment")
                    .getDeclaredMethod("getheadsetStatus")
                hookWithId(m, "hook_59") { 1 }
                logger.info("Successfully hooked Android 13 DolbyAtmosPreferenceFragment.getheadsetStatus")
            } else if (Build.VERSION.SDK_INT == 34) {
                // Hook 耳机连接状态检测
                val isHeadsetMethod = classLoader
                    .loadClass("com.lenovo.settings.sound.dolby.DolbyAtmosFragment")
                    .getDeclaredMethod("isHeadsetConnected")
                hookWithId(
                    isHeadsetMethod,
                    "is_headset_1"
                ) { Boolean.TRUE }

                // Hook 初始化视图，清除摘要显示
                val initViewMethod = classLoader
                    .loadClass("com.lenovo.settings.sound.dolby.DolbyAtmosFragment")
                    .getDeclaredMethod("initView")
                hookWithId(initViewMethod, "init_view") { chain: XposedInterface.Chain? ->
                    val result = chain!!.proceed()
                    try {
                        val field = findField(chain.thisObject.javaClass, "mDolbySwitchPreference")
                        val preference = field.get(chain.thisObject)
                        if (preference != null) {
                            val setSummary = preference.javaClass
                                .getDeclaredMethod("setSummary", CharSequence::class.java)
                            setSummary.invoke(preference, null as Any?)
                            logger.debug("Successfully cleared Dolby switch preference summary")
                        }
                    } catch (t: Throwable) {
                        logger.error("Failed to clear Dolby switch preference summary", t)
                    }
                    result
                }
                logger.info("Successfully hooked Android 14 DolbyAtmosFragment methods")
            } else if (Build.VERSION.SDK_INT >= 35) {
                // Hook 工具类中的耳机连接检测
                val isHeadsetMethod = classLoader
                    .loadClass("com.lenovo.settings.sound.dolby.DolbyAtmosUtils")
                    .getDeclaredMethod("isHeadsetConnected", Context::class.java)
                hookWithId(
                    isHeadsetMethod,
                    "is_headset_2"
                ) { Boolean.TRUE }

                // Hook 控制器更新状态，清除摘要
                val prefClass = classLoader.loadClass("androidx.preference.Preference")
                val updateStateMethod = classLoader
                    .loadClass("com.lenovo.settings.sound.dolby.DolbySwitchPreferenceController")
                    .getDeclaredMethod("updateState", prefClass)
                hookWithId(
                    updateStateMethod,
                    "update_state"
                ) { chain: XposedInterface.Chain? ->
                    try {
                        val arg0 = chain!!.getArg(0)
                        if (arg0 != null) {
                            val setSummary = findMethod(
                                arg0.javaClass,
                                "setSummary", CharSequence::class.java
                            )
                            setSummary.invoke(arg0, null as Any?)
                            logger.debug("Successfully cleared preference summary in updateState")
                        }
                    } catch (t: Throwable) {
                        logger.error("Failed to clear preference summary in updateState", t)
                    }
                    chain!!.proceed()
                }
                logger.info("Successfully hooked Android 15 DolbyAtmosUtils and DolbySwitchPreferenceController")
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook Settings package", t)
        }
    }

    /**
     * Hook SystemUI中的杜比音效磁贴
     */
    private fun hookSystemUIPackage(classLoader: ClassLoader) {
        try {
            // Hook QDolbyAtmosTile 耳机检测方法
            if (Build.VERSION.SDK_INT <= 34) {
                val m = classLoader
                    .loadClass("com.android.systemui.qs.tiles.QDolbyAtmosTile")
                    .getDeclaredMethod("isHeadSetConnect")
                hookWithId(m, "hook_138") { Boolean.TRUE }
                logger.info("Successfully hooked QDolbyAtmosTile.isHeadSetConnect (SDK <= 34)")
            } else {
                val m = classLoader
                    .loadClass("com.android.systemui.qs.tiles.QDolbyAtmosTile")
                    .getDeclaredMethod("isHeadSetConnect$2")
                hookWithId(m, "hook_144") { Boolean.TRUE }
                logger.info("Successfully hooked QDolbyAtmosTile.isHeadSetConnect$2 (SDK > 34)")
            }

            // Hook 详情视图中的耳机检测
            val detailMethod = classLoader
                .loadClass("com.android.systemui.qs.tiles.QDolbyAtmosDetailView")
                .getDeclaredMethod("isHeadSetConnect")
            hookWithId(
                detailMethod,
                "detail"
            ) { Boolean.TRUE }
            logger.info("Successfully hooked QDolbyAtmosDetailView.isHeadSetConnect")
        } catch (t: Throwable) {
            logger.error("Failed to hook SystemUI package", t)
        }
    }
}
