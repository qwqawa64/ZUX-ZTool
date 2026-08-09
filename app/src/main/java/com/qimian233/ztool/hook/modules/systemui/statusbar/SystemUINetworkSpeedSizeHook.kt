package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.widget.TextView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 系统UI网速显示样式Hook模块
 * 修改系统状态栏中的网速显示，使数字部分更大、单位部分更小
 *
 * 识别方式：hook [TextView.setText] 后通过 [Class.isInstance] 判断调用者是否为
 * com.android.systemui.zui.NetworkSpeedView 实例。根据 Jadx 反编译确认，该类内部
 * 所有 setText(...) 调用点（updateNetworkSpeedViewStatus 的直接调用、内部 Handler
 * what==1/what==10 分支）均只用于显示网速文本，因此按调用来源识别比原先匹配
 * "K/s"/"M/s" 等字符串后缀的方式更精确可靠，且不受系统文本格式变化影响。
 */
class SystemUINetworkSpeedSizeHook : AppHookModule() {

    companion object {
        private const val NETWORK_SPEED_VIEW_CLASS = "com.android.systemui.zui.NetworkSpeedView"
    }

    override fun getModuleName(): String = PreferenceKeys.SYSTEMUI_NETWORK_SPEED_SIZE.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        try {
            logger.info("开始Hook系统UI网速显示")

            // 加载 NetworkSpeedView 类，用于在回调中判断调用者类型
            val networkSpeedViewClass =
                param.defaultClassLoader.loadClass(NETWORK_SPEED_VIEW_CLASS)

            // 使用 beforeHookedMethod避免递归调用
            val setTextMethod =
                TextView::class.java.getDeclaredMethod("setText", CharSequence::class.java)
            hookWithId(setTextMethod, "set_text") { chain ->
                try {
                    // 仅处理 NetworkSpeedView 实例的文本，不影响状态栏中其他 TextView
                    if (networkSpeedViewClass.isInstance(chain.thisObject)) {
                        val text = chain.args[0] as CharSequence
                        if (isNetworkSpeedText(text)) {
                            val styledText = createStyledSpeedText(text.toString())
                            logger.debug("成功修改网速显示样式")
                            return@hookWithId chain.proceed(arrayOf<Any>(styledText))
                        }
                    }
                } catch (_: Throwable) {
                    // 忽略处理过程中的异常
                }
                chain.proceed()
            }

            logger.info("系统UI网速显示Hook成功")
        } catch (e: Throwable) {
            logger.error("系统UI网速显示Hook失败", e)
        }
    }

    /**
     * 检查是否为网速文本。
     * NetworkSpeedView 的 setText 内容恒为 "数字\n单位" 两行格式（如 "12.3\nK/s"）。
     * 调用来源已限定为 NetworkSpeedView，这里仅保留换行符这一格式前提，
     * 不再依赖具体单位后缀匹配。
     */
    private fun isNetworkSpeedText(text: CharSequence?): Boolean {
        return text != null && text.contains("\n")
    }

    /**
     * 创建带样式的网速文本
     * 数字部分1.3倍大小，单位部分0.9倍大小
     */
    private fun createStyledSpeedText(originalText: String): CharSequence {
        if (!originalText.contains("\n")) {
            return originalText
        }

        val parts = originalText.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size != 2) {
            return originalText
        }

        val numberPart = parts[0]
        val unitPart: String? = parts[1]

        val spannableString = SpannableString(numberPart + "\n" + unitPart)

        // 设置数字部分相对大小为1.3倍（更大）
        spannableString.setSpan(
            RelativeSizeSpan(1.3f),
            0, numberPart.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // 设置单位部分相对大小为0.9倍（更小）
        spannableString.setSpan(
            RelativeSizeSpan(0.9f),
            numberPart.length + 1, spannableString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        return spannableString
    }
}
