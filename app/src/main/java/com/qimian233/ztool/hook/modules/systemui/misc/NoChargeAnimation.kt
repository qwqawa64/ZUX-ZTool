package com.qimian233.ztool.hook.modules.systemui.misc

import android.annotation.SuppressLint
import android.os.Message
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore.string
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 移除充电动画 Hook。
 * 
 *
 * 使用 DEXKit 通过字段类型而非混淆后的名称（H）定位 Handler 字段，
 * 确保跨版本兼容。
 * 
 */
class NoChargeAnimation : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.NO_CHARGE_ANIMATION.name

    override fun getTargetPackages(): Array<String> = arrayOf(SYSTEMUI_PACKAGE)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        logger.info("Loading module No_ChargeAnimation.")
        handleLoadSystemUi(classLoader)
    }

    fun handleLoadSystemUi(classLoader: ClassLoader) {
        try {
            logger.info("Hooking ChargingAnimationController...")
            @SuppressLint("PrivateApi") val controllerClass = classLoader.loadClass(TARGET_CLASS)

            // 从离线索引读取 Handler 字段名
            var handlerFieldName = string(
                xposed, SYSTEMUI_PACKAGE,
                DexIndexConstants.ModuleKeys.NO_CHARGE_ANIMATION,
                DexIndexConstants.Keys.HANDLER_FIELD_NAME
            )
            if (handlerFieldName == null) handlerFieldName = "H" // 默认回退


            logger.debug("Using handler field name: $handlerFieldName")
            val handlerField = controllerClass.getDeclaredField(handlerFieldName)
            handlerField.isAccessible = true
            val handlerType = handlerField.type
            val handleMessageMethod =
                handlerType.getDeclaredMethod("handleMessage", Message::class.java)
            hookWithId(
                handleMessageMethod,
                "handle_message"
            ) { null }
            logger.info("Hooked ChargingAnimationController [OK]")
        } catch (e: Exception) {
            logger.error("Error hooking ChargingAnimationController", e)
        }
    }

    companion object {
        private val SYSTEMUI_PACKAGE = ScopeKeys.SYSTEM_UI.packageName
        private const val TARGET_CLASS =
            "com.android.keyguard.lockscreen.charge.ChargingAnimationController"
    }
}
