package com.qimian233.ztool.hook.modules.tbengine

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
 * 本地 OTA 包重签 Hook。
 *
 * 用户流程：第三方 ota.zip 放在 /sdcard → 系统更新 APP"本地安装"→
 * UI 发送 "com.lenovo.ota.ab.installing" 广播到 tbengine 的 NotificationReceiver
 * → SwfABInstalling.doMyPrimaryJob() 把 /sdcard/ota.zip 复制到
 * /data/ota_package/local_lenovoota.zip 并交给 update_engine。
 *
 * 本 Hook 拦截 doMyPrimaryJob()：在复制发生前用 ZTool 密钥对（应用侧生成，
 * 经 xposed_module_config 下发）重签 payload，然后 chain.proceed() 放行原逻辑；
 * 同时 hook UpdateEngine.applyPayload 注入 "public_key" 属性，让 update_engine
 * 用 ZTool 公钥验证（AOSP kPublicKeyPropertyName，key rotation 通道）。
 *
 * payload 格式（实测 TB710FU OTA_414_479774.zip，AOSP version 2）：
 * [24B header(CrAU)][manifest][metadata 签名 267B][数据段][payload 签名 267B(文件末尾)]
 * - metadata 签名 = RSA2048-SHA256(header+manifest)，manifest 的
 *   signatures_offset(field4)/signatures_size(field5) 是相对数据段起点的偏移；
 * - payload 签名 = RSA2048-SHA256(header+manifest+metadata签名+数据段)；
 * - 签名块结构固定 267B：0a8802 128002 <256B> 1d00010000。
 * 数据段原样保留 ⇒ manifest 与 signatures_offset/size 均不变，只替换两个签名块。
 *
 * doMyPrimaryJob 跑在 tbengine 的 worker 线程上，阻塞签名不会引发 ANR。
 * 磁盘开销：重签需要约 2 倍包体积的临时空间（新 payload.bin + 新 zip）。
 */
class SignTbEngineLocalOta : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.SIGN_TB_ENGINE_LOCAL_OTA.name

    override fun getTargetPackages(): Array<out String?>? = arrayOf(ScopeKeys.TB_ENGINE.packageName)

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
        private const val RSA_KEY_BITS = 2048
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        // 1. 拦截本地安装工作流：复制前重签
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
                            interceptLocalInstall(context)
                        } else {
                            logger.warn("Unable to resolve Context from SwfABInstalling instance")
                        }
                    } catch (t: Throwable) {
                        // 绝不能向宿主抛异常：tbengine 有 crash 自熔断保护
                        logger.error("Local OTA sign interception failed", t)
                    }
                }
                chain.proceed()
            }
            logger.info("SignTbEngineLocalOta workflow hook installed")
        } catch (t: Throwable) {
            logger.error("Failed to hook SwfABInstalling.doMyPrimaryJob", t)
        }

        // 2. UpdateEngine.applyPayload 注入 public_key 属性
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
     * 从 SwfBase 继承链上提取 MainService（Context）。
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

    private fun interceptLocalInstall(context: Context) {
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

    // ── 密钥 ────────────────────────────────────────────────────────

    /**
     * 从 xposed_module_config 读取应用侧生成的 PKCS#8 私钥。
     * 密钥由 TbEngineSettingsRepository.ensureOtaSigningKeys() 首次打开页面时生成。
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
     * 把 X509 公钥写成 PEM 供 update_engine 读取（update_engine 是 root，可读该路径）。
     */
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

    // ── 重签 ────────────────────────────────────────────────────────

    /**
     * 原地重签 /sdcard/ota.zip：生成新 payload.bin（替换两个签名块，数据段不动）
     * 与更新哈希后的 payload_properties.txt，重建 zip 后原子替换原文件。
     * 返回 true 表示签名完成。
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

                // 1. 读头部 / manifest / 原 metadata 签名块
                val header = readFully(src, 24)
                if (!header.copyOfRange(0, 4).contentEquals(PAYLOAD_MAGIC.toByteArray())) {
                    logger.warn("Unrecognized payload magic: ${header.copyOfRange(0, 4).decodeToString()}")
                    return false
                }
                val manifestSize = readBeLong(header, 12)
                val metadataSigSize = readBeInt(header, 20)
                val manifest = readFully(src, manifestSize.toInt())
                val originalMetadataSig = readFully(src, metadataSigSize.toInt())
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
                // 数据段 = metadata 前缀之后到 payload 签名块之前，原样保留
                val dataLength = sigOffset // 相对数据段起点，即数据段长度

                // 2. metadata 签名 = RSA_sign(SHA256(header + manifest))
                val metadataDigest = MessageDigest.getInstance("SHA-256")
                    .digest(concat(header, manifest))
                val newMetadataSig = buildSigBlob(sign(privateKey, metadataDigest))
                if (newMetadataSig.size != SIG_BLOB_SIZE) {
                    logger.error("Unexpected metadata sig blob size ${newMetadataSig.size}")
                    return false
                }

                // 3. 流式写出新 payload.bin：header + manifest + metadata签名 + 数据段 + payload签名
                val payloadHashBeforeSig = MessageDigest.getInstance("SHA-256")
                val crc32 = CRC32()
                var written = 0L
                FileOutputStream(tmpPayload).use { out ->
                    out.write(header); written += header.size
                    out.write(manifest); written += manifest.size
                    out.write(newMetadataSig); written += newMetadataSig.size
                    payloadHashBeforeSig.update(header)
                    payloadHashBeforeSig.update(manifest)
                    payloadHashBeforeSig.update(newMetadataSig)

                    // 数据段：从 metaSize 起复制 dataLength 字节，跳过原 payload 签名块
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
                    // 追加 payload 签名块
                    val payloadSig = buildSigBlob(sign(privateKey, payloadHashBeforeSig.digest()))
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

                // 4. 更新 payload_properties.txt
                val fileHash = sha256Of(tmpPayload)
                val metadataHash = MessageDigest.getInstance("SHA-256")
                    .digest(concat(header, manifest))
                val newProps = if (propsEntry != null) {
                    val old = zf.getInputStream(propsEntry).bufferedReader().readText()
                    updateProperties(
                        old,
                        fileHash to written,
                        metadataHash to (24L + manifestSize)
                    )
                } else {
                    null
                }

                // 5. 重建 zip（payload.bin/payload_properties.txt 保持 STORED，其余条目原样）
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
                                    crc = java.util.zip.CRC32().apply { update(bytes) }.value
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
                                        crc = java.util.zip.CRC32().apply { update(bytes) }.value
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
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    /**
     * 构造 267 字节签名块：0a8802 128002 <256B 签名> 1d00010000
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

    private fun sign(privateKey: PrivateKey, digest: ByteArray): ByteArray =
        Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(digest)
            sign()
        }

    /**
     * 解析 manifest 顶层 protobuf，返回指定 field 的 varint 值（找不到返回 null）。
     */
    private fun findManifestField(manifest: ByteArray, target: Int): Long? {
        var i = 0
        while (i < manifest.size) {
            val (key, next) = readVarint(manifest, i)
            val field = (key ushr 3).toInt()
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
