package com.qimian233.ztool.service

import android.content.Intent
import android.service.quicksettings.TileService

/**
 * 媒体输出切换磁贴：点击后拉起系统的媒体输出切换 Dialog
 * （即媒体卡片上"输出切换"入口弹出的 MediaOutputDialog）。
 *
 * 实现完全依赖 Android 标准行为，无 Hook：
 * 1. 以标准 TileService 注册，用户在控制中心编辑器手动拖入；
 * 2. 点击时向 SystemUI 的 MediaOutputDialogReceiver 发送显式广播
 *    （真机已验证该 receiver 对第三方应用可达），由系统侧
 *    MediaOutputDialogManager.createAndShow(null, ...) 渲染弹窗，
 *    空态（无媒体会话）也可正常显示。
 *
 * onClick 在主线程回调，广播为 fire-and-forget，无需额外线程。
 */
class MediaOutputTileService : TileService() {

    override fun onClick() {
        super.onClick()
        sendBroadcast(Intent(ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG).apply {
            setClassName(SYSTEMUI_PACKAGE, RECEIVER_CLASS)
        })
    }

    private companion object {
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
        const val RECEIVER_CLASS = "com.android.systemui.media.dialog.MediaOutputDialogReceiver"
        // AOSP SystemUI 公开 action，MediaOutputDialogReceiver 以静态 receiver 消费
        const val ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG =
            "com.android.systemui.action.LAUNCH_SYSTEM_MEDIA_OUTPUT_DIALOG"
    }
}
