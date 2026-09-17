package com.qimian233.ztool.hook.modules.tbengine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.util.Base64
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Local OTA package re-signing hook.
 *
 * User flow: a third-party ota.zip is placed on /sdcard → the system update app
 * "local install" → tbengine's SwfABInstalling.doMyPrimaryJob() copies
 * /sdcard/ota.zip and hands it to update_engine.
 *
 * This hook intercepts doMyPrimaryJob(): before the copy happens, it re-signs the
 * payload with the ZTool key pair (generated app-side and delivered via
 * xposed_module_config), then chain.proceed() lets the original logic continue;
 * it also hooks UpdateEngine.applyPayload to inject the "public_key" property so
 * update_engine verifies with the ZTool public key.
 *
 * Payload format (A/B OTA, AOSP version 2): the metadata signature covers
 * header+manifest, the payload signature covers everything before it; the data
 * section is kept as-is, so only the two fixed-size signature blocks are replaced.
 *
 * doMyPrimaryJob runs on tbengine's worker thread, so blocking signing will not
 * cause an ANR. Disk cost: re-signing needs roughly 2x the package size of
 * temporary space (new payload.bin + new zip).
 */
@SuppressLint("PrivateApi")
class SignTbEngineLocalOta : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.SIGN_TB_ENGINE_LOCAL_OTA.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.TB_ENGINE.packageName)

    companion object {
        private const val PAYLOAD_ENTRY = "payload.bin"
        private const val PROPERTY_ENTRY = "payload_properties.txt"
        private const val PAYLOAD_MAGIC = "CrAU"
        private const val SIG_BLOB_SIZE = 267
        private val SIG_BLOB_OUTER_HEAD = byteArrayOf(0x0a, 0x88.toByte(), 0x02)
        private val SIG_BLOB_INNER_HEAD = byteArrayOf(0x12, 0x80.toByte(), 0x02)
        private val SIG_BLOB_TAIL = byteArrayOf(0x1d, 0x00, 0x01, 0x00, 0x00)
        private const val PUBLIC_KEY_PROPERTY = "public_key"
        private const val PUBLIC_KEY_PEM_PATH = "/data/ota_package/ztool_ota_pub.pem"
        private const val FIELD_MAX_TIMESTAMP = 14L
        // Anti-rollback clamp margin: now + 5 years
        private const val FUTURE_TIMESTAMP_MARGIN_SECONDS = 5L * 365 * 24 * 60 * 60
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        // 1. Intercept the local install workflow: re-sign before the copy
        try {
            val swfAbInstallingClass = classLoader.loadClass(
                "com.lenovo.tbengine.core.services.SwfABInstalling"
            )
            val doMyPrimaryJob = findMethod(swfAbInstallingClass, "doMyPrimaryJob")
            hookWithId(doMyPrimaryJob, "tbengine_local_ota_sign") { chain ->
                if (isEnabled()) {
                    try {
                        val context = extractContext(chain.thisObject)
                        if (context != null) {
                            interceptLocalInstall()
                        } else {
                            logger.warn("Unable to resolve Context from SwfABInstalling instance")
                        }
                    } catch (t: Throwable) {
                        // Never throw into the host: tbengine has a crash-based self-fuse
                        logger.error("Local OTA sign interception failed", t)
                    }
                }
                chain.proceed()
            }
            logger.info("SignTbEngineLocalOta workflow hook installed")
        } catch (t: Throwable) {
            logger.error("Failed to hook SwfABInstalling.doMyPrimaryJob", t)
        }

        // 2. UpdateEngine.applyPayload: inject the public_key property
        try {
            val updateEngineClass = classLoader.loadClass("android.os.UpdateEngine")
            val applyPayload = findMethod(
                updateEngineClass, "applyPayload",
                String::class.java, Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType, Array<String>::class.java
            )
            hookWithId(applyPayload, "tbengine_apply_payload_pubkey") { chain ->
                val args = chain.args.toMutableList()
                @Suppress("UNCHECKED_CAST")
                val props = (args[3] as? Array<String>)?.toMutableList() ?: mutableListOf()
                if (props.none { it.startsWith("$PUBLIC_KEY_PROPERTY=") }) {
                    props.add("$PUBLIC_KEY_PROPERTY=$PUBLIC_KEY_PEM_PATH")
                    args[3] = props.toTypedArray()
                    logger.info("Injected $PUBLIC_KEY_PROPERTY into applyPayload")
                }
                chain.proceed(args.toTypedArray())
            }
            logger.info("SignTbEngineLocalOta applyPayload hook installed")
        } catch (t: Throwable) {
            logger.error("Failed to hook UpdateEngine.applyPayload", t)
        }
    }

    /**
     * Extracts the MainService (Context) from the SwfBase inheritance chain.
     */
    private fun extractContext(thisObject: Any?): Context? {
        if (thisObject == null) return null
        return try {
            val serviceField = findField(thisObject.javaClass, "mService")
            serviceField.get(thisObject) as? Context
        } catch (t: Throwable) {
            logger.error("Failed to read mService field", t)
            null
        }
    }

    private fun interceptLocalInstall() {
        val localZip = File(Environment.getExternalStorageDirectory(), "ota.zip")
            .takeIf { it.exists() } ?: run {
            logger.debug("No /sdcard/ota.zip found; not a local install flow")
            return
        }
        logger.info("Local install flow detected: $localZip")
        ensurePublicKeyPem()
        val signed = signOtaZip(localZip)
        if (signed) {
            logger.info("OTA package signed, original flow will continue with the signed package")
        } else {
            logger.warn("Signing not performed; original package will be used as-is")
        }
    }

    /**
     * Reads the app-side generated PKCS#8 private key from xposed_module_config.
     * The key is generated by TbEngineSettingsRepository.ensureOtaSigningKeys()
     * the first time the page is opened.
     */
    private fun loadSigningKey(): PrivateKey? {
        val b64 = remotePreferences.getString(
            PreferenceKeys.TB_ENGINE_OTA_PRIVATE_KEY.name, ""
        ) ?: ""
        if (b64.isBlank()) {
            logger.warn("ZTool OTA signing key not provisioned; open TB Engine page once to generate it")
            return null
        }
        return try {
            val kf = KeyFactory.getInstance("RSA")
            kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(b64, Base64.DEFAULT)))
        } catch (t: Throwable) {
            logger.error("Failed to decode OTA signing key", t)
            null
        }
    }

    /**
     * Writes the X509 public key as PEM for update_engine to read (update_engine
     * runs as root and can read that path).
     */
    @SuppressLint("SetWorldReadable")
    private fun ensurePublicKeyPem() {
        try {
            val b64 = remotePreferences.getString(
                PreferenceKeys.TB_ENGINE_OTA_PUBLIC_KEY.name, ""
            ) ?: ""
            if (b64.isBlank()) {
                logger.warn("ZTool OTA public key not provisioned")
                return
            }
            val pem = File(PUBLIC_KEY_PEM_PATH)
            val expected = buildPem(b64)
            if (pem.exists() && pem.readText() == expected) return
            pem.writeText(expected)
            pem.setReadable(true, false)
            pem.setWritable(false, false)
            logger.info("Public key PEM written to $PUBLIC_KEY_PEM_PATH")
        } catch (t: Throwable) {
            logger.error("Failed to write public key PEM", t)
        }
    }

    private fun buildPem(b64Key: String): String {
        val raw = b64Key.replace("\n", "").replace("\r", "")
        return buildString {
            append("-----BEGIN PUBLIC KEY-----\n")
            raw.chunked(64).forEach {
                append(it).append('\n')
            }
            append("-----END PUBLIC KEY-----\n")
        }
    }

    /**
     * Re-signs /sdcard/ota.zip in place: generates a new payload.bin (replacing
     * the two signature blocks, data section untouched) and an updated
     * payload_properties.txt with new hashes, rebuilds the zip, then atomically
     * replaces the original file.
     * Returns true when signing completed.
     */
    private fun signOtaZip(zipFile: File): Boolean {
        val privateKey = loadSigningKey() ?: return false
        val tmpZip = File(zipFile.parentFile, "${zipFile.name}.ztool.tmp")
        val tmpPayload = File(zipFile.parentFile, "${zipFile.name}.ztool.payload.tmp")
        try {
            ZipFile(zipFile).use { zf ->
                val payloadEntry = zf.getEntry(PAYLOAD_ENTRY)
                val propsEntry = zf.getEntry(PROPERTY_ENTRY)
                if (payloadEntry == null) {
                    logger.warn("No payload.bin entry in OTA zip")
                    return false
                }
                val originalSize = payloadEntry.size
                val src = zf.getInputStream(payloadEntry)

                // 1. Read header / manifest / original metadata signature block
                val header = readFully(src, 24)
                if (!header.copyOfRange(0, 4).contentEquals(PAYLOAD_MAGIC.toByteArray())) {
                    logger.warn("Unrecognized payload magic: ${header.copyOfRange(0, 4).decodeToString()}")
                    return false
                }
                val manifestSize = readBeLong(header, 12)
                val metadataSigSize = readBeInt(header, 20)
                val manifest = readFully(src, manifestSize.toInt())
                val metaSize = 24L + manifestSize + metadataSigSize
                val sigOffset = findManifestField(manifest, 4)
                    ?: run { logger.warn("Manifest lacks signatures_offset"); return false }
                val sigSize = findManifestField(manifest, 5)
                    ?: run { logger.warn("Manifest lacks signatures_size"); return false }
                if (sigOffset + sigSize != originalSize - metaSize) {
                    logger.warn(
                        "Unexpected signatures layout: offset=$sigOffset size=$sigSize " +
                                "metaSize=$metaSize payloadSize=$originalSize"
                    )
                    return false
                }
                if (metadataSigSize != SIG_BLOB_SIZE.toLong() || sigSize != SIG_BLOB_SIZE.toLong()) {
                    logger.warn(
                        "Unsupported signature blob size (metadata=$metadataSigSize payload=$sigSize, " +
                                "expect $SIG_BLOB_SIZE); unsigned or non-standard payload"
                    )
                    return false
                }
                // Data section = from after the metadata prefix to before the payload
                // signature block, kept as-is
                val dataLength = sigOffset // relative to the data section start, i.e. the data section length

                // 2a. Rewrite the manifest's max_timestamp (field 14) to pass the
                // anti-rollback check: update_engine compares manifest.max_timestamp
                // with ro.build.date.utc, and stale third-party packages are rejected
                // with kPayloadTimestampError(51). As the signer, we clamp the
                // timestamp to a future value before signing; other fields keep
                // their original bytes.
                val futureTimestamp = System.currentTimeMillis() / 1000 + FUTURE_TIMESTAMP_MARGIN_SECONDS
                val maxTimestamp = findManifestField(manifest, FIELD_MAX_TIMESTAMP)
                var newManifest = manifest
                var newHeader = header
                if (maxTimestamp != null && maxTimestamp < futureTimestamp) {
                    logger.info(
                        "Bumping manifest max_timestamp $maxTimestamp -> $futureTimestamp " +
                                "(anti-rollback clamp)"
                    )
                    newManifest = rewriteManifestField(manifest, FIELD_MAX_TIMESTAMP, futureTimestamp)
                }
                // 2a-2. Per-partition version clamping: Lenovo's hardware_android.cc
                // compares PartitionUpdate.new_partition_version (a build timestamp
                // string) with the device's current partition version; stale
                // third-party packages are likewise rejected with
                // kPayloadTimestampError(51).
                val clampedManifest = clampPartitionVersions(newManifest, futureTimestamp)
                if (!clampedManifest.contentEquals(newManifest)) {
                    logger.info("Partition version timestamps clamped to $futureTimestamp")
                    newManifest = clampedManifest
                }
                if (!newManifest.contentEquals(manifest)) {
                    // The manifest_size inside the header must be rewritten in sync
                    newHeader = header.copyOf().also {
                        putBeLong(it, 12, newManifest.size.toLong())
                    }
                }

                // 2b. metadata signature = RSA_sign(SHA256(newHeader + newManifest))
                // Note: SHA256withRSA hashes the input itself, so feed it the raw
                // data directly. Never pass a pre-computed digest (would cause
                // double hashing).
                val metadataDigest = MessageDigest.getInstance("SHA-256")
                    .digest(concat(newHeader, newManifest))
                logger.info(
                    "Signing metadata digest: " +
                            metadataDigest.joinToString("") { "%02x".format(it) }
                )
                val newMetadataSig = buildSigBlob(signData(privateKey, concat(newHeader, newManifest)))
                if (newMetadataSig.size != SIG_BLOB_SIZE) {
                    logger.error("Unexpected metadata sig blob size ${newMetadataSig.size}")
                    return false
                }

                // 3. Stream out the new payload.bin: header + manifest + metadata
                // signature + data section + payload signature.
                // Payload signature coverage = header + manifest + data section
                // (AOSP DeltaPerformer's signed_hash_calculator_ continuously
                // accumulates header+manifest (used for metadata signature
                // verification) and the data section; the only exclusions are the
                // trailing signature block itself and the middle metadata signature
                // block — the latter is truncated at metadata_size_ by
                // DiscardBuffer(false, metadata_size_) and does not enter the hash).
                val payloadHashBeforeSig = MessageDigest.getInstance("SHA-256")
                val crc32 = CRC32()
                var written = 0L
                FileOutputStream(tmpPayload).use { out ->
                    out.write(newHeader); written += newHeader.size
                    out.write(newManifest); written += newManifest.size
                    out.write(newMetadataSig); written += newMetadataSig.size
                    payloadHashBeforeSig.update(newHeader)
                    payloadHashBeforeSig.update(newManifest)
                    crc32.update(newHeader)
                    crc32.update(newManifest)
                    crc32.update(newMetadataSig)

                    // Data section: copy dataLength bytes from metaSize, skipping the
                    // original payload signature block
                    var skipped = 0L
                    val buffer = ByteArray(1 shl 20)
                    while (skipped < dataLength) {
                        val chunk = src.read(
                            buffer, 0,
                            minOf(buffer.size.toLong(), dataLength - skipped).toInt()
                        )
                        if (chunk <= 0) {
                            logger.error("Payload data section ended early")
                            return false
                        }
                        out.write(buffer, 0, chunk)
                        payloadHashBeforeSig.update(buffer, 0, chunk)
                        crc32.update(buffer, 0, chunk)
                        written += chunk
                        skipped += chunk
                    }
                    // Append the payload signature block
                    val payloadDigest = payloadHashBeforeSig.digest()
                    logger.info(
                        "Signing payload digest (header+manifest+data, excl. metadata sig): " +
                                payloadDigest.joinToString("") { "%02x".format(it) }
                    )
                    val payloadSig = buildSigBlob(signDigest(privateKey, payloadDigest))
                    if (payloadSig.size != SIG_BLOB_SIZE) {
                        logger.error("Unexpected payload sig blob size ${payloadSig.size}")
                        return false
                    }
                    out.write(payloadSig)
                    written += payloadSig.size
                    crc32.update(payloadSig)
                }
                logger.info(
                    "New payload.bin staged: $written bytes (original $originalSize), crc=${crc32.value}"
                )

                // 4. Update payload_properties.txt
                val fileHash = sha256Of(tmpPayload)
                val metadataHash = MessageDigest.getInstance("SHA-256")
                    .digest(concat(newHeader, newManifest))
                val newProps = if (propsEntry != null) {
                    val old = zf.getInputStream(propsEntry).bufferedReader().readText()
                    updateProperties(
                        old,
                        fileHash to written,
                        metadataHash to (24L + newManifest.size)
                    )
                } else {
                    null
                }

                // 5. Rebuild the zip (payload.bin/payload_properties.txt stay STORED,
                // other entries keep their original method)
                ZipOutputStream(FileOutputStream(tmpZip)).use { zos ->
                    for (entry in zf.entries()) {
                        when (entry.name) {
                            PAYLOAD_ENTRY -> {
                                val ne = ZipEntry(PAYLOAD_ENTRY).apply {
                                    method = ZipEntry.STORED
                                    size = written
                                    crc = crc32.value
                                    time = entry.time
                                }
                                zos.putNextEntry(ne)
                                FileInputStream(tmpPayload).use { it.copyTo(zos, 1 shl 20) }
                                zos.closeEntry()
                            }
                            PROPERTY_ENTRY -> {
                                val bytes = (newProps ?: "").toByteArray(Charsets.UTF_8)
                                val ne = ZipEntry(PROPERTY_ENTRY).apply {
                                    method = ZipEntry.STORED
                                    size = bytes.size.toLong()
                                    crc = CRC32().apply { update(bytes) }.value
                                    time = entry.time
                                }
                                zos.putNextEntry(ne)
                                zos.write(bytes)
                                zos.closeEntry()
                            }
                            else -> {
                                val bytes = zf.getInputStream(entry).readBytes()
                                val ne = ZipEntry(entry.name).apply {
                                    method = entry.method
                                    if (entry.method == ZipEntry.STORED) {
                                        size = bytes.size.toLong()
                                        crc = CRC32().apply { update(bytes) }.value
                                    }
                                    time = entry.time
                                }
                                zos.putNextEntry(ne)
                                zos.write(bytes)
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            logger.error("Failed to sign OTA zip", t)
            tmpZip.delete()
            return false
        } finally {
            tmpPayload.delete()
        }
        if (!tmpZip.renameTo(zipFile)) {
            logger.error("Failed to replace original OTA zip with signed one")
            tmpZip.delete()
            return false
        }
        logger.info("Signed OTA zip written: ${zipFile.absolutePath} (${zipFile.length()} bytes)")
        return true
    }

    private fun sha256Of(file: File): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest()
    }

    private fun updateProperties(
        old: String,
        fileHashAndSize: Pair<ByteArray, Long>,
        metadataHashAndSize: Pair<ByteArray, Long>
    ): String {
        val replacements = mapOf(
            "FILE_HASH" to base64(fileHashAndSize.first),
            "FILE_SIZE" to fileHashAndSize.second.toString(),
            "METADATA_HASH" to base64(metadataHashAndSize.first),
            "METADATA_SIZE" to metadataHashAndSize.second.toString()
        )
        return old.lines().joinToString("\n") { line ->
            val key = line.substringBefore('=', "")
            if (key in replacements) "$key=${replacements[key]}" else line
        }
    }

    private fun base64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    /**
     * Builds the 267-byte signature block: 0a8802 128002 <256B signature> 1d00010000
     */
    private fun buildSigBlob(signature: ByteArray): ByteArray {
        require(signature.size == 256) { "RSA-2048 signature must be 256 bytes" }
        return ByteArrayOutputStream(SIG_BLOB_SIZE).use { out ->
            out.write(SIG_BLOB_OUTER_HEAD)
            out.write(SIG_BLOB_INNER_HEAD)
            out.write(signature)
            out.write(SIG_BLOB_TAIL)
            out.toByteArray()
        }
    }

    /**
     * RSA-SHA256 signs raw data (Signature hashes it itself; the data must be
     * the un-hashed original).
     * Only suitable for small data (the metadata case is header+manifest, ~437KB).
     */
    private fun signData(privateKey: PrivateKey, data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(data)
            sign()
        }

    /**
     * Signs a pre-computed digest: NONEwithRSA + manual DigestInfo(SHA-256).
     * Used for large data (reuses the streamed hash result when the payload data
     * section cannot be read a second time).
     * DigestInfo prefix = SEQUENCE{SEQ{OID 2.16.840.1.101.3.4.2.1, NULL}, OCTET(32)}.
     */
    private fun signDigest(privateKey: PrivateKey, digest: ByteArray): ByteArray {
        require(digest.size == 32) { "Expected SHA-256 digest" }
        val digestInfo = byteArrayOf(
            0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60.toByte(), 0x86.toByte(),
            0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20
        ) + digest
        return Signature.getInstance("NONEwithRSA").run {
            initSign(privateKey)
            update(digestInfo)
            sign()
        }
    }

    /**
     * Parses the manifest top-level protobuf and returns the varint value of the
     * specified field (null if not found).
     */
    private fun findManifestField(manifest: ByteArray, target: Long): Long? {
        var i = 0
        while (i < manifest.size) {
            val (key, next) = readVarint(manifest, i)
            val field = key ushr 3
            val wireType = (key and 0x7).toInt()
            i = next
            when (wireType) {
                0 -> {
                    val (v, n) = readVarint(manifest, i)
                    i = n
                    if (field == target) return v
                }
                2 -> {
                    val (len, n) = readVarint(manifest, i)
                    i = n + len.toInt()
                }
                5 -> i += 4
                1 -> i += 8
                else -> return null
            }
        }
        return null
    }

    private fun readVarint(buf: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (true) {
            require(i < buf.size) { "varint out of bounds" }
            val b = buf[i].toInt() and 0xff
            i++
            result = result or ((b and 0x7f).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return result to i
    }

    /**
     * Rewrites the specified top-level varint field of the manifest (standard
     * protobuf encoding); other fields keep their original bytes.
     * Appends at the end when the target field is not found.
     */
    private fun rewriteManifestField(manifest: ByteArray, field: Long, value: Long): ByteArray {
        val out = ByteArrayOutputStream(manifest.size + 16)
        var i = 0
        var replaced = false
        while (i < manifest.size) {
            val keyStart = i
            val (key, afterKey) = readVarint(manifest, i)
            val fieldNum = key ushr 3
            val wireType = (key and 0x7).toInt()
            i = afterKey
            when (wireType) {
                0 -> {
                    val (_, afterValue) = readVarint(manifest, i)
                    if (fieldNum == field) {
                        out.write(encodeVarint((field shl 3))) // wireType 0
                        out.write(encodeVarint(value))
                        replaced = true
                    } else {
                        out.write(manifest, keyStart, afterValue - keyStart)
                    }
                    i = afterValue
                }
                2 -> {
                    val (len, afterLen) = readVarint(manifest, i)
                    val end = afterLen + len.toInt()
                    out.write(manifest, keyStart, end - keyStart)
                    i = end
                }
                5 -> {
                    out.write(manifest, keyStart, i + 4 - keyStart)
                    i += 4
                }
                1 -> {
                    out.write(manifest, keyStart, i + 8 - keyStart)
                    i += 8
                }
                else -> {
                    logger.error("Unexpected wire type $wireType at $keyStart during manifest rewrite")
                    return manifest
                }
            }
        }
        if (!replaced) {
            out.write(encodeVarint((field shl 3)))
            out.write(encodeVarint(value))
        }
        return out.toByteArray()
    }

    private fun encodeVarint(value: Long): ByteArray {
        var v = value
        val out = ByteArrayOutputStream()
        while (true) {
            if (v and 0x7fL.inv() == 0L) {
                out.write(v.toInt())
                return out.toByteArray()
            }
            out.write(((v and 0x7f) or 0x80).toInt())
            v = v ushr 7
        }
    }

    private fun putBeLong(buf: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) {
            buf[offset + i] = (value ushr ((7 - i) * 8)).toByte()
        }
    }

    /**
     * Clamps the timestamp version strings inside each PartitionUpdate sub-message.
     * The version field is written by third-party packing tools (field number
     * varies with packer version), so it is identified by content: fields at the
     * top level of a partition sub-message whose value is a 9-11 digit pure
     * numeric string numerically smaller than [futureTimestamp] are uniformly
     * rewritten to the decimal string of futureTimestamp. Operands and other
     * binary fields are unaffected.
     */
    private fun clampPartitionVersions(manifest: ByteArray, futureTimestamp: Long): ByteArray {
        val out = ByteArrayOutputStream(manifest.size + 64)
        var i = 0
        while (i < manifest.size) {
            val keyStart = i
            val (key, afterKey) = readVarint(manifest, i)
            val fieldNum = key ushr 3
            val wireType = (key and 0x7).toInt()
            i = afterKey
            when (wireType) {
                0 -> {
                    val (_, afterValue) = readVarint(manifest, i)
                    out.write(manifest, keyStart, afterValue - keyStart)
                    i = afterValue
                }
                2 -> {
                    val (len, afterLen) = readVarint(manifest, i)
                    val end = afterLen + len.toInt()
                    val content = manifest.copyOfRange(afterLen, end)
                    val rewritten = if (fieldNum == 13L) {
                        clampTimestampStrings(content, futureTimestamp)
                    } else {
                        content
                    }
                    // Write only the tag (key); re-encode the length for the new content
                    out.write(manifest, keyStart, afterKey - keyStart)
                    out.write(encodeVarint(rewritten.size.toLong()))
                    out.write(rewritten)
                    i = end
                }
                5 -> {
                    out.write(manifest, keyStart, i + 4 - keyStart)
                    i += 4
                }
                1 -> {
                    out.write(manifest, keyStart, i + 8 - keyStart)
                    i += 8
                }
                else -> {
                    logger.error("Unexpected wire type $wireType during partition clamp")
                    return manifest
                }
            }
        }
        return out.toByteArray()
    }

    /** Clamps pure-numeric timestamp-like string fields to futureTimestamp inside a protobuf message. */
    private fun clampTimestampStrings(message: ByteArray, futureTimestamp: Long): ByteArray {
        val out = ByteArrayOutputStream(message.size + 16)
        var i = 0
        var changed = false
        while (i < message.size) {
            val keyStart = i
            val (key, afterKey) = readVarint(message, i)
            val wireType = (key and 0x7).toInt()
            i = afterKey
            when (wireType) {
                0 -> {
                    val (_, afterValue) = readVarint(message, i)
                    out.write(message, keyStart, afterValue - keyStart)
                    i = afterValue
                }
                2 -> {
                    val (len, afterLen) = readVarint(message, i)
                    val end = afterLen + len.toInt()
                    val content = message.copyOfRange(afterLen, end)
                    val asText = content.toString(Charsets.US_ASCII)
                    val numeric = asText.length in 9..11 && asText.all { it.isDigit() }
                    if (numeric && asText.toLong() < futureTimestamp) {
                        val newText = futureTimestamp.toString().toByteArray(Charsets.US_ASCII)
                        // Write only the tag (key); re-encode the length for the new content
                        out.write(message, keyStart, afterKey - keyStart)
                        out.write(encodeVarint(newText.size.toLong()))
                        out.write(newText)
                        changed = true
                    } else {
                        out.write(message, keyStart, end - keyStart)
                    }
                    i = end
                }
                5 -> {
                    out.write(message, keyStart, i + 4 - keyStart)
                    i += 4
                }
                1 -> {
                    out.write(message, keyStart, i + 8 - keyStart)
                    i += 8
                }
                else -> return message
            }
        }
        return if (changed) out.toByteArray() else message
    }

    private fun readFully(input: InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(out, read, count - read)
            require(n > 0) { "unexpected EOF" }
            read += n
        }
        return out
    }

    private fun readBeLong(buf: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (buf[offset + i].toLong() and 0xff)
        }
        return result
    }

    private fun readBeInt(buf: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 4) {
            result = (result shl 8) or (buf[offset + i].toLong() and 0xff)
        }
        return result
    }

    private fun concat(a: ByteArray, b: ByteArray): ByteArray =
        a + b
}
