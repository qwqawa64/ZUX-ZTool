package com.qimian233.ztool.hook.modules.mobiledesktop

import android.content.Context
import com.qimian233.ztool.hook.base.BaseHookModule
import com.qimian233.ztool.hook.base.DexKitHelper
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Modifier

/**
 * 绕过超级互联分享警告弹窗的 Hook。
 *
 * 使用 DEXKit 通过方法签名（参数类型+返回类型）动态匹配混淆后的方法名，
 * 不再依赖硬编码的单字母名称。
 */
class BypassShareWarningHook : BaseHookModule() {

    companion object {
        private const val TARGET_PACKAGE = "com.motorola.mobiledesktop"
        private const val TARGET_CLASS = "com.motorola.readyfor.tile.BaseFileUnionTile"
        private const val DIALOG_CLASS =
            "com.motorola.readyfor.common.dialog.ActionNoticeCommonDialogActivity"
        private const val MANAGER_PKG = "com.motorola.mobiledesktop.manager"
        // 已知存在于主 APK 中的管理类，用作 DexKit 桥梁的锚点。
        // TARGET_CLASS (com.motorola.readyfor.*) 在 LSPosed 环境下
        // protectionDomain 可能为 null，导致 getBridgeForClass 失败。
        // c0 位于 com.motorola.mobiledesktop 包名下，protectionDomain 稳定有效。
        private const val ANCHOR_CLASS = "com.motorola.mobiledesktop.manager.c0"
        private const val PREFS_NAME = "moto_ble_preference"
        private const val PREF_KEY = "file_union_transfer_switch"
    }

    override fun getModuleName(): String = "bypass_share_warning"

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        // ── DEXKit：预解析管理器类和方法 ─────────────────────────────
        val bridge = DexKitHelper.getBridgeForClass(classLoader, ANCHOR_CLASS)

        var managerClassName: String? = null
        var managerFactoryMethodName: String? = null   // static factory: (Context) → manager
        var managerSetMethodName: String? = null        // instance: (boolean) → void

        if (bridge != null) {
            try {
                // 1. 查找管理器类：在 manager 包中找一个有 static (Context) 方法和 (boolean)void 方法的类
                val managerData = bridge.findClass {
                    searchPackages(MANAGER_PKG)
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
                    managerClassName = managerData.name
                    for (md in managerData.methods) {
                        val params = md.paramTypeNames
                        if (params.size == 1 && params[0] == "boolean" && md.returnTypeName == "void") {
                            managerSetMethodName = md.name
                        } else if (params.size == 1 && params[0] == "android.content.Context" && md.returnTypeName != "void") {
                            managerFactoryMethodName = md.name
                        }
                    }
                }

                // 回退硬编码名称
                if (managerClassName == null) managerClassName = "$MANAGER_PKG.c0"
                if (managerFactoryMethodName == null) managerFactoryMethodName = "l"
                if (managerSetMethodName == null) managerSetMethodName = "z"

            } catch (dexKitError: Throwable) {
                logError("DEXKit method discovery failed, using hardcoded names", dexKitError)
                managerClassName = "$MANAGER_PKG.c0"
                managerFactoryMethodName = "l"
                managerSetMethodName = "z"
            }
        } else {
            managerClassName = "$MANAGER_PKG.c0"
            managerFactoryMethodName = "l"
            managerSetMethodName = "z"
        }

        val finalManagerClass = managerClassName
        val finalFactoryMethod = managerFactoryMethodName
        val finalSetMethod = managerSetMethodName

        try {
            // ── Hook 1: 磁贴点击 ───────────────────────────────────
            val baseFileUnionTileClass = classLoader.loadClass(TARGET_CLASS)
            val onClickMethod = baseFileUnionTileClass.getDeclaredMethod("onClick")
            xposed.hook(onClickMethod).intercept { chain ->
                val tile = chain.thisObject
                val context = getContext(tile) ?: return@intercept chain.proceed()

                val enabled = isNearbyShareEnabled(context)
                log("IsNearbyShareEnabled: $enabled")
                if (enabled) {
                    log("Nearby share already enabled, keep original disable flow.")
                    return@intercept chain.proceed()
                }

                setNearbyShareEnabled(
                    tile, context, classLoader,
                    finalManagerClass, finalFactoryMethod, finalSetMethod
                )
                log("Bypassed warning and enabled nearby share directly.")
                null
            }
            log("Installed hook for BaseFileUnionTile.onClick")
        } catch (t: Throwable) {
            logError("Failed to hook BaseFileUnionTile.onClick", t)
        }

