package com.qimian233.ztool.hook.modules.setting

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Temporary probe for ZUI dialog button-panel restyling in com.android.settings.
 *
 * Validates that hot reload reaches a UI-component hook and that an in-process view
 * override takes effect. Uses the test key, so it is always enabled. Delete after
 * validation; never ship.
 */
@SuppressLint("PrivateApi")
class ZuiDialogPanelTestHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.TEST_HOOK.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.SETTINGS.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val controllerClass = param.defaultClassLoader
            .loadClass("com.zui.internal.app.DialogController")
        // The no-arg assembly routine is the only place that resolves the button panel;
        // Action/Float controllers reach it through super.installContent().
        val assemble = findMethod(controllerClass, "c")
        hookWithId(assemble, "zui_dialog_panel_test") { chain ->
            val result = chain.proceed()
            runCatching { restyleButtonPanel(chain.thisObject) }
                .onFailure { logger.warn("ZUI dialog panel restyle skipped: " + it.message) }
            result
        }
        logger.info("ZUI dialog panel test hook installed for " + param.packageName)
    }

    private fun restyleButtonPanel(controller: Any) {
        val window = findField(controller.javaClass, "mWindow").get(controller) as? Window ?: return
        // android.R.id.button1 is public API, so the panel is reached without depending on
        // any obfuscated app resource id.
        val panel = window.findViewById<View>(android.R.id.button1)?.parent as? ViewGroup ?: return

        // ZUI paints the dialog body, the panel and every button with the SAME opaque colour
        // (all alias system_dialog_background_zui), so tinting the panel alone is invisible.
        // Clearing the button backgrounds is what lets the tint show through.
        var buttons = 0
        for (index in 0 until panel.childCount) {
            val child = panel.getChildAt(index)
            if (child is Button) {
                child.background = ColorDrawable(Color.TRANSPARENT)
                buttons++
            } else {
                // The separators: a plain View (horizontal) or an inflated LinearLayout (vertical).
                child.visibility = View.GONE
            }
        }
        if (buttons == 0) return

        // The stock bottom rounding lives on the button backgrounds, so the panel takes it over.
        val radius = 20f * panel.resources.displayMetrics.density
        panel.background = GradientDrawable().apply {
            setColor(TEST_PANEL_TINT)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
        }
        logger.info("ZUI dialog panel restyled: buttons=" + buttons)
    }

    private companion object {
        /** Translucent blue: chosen only so the override is unmistakable on screen. */
        const val TEST_PANEL_TINT = 0x662196F3
    }
}
