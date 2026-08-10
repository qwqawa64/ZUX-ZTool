package com.qimian233.ztool.dexindex

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.qimian233.ztool.data.keys.ScopeKeys
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.FieldMatcher
import org.luckypray.dexkit.query.matchers.FieldsMatcher

/**
 * systemui 作用域（com.android.systemui）离线索引器。
 *
 * 原样迁移自以下 Hook 的 DexKit 查询：
 * - NoChargeAnimation（ChargingAnimationController 的 Handler 字段名）
 * - SystemUINetworkSpeeddoublelayerHook（NetworkSpeedView 的 Handler 内部类名）
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

    // ── NoChargeAnimation ───────────────────────────────────────────

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
                // 1) 优先 Handler 类型字段（含 $ 内部类形态）
                var handlerFieldName: String? = null
                for (fd in fields) {
                    val ft = fd.typeName
                    if (ft == "android.os.Handler" || ft.endsWith(".Handler") || ft.contains("$")) {
                        handlerFieldName = fd.name
                        break
                    }
                }
                // 2) 回退：第一个非 java./android. 的非基本类型字段
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

    // ── SystemUINetworkSpeeddoublelayerHook ─────────────────────────

    private fun indexNetworkSpeedDoublelayer(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        // superClass = Handler 且名字以 NetworkSpeedView$ 开头的内部类
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
