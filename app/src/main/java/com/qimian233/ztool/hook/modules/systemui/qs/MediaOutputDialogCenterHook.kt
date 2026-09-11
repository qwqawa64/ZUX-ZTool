package com.qimian233.ztool.hook.modules.systemui.qs

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 媒体输出弹窗居中：修正从"媒体输出"磁贴拉起 MediaOutputDialog 时窗口贴屏幕左缘的问题。
 *
 * 根因（ZUXOS 1.5.04.495 实测 + 反编译证实）：
 * - MediaOutputBaseDialog.getGravity() 硬编码返回 19（LEFT|CENTER_VERTICAL），
 *   SystemUIDialog.updateWindowSize() 会把它写入窗口属性；
 * - 正常路径（媒体卡片输出 chip）经 DialogTransitionAnimator.show() 显示，Dialog 被
 *   装进全屏透明壳，gravity 只影响壳内对齐，内容由 qsFrame margin 定位到媒体卡上方；
 * - 磁贴路径（广播 MediaOutputDialogReceiver → createAndShow(null,...)）没有锚点
 *   Controller，走普通 dialog.show()：1180 宽的窗口带着 LEFT gravity 直接落屏 → 贴左。
 *
 * 修法：receiver 处理 LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG 期间置线程内标志，
 * getGravity() 看到标志时返回 17（CENTER）。onReceive → createAndShow → show →
 * onCreate → updateWindowSize → getGravity 是同一主线程同步调用栈，标志天然精确；
 * 正常路径不经 receiver，零影响。hook 失效时磁贴照常工作，仅弹窗退回贴左形态。
 */
class MediaOutputDialogCenterHook : AppHookModule() {

    // 线程内标志：仅主线程调用栈内可见，避免任何跨线程同步
    private val launchViaTileBroadcast = ThreadLocal.withInitial { false }

    override fun getModuleName(): String = PreferenceKeys.MEDIA_OUTPUT_DIALOG_CENTER.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        val receiverOk = hookReceiver(classLoader)
        val gravityOk = hookGetGravity(classLoader)

        if (receiverOk && gravityOk) {
            logger.info("MediaOutputDialogCenterHook installed")
        } else {
            logger.warn(
                "MediaOutputDialogCenterHook partial: receiver=$receiverOk gravity=$gravityOk"
            )
        }
    }

    /**
     * 在 onReceive 前后维护标志。兼容性说明：onReceive 是 BroadcastReceiver 的标准
     * 覆写方法，两版固件签名一致；MediaOutputDialogReceiver 未被混淆。
     */
    private fun hookReceiver(classLoader: ClassLoader): Boolean {
        return try {
            val receiverClass = classLoader.loadClass(RECEIVER_CLASS)
            val onReceive = findMethod(
                receiverClass, "onReceive",
                android.content.Context::class.java, android.content.Intent::class.java
            )
            hookWithId(onReceive, "media_output_dialog_receiver") { chain ->
                launchViaTileBroadcast.set(true)
                try {
                    chain.proceed()
                } finally {
                    launchViaTileBroadcast.set(false)
                }
            }
            true
        } catch (t: Throwable) {
            logger.error("Hook MediaOutputDialogReceiver.onReceive failed", t)
            false
        }
    }

    /**
     * 覆写 gravity。getGravity() 是 SystemUIDialog 的 virtual 方法且被
     * MediaOutputBaseDialog final 覆写，按显式签名在子类上查找。
     */
    private fun hookGetGravity(classLoader: ClassLoader): Boolean {
        return try {
            val dialogClass = classLoader.loadClass(BASE_DIALOG_CLASS)
            val getGravity = findMethod(dialogClass, "getGravity")
            hookWithId(getGravity, "media_output_dialog_gravity") { chain ->
                if (launchViaTileBroadcast.get()) {
                    android.view.Gravity.CENTER
                } else {
                    chain.proceed()
                }
            }
            true
        } catch (t: Throwable) {
            logger.error("Hook MediaOutputBaseDialog.getGravity failed", t)
            false
        }
    }

    private companion object {
        const val RECEIVER_CLASS =
            "com.android.systemui.media.dialog.MediaOutputDialogReceiver"
        const val BASE_DIALOG_CLASS =
            "com.android.systemui.media.dialog.MediaOutputBaseDialog"
    }
}
