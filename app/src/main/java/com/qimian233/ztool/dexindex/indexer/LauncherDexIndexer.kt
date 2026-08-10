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
 * launcher 作用域（com.zui.launcher）离线索引器。
 *
 * 原样迁移自以下 Hook 的 DexKit 查询：
 * - CleanGlobalSearch（热词初始化/数据填充方法名）
 * - DisableForceStop（OverviewUtilities force-stop 方法名）
 * - ZuiLauncherHotseatHook（LoaderCursor 尺寸检查方法名）
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

    // ── CleanGlobalSearch ───────────────────────────────────────────

    private fun indexCleanGlobalSearch(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        // 无参 void 方法（HotWordView 初始化），原 discoverInitMethods 取第一个匹配
        try {
            val methods = bridge.findMethod {
                searchPackages(scopePackage)
                matcher {
                    paramTypes()
                    returnType = "void"
                    declaredClass = "com.zui.launcher.GlobalSearchView"
                }
            }
            // 过滤类初始化方法（无参 void 查询会命中 <clinit>，反射无法获取）；
            // 遍历取第一个匹配（DexKit 的 firstOrNull 扩展不推荐非唯一结果）
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

        // (List) → void 方法（热词数据填充 E0），原 discoverE0Method 取 singleOrNull
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

    // ── DisableForceStop ────────────────────────────────────────────

    private fun indexDisableForceStop(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        // (Context, String, int) → void，声明类 OverviewUtilities，跳过 removeAppProcess
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

    // ── ZuiLauncherHotseatHook ──────────────────────────────────────

    private fun indexZuiLauncherHotseat(bridge: DexKitBridge): JsonObject {
        val out = JsonObject()
        // (ItemInfo) → boolean，声明类 LoaderCursor；ItemInfo 为未混淆公开类
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
