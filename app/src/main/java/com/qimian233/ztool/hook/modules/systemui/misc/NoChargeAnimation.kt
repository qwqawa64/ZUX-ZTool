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
 * Remove charge animation hook.
 *
 *
 * Uses DEXKit to locate the Handler field by field type instead of the obfuscated
 * name (H), ensuring cross-version compatibility.
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

            // Read the Handler field name from the offline index
            var handlerFieldName = string(
                xposed, SYSTEMUI_PACKAGE,
                DexIndexConstants.ModuleKeys.NO_CHARGE_ANIMATION,
                DexIndexConstants.Keys.HANDLER_FIELD_NAME
            )
            if (handlerFieldName == null) handlerFieldName = "H" // default fallback


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
