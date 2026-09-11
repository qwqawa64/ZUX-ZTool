package com.qimian233.ztool.hook.modules.systemui.qs

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.os.UserHandle
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 媒体输出弹窗居中 + 主题色修复：修正从"媒体输出"磁贴拉起 MediaOutputDialog 时
 * 窗口贴屏幕左缘、主题色恒为默认黄色的两个问题。
 *
 * 根因（ZUXOS 1.5.04.495 实测 + 反编译证实）：
 * 1. 贴左：MediaOutputBaseDialog.getGravity() 硬编码返回 19（LEFT|CENTER_VERTICAL）。
 *    正常路径（媒体卡片输出 chip）经 DialogTransitionAnimator.show() 装进全屏透明壳，
 *    gravity 只影响壳内对齐；磁贴路径（广播 → createAndShow(null,...)）没有锚点
 *    Controller，走普通 dialog.show()，1180 宽的窗口带 LEFT gravity 直接落屏。
 * 2. 黄色主题：createAndShow 的 packageName 为 null 时，MediaSwitchingController.start()
 *    跳过 MediaController 绑定，getHeaderIcon() 恒 null，refresh() 的
 *    WallpaperColors.fromBitmap 动态配色链不启动，落到默认 legacy 配色。
 *    而 receiver 的 LAUNCH_MEDIA_OUTPUT_DIALOG 分支证明：只要带包名
 *    （createAndShow(pkg,false,null,true,...)，真机已验证），主题色即随封面正确解析，
 *    且 gravity hook 同样生效。
 *
 * 修法（三个 hook，模块开关统一控制）：
 * - onReceive：LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG 期间置线程内标志（gravity 用）；
 * - getGravity：标志置位时返回 17（CENTER）；
 * - createAndShow：pkg == null 时查询当前活跃媒体会话，注入包名并将
 *   includePlaybackAndAppMetadata 置 true，使磁贴路径等价于"带包名的完整链路"。
 *   SystemUI 持 MODIFY_AUDIO_ROUTING 特权，可全量查询活跃会话（与其自身 start()
 *   回退逻辑同源）。查不到会话时保持原参数（空态弹窗）。
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
        val pkgOk = hookCreateAndShow(classLoader)

        if (receiverOk && gravityOk && pkgOk) {
            logger.info("MediaOutputDialogCenterHook installed")
        } else {
            logger.warn(
                "MediaOutputDialogCenterHook partial: receiver=$receiverOk " +
                    "gravity=$gravityOk createAndShow=$pkgOk"
            )
        }
    }

    /**
     * 在 onReceive 前后维护标志。onReceive 是 BroadcastReceiver 的标准覆写方法，
     * 签名跨版本稳定；MediaOutputDialogReceiver 未被混淆。
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

    /**
     * 磁贴路径升级：createAndShow(pkg, ...) 的 pkg 为 null 时，查活跃媒体会话补包名，
     * 并开启 includePlaybackAndAppMetadata（args[3]）以走完整元数据链路。
     * 正常路径（chip 点击自带包名）与空态（无活跃会话）均保持原参数。
     */
    private fun hookCreateAndShow(classLoader: ClassLoader): Boolean {
        return try {
            val managerClass = classLoader.loadClass(MANAGER_CLASS)
            val createAndShow = findMethod(
                managerClass, "createAndShow",
                String::class.java,                    // packageName
                Boolean::class.javaPrimitiveType!!,    // aboveStatusBar
                classLoader.loadClass(CONTROLLER_CLASS), // DialogTransitionAnimator.Controller
                Boolean::class.javaPrimitiveType!!,    // includePlaybackAndAppMetadata
                UserHandle::class.java,                // userHandle
                android.media.session.MediaSession.Token::class.java // token
            )
            hookWithId(createAndShow, "media_output_create_and_show") { chain ->
                val args = chain.args
                if (args[0] != null) {
                    return@hookWithId chain.proceed()
                }
                val pkg = findActiveMediaPackage(chain.getThisObject())
                if (pkg == null) {
                    return@hookWithId chain.proceed()
                }
                logger.debug("Inject active media package: $pkg")
                chain.proceed(
                    arrayOf(
                        pkg,
                        args[1],
                        args[2],
                        true,   // includePlaybackAndAppMetadata：启用封面配色与播放元数据
                        args[4],
                        args[5]
                    )
                )
            }
            true
        } catch (t: Throwable) {
            logger.error("Hook MediaOutputDialogManager.createAndShow failed", t)
            false
        }
    }

    /**
     * 查询最近活跃的媒体会话包名。getActiveSessionsForUser 是 hidden API
     * （SDK 公开层无此方法，SystemUI 内部即用此全量查询，特权进程内可用），故反射调用。
     * 不过滤播放态：与正常路径语义一致——媒体卡片在暂停态同样显示并可点出弹窗，
     * 仅取系统列表首个会话（列表按媒体按钮会话优先排序）。
     *
     * @param manager MediaOutputDialogManager 实例（借其 context 获取服务）
     */
    private fun findActiveMediaPackage(manager: Any?): String? {
        if (manager == null) return null
        return try {
            val contextField = findField(manager.javaClass, "context")
            val context = contextField.get(manager) as Context
            val sm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val current = android.os.Process.myUserHandle()
            // hidden API：getActiveSessionsForUser(ComponentName, UserHandle)
            val method = MediaSessionManager::class.java.methods.firstOrNull {
                it.name == "getActiveSessionsForUser"
            } ?: run {
                logger.warn("getActiveSessionsForUser not found on this firmware")
                return null
            }
            @Suppress("UNCHECKED_CAST")
            val sessions = method.invoke(sm, null, current) as List<android.media.session.MediaController>
            sessions.firstOrNull()?.packageName
        } catch (t: Throwable) {
            logger.warn("findActiveMediaPackage failed: ${t.message}")
            null
        }
    }

    private companion object {
        const val RECEIVER_CLASS =
            "com.android.systemui.media.dialog.MediaOutputDialogReceiver"
        const val BASE_DIALOG_CLASS =
            "com.android.systemui.media.dialog.MediaOutputBaseDialog"
        const val MANAGER_CLASS =
            "com.android.systemui.media.dialog.MediaOutputDialogManager"
        const val CONTROLLER_CLASS =
            "com.android.systemui.animation.DialogTransitionAnimator\$Controller"
    }
}
