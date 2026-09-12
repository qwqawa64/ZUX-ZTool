package com.qimian233.ztool.utils

import java.io.ByteArrayOutputStream
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.util.Calendar

/**
 * 纯 Java（无 BouncyCastle）构造自签名 X.509 v3 证书，供本地 OTA 重签的
 * otacerts.zip 信任链使用。
 *
 * 仅支持 RSA 密钥 + SHA256withRSA（与 update_engine payload_verifier 的
 * otacerts 验签路径一致）。证书只在本机生成与使用，CN 标记为 ZTool。
 *
 * DER 结构遵循 RFC 5280：
 * Certificate ::= SEQUENCE { tbsCertificate, signatureAlgorithm, signatureValue }
 */
object OtaCertBuilder {

    private const val OID_SHA256_RSA = "2a864886f70d01010b" // 1.2.840.113549.1.1.11
    private const val OID_COMMON_NAME = "550403"            // 2.5.4.3 (CN)
    private const val VALIDITY_YEARS = 20

    /**
     * 用现有 RSA 密钥对生成自签名证书，返回 X.509 DER 编码。
     * 公钥的 SubjectPublicKeyInfo 直接复用 [publicKey].encoded，不做二次编码。
     */
    fun buildSelfSignedCertificate(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        require(publicKey is RSAPublicKey) { "Only RSA keys are supported" }
        val spki = publicKey.encoded
        val notBefore = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val notAfter = Calendar.getInstance().apply {
            add(Calendar.YEAR, VALIDITY_YEARS)
        }

        val tbs = buildTbsCertificate(
            serialNumber = randomSerial(),
            subjectPublicKeyInfo = spki,
            notBefore = notBefore,
            notAfter = notAfter
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(tbs)
            sign()
        }
        return derSequence(tbs, algorithmIdentifier(), derBitString(signature))
    }

    /**
     * 校验生成的证书可以被 Android 的 CertificateFactory 解析，
     * 返回解析后的 X509Certificate（失败抛 CertificateException）。
     */
    @Throws(CertificateException::class)
    fun toX509Certificate(der: ByteArray): X509Certificate {
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(der.inputStream()) as X509Certificate
        // 主动触发解析，尽早发现 DER 构造错误
        cert.checkValidity()
        return cert
    }

    /** 由 X.509 DER 解出公钥（用于反向校验证书与密钥对匹配）。 */
    fun publicKeyFromCertificate(der: ByteArray): PublicKey {
        val cert = toX509Certificate(der)
        return KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(cert.publicKey.encoded))
    }

    // ── TBSCertificate ──────────────────────────────────────────────

    private fun buildTbsCertificate(
        serialNumber: ByteArray,
        subjectPublicKeyInfo: ByteArray,
        notBefore: Calendar,
        notAfter: Calendar
    ): ByteArray {
        val algId = algorithmIdentifier()
        val name = name()
        return derSequence(
            // [0] EXPLICIT version = v3 (2)
            byteArrayOf(0xa0.toByte(), 0x03, 0x02, 0x01, 0x02),
            derInteger(serialNumber),
            algId,
            name, // issuer
            derSequence(derUtcTime(notBefore), derUtcTime(notAfter)),
            name, // subject（自签，issuer == subject）
            subjectPublicKeyInfo // 已是合法 DER，原样嵌入
        )
    }

    /** SEQUENCE { OID sha256WithRSAEncryption, NULL } */
    private fun algorithmIdentifier(): ByteArray =
        derSequence(derOid(hexToBytes(OID_SHA256_RSA)), byteArrayOf(0x05, 0x00))

    /** SEQUENCE { SET { SEQUENCE { OID commonName, UTF8String "ZTool OTA Local Signing" } } } */
    private fun name(): ByteArray {
        val cn = derSequence(derOid(hexToBytes(OID_COMMON_NAME)), derUtf8("ZTool OTA Local Signing"))
        return derSequence(derSet(cn))
    }

    // ── DER 基础编码 ─────────────────────────────────────────────────

    private fun derSequence(vararg parts: ByteArray): ByteArray =
        derWrap(0x30, parts.reduce { acc, bytes -> acc + bytes })

    private fun derSet(content: ByteArray): ByteArray = derWrap(0x31, content)

    private fun derInteger(value: ByteArray): ByteArray = derWrap(0x02, value)

    private fun derBitString(content: ByteArray): ByteArray =
        derWrap(0x03, byteArrayOf(0x00) + content)

    private fun derUtf8(text: String): ByteArray =
        derWrap(0x0c, text.toByteArray(Charsets.UTF_8))

    private fun derUtcTime(calendar: Calendar): ByteArray {
        // UTCTime 只支持到 2049；2049 年后的 notAfter 需换 GeneralizedTime，
        // notAfter = now + 20 年，若越界则钳制到 2049-12-31。
        var year = calendar.get(Calendar.YEAR)
        if (year > 2049) {
            calendar.set(2049, Calendar.DECEMBER, 31, 23, 59, 59)
            year = 2049
        }
        val text = buildString {
            append("%02d".format(year % 100))
            append("%02d".format(calendar.get(Calendar.MONTH) + 1))
            append("%02d".format(calendar.get(Calendar.DAY_OF_MONTH)))
            append("%02d".format(calendar.get(Calendar.HOUR_OF_DAY)))
            append("%02d".format(calendar.get(Calendar.MINUTE)))
            append("%02d".format(calendar.get(Calendar.SECOND)))
            append("Z")
        }
        return derWrap(0x17, text.toByteArray(Charsets.US_ASCII))
    }

    private fun derOid(content: ByteArray): ByteArray = derWrap(0x06, content)

    private fun derWrap(tag: Byte, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag.toInt())
        out.write(encodeLength(content.size))
        out.write(content)
        return out.toByteArray()
    }

    private fun encodeLength(length: Int): ByteArray = when {
        length < 0x80 -> byteArrayOf(length.toByte())
        length < 0x100 -> byteArrayOf(0x81.toByte(), length.toByte())
        length < 0x10000 -> byteArrayOf(
            0x82.toByte(),
            (length shr 8).toByte(),
            length.toByte()
        )
        else -> byteArrayOf(
            0x83.toByte(),
            (length shr 16).toByte(),
            (length shr 8).toByte(),
            length.toByte()
        )
    }

    private fun randomSerial(): ByteArray {
        val serial = ByteArray(8)
        SecureRandom().nextBytes(serial)
        // 保证正整数：最高位清零
        serial[0] = (serial[0].toInt() and 0x7f).toByte()
        // 避免全 0
        if (serial.all { it == 0.toByte() }) serial[7] = 1
        return serial
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) +
                    Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
}
