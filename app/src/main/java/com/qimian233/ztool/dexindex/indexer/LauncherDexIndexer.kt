package com.qimian233.ztool.dexindex.indexer

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.dexindex.base.DexIndexer
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

/**
 * launcher scope (com.zui.launcher) offline indexer.
 *
 * Migrated as-is from the DexKit queries of these Hooks:
 * - CleanGlobalSearch (hotword init / data population method names)
 * - DisableForceStop (OverviewUtilities force-stop method name)
 * - ZuiLauncherHotseatHook (LoaderCursor size-check method name)
 */
class LauncherDexIndexer : DexIndexer {

    override val scopePackage: String = ScopeKeys.LAUNCHER.packageName

    override fun index(bridge: DexKitBridge, context: Context): JsonObject {
        val modules = JsonObject()
        modules.add(DexIndexConstants.ModuleKeys.CLEAN_GLOBAL_SEARCH, indexCleanGlobalSearch(bridge))
        modules.add(DexIndexConstants.ModuleKeys.DISABLE_FORCE_STOP, indexDisableForceStop(bridge))
        modules.add(DexIndexConstants.ModuleKeys.ZUI_LAUNCHER_HOTSEAT, indexZuiLauncherHotseat(bridge))
        return modules
    }

    private fun indexCleanGlobalSearch(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        // No-arg void method (HotWordView init); original discoverInitMethods took the first match
        try {
            val methods = bridge.findMethod {
                searchPackages(scopePackage)
                matcher {
                    paramTypes()
                    returnType = "void"
                    declaredClass = "com.zui.launcher.GlobalSearchView"
                }
            }
            // Filter class initializers (the no-arg void query matches <clinit>, which reflection cannot get);
            // iterate and take the first match (DexKit's firstOrNull extension is not recommended for non-unique results)
            for (md in methods) {
                if (md.name != "<clinit>") {
                    out.addProperty(DexIndexConstants.Keys.HOTWORD_INIT_METHOD, md.name)
                    Log.i(TAG, "CleanGlobalSearch: hotword init method = ${md.name}")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "CleanGlobalSearch: hotword init query failed", t)
        }

        // (List) → void method (hotword data population E0); original discoverE0Method took singleOrNull
        try {
            val result = bridge.findMethod {
                searchPackages(scopePackage)
                matcher {
                    paramTypes("java.util.List")
                    returnType = "void"
                    declaredClass = "com.zui.launcher.GlobalSearchView"
                }
            }.singleOrNull()
            if (result != null) {
                out.addProperty(DexIndexConstants.Keys.HOTWORD_DATA_METHOD, result.name)
                Log.i(TAG, "CleanGlobalSearch: hotword data method = ${result.name}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "CleanGlobalSearch: hotword data query failed", t)
        }
        return out
    }

    private fun indexDisableForceStop(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        // (Context, String, int) → void, declared in OverviewUtilities, skip removeAppProcess
        try {
            val methods = bridge.findMethod(
                FindMethod.create()
                    .searchPackages(scopePackage)
                    .matcher(
                        MethodMatcher.create()
                            .paramTypes("android.content.Context", "java.lang.String", "int")
                            .returnType("void")
                            .declaredClass("com.zui.launcher.util.OverviewUtilities")
                    )
            )
            for (md in methods) {
                if (md.name != "removeAppProcess") {
                    out.addProperty(DexIndexConstants.Keys.FORCE_STOP_METHOD, md.name)
                    Log.i(TAG, "DisableForceStop: force-stop method = ${md.name}")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "DisableForceStop: force-stop query failed", t)
        }
        return out
    }

    private fun indexZuiLauncherHotseat(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        // (ItemInfo) → boolean, declared in LoaderCursor; ItemInfo is an unobfuscated public class
        try {
            val methods = bridge.findMethod(
                FindMethod.create()
                    .searchPackages("com.android.launcher3")
                    .matcher(
                        MethodMatcher.create()
                            .paramTypes("com.android.launcher3.model.data.ItemInfo")
                            .returnType("boolean")
                            .declaredClass("com.android.launcher3.model.LoaderCursor")
                    )
            )
            for (md in methods) {
                out.addProperty(DexIndexConstants.Keys.LOADER_CURSOR_B_METHOD, md.name)
                Log.i(TAG, "ZuiLauncherHotseatHook: LoaderCursor method = ${md.name}")
                break
            }
        } catch (t: Throwable) {
            Log.w(TAG, "ZuiLauncherHotseatHook: LoaderCursor query failed", t)
        }
        return out
    }

    private companion object {
        const val TAG = "LauncherDexIndexer"
    }
}
