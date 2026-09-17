package com.qimian233.ztool.dexindex.indexer

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.dexindex.base.DexIndexer
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.FieldMatcher
import org.luckypray.dexkit.query.matchers.FieldsMatcher

/**
 * systemui scope (com.android.systemui) offline indexer.
 *
 * Migrated as-is from the DexKit queries of these Hooks:
 * - NoChargeAnimation (Handler field name of ChargingAnimationController)
 * - SystemUINetworkSpeeddoublelayerHook (Handler inner class name of NetworkSpeedView)
 */
class SystemUiDexIndexer : DexIndexer {

    override val scopePackage: String = ScopeKeys.SYSTEM_UI.packageName

    override fun index(bridge: DexKitBridge, context: Context): JsonObject {
        val modules = JsonObject()
        modules.add(DexIndexConstants.ModuleKeys.NO_CHARGE_ANIMATION, indexNoChargeAnimation(bridge))
        modules.add(
            DexIndexConstants.ModuleKeys.SYSTEMUI_NETWORK_SPEED_DOUBLELAYER,
            indexNetworkSpeedDoublelayer(bridge)
        )
        return modules
    }

    private fun indexNoChargeAnimation(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        try {
            val classData = bridge.findClass(
                FindClass.create()
                    .searchPackages(scopePackage)
                    .matcher(
                        ClassMatcher.create()
                            .className("com.android.keyguard.lockscreen.charge.ChargingAnimationController")
                            .fields(
                                FieldsMatcher.create()
                                    .add(FieldMatcher.create().type("android.os.Handler"))
                            )
                    )
            ).singleOrNull()

            if (classData != null) {
                val fields = classData.fields
                // 1) Prefer Handler-type fields (including $ inner-class forms)
                var handlerFieldName: String? = null
                for (fd in fields) {
                    val ft = fd.typeName
                    if (ft == "android.os.Handler" || ft.endsWith(".Handler") || ft.contains("$")) {
                        handlerFieldName = fd.name
                        break
                    }
                }
                // 2) Fallback: first non-primitive field whose type is not java./android.
                if (handlerFieldName == null) {
                    for (fd in fields) {
                        val ft = fd.typeName
                        if (!ft.startsWith("java.") && !ft.startsWith("android.") && !isPrimitiveType(ft)) {
                            handlerFieldName = fd.name
                            break
                        }
                    }
                }
                if (handlerFieldName != null) {
                    out.addProperty(DexIndexConstants.Keys.HANDLER_FIELD_NAME, handlerFieldName)
                    Log.i(TAG, "NoChargeAnimation: handler field = $handlerFieldName")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "NoChargeAnimation: field discovery failed", t)
        }
        return out
    }

    private fun indexNetworkSpeedDoublelayer(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        // Inner class with superClass = Handler whose name starts with NetworkSpeedView$
        try {
            val matches = bridge.findClass(
                FindClass.create()
                    .searchPackages(scopePackage)
                    .matcher(ClassMatcher.create().superClass("android.os.Handler"))
            )
            for (cd in matches) {
                val name = cd.name
                if (name.startsWith("com.android.systemui.zui.NetworkSpeedView$")) {
                    out.addProperty(DexIndexConstants.Keys.HANDLER_INNER_CLASS, name)
                    Log.i(TAG, "SystemUINetworkSpeeddoublelayerHook: handler inner class = $name")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "SystemUINetworkSpeeddoublelayerHook: handler class query failed", t)
        }
        return out
    }

    private fun isPrimitiveType(typeName: String): Boolean {
        return typeName == "boolean" || typeName == "byte" || typeName == "char"
                || typeName == "short" || typeName == "int" || typeName == "long"
                || typeName == "float" || typeName == "double" || typeName == "void"
    }

    private companion object {
        const val TAG = "SystemUiDexIndexer"
    }
}
