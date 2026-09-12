package com.qimian233.ztool.dexindex.indexer

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.dexindex.base.DexIndexer
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData

/**
 * mobiledesktop 作用域（com.motorola.mobiledesktop）离线索引器。
 *
 * 原样迁移自以下 Hook 的 DexKit 查询：
 * - BypassShareWarningHook（弹窗方法、磁贴刷新方法；旧版 manager 类已随
 *   新版混淆消失，启用路径改走 Hook 内硬编码的 MotoDiscoveryManager）
 * - DisableNearbyShareAutoOffHook（FileUnionSwitchManager 混淆类/方法，
 *   以日志字符串 "startCountDown()" 作为反混淆锚点）
 * - AutoAcceptFileTransferHook（ViewModel 字段、boolean 字段、LiveData 字段、
 *   LiveData 更新方法）
 *
 * 注意：目标类大多位于 `com.motorola.readyfor.*` / `com.motorola.motoaccount.sdk.*`
 * 包（不在 scopePackage 之下），因此查询不得用 `searchPackages(scopePackage)` 收窄，
 * 否则会静默查空。
 */
class MobileDesktopDexIndexer : DexIndexer {

    override val scopePackage: String = ScopeKeys.MOBILE_DESKTOP.packageName

    override fun index(bridge: DexKitBridge, context: Context): JsonObject {
        val modules = JsonObject()
        modules.add(DexIndexConstants.ModuleKeys.BYPASS_SHARE_WARNING, indexBypassShareWarning(bridge))
        modules.add(
            DexIndexConstants.ModuleKeys.DISABLE_NEARBY_SHARE_COUNTDOWN,
            indexDisableNearbyShareCountdown(bridge)
        )
        modules.add(
            DexIndexConstants.ModuleKeys.AUTO_ACCEPT_FILE_TRANSFER,
            indexAutoAcceptFileTransfer(bridge)
        )
        return modules
    }

    // ── BypassShareWarningHook ──────────────────────────────────────

    private fun indexBypassShareWarning(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        indexBypassDialogMethod(bridge, out)
        indexBypassTileRefreshMethod(bridge, out)
        return out
    }