        try {
            // ── Hook 2: 通用弹窗场景 ─────────────────────────────────
            val actionNoticeClass = classLoader.loadClass(DIALOG_CLASS)

            // 通过 DEXKit 动态查找 p() 方法 — 无参 void + 引用 file_share_expose_title 字段
            var pMethodName = "p" // 默认回退
            if (bridge != null) {
                try {
                    val md = bridge.findMethod {
                        searchPackages(TARGET_PACKAGE)
                        matcher {
                            paramTypes()
                            returnType = "void"
                            declaredClass = DIALOG_CLASS
                            // 收窄：方法体中引用了 R.string.file_share_expose_title 字段，
                            // R 类不会被混淆，因此字段名跨版本稳定。
                            usingFields {
                                add {
                                    name = "file_share_expose_title"
                                }
                            }
                        }
                    }.singleOrNull()
                    log("md: $md")
                    if (md != null) pMethodName = md.name
                } catch (th: Throwable) {
                    logError("Unable to find method with DexKit: ", th)
                }
            }
            val finalPMethodName = pMethodName
            log("target method name of \"createAndStartExposureWarnDialog\": $finalPMethodName")

            val pMethod = actionNoticeClass.getDeclaredMethod(finalPMethodName)
            xposed.hook(pMethod).intercept { chain ->
                val myObject = chain.thisObject
                val context = getContext(myObject)

                val managerClass = classLoader.loadClass(finalManagerClass)
                val lMethod = managerClass.getDeclaredMethod(finalFactoryMethod, Context::class.java)
                val manager = lMethod.invoke(null, context)
                if (manager == null) {
                    log("Unable to get manager!")
                    return@intercept null
                }
                val zMethod = manager.javaClass.getDeclaredMethod(
                    finalSetMethod, Boolean::class.javaPrimitiveType
                )
                zMethod.isAccessible = true
                zMethod.invoke(manager, true)
                null
            }
            log("Installed hook for dialog activity method: $finalPMethodName")
        } catch (e: Exception) {
            logError("Failed to hook createAndStartExposureWarnIntent: ", e)
        }
    }

    private fun getContext(tile: Any?): Context? {
        return try {
            val getApplicationContextMethod = tile!!.javaClass.getMethod("getApplicationContext")
            val context = getApplicationContextMethod.invoke(tile)
            context as? Context
        } catch (_: Throwable) {
            null
        }
    }

    private fun isNearbyShareEnabled(context: Context): Boolean {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_KEY, false)
        } catch (t: Throwable) {
            logError("Failed to read nearby share state", t)
            false
        }
    }

    private fun setNearbyShareEnabled(
        tile: Any?,
        context: Context,
        classLoader: ClassLoader,
        managerClass: String,
        factoryMethod: String,
        setMethod: String
    ) {
        try {
            val mc = classLoader.loadClass(managerClass)
            val lMethod = mc.getDeclaredMethod(factoryMethod, Context::class.java)
            val manager = lMethod.invoke(null, context)
            if (manager == null) {
                log("Unable to get manager!")
                return
            }
            val zMethod = manager.javaClass.getDeclaredMethod(
                setMethod, Boolean::class.javaPrimitiveType
            )
            zMethod.isAccessible = true
            zMethod.invoke(manager, true)

            // 同样动态查找 b() 方法
            var bMethodName = "b"
            val bridge = DexKitHelper.getBridgeForClass(classLoader, ANCHOR_CLASS)
            if (bridge != null) {
                try {
                    val md = bridge.findMethod {
                        searchPackages(TARGET_PACKAGE)
                        matcher {
                            paramTypes()
                            returnType = "void"
                            declaredClass = TARGET_CLASS
                        }
                    }.singleOrNull()
                    if (md != null) bMethodName = md.name
                } catch (_: Throwable) {}
            }
            val bMethod = findMethod(tile!!.javaClass, bMethodName)
            bMethod.isAccessible = true
            bMethod.invoke(tile)

            log("successfully set share to enabled")
        } catch (e: Exception) {
            logError("Failed to set nearby share to enable: ", e)
        }
    }
}
