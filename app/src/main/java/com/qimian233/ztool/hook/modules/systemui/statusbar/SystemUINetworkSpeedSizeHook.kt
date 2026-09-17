package com.qimian233.ztool.hook.modules.systemui.statusbar

import android.annotation.SuppressLint
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.widget.TextView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * SystemUI network speed display style hook module.
 * Modifies the network speed display in the system status bar so the number part is
 * larger and the unit part is smaller.
 *
 * Detection: hooks [TextView.setText] and uses [Class.isInstance] to check whether the
 * caller is a com.android.systemui.zui.NetworkSpeedView instance. Confirmed via Jadx
 * decompilation, all setText(...) call sites inside that class (direct calls from
 * updateNetworkSpeedViewStatus, internal Handler what==1/what==10 branches) are used
 * only for network speed text, so identifying by call source is more precise and
 * reliable than the previous approach of matching "K/s"/"M/s" string suffixes, and is
 * unaffected by system text format changes.
 */
@SuppressLint("PrivateApi")
class SystemUINetworkSpeedSizeHook : AppHookModule() {

    companion object {
        private const val NETWORK_SPEED_VIEW_CLASS = "com.android.systemui.zui.NetworkSpeedView"
    }

    override fun getModuleName(): String = PreferenceKeys.SYSTEMUI_NETWORK_SPEED_SIZE.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SYSTEM_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        try {
            logger.info("Hooking SystemUI network speed display")

            // Load the NetworkSpeedView class for caller type checks in the callback
            val networkSpeedViewClass =
                param.defaultClassLoader.loadClass(NETWORK_SPEED_VIEW_CLASS)

            // Use the hook callback to avoid recursive calls
            val setTextMethod =
                TextView::class.java.getDeclaredMethod("setText", CharSequence::class.java)
            hookWithId(setTextMethod, "set_text") { chain ->
                try {
                    // Only handle NetworkSpeedView instance text, not other status bar TextViews
                    if (networkSpeedViewClass.isInstance(chain.thisObject)) {
                        val text = chain.args[0] as CharSequence
                        if (isNetworkSpeedText(text)) {
                            val styledText = createStyledSpeedText(text.toString())
                            logger.debug("Successfully modified network speed display style")
                            return@hookWithId chain.proceed(arrayOf<Any>(styledText))
                        }
                    }
                } catch (_: Throwable) {
                    // Ignore exceptions during processing
                }
                chain.proceed()
            }

            logger.info("SystemUI network speed display hooks applied")
        } catch (e: Throwable) {
            logger.error("Failed to hook SystemUI network speed display", e)
        }
    }

    /**
     * Check whether this is network speed text.
     * NetworkSpeedView's setText content is always the two-line "number\nunit" format
     * (e.g. "12.3\nK/s"). The call source is already restricted to NetworkSpeedView,
     * so only the newline format requirement is checked here; no dependency on
     * specific unit suffix matching.
     */
    private fun isNetworkSpeedText(text: CharSequence?): Boolean {
        return text != null && text.contains("\n")
    }

    /**
     * Create styled network speed text.
     * Number part at 1.3x size, unit part at 0.9x size.
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
        val unitPart = parts[1]

        val spannableString = SpannableString(numberPart + "\n" + unitPart)

        // Set the number part relative size to 1.3x (larger)
        spannableString.setSpan(
            RelativeSizeSpan(1.3f),
            0, numberPart.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Set the unit part relative size to 0.9x (smaller)
        spannableString.setSpan(
            RelativeSizeSpan(0.9f),
            numberPart.length + 1, spannableString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        return spannableString
    }
}
