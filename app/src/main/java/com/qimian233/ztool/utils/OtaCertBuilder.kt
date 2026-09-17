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
 * Pure Java (no BouncyCastle) construction of a self-signed X.509 v3 certificate,
 * used for the otacerts.zip trust chain of local OTA re-signing.
 *
 * Only RSA keys + SHA256withRSA are supported (matching the otacerts verification
 * path of update_engine's payload_verifier). The certificate is generated and used
 * locally only, with CN set to ZTool.
 *
 * DER structure follows RFC 5280:
 * Certificate ::= SEQUENCE { tbsCertificate, signatureAlgorithm, signatureValue }
 */
object OtaCertBuilder {

    private const val OID_SHA256_RSA = "2a864886f70d01010b" // 1.2.840.113549.1.1.11
    private const val OID_COMMON_NAME = "550403"            // 2.5.4.3 (CN)
    private const val VALIDITY_YEARS = 20

    /**
     * Build a self-signed certificate from an existing RSA key pair, returning the
     * X.509 DER encoding. The public key's SubjectPublicKeyInfo reuses
     * [publicKey].encoded directly, without re-encoding.
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
     * Verify that the generated certificate can be parsed by Android's
     * CertificateFactory, returning the parsed X509Certificate
     * (throws CertificateException on failure).
     */
    @Throws(CertificateException::class)
    fun toX509Certificate(der: ByteArray): X509Certificate {
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(der.inputStream()) as X509Certificate
        // Force parsing early to surface DER construction errors as soon as possible
        cert.checkValidity()
        return cert
    }

    /** Extract the public key from X.509 DER (used to verify the cert matches the key pair). */
    fun publicKeyFromCertificate(der: ByteArray): PublicKey {
        val cert = toX509Certificate(der)
        return KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(cert.publicKey.encoded))
    }

    // ── TBSCertificate ─────────────────────────────────────────────

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
            name, // subject (self-signed, issuer == subject)
            subjectPublicKeyInfo // already valid DER, embedded as-is
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

    // ── DER basic encoding ─────────────────────────────────────────

    private fun derSequence(vararg parts: ByteArray): ByteArray =
        derWrap(0x30, parts.reduce { acc, bytes -> acc + bytes })

    private fun derSet(content: ByteArray): ByteArray = derWrap(0x31, content)

    private fun derInteger(value: ByteArray): ByteArray = derWrap(0x02, value)

    private fun derBitString(content: ByteArray): ByteArray =
        derWrap(0x03, byteArrayOf(0x00) + content)

    private fun derUtf8(text: String): ByteArray =
        derWrap(0x0c, text.toByteArray(Charsets.UTF_8))

    private fun derUtcTime(calendar: Calendar): ByteArray {
        // UTCTime only supports years up to 2049; a notAfter beyond 2049 would need
        // GeneralizedTime. With notAfter = now + 20 years, clamp to 2049-12-31 if out of range.
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
        // Guarantee a positive integer: clear the highest bit
        serial[0] = (serial[0].toInt() and 0x7f).toByte()
        // Avoid all-zero serials
        if (serial.all { it == 0.toByte() }) serial[7] = 1
        return serial
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) +
                    Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
}
