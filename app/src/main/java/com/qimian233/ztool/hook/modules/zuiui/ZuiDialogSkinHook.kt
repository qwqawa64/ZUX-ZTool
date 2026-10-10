package com.qimian233.ztool.hook.modules.zuiui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Replaces the ZUI dialog button separator lines with a tinted button container.
 *
 * Reaches every package the user placed in the LSPosed scope, so [supportsPackage]
 * accepts any name; whether a package can show ZUI dialogs at all is decided by
 * resolving the controller class. Disabled by default.
 */
@SuppressLint("PrivateApi")
class ZuiDialogSkinHook : AppHookModule() {

    @Volatile
    private var installed = false

    override fun getModuleName(): String = PreferenceKeys.ZUI_DIALOG_SKIN.name

    /** Seed list for readers only; see [supportsPackage]. */
    override fun getTargetPackages(): Array<String> = arrayOf(
        ScopeKeys.SETTINGS.packageName,
        ScopeKeys.SYSTEM_UI.packageName,
        ScopeKeys.LAUNCHER.packageName,
        ScopeKeys.ZUI_SAFE_CENTER.packageName,
        ScopeKeys.PACKAGE_INSTALLER.packageName
    )

    /** Membership means "the user scoped this app"; the capability check is the real gate. */
    override fun supportsPackage(packageName: String?): Boolean = true

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        // One process reports several loaded packages; the skin is a per-process concern.
        if (installed) return
        val controllerClass = runCatching {
            param.defaultClassLoader.loadClass(CONTROLLER_CLASS)
        }.getOrNull() ?: return
        // The no-arg assembly routine is the only place that resolves the button panel;
        // Action/Float controllers reach it through super.installContent().
        val assemble = runCatching { findMethod(controllerClass, "c") }.getOrNull() ?: return

        hookWithId(assemble, "zui_dialog_skin") { chain ->
            val result = chain.proceed()
            runCatching { applySkin(chain.thisObject) }
                .onFailure { logger.warn("ZUI dialog skin skipped: " + it.message) }
            result
        }
        installed = true
        logger.info("ZUI dialog skin installed for " + param.packageName)
    }

    private fun applySkin(controller: Any) {
        val window = findField(controller.javaClass, "mWindow").get(controller) as? Window ?: return
        // android.R.id.button1 is public API, so the panel is reached without depending on
        // any obfuscated app resource id.
        val panel = window.findViewById<View>(android.R.id.button1)?.parent as? ViewGroup ?: return

        var buttons = 0
        for (index in 0 until panel.childCount) {
            val child = panel.getChildAt(index)
            if (child is Button) {
                child.background = buttonBackground()
                buttons++
            } else {
                // The separators: a plain View (horizontal) or an inflated LinearLayout (vertical).
                child.visibility = View.GONE
            }
        }
        if (buttons == 0) return

        val radius = dialogRadius(panel)
        panel.background = GradientDrawable().apply {
            setColor(containerOverlay(panel))
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
        }
    }

    /**
     * Stock ZUI paints the panel with the same opaque colour as the dialog body, so the
     * container only becomes visible through a translucent overlay.
     */
    private fun containerOverlay(panel: View): Int {
        val night = (panel.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return if (night) OVERLAY_DARK else OVERLAY_LIGHT
    }

    /**
     * Transparent in the default state so the container shows through, while keeping the
     * press and focus feedback that the stock selector provided.
     */
    private fun buttonBackground(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(FEEDBACK_PRESSED))
        addState(intArrayOf(android.R.attr.state_focused), ColorDrawable(FEEDBACK_FOCUSED))
        addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    }

    /** The stock bottom rounding lives on the button background, so the container takes it over. */
    private fun dialogRadius(panel: View): Float {
        val resources = panel.resources
        val id = resources.getIdentifier(
            RADIUS_RES_NAME, "dimen", panel.context.packageName
        )
        return if (id != 0) {
            resources.getDimensionPixelSize(id).toFloat()
        } else {
            RADIUS_FALLBACK_DP * resources.displayMetrics.density
        }
    }

    private companion object {
        const val CONTROLLER_CLASS = "com.zui.internal.app.DialogController"
        const val RADIUS_RES_NAME = "dialog_radius_zui"

        /** Mirrors the stock dialog_radius_zui, used only when the app's dimen is unreachable. */
        const val RADIUS_FALLBACK_DP = 20f

        /** 12% white / 6% black: a visible surface step without inventing a hue. */
        const val OVERLAY_DARK = 0x1FFFFFFF
        const val OVERLAY_LIGHT = 0x0F000000

        const val FEEDBACK_PRESSED = 0x33FFFFFF
        const val FEEDBACK_FOCUSED = 0x1FFFFFFF
    }
}
