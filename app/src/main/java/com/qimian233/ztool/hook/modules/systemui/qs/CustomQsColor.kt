package com.qimian233.ztool.hook.modules.systemui.qs

import android.annotation.SuppressLint
import android.graphics.Color
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
class CustomQsColor : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.QS_COLOR.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        updatePrefs()
        try {
            val getBackgroundMethod: Method = classLoader
                .loadClass("com.android.systemui.qs.tileimpl.QSTileViewImpl")
                .getDeclaredMethod(
                    "getBackgroundColorForState",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
            hookWithId(getBackgroundMethod, "get_background") { chain ->
                updatePrefs()
                val state = chain.args[0] as Int
                val disabledByPolicy = chain.args[2] as Boolean
                if (state == STATE_ACTIVE && !disabledByPolicy && customQsColor) {
                    return@hookWithId customQsActiveColorVal
                }
                chain.proceed()
            }

            val getLabelMethod: Method = classLoader
                .loadClass("com.android.systemui.qs.tileimpl.QSTileViewImpl")
                .getDeclaredMethod(
                    "getLabelColorForState",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
            hookWithId(getLabelMethod, "get_label") { chain ->
                updatePrefs()
                val state = chain.args[0] as Int
                val disabledByPolicy = chain.args[1] as Boolean
                if (state == STATE_ACTIVE && !disabledByPolicy && customLabelColor) {
                    return@hookWithId customLabelActiveColorVal
                }
                chain.proceed()
            }

            val getSecondaryMethod: Method = classLoader
                .loadClass("com.android.systemui.qs.tileimpl.QSTileViewImpl")
                .getDeclaredMethod(
                    "getSecondaryLabelColorForState",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
            hookWithId(getSecondaryMethod, "get_secondary") { chain ->
                updatePrefs()
                val state = chain.args[0] as Int
                val disabledByPolicy = chain.args[1] as Boolean
                if (state == STATE_ACTIVE && !disabledByPolicy && customSecondLabelColor) {
                    return@hookWithId customSecondLabelActiveColorVal
                }
                chain.proceed()
            }
        } catch (e: Exception) {
            logger.error("Error!", e)
        }
    }

    private fun updatePrefs() {
        customQsColor = try {
            remotePreferences.getBoolean(PreferenceKeys.CUSTOM_QS_COLOR.name, false)
        } catch (_: Throwable) {
            false
        }
        customLabelColor = try {
            remotePreferences.getBoolean(PreferenceKeys.CUSTOM_LABEL_COLOR.name, false)
        } catch (_: Throwable) {
            false
        }
        customSecondLabelColor = try {
            remotePreferences.getBoolean(PreferenceKeys.CUSTOM_SECOND_LABEL_COLOR.name, false)
        } catch (_: Throwable) {
            false
        }
        customQsActiveColorVal = try {
            remotePreferences.getInt(PreferenceKeys.CUSTOM_QS_ACTIVE_COLOR_VAL.name, DEFAULT_QS_ACTIVE_COLOR)
        } catch (_: Throwable) {
            DEFAULT_QS_ACTIVE_COLOR
        }
        customLabelActiveColorVal = try {
            remotePreferences.getInt(PreferenceKeys.CUSTOM_LABEL_ACTIVE_COLOR_VAL.name, DEFAULT_LABEL_ACTIVE_COLOR)
        } catch (_: Throwable) {
            DEFAULT_LABEL_ACTIVE_COLOR
        }
        customSecondLabelActiveColorVal = try {
            remotePreferences.getInt(PreferenceKeys.CUSTOM_SECOND_LABEL_ACTIVE_COLOR_VAL.name, DEFAULT_LABEL_ACTIVE_COLOR)
        } catch (_: Throwable) {
            DEFAULT_LABEL_ACTIVE_COLOR
        }
    }

    private var customQsColor = false // 是否启用磁贴背景色修改
    private var customLabelColor = false // 是否启用磁贴主要说明文本在开关被启用时的颜色修改
    private var customSecondLabelColor = false // 是否启用磁贴次要说明文本在开关被启用后的颜色修改
    private var customQsActiveColorVal = 0 // 磁贴背景色，AARRGGBB 格式，在存储时要通过 Color.argb() 提前转化好
    private var customLabelActiveColorVal = 0 // 主要说明文本激活时的颜色，在存储时要通过 Color.argb() 提前转化好
    private var customSecondLabelActiveColorVal = 0 // 次要说明文本激活时的颜色，在存储时要通过 Color.argb() 提前转化好

    companion object {
        private const val STATE_ACTIVE = 2
        private val DEFAULT_QS_ACTIVE_COLOR = Color.argb(0xff, 0xff, 0xff, 0xff)
        private val DEFAULT_LABEL_ACTIVE_COLOR = Color.argb(0xff, 0x00, 0x00, 0x00)
    }
}