    /** 弹窗 Activity 中无参 void 且引用 R.string.file_share_expose_title 字段的方法。 */
    private fun indexBypassDialogMethod(bridge: DexKitBridge, out: JsonObject) {
        try {
            val md = bridge.findMethod {
                matcher {
                    paramTypes()
                    returnType = "void"
                    declaredClass = "com.motorola.readyfor.common.dialog.ActionNoticeCommonDialogActivity"
                    usingFields {
                        add {
                            name = "file_share_expose_title"
                        }
                    }
                }
            }
                // 排除类初始化方法后要求唯一匹配（保留原 singleOrNull 语义）
                .singleOrNull { it.name != "<clinit>" }
            if (md != null) {
                out.addProperty(DexIndexConstants.Keys.DIALOG_METHOD, md.name)
                Log.i(TAG, "BypassShareWarningHook: dialog method = ${md.name}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "BypassShareWarningHook: dialog method query failed", t)
        }
    }

    /** BaseFileUnionTile 中引用 "refreshTile" 日志串的无参 void 方法（刷新磁贴）。 */
    private fun indexBypassTileRefreshMethod(bridge: DexKitBridge, out: JsonObject) {
        try {
            val md = bridge.findMethod {
                matcher {
                    paramTypes()
                    returnType = "void"
                    declaredClass = "com.motorola.readyfor.tile.BaseFileUnionTile"
                    usingStrings("refreshTile")
                }
            }
                .singleOrNull { it.name != "<clinit>" }
            if (md != null) {
                out.addProperty(DexIndexConstants.Keys.TILE_REFRESH_METHOD, md.name)
                Log.i(TAG, "BypassShareWarningHook: tile refresh method = ${md.name}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "BypassShareWarningHook: tile refresh method query failed", t)
        }
    }

    // ── DisableNearbyShareAutoOffHook ───────────────────────────────

    /**
     * FileUnionSwitchManager（新版本混淆至 com.motorola.motoaccount.sdk.se.c）：
     * 其 startCountDown 方法是全 APK 唯一引用日志串 "startCountDown()" 的
     * 无参 void 方法，以此作为反混淆锚点同时定位类与方法。
     */
    private fun indexDisableNearbyShareCountdown(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        try {
            val md = bridge.findMethod {
                matcher {
                    paramTypes()
                    returnType = "void"
                    usingStrings("startCountDown()")
                }
            }.singleOrNull()
            if (md != null) {
                val targetClass = md.declaredClass?.name
                if (targetClass == null) {
                    Log.w(TAG, "DisableNearbyShareAutoOffHook: declared class unavailable")
                    return out
                }
                out.addProperty(DexIndexConstants.Keys.TARGET_CLASS, targetClass)
                out.addProperty(DexIndexConstants.Keys.TARGET_METHOD, md.name)
                Log.i(TAG, "DisableNearbyShareAutoOffHook: target = $targetClass / ${md.name}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "DisableNearbyShareAutoOffHook: discovery failed", t)
        }
        return out
    }

    // ── AutoAcceptFileTransferHook ──────────────────────────────────

    /**
     * 链式 4 查询：Activity 中找 ViewModel 字段 → ViewModel 类中找 boolean 字段 →
     * 在 Activity.onStart 引用的 LiveData 字段（用户接受/拒绝决策信号，
     * 对应通知栏"接受"按钮的写入路径）→ LiveData 继承链中找 (Object)void 更新方法。
     * 查询 D 沿 superClass 链上溯（与 Java 反射版 while 循环语义一致）。
     */
    private fun indexAutoAcceptFileTransfer(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        try {
            // 步骤 A：FileConnectionConfirmActivity 中找 ViewModel 子类型字段
            val activityClass = bridge.findClass {
                matcher {
                    className("com.motorola.mobiledesktop.files.pc2phone.FileConnectionConfirmActivity")
                }
            }.singleOrNull()
            if (activityClass == null) {
                Log.w(TAG, "AutoAcceptFileTransferHook: activity class not found")
                return out
            }

            val vmField: FieldData? = activityClass.fields.firstOrNull { field ->
                isSubclassOf(field.type, "androidx.lifecycle.ViewModel")
            }
            if (vmField == null) {
                Log.w(TAG, "AutoAcceptFileTransferHook: ViewModel field not found")
                return out
            }
            out.addProperty(DexIndexConstants.Keys.VM_FIELD_NAME, vmField.name)
            val vmClass: ClassData = vmField.type
            Log.i(TAG, "AutoAcceptFileTransferHook: vm field = ${vmField.name} / class = ${vmClass.name}")

            // 步骤 B：ViewModel 类中找 boolean 字段（accepted 标记）
            val acceptedField: FieldData? = vmClass.fields.firstOrNull { field ->
                field.typeName == "boolean"
            }
            if (acceptedField != null) {
                out.addProperty(DexIndexConstants.Keys.ACCEPTED_FIELD_NAME, acceptedField.name)
                Log.i(TAG, "AutoAcceptFileTransferHook: accepted field = ${acceptedField.name}")
            }

            // 步骤 C：onStart 中写入的那个 LiveData 字段即"用户决策"信号；
            // 通知栏接受路径为 onStart 内 accepted=true + 该字段 postValue(true)。
            val liveDataField: FieldData? = findOnStartLiveDataField(bridge, activityClass, vmClass)
            if (liveDataField == null) {
                Log.w(TAG, "AutoAcceptFileTransferHook: accept LiveData field not found")
                return out
            }
            out.addProperty(DexIndexConstants.Keys.LIVE_DATA_FIELD_NAME, liveDataField.name)
            val liveDataClass: ClassData = liveDataField.type
            Log.i(TAG, "AutoAcceptFileTransferHook: liveData field = ${liveDataField.name} / class = ${liveDataClass.name}")

            // 步骤 D：LiveData 继承链中找 (Object)void 方法（跳过构造器）
            val updateMethod: MethodData? = findObjectVoidMethod(liveDataClass)
            if (updateMethod != null) {
                out.addProperty(DexIndexConstants.Keys.LIVE_DATA_UPDATE_METHOD, updateMethod.name)
                Log.i(TAG, "AutoAcceptFileTransferHook: update method = ${updateMethod.name}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "AutoAcceptFileTransferHook: discovery failed", t)
        }
        return out
    }

    /**
     * 在 Activity.onStart 方法引用的 ViewModel LiveData 字段中定位"用户决策"字段。
     * onStart 为框架回调（不可混淆），其中对该 LiveData 的 postValue 调用即
     * 通知栏接受/拒绝按钮的写入路径；而 onCreate 观察了全部 LiveData 字段，
     * 不能作为区分依据。
     */
    private fun findOnStartLiveDataField(
        bridge: DexKitBridge,
        activityClass: ClassData,
        vmClass: ClassData,
    ): FieldData? {
        val liveDataFields = vmClass.fields.filter { field ->
            isSubclassOf(field.type, "androidx.lifecycle.LiveData")
        }
        if (liveDataFields.isEmpty()) return null
        // 仅一个候选时无需区分
        if (liveDataFields.size == 1) return liveDataFields.first()
        for (candidate in liveDataFields) {
            val used = bridge.findMethod {
                matcher {
                    name = "onStart"
                    declaredClass = activityClass.name
                    usingFields {
                        add {
                            name = candidate.name
                            declaredClass = vmClass.name
                        }
                    }
                }
            }
            if (used.isNotEmpty()) {
                return candidate
            }
        }
        return null
    }

    /** 检查 [cls] 的继承链（含接口）是否包含指定名称的超类。 */
    private fun isSubclassOf(cls: ClassData, superName: String): Boolean {
        var current: ClassData? = cls
        while (current != null && current.name != "java.lang.Object") {
            if (superName == current.name) return true
            for (iface in current.interfaces) {
                if (superName == iface.name) return true
            }
            current = current.superClass
        }
        return false
    }

    /** 沿继承链上溯找第一个签名 (Object)void 的方法（构造器不是更新方法，跳过）。 */
    private fun findObjectVoidMethod(cls: ClassData): MethodData? {
        var current: ClassData? = cls
        while (current != null && current.name != "java.lang.Object") {
            for (m in current.methods) {
                if (m.name == "<init>" || m.name == "<clinit>") continue
                val params = m.paramTypeNames
                if (params.size == 1 && params[0] == "java.lang.Object"
                    && m.returnTypeName == "void"
                ) {
                    return m
                }
            }
            current = current.superClass
        }
        return null
    }

    private companion object {
        const val TAG = "MobileDesktopDexIndexer"
    }
}
