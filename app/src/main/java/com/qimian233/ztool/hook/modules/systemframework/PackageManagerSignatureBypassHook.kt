package com.qimian233.ztool.hook.modules.systemframework

import android.content.pm.Signature
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.security.cert.Certificate
import java.util.jar.Attributes
import java.util.zip.ZipEntry

/**
 * 绕过安装时的签名校验链：
 * - services 层：verifySignatures 直接返回 false、assertMinSignatureSchemeIsValid 短路
 * - framework 层：ApkSignatureVerifier V1 失败恢复、最低签名方案要求归零、
 *   ApkSigningBlockUtils verity 完整性短路
 * - libcore 层：StrictJarVerifier 的 JAR 摘要与证书回滚保护、MessageDigest.isEqual
 *
 * V1 校验失败后伪造签名：开启摘要绕过时优先从 APK 自身提取证书，
 * 提取不出再回退到占位签名。
 */
class PackageManagerSignatureBypassHook : SystemHookModule() {

    override fun getModuleName(): String = PreferenceKeys.PKG_MGR_BYPASS_VERIFICATION.name

    override fun getTargetPackages(): Array<out String> = arrayOf(ScopeKeys.SYSTEM_SERVER.packageName)

    @Throws(Throwable::class)
    override fun handleSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader
        val digestEnabled = remotePreferences.getBoolean(
            PreferenceKeys.PKG_MGR_BYPASS_DIGEST.name,
            PreferenceKeys.PKG_MGR_BYPASS_DIGEST.default
        )
        hookServicesLayer(classLoader)
        hookApkSigningBlockUtils(classLoader)
        hookStrictJarVerifier(classLoader)
        hookMessageDigest(classLoader)
        hookApkSignatureVerifier(classLoader, digestEnabled)
    }

    private fun hookServicesLayer(classLoader: ClassLoader) {
        try {
            val utilsClass =
                classLoader.loadClass("com.android.server.pm.PackageManagerServiceUtils")
            val verifySignatures = utilsClass.declaredMethods.first {
                it.name == "verifySignatures" && it.returnType == Boolean::class.javaPrimitiveType
            }
            if (!xposed.deoptimize(verifySignatures)) {
                logger.warn("deoptimize verifySignatures failed")
            }
            hookWithId(verifySignatures, "pkgmgr_verify_signatures") { _ ->
                // 原方法返回 boolean，短路即视为"无需比对"
                false
            }
            logger.info("Hooked PackageManagerServiceUtils.verifySignatures")
        } catch (e: Throwable) {
            logger.error("Failed hooking verifySignatures", e)
        }
        try {
            val scanClass = classLoader.loadClass("com.android.server.pm.ScanPackageUtils")
            val assertMinScheme = scanClass.declaredMethods.first {
                it.name == "assertMinSignatureSchemeIsValid"
            }
            hookWithId(assertMinScheme, "pkgmgr_assert_min_signature_scheme") { _ -> null }
            logger.info("Hooked ScanPackageUtils.assertMinSignatureSchemeIsValid")
        } catch (e: Throwable) {
            logger.error("Failed hooking assertMinSignatureSchemeIsValid", e)
        }
    }

    private fun hookApkSigningBlockUtils(classLoader: ClassLoader) {
        try {
            val utilsClass = classLoader.loadClass("android.util.apk.ApkSigningBlockUtils")
            val signatureInfoClass = classLoader.loadClass("android.util.apk.SignatureInfo")
            val parseVerity = utilsClass.declaredMethods.first {
                it.name == "parseVerityDigestAndVerifySourceLength"
            }
            hookWithId(parseVerity, "pkgmgr_parse_verity_digest") { chain ->
                // 伪装 verity 摘要校验通过：直接返回前 32 字节作为摘要
                (chain.getArg(0) as ByteArray).copyOfRange(0, 32)
            }
            val verifyIntegrity = utilsClass.declaredMethods.first {
                it.name == "verifyIntegrityForVerityBasedAlgorithm"
            }
            hookWithId(verifyIntegrity, "pkgmgr_verify_verity_integrity") { _ -> null }
            logger.info("Hooked ApkSigningBlockUtils verity checks")
        } catch (e: Throwable) {
            logger.error("Failed hooking ApkSigningBlockUtils", e)
        }
    }

    private fun hookStrictJarVerifier(classLoader: ClassLoader) {
        try {
            val verifierClass = classLoader.loadClass("android.util.jar.StrictJarVerifier")
            val verifyMessageDigest = findMethod(
                verifierClass, "verifyMessageDigest",
                ByteArray::class.java, ByteArray::class.java
            )
            hookWithId(verifyMessageDigest, "pkgmgr_jar_verify_message_digest") { _ -> true }

            val verifyEntry = findMethod(
                verifierClass, "verify",
                Attributes::class.java, String::class.java, ByteArray::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType
            )
            hookWithId(verifyEntry, "pkgmgr_jar_verify_entry") { _ -> true }

            val rollbackField = findField(
                verifierClass, "signatureSchemeRollbackProtectionsEnforced"
            )
            val verifierCtor = verifierClass.declaredConstructors.first()
            hookWithId(verifierCtor, "pkgmgr_jar_verifier_ctor") { chain ->
                chain.proceed()
                rollbackField.set(chain.thisObject, false)
                null
            }
            logger.info("Hooked StrictJarVerifier digest and rollback protection")
        } catch (e: Throwable) {
            logger.error("Failed hooking StrictJarVerifier", e)
        }
    }

    private fun hookMessageDigest(classLoader: ClassLoader) {
        try {
            val messageDigestClass = classLoader.loadClass("java.security.MessageDigest")
            val isEqual = findMethod(
                messageDigestClass, "isEqual",
                ByteArray::class.java, ByteArray::class.java
            )
            hookWithId(isEqual, "pkgmgr_message_digest_is_equal") { _ ->
                // 注意：该键开启后 system_server 内所有摘要比较恒等，属预期行为
                true
            }
            logger.info("Hooked MessageDigest.isEqual")
        } catch (e: Throwable) {
            logger.error("Failed hooking MessageDigest.isEqual", e)
        }
    }

    private fun hookApkSignatureVerifier(classLoader: ClassLoader, digestEnabled: Boolean) {
        try {
            val verifierClass = classLoader.loadClass("android.util.apk.ApkSignatureVerifier")
            val minSchemeMethod = findMethod(
                verifierClass, "getMinimumSignatureSchemeVersionForTargetSdk",
                Int::class.javaPrimitiveType
            )
            hookWithId(minSchemeMethod, "pkgmgr_min_signature_scheme_version") { _ -> 0 }
        } catch (e: Throwable) {
            logger.error("Failed hooking getMinimumSignatureSchemeVersionForTargetSdk", e)
        }
        try {
            val verifierClass = classLoader.loadClass("android.util.apk.ApkSignatureVerifier")
            val parseResultClass =
                classLoader.loadClass("android.content.pm.parsing.result.ParseResult")
            val parseInputClass =
                classLoader.loadClass("android.content.pm.parsing.result.ParseInput")
            val isError = parseResultClass.getMethod("isError")
            val getErrorCode = parseResultClass.getMethod("getErrorCode")
            val getResult = parseResultClass.getMethod("getResult")
            val reset = parseInputClass.getMethod("reset")
            val success = parseInputClass.getMethod("success", Any::class.java)

            val signingDetailsClass = classLoader.loadClass("android.content.pm.SigningDetails")
            val signingDetailsCtor = signingDetailsClass.getDeclaredConstructor(
                emptyArray<Signature>().javaClass,
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }
            val withDigestsClass = classLoader.loadClass(
                "android.util.apk.ApkSignatureVerifier\$SigningDetailsWithDigests"
            )
            val withDigestsCtor = withDigestsClass.getDeclaredConstructor(
                signingDetailsClass, java.util.Map::class.java
            ).apply { isAccessible = true }

            val strictJarFileClass = classLoader.loadClass("android.util.jar.StrictJarFile")
            val jarFileCtor = strictJarFileClass.getDeclaredConstructor(
                String::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }
            val findEntry = findMethod(strictJarFileClass, "findEntry", String::class.java)
            val close = findMethod(strictJarFileClass, "close")
            val loadCertificates = verifierClass.declaredMethods.first {
                it.name == "loadCertificates"
            }
            val convertToSignatures = verifierClass.declaredMethods.first {
                it.name == "convertToSignatures"
            }

            val verifyV1 = verifierClass.declaredMethods.first { it.name == "verifyV1Signature" }
            hookWithId(verifyV1, "pkgmgr_v1_signature_recovery") { chain ->
                val original = chain.proceed()
                try {
                    val parseResult = original ?: return@hookWithId original
                    if (!(isError.invoke(parseResult) as Boolean)) return@hookWithId original
                    val errorCode = getErrorCode.invoke(parseResult) as Int
                    if (errorCode != INSTALL_PARSE_FAILED_NO_CERTIFICATES) {
                        return@hookWithId original
                    }
                    val apkPath = chain.getArg(1) as String
                    val signatures = collectRecoverySignatures(
                        digestEnabled, apkPath, chain.getArg(0),
                        strictJarFileClass, jarFileCtor, findEntry, close,
                        loadCertificates, convertToSignatures, isError, getResult
                    )
                    val signingDetails = signingDetailsCtor.newInstance(signatures, 1)
                    val wrapped = withDigestsCtor.newInstance(signingDetails, null as Any?)
                    val input = chain.getArg(0)
                    reset.invoke(input)
                    logger.info("Recovered V1 signature verification for $apkPath")
                    success.invoke(input, wrapped)
                } catch (t: Throwable) {
                    logger.error("Failed to recover V1 signature verification", t)
                    original
                }
            }
            logger.info("Hooked ApkSignatureVerifier.verifyV1Signature recovery")
        } catch (e: Throwable) {
            logger.error("Failed hooking ApkSignatureVerifier", e)
        }
    }

    private fun collectRecoverySignatures(
        digestEnabled: Boolean,
        apkPath: String,
        parseInput: Any,
        strictJarFileClass: Class<*>,
        jarFileCtor: java.lang.reflect.Constructor<*>,
        findEntry: java.lang.reflect.Method,
        close: java.lang.reflect.Method,
        loadCertificates: java.lang.reflect.Method,
        convertToSignatures: java.lang.reflect.Method,
        isError: java.lang.reflect.Method,
        getResult: java.lang.reflect.Method
    ): Array<Signature> {
        // 从 APK 自身解析 V1 证书（不验证，仅提取），失败时回退占位签名
        if (digestEnabled) {
            var jarFile: Any? = null
            try {
                jarFile = jarFileCtor.newInstance(apkPath, true, false)
                val manifestEntry = findEntry.invoke(jarFile, "AndroidManifest.xml") as ZipEntry?
                if (manifestEntry != null) {
                    val certsResult = loadCertificates.invoke(null, parseInput, jarFile, manifestEntry)
                    if (certsResult != null && !(isError.invoke(certsResult) as Boolean)) {
                        val certs = getResult.invoke(certsResult) as? Array<Array<Certificate>>
                        if (certs != null && certs.isNotEmpty()) {
                            @Suppress("UNCHECKED_CAST")
                            return convertToSignatures.invoke(null, certs) as Array<Signature>
                        }
                    }
                }
            } catch (t: Throwable) {
                logger.error("Failed parsing certificates from $apkPath", t)
            } finally {
                runCatching { close.invoke(jarFile) }
            }
        }
        return arrayOf(Signature(FALLBACK_SIGNATURE_HEX))
    }

    companion object {
        private const val INSTALL_PARSE_FAILED_NO_CERTIFICATES = -103

        // 占位签名（非真实证书）：V1 失败且无法从 APK 提取证书时，
        // 给签名相关逻辑一个可比较的稳定值
        private const val FALLBACK_SIGNATURE_HEX =
            "5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c" +
                "5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c" +
                "5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c" +
                "5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c"
    }
}
