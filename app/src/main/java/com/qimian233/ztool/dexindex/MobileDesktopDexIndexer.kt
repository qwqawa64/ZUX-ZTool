package com.qimian233.ztool.dexindex

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.qimian233.ztool.data.keys.ScopeKeys
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier

/**
 * mobiledesktop 作用域（com.motorola.mobiledesktop）离线索引器。
 *
 * 原样迁移自以下 Hook 的 DexKit 查询：
 * - BypassShareWarningHook（manager 类/工厂/设置方法、弹窗方法、磁贴刷新方法）
 * - DisableNearbyShareAutoOffHook（混淆类名与方法名）
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
                // 过滤类初始化方法后要求唯一匹配（保留原 singleOrNull 语义）
                .filter { it.name != "<clinit>" }
                .singleOrNull()
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
                // 过滤类初始化方法后要求唯一匹配（保留原 singleOrNull 语义）
                .filter { it.name != "<clinit>" }
                .singleOrNull()
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

    private companion object {
        const val TAG = "MobileDesktopDexIndexer"
    }
}
