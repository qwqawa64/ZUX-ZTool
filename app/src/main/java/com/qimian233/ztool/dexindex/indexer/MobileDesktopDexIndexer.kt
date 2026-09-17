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
 * mobiledesktop scope (com.motorola.mobiledesktop) offline indexer.
 *
 * Migrated as-is from the DexKit queries of these Hooks:
 * - BypassShareWarningHook (dialog method, tile refresh method)
 * - DisableNearbyShareAutoOffHook (obfuscated FileUnionSwitchManager class /
 *   method, anchored on the log string "startCountDown()" for deobfuscation)
 * - AutoAcceptFileTransferHook (ViewModel field, boolean field, LiveData field,
 *   LiveData update method)
 *
 * Note: target classes mostly live in `com.motorola.readyfor.*` /
 * `com.motorola.motoaccount.sdk.*` packages (not under scopePackage), so the
 * queries must not narrow with `searchPackages(scopePackage)`, otherwise they
 * silently return nothing.
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

    private fun indexBypassShareWarning(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        indexBypassDialogMethod(bridge, out)
        indexBypassTileRefreshMethod(bridge, out)
        return out
    }

    /** No-arg void method in the dialog Activity that references an R.string.file_share_expose_title field. */
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
                // Exclude class initializers, then require a unique match (keeps the original singleOrNull semantics)
                .singleOrNull { it.name != "<clinit>" }
            if (md != null) {
                out.addProperty(DexIndexConstants.Keys.DIALOG_METHOD, md.name)
                Log.i(TAG, "BypassShareWarningHook: dialog method = ${md.name}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "BypassShareWarningHook: dialog method query failed", t)
        }
    }

    /** No-arg void method in BaseFileUnionTile that references the "refreshTile" log string (refreshes the tile). */
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

    /**
     * FileUnionSwitchManager (obfuscated to com.motorola.motoaccount.sdk.se.c in
     * newer versions): its startCountDown method is the only no-arg void method
     * in the whole APK referencing the log string "startCountDown()", used as a
     * deobfuscation anchor to locate both the class and the method.
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

    /**
     * Chained 4-query: find the ViewModel field in the Activity → find the
     * boolean field in the ViewModel class → find the LiveData field referenced
     * in Activity.onStart (the user accept/reject decision signal, the write
     * path of the notification "accept" button) → find the (Object)void update
     * method in the LiveData inheritance chain.
     * Query D walks up the superClass chain (same semantics as the Java
     * reflection version's while loop).
     */
    private fun indexAutoAcceptFileTransfer(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        try {
            // Step A: find the ViewModel subtype field in FileConnectionConfirmActivity
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

            // Step B: find the boolean field in the ViewModel class (accepted flag)
            val acceptedField: FieldData? = vmClass.fields.firstOrNull { field ->
                field.typeName == "boolean"
            }
            if (acceptedField != null) {
                out.addProperty(DexIndexConstants.Keys.ACCEPTED_FIELD_NAME, acceptedField.name)
                Log.i(TAG, "AutoAcceptFileTransferHook: accepted field = ${acceptedField.name}")
            }

            // Step C: the LiveData field written in onStart is the "user decision" signal;
            // the notification accept path is accepted=true inside onStart plus postValue(true) on this field.
            val liveDataField: FieldData? = findOnStartLiveDataField(bridge, activityClass, vmClass)
            if (liveDataField == null) {
                Log.w(TAG, "AutoAcceptFileTransferHook: accept LiveData field not found")
                return out
            }
            out.addProperty(DexIndexConstants.Keys.LIVE_DATA_FIELD_NAME, liveDataField.name)
            val liveDataClass: ClassData = liveDataField.type
            Log.i(TAG, "AutoAcceptFileTransferHook: liveData field = ${liveDataField.name} / class = ${liveDataClass.name}")

            // Step D: find the (Object)void method in the LiveData inheritance chain (skip constructors)
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
     * Locates the "user decision" LiveData field among the ViewModel LiveData
     * fields referenced by Activity.onStart. onStart is a framework callback
     * (cannot be obfuscated); the postValue call on that LiveData inside it is
     * the write path of the notification accept/reject buttons, whereas onCreate
     * observes all LiveData fields and cannot be used for discrimination.
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
        // No need to discriminate when there is only one candidate
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

    /** Checks whether the inheritance chain (including interfaces) of [cls] contains the named superclass. */
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

    /** Walks up the inheritance chain to find the first (Object)void method (constructors are not update methods, skip them). */
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
