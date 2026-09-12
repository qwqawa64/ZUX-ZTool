package com.qimian233.ztool.hook.modules.tbengine

import android.content.Context
import android.os.Environment
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.File

/**
 * 本地 OTA 包签名 Hook（骨架）。
 *
 * 用户流程：第三方 ota.zip 放在 /sdcard → 系统更新 APP"本地安装"→
 * UI 发送 "com.lenovo.ota.ab.installing" 广播到 tbengine 的 NotificationReceiver
 * → SwfABInstalling.doMyPrimaryJob() 把 /sdcard/ota.zip 复制到
 * /data/ota_package/local_lenovoota.zip 并交给 update_engine。
 *
 * 本 Hook 拦截 doMyPrimaryJob()：在复制发生前（ota.zip 仍在 sdcard 时）
 * 用 ZTool 密钥对重签 payload（【骨架阶段：仅记录日志，未实现重签算法】），
 * 然后 chain.proceed() 放行原逻辑。doMyPrimaryJob 跑在 tbengine 的
 * "ab install work" worker 线程上，阻塞签名不会引发 ANR。
 *
 * 已知边界：若引擎内残留上一次下载的包信息（getmOtaPackageLocalFile 非空且文件存在），
 * 原逻辑不会走 sdcard fallback；签名逻辑不会触发，属预期行为。
 * 重签时必须保持 payload.bin / payload_properties.txt 的 STORED 布局，
 * 否则 forNonStreaming 按 zip entry 偏移计算的 payload offset 会失效。
 */
class SignTbEngineLocalOta : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.SIGN_TB_ENGINE_LOCAL_OTA.name

    override fun getTargetPackages(): Array<out String?>? = arrayOf(ScopeKeys.TB_ENGINE.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

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
            logger.info("SignTbEngineLocalOta hook installed")
        } catch (t: Throwable) {
            logger.error("Failed to hook SwfABInstalling.doMyPrimaryJob", t)
        }
    }

    /**
     * 从 SwfBase 继承链上提取 MainService（Context）。
     * SwfBase 持有 mService 字段（MainService 实例）。
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

    /**
     * 拦截本地安装：ota.zip 存在于 sdcard 时执行签名，然后由放行的原逻辑完成
     * 复制 → 解析 → applyPayload。
     * 【骨架】签名算法未实现，这里只做条件判断与日志埋点。
     */
    private fun interceptLocalInstall(context: Context) {
        val localZip = File(Environment.getExternalStorageDirectory(), "ota.zip")
            .takeIf { it.exists() } ?: run {
            logger.debug("No /sdcard/ota.zip found; not a local install flow")
            return
        }
        logger.info("Local install flow detected: $localZip")
        val signed = signOtaZip(localZip)
        if (signed) {
            logger.info("OTA package signed, original flow will continue with the signed package")
        } else {
            logger.warn("Signing not performed (skeleton); original package will be used as-is")
        }
    }

    /**
     * 用 ZTool 密钥对重签 payload。
     * 【骨架占位】待实现：解析 payload.bin manifest protobuf →
     * 生成 metadata/payload 签名块 → 回填 signatures_offset/size →
     * 保持 zip 内 payload.bin / payload_properties.txt 的 STORED 布局回写。
     */
    private fun signOtaZip(zipFile: File): Boolean {
        logger.info(
            "signOtaZip placeholder hit: ${zipFile.absolutePath} " +
                    "(size=${zipFile.length()}), signing algorithm not implemented yet"
        )
        return false
    }
}
