package com.qimian233.ztool.hook.modules.systemframework

import android.annotation.SuppressLint
import android.content.pm.Signature
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.SystemHookModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.jar.Attributes
import java.util.zip.ZipEntry

/**
 * Bypasses install-time signature verification in three layers: services (verifySignatures,
 * minimum signature scheme), framework (ApkSignatureVerifier V1 recovery, verity checks),
 * and libcore (StrictJarVerifier digests, MessageDigest.isEqual).
 * Forges signatures after V1 failure: certificates are read from the APK itself when the
 * digest bypass is enabled, falling back to a placeholder signature.
 */
@SuppressLint("PrivateApi")
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
                // The original method returns boolean; short-circuiting means "no comparison needed"
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
            val parseVerity = utilsClass.declaredMethods.first {
                it.name == "parseVerityDigestAndVerifySourceLength"
            }
            hookWithId(parseVerity, "pkgmgr_parse_verity_digest") { chain ->
                // Fake verity digest verification passing: return the first 32 bytes directly as the digest
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
                // Note: once this key is enabled, all digest comparisons inside system_server return equal; this is expected
                true
            }
            logger.info("Hooked MessageDigest.isEqual")
        } catch (e: Throwable) {
            logger.error("Failed hooking MessageDigest.isEqual", e)
        }
    }

    private fun hookApkSignatureVerifier(classLoader: ClassLoader, digestEnabled: Boolean) {
        val verifierClass = runCatching {
            classLoader.loadClass("android.util.apk.ApkSignatureVerifier")
        }.getOrNull()
        if (verifierClass == null) {
            logger.warn("ApkSignatureVerifier unavailable; signature verification hooks skipped")
            return
        }
        try {
            val minSchemeMethod = findMethod(
                verifierClass, "getMinimumSignatureSchemeVersionForTargetSdk",
                Int::class.javaPrimitiveType
            )
            hookWithId(minSchemeMethod, "pkgmgr_min_signature_scheme_version") { _ -> 0 }
        } catch (e: Throwable) {
            logger.error("Failed hooking getMinimumSignatureSchemeVersionForTargetSdk", e)
        }

        val support = runCatching {
            resolveV1RecoverySupport(classLoader, verifierClass)
        }.getOrNull()
        if (support == null) {
            logger.warn("ParseInput.reset or V1 verification internals unavailable; recovery hook skipped")
            return
        }
        try {
            hookWithId(support.verifyV1Signature, "pkgmgr_v1_signature_recovery") { chain ->
                val original = chain.proceed()
                try {
                    val parseResult = original ?: return@hookWithId original
                    if (!(support.isError.invoke(parseResult) as Boolean)) {
                        return@hookWithId original
                    }
                    if ((support.getErrorCode.invoke(parseResult) as Int) != INSTALL_PARSE_FAILED_NO_CERTIFICATES) {
                        return@hookWithId original
                    }
                    val apkPath = chain.getArg(1) as String
                    val input = chain.getArg(0)
                    val signatures =
                        collectRecoverySignatures(digestEnabled, apkPath, input, support)
                    val signingDetails = support.signingDetailsCtor.newInstance(signatures, 1)
                    val wrapped = support.withDigestsCtor.newInstance(signingDetails, null as Any?)
                    support.resetParseInput.invoke(input)
                    val recovered = support.success.invoke(input, wrapped)
                    logger.info("Recovered V1 signature verification for $apkPath")
                    recovered
                } catch (t: Throwable) {
                    logger.error("Failed to recover V1 signature verification", t)
                    original
                }
            }
            logger.info("Hooked ApkSignatureVerifier.verifyV1Signature recovery")
        } catch (e: Throwable) {
            logger.error("Failed hooking ApkSignatureVerifier V1 recovery", e)
        }
    }

    /**
     * Resolves every reflective handle needed to turn a failed V1 result into a success result.
     *
     * @return null when the build no longer exposes an equivalent reset/success path
     */
    private fun resolveV1RecoverySupport(
        classLoader: ClassLoader,
        verifierClass: Class<*>
    ): V1RecoverySupport? {
        val parseResultClass = classLoader.loadClass("android.content.pm.parsing.result.ParseResult")
        val parseInputClass = classLoader.loadClass("android.content.pm.parsing.result.ParseInput")
        val strictJarFileClass = classLoader.loadClass("android.util.jar.StrictJarFile")
        val signingDetailsClass = classLoader.loadClass("android.content.pm.SigningDetails")
        val withDigestsClass = classLoader.loadClass(
            $$"android.util.apk.ApkSignatureVerifier$SigningDetailsWithDigests"
        )

        // Older builds declare reset() on the ParseInput interface; Android 17 keeps it on ParseTypeImpl only
        val resetParseInput = runCatching { parseInputClass.getMethod("reset") }.getOrNull()
            ?: runCatching {
                classLoader.loadClass(PARSE_TYPE_IMPL_CLASS).getMethod("reset")
            }.getOrNull()
            ?: return null

        return V1RecoverySupport(
            isError = parseResultClass.getMethod("isError"),
            getErrorCode = parseResultClass.getMethod("getErrorCode"),
            getResult = parseResultClass.getMethod("getResult"),
            success = parseInputClass.getMethod("success", Any::class.java),
            resetParseInput = resetParseInput,
            signingDetailsCtor = signingDetailsClass.getDeclaredConstructor(
                emptyArray<Signature>().javaClass,
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true },
            withDigestsCtor = withDigestsClass.getDeclaredConstructor(
                signingDetailsClass, Map::class.java
            ).apply { isAccessible = true },
            strictJarFileCtor = strictJarFileClass.getDeclaredConstructor(
                String::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true },
            findEntry = findMethod(strictJarFileClass, "findEntry", String::class.java),
            closeJarFile = findMethod(strictJarFileClass, "close"),
            // Both certificate helpers are private static inside ApkSignatureVerifier
            loadCertificates = verifierClass.declaredMethods.first {
                it.name == "loadCertificates"
            }.apply { isAccessible = true },
            convertToSignatures = verifierClass.declaredMethods.first {
                it.name == "convertToSignatures"
            }.apply { isAccessible = true },
            verifyV1Signature = findMethod(
                verifierClass, "verifyV1Signature",
                parseInputClass, String::class.java, Boolean::class.javaPrimitiveType
            )
        )
    }

    private fun collectRecoverySignatures(
        digestEnabled: Boolean,
        apkPath: String,
        parseInput: Any,
        support: V1RecoverySupport
    ): Array<Signature> {
        // Parse V1 certificates from the APK itself (no validation, extraction only); fall back to the placeholder signature on failure
        if (digestEnabled) {
            var jarFile: Any? = null
            try {
                jarFile = support.strictJarFileCtor.newInstance(apkPath, true, false)
                val manifestEntry = support.findEntry.invoke(jarFile, "AndroidManifest.xml") as ZipEntry?
                if (manifestEntry != null) {
                    val certsResult = support.loadCertificates.invoke(null, parseInput, jarFile, manifestEntry)
                    if (certsResult != null && !(support.isError.invoke(certsResult) as Boolean)) {
                        val certs = support.getResult.invoke(certsResult) as? Array<*>
                        if (!certs.isNullOrEmpty()) {
                            @Suppress("UNCHECKED_CAST")
                            return support.convertToSignatures.invoke(null, certs) as Array<Signature>
                        }
                    }
                }
            } catch (t: Throwable) {
                logger.error("Failed parsing certificates from $apkPath", t)
            } finally {
                runCatching { support.closeJarFile.invoke(jarFile) }
            }
        }
        return arrayOf(Signature(FALLBACK_SIGNATURE_HEX))
    }

    /**
     * Reflective handles required by the V1 signature recovery hook.
     */
    private class V1RecoverySupport(
        val isError: Method,
        val getErrorCode: Method,
        val getResult: Method,
        val success: Method,
        val resetParseInput: Method,
        val signingDetailsCtor: Constructor<*>,
        val withDigestsCtor: Constructor<*>,
        val strictJarFileCtor: Constructor<*>,
        val findEntry: Method,
        val closeJarFile: Method,
        val loadCertificates: Method,
        val convertToSignatures: Method,
        val verifyV1Signature: Method
    )

    companion object {
        private const val INSTALL_PARSE_FAILED_NO_CERTIFICATES = -103

        private const val PARSE_TYPE_IMPL_CLASS = "android.content.pm.parsing.result.ParseTypeImpl"

        // Placeholder signature (not a real certificate): when V1 fails and no certificate
        // can be extracted from the APK, gives signature-related logic a stable comparable value
        private const val FALLBACK_SIGNATURE_HEX =
            "5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c" +
                "5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c" +
                "5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c" +
                "5a5558544f4f4c5a5558544f4f4c5a5558544f4f4c"
    }
}
