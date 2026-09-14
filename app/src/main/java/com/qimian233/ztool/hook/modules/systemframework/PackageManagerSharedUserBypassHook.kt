package com.qimian233.ztool.hook.modules.systemframework

import android.content.pm.ApplicationInfo
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 绕过 sharedUserId 签名一致性限制（需同时开启摘要绕过）：
 * - 放开 ReconcilePackageUtils.ALLOW_NON_PRELOADS_SYSTEM_SHAREDUIDS
 * - SharedUserSetting 增删成员时以首个成员签名为基准合并谱系，
 *   避免 sharedUser 聚合签名因成员更换而失配
 *
 * 能力位比对必须走原始方法（ORIGIN invoker）：
 * 本功能组的摘要绕过会把普通调用变成恒真，导致合并判断失效。
 */
class PackageManagerSharedUserBypassHook : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.PKG_MGR_BYPASS_SHARED_USER.name

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    @Throws(Throwable::class)
    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        val digestEnabled = remotePreferences.getBoolean(
            PreferenceKeys.PKG_MGR_BYPASS_DIGEST.name,
            PreferenceKeys.PKG_MGR_BYPASS_DIGEST.default
        )
        if (!digestEnabled) {
            logger.info("Digest bypass disabled, sharedUser bypass takes no effect")
            return
        }
        deoptReconcilePackages(classLoader)
        unlockNonPreloadSharedUids(classLoader)
        hookSharedUserSetting(classLoader)
    }

    private fun deoptReconcilePackages(classLoader: ClassLoader) {
        try {
            val reconcileClass = classLoader.loadClass("com.android.server.pm.ReconcilePackageUtils")
            val reconcilePackages = reconcileClass.declaredMethods.first {
                it.name == "reconcilePackages"
            }
            if (!xposed.deoptimize(reconcilePackages)) {
                logger.warn("deoptimize reconcilePackages failed")
            }
        } catch (e: Throwable) {
            logger.error("Failed deoptimizing reconcilePackages", e)
        }
    }

    private fun unlockNonPreloadSharedUids(classLoader: ClassLoader) {
        try {
            val reconcileClass = classLoader.loadClass("com.android.server.pm.ReconcilePackageUtils")
            val flagField = findField(
                reconcileClass, "ALLOW_NON_PRELOADS_SYSTEM_SHAREDUIDS"
            )
            ArtStaticFieldPatcher.putStaticBoolean(flagField, true)
            logger.info("ALLOW_NON_PRELOADS_SYSTEM_SHAREDUIDS set to true")
        } catch (e: Throwable) {
            logger.error("Failed unlocking non-preload system sharedUIDs", e)
        }
    }

    private fun hookSharedUserSetting(classLoader: ClassLoader) {
        try {
            val sharedUserClass = classLoader.loadClass("com.android.server.pm.SharedUserSetting")
            val packageSettingClass = classLoader.loadClass("com.android.server.pm.PackageSetting")
            val uidFlagsField = findField(sharedUserClass, "uidFlags")
            val packagesField = findField(sharedUserClass, "mPackages")
            val signaturesOnSharedUser = findField(sharedUserClass, "signatures")
            val signaturesOnPackage = findField(packageSettingClass, "signatures")
            val signingDetailsField = findField(
                signaturesOnSharedUser.type, "mSigningDetails"
            )
            val signingDetailsClass = signingDetailsField.type
            val checkCapability = findMethod(
                signingDetailsClass, "checkCapability",
                signingDetailsClass, Int::class.javaPrimitiveType
            )
            val originCheckCapability: XposedInterface.Invoker<*, *> =
                xposed.getInvoker(checkCapability)
                    .setType(XposedInterface.Invoker.Type.ORIGIN)
            val mergeLineageWith = findMethod(
                signingDetailsClass, "mergeLineageWith",
                signingDetailsClass, Int::class.javaPrimitiveType
            )

            val addPackage = findMethod(sharedUserClass, "addPackage", packageSettingClass)
            hookWithId(addPackage, "pkgmgr_shared_user_add") { chain ->
                reconcileSharedUserSignatures(
                    chain, targetParticipatesInMerge = true, uidFlagsField = uidFlagsField,
                    packagesField = packagesField,
                    signaturesOnSharedUser = signaturesOnSharedUser,
                    signaturesOnPackage = signaturesOnPackage,
                    signingDetailsField = signingDetailsField,
                    originCheckCapability = originCheckCapability,
                    mergeLineageWith = mergeLineageWith
                )
            }
            val removePackage = findMethod(sharedUserClass, "removePackage", packageSettingClass)
            hookWithId(removePackage, "pkgmgr_shared_user_remove") { chain ->
                reconcileSharedUserSignatures(
                    chain, targetParticipatesInMerge = false, uidFlagsField = uidFlagsField,
                    packagesField = packagesField,
                    signaturesOnSharedUser = signaturesOnSharedUser,
                    signaturesOnPackage = signaturesOnPackage,
                    signingDetailsField = signingDetailsField,
                    originCheckCapability = originCheckCapability,
                    mergeLineageWith = mergeLineageWith
                )
            }
            logger.info("Hooked SharedUserSetting.addPackage/removePackage")
        } catch (e: Throwable) {
            logger.error("Failed hooking SharedUserSetting", e)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun reconcileSharedUserSignatures(
        chain: XposedInterface.Chain,
        targetParticipatesInMerge: Boolean,
        uidFlagsField: Field,
        packagesField: Field,
        signaturesOnSharedUser: Field,
        signaturesOnPackage: Field,
        signingDetailsField: Field,
        originCheckCapability: XposedInterface.Invoker<*, *>,
        mergeLineageWith: Method
    ) {
        val sharedUser = chain.thisObject
        if (sharedUser == null) {
            chain.proceed()
            return
        }
        try {
            if (uidFlagsField.getInt(sharedUser) and ApplicationInfo.FLAG_SYSTEM != 0) {
                // 系统应用 sharedUser 签名不动
                chain.proceed()
                return
            }
            val packages = packagesField.get(sharedUser) ?: run {
                chain.proceed()
                return
            }
            val storage = packages.javaClass.declaredFields
                .firstOrNull { it.name == "mStorage" }?.let { field ->
                    field.isAccessible = true
                    field.get(packages)
                } ?: packages
            val valueAt = storage.javaClass.methods.first { it.name == "valueAt" }
            val size = storage.javaClass.methods.first { it.name == "size" }
                .invoke(storage) as Int
            val sharedUserSig = signingDetailsField.get(signaturesOnSharedUser.get(sharedUser))
            if (size == 0 || sharedUserSig == null) {
                chain.proceed()
                return
            }
            var memberChanged = false
            var mergedSignatures: Any? = null
            for (index in 0 until size) {
                var member = valueAt.invoke(storage, index) ?: continue
                if (member === chain.getArg(0)) {
                    memberChanged = true
                    if (!targetParticipatesInMerge) {
                        // 移除成员：移除者不参与合并
                        continue
                    }
                }
                val memberSig = signingDetailsField.get(signaturesOnPackage.get(member))
                    ?: continue
                // 已存在可用签名关系时保持现状
                val forward = originCheckCapability.invoke(memberSig, sharedUserSig, 0) as Boolean
                val backward = originCheckCapability.invoke(sharedUserSig, memberSig, 0) as Boolean
                if (forward || backward) {
                    chain.proceed()
                    return
                }
                mergedSignatures = if (mergedSignatures == null) {
                    memberSig
                } else {
                    mergeLineageWith.invoke(mergedSignatures, memberSig, MERGE_RESTRICTED_CAPABILITY)
                }
            }
            if (memberChanged && mergedSignatures != null) {
                signingDetailsField.set(signaturesOnSharedUser.get(sharedUser), mergedSignatures)
                logger.debug("Reconciled sharedUser signatures on member change")
            }
        } catch (t: Throwable) {
            logger.error("Failed reconciling sharedUser signatures", t)
        }
        chain.proceed()
    }

    companion object {
        private const val MERGE_RESTRICTED_CAPABILITY = 2
    }
}

/**
 * ART 静态 final 基础类型字段补丁：反射 Field.set 对 static final 字段会被
 * 拒绝，这里借 Unsafe 定位 java.lang.reflect.Field 内缓存的 ART 字段偏移，
 * 直接写入字段槽位。
 */
private object ArtStaticFieldPatcher {
    private val unsafeClass = Class.forName("sun.misc.Unsafe")
    private val unsafe: Any = try {
        unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    } catch (_: NoSuchFieldException) {
        unsafeClass.getDeclaredField("THE_ONE").apply { isAccessible = true }.get(null)
    }
    private val getInt = unsafeClass.getMethod(
        "getInt", Any::class.java, Long::class.javaPrimitiveType
    )
    private val putInt = unsafeClass.getMethod(
        "putInt", Any::class.java, Long::class.javaPrimitiveType, Int::class.javaPrimitiveType
    )
    private val putBoolean = unsafeClass.getMethod(
        "putBoolean", Any::class.java, Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType
    )
    private val objectFieldOffset = unsafeClass.getMethod(
        "objectFieldOffset", Field::class.java
    )

    private val offsetOfOffset: Long by lazy { locateOffsetOfOffset() }

    fun putStaticBoolean(field: Field, value: Boolean) {
        field.isAccessible = true
        runCatching { field.get(null) }
        val artFieldOffset = (getInt.invoke(unsafe, field, offsetOfOffset) as Int).toLong()
        putBoolean.invoke(unsafe, field.declaringClass, artFieldOffset, value)
    }

    private fun locateOffsetOfOffset(): Long {
        try {
            val offsetField = Field::class.java.getDeclaredField("offset")
            offsetField.isAccessible = true
            offsetField.getInt(offsetField)
            return objectFieldOffset.invoke(unsafe, offsetField) as Long
        } catch (_: NoSuchFieldException) {
        }
        // 回退：借 Point.x 探测 Field 对象内缓存 ART 偏移的位置
        val pointClass = Class.forName("android.graphics.Point")
        val probeField = pointClass.getDeclaredField("x").apply { isAccessible = true }
        val probe = probeField.getInt(pointClass.getDeclaredConstructor().newInstance())
        val probeOffset = objectFieldOffset.invoke(unsafe, probeField) as Int
        var candidate = 8L
        while (candidate < 256) {
            if ((getInt.invoke(unsafe, probeField, candidate) as Int) == probeOffset) {
                val flipped = probe.inv()
                putInt.invoke(unsafe, probeField, candidate, flipped)
                val changed = objectFieldOffset.invoke(unsafe, probeField) as Int
                putInt.invoke(unsafe, probeField, candidate, probeOffset)
                if (changed == flipped) {
                    return candidate
                }
            }
            candidate += 4
        }
        throw NoSuchFieldException("Field.offset")
    }
}
