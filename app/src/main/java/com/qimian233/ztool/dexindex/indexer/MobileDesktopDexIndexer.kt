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
import java.lang.reflect.Modifier

/**
 * mobiledesktop 作用域（com.motorola.mobiledesktop）离线索引器。
 *
 * 原样迁移自以下 Hook 的 DexKit 查询：
 * - BypassShareWarningHook（manager 类/工厂/设置方法、弹窗方法、磁贴刷新方法）
 * - DisableNearbyShareAutoOffHook（混淆类名与方法名）
 * - AutoAcceptFileTransferHook（ViewModel 字段、boolean 字段、LiveData 字段、LiveData 更新方法）
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
        indexBypassManager(bridge, out)
        indexBypassDialogMethod(bridge, out)
        indexBypassTileRefreshMethod(bridge, out)
        return out
    }

    /** 在 manager 包中找含 static (Context) 与 (boolean)→void 方法的类。 */
    private fun indexBypassManager(bridge: DexKitBridge, out: JsonObject) {
        try {
            val managerData = bridge.findClass {
                searchPackages("com.motorola.mobiledesktop.manager")
                matcher {
                    methods {
                        add {
                            modifiers = Modifier.STATIC or Modifier.PUBLIC
                            paramTypes("android.content.Context")
                        }
                        add {
                            paramTypes("boolean")
                            returnType = "void"
                        }
                    }
                }
            }.singleOrNull()

            if (managerData != null) {
                var factoryMethod: String? = null
                var setMethod: String? = null
                for (md in managerData.methods) {
                    val params = md.paramTypeNames
                    if (params.size == 1 && params[0] == "boolean" && md.returnTypeName == "void") {
                        setMethod = md.name
                    } else if (params.size == 1 && params[0] == "android.content.Context" && md.returnTypeName != "void") {
                        factoryMethod = md.name
                    }
                }
                out.addProperty(DexIndexConstants.Keys.MANAGER_CLASS, managerData.name)
                factoryMethod?.let { out.addProperty(DexIndexConstants.Keys.MANAGER_FACTORY_METHOD, it) }
                setMethod?.let { out.addProperty(DexIndexConstants.Keys.MANAGER_SET_METHOD, it) }
                Log.i(TAG, "BypassShareWarningHook: manager = ${managerData.name} / $factoryMethod / $setMethod")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "BypassShareWarningHook: manager discovery failed", t)
        }
    }

    /** 弹窗 Activity 中无参 void 且引用 R.string.file_share_expose_title 的方法。 */
    private fun indexBypassDialogMethod(bridge: DexKitBridge, out: JsonObject) {
        try {
            val md = bridge.findMethod {
                searchPackages(scopePackage)
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

    /** BaseFileUnionTile 中无参 void 方法（刷新磁贴）。 */
    private fun indexBypassTileRefreshMethod(bridge: DexKitBridge, out: JsonObject) {
        try {
            val md = bridge.findMethod {
                searchPackages(scopePackage)
                matcher {
                    paramTypes()
                    returnType = "void"
                    declaredClass = "com.motorola.readyfor.tile.BaseFileUnionTile"
                }
            }
                // 排除类初始化方法后要求唯一匹配（保留原 singleOrNull 语义）
                .singleOrNull { it.name != "<clinit>" }
            if (md != null) {
                out.addProperty(DexIndexConstants.Keys.TILE_REFRESH_METHOD, md.name)
                Log.i(TAG, "BypassShareWarningHook: tile refresh method = ${md.name}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "BypassShareWarningHook: tile refresh query failed", t)
        }
    }

    // ── DisableNearbyShareAutoOffHook ───────────────────────────────

    private fun indexDisableNearbyShareCountdown(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        // 包 ra 中含 ()→void 与 static (Context) 方法的类
        try {
            val classData = bridge.findClass {
                searchPackages("ra")
                matcher {
                    methods {
                        add {
                            paramTypes()
                            returnType = "void"
                        }
                        add {
                            modifiers = Modifier.STATIC or Modifier.PUBLIC
                            paramTypes("android.content.Context")
                        }
                    }
                }
            }.singleOrNull()

            if (classData != null) {
                out.addProperty(DexIndexConstants.Keys.TARGET_CLASS, classData.name)
                for (md in classData.methods) {
                    if (md.paramTypeNames.isEmpty() && md.returnTypeName == "void" && md.name != "<clinit>") {
                        out.addProperty(DexIndexConstants.Keys.TARGET_METHOD, md.name)
                        break
                    }
                }
                Log.i(TAG, "DisableNearbyShareAutoOffHook: target = ${classData.name}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "DisableNearbyShareAutoOffHook: discovery failed", t)
        }
        return out
    }

    // ── AutoAcceptFileTransferHook ──────────────────────────────────

    /**
     * 链式 4 查询：Activity 中找 ViewModel 字段 → ViewModel 类中找
     * boolean/LiveData 字段 → LiveData 继承链中找 (Object)void 更新方法。
     * 查询 A/C 按父类/接口匹配字段类型，查询 D 沿 superClass 链上溯
     * （与 Java 反射版 while 循环语义一致）。
     */
    private fun indexAutoAcceptFileTransfer(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        try {
            // 步骤 A：FileConnectionConfirmActivity 中找 ViewModel 子类型字段
            val activityClass = bridge.findClass {
                searchPackages(scopePackage)
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

            // 步骤 B：ViewModel 类中找 boolean 字段
            val acceptedField: FieldData? = vmClass.fields.firstOrNull { field ->
                field.typeName == "boolean"
            }
            if (acceptedField != null) {
                out.addProperty(DexIndexConstants.Keys.ACCEPTED_FIELD_NAME, acceptedField.name)
                Log.i(TAG, "AutoAcceptFileTransferHook: accepted field = ${acceptedField.name}")
            }

            // 步骤 C：ViewModel 类中找 LiveData 子类型字段
            val liveDataField: FieldData? = vmClass.fields.firstOrNull { field ->
                isSubclassOf(field.type, "androidx.lifecycle.LiveData")
            }
            if (liveDataField == null) {
                Log.w(TAG, "AutoAcceptFileTransferHook: LiveData field not found")
                return out
            }
            out.addProperty(DexIndexConstants.Keys.LIVE_DATA_FIELD_NAME, liveDataField.name)
            val liveDataClass: ClassData = liveDataField.type
            Log.i(TAG, "AutoAcceptFileTransferHook: liveData field = ${liveDataField.name} / class = ${liveDataClass.name}")

            // 步骤 D：LiveData 继承链中找 (Object)void 方法（第一个匹配，与 Java 语义一致）
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

    /** 沿继承链上溯找第一个签名 (Object)void 的方法。 */
    private fun findObjectVoidMethod(cls: ClassData): MethodData? {
        var current: ClassData? = cls
        while (current != null && current.name != "java.lang.Object") {
            for (m in current.methods) {
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
