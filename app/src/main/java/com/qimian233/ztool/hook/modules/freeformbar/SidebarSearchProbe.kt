package com.qimian233.ztool.hook.modules.freeformbar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.RelativeLayout
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * TEMPORARY probe for PLAN_sidebar_drawer_upgrade.md §5.1 (run 2).
 *
 * Run 1 proved the host overlay's `FLAG_NOT_FOCUSABLE` does not block the panel `EditText`:
 * with no flag change the window took input focus and the IME reported `mInputShown=true`.
 * Run 2 checks the realistic path instead: no explicit `showSoftInput`, just a tap on the box.
 *
 * Armed only when `Settings.Global.ztool_ime_probe == 1`; registered as `hook_test` so it needs
 * no frontend switch. Delete this file once the answer is recorded in the plan.
 */
class SidebarSearchProbe : AppHookModule() {

    override fun getModuleName(): String = "hook_test"

    override fun getTargetPackages(): Array<out String?>? =
        arrayOf(ScopeKeys.FREEFORM_SIDEBAR.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val serviceClass = classLoader.loadClass("com.zui.freeform.sidebar.FloatService")
        val onCreate = findMethod(serviceClass, "onCreate")
        val showAllAppWindow = findMethod(serviceClass, "showAllAppWindow")
        val allAppLayoutField = findField(serviceClass, "mAllAppLayout")
        val appIconGvField = findField(serviceClass, "mAppIconGv")
        val popupWindowField = findField(serviceClass, "popupWindow")

        hookWithId(onCreate, "probe_ime_oncreate") { chain ->
            val result = chain.proceed()
            try {
                val context = chain.thisObject as Context
                if (Settings.Global.getInt(context.contentResolver, ARM_KEY, 0) != 1) {
                    return@hookWithId result
                }
                val service = chain.thisObject
                Handler(Looper.getMainLooper()).postDelayed({
                    runProbe(context, service, showAllAppWindow, allAppLayoutField,
                        appIconGvField, popupWindowField)
                }, 4000L)
            } catch (t: Throwable) {
                logger.error("probe: failed to arm", t)
            }
            result
        }
        logger.info("probe: armed on FloatService.onCreate")
    }

    @SuppressLint("SetTextI18n")
    private fun runProbe(
        context: Context,
        service: Any,
        showAllAppWindow: java.lang.reflect.Method,
        allAppLayoutField: java.lang.reflect.Field,
        appIconGvField: java.lang.reflect.Field,
        popupWindowField: java.lang.reflect.Field
    ) {
        try {
            showAllAppWindow.invoke(service)
            val popup = popupWindowField.get(service) as? android.widget.PopupWindow
            val allAppLayout = allAppLayoutField.get(service) as? RelativeLayout
            val grid = appIconGvField.get(service) as? View
            if (allAppLayout == null) {
                logger.error("probe: mAllAppLayout missing, abort")
                return
            }
            val editText = buildEditText(context, allAppLayout)
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            log("panel showing=${popup?.isShowing} focusable=${popup?.isFocusable} " +
                    "immActiveBefore=${imm.isActive}")

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                val loc = IntArray(2)
                editText.getLocationOnScreen(loc)
                val cx = loc[0] + editText.width / 2
                val cy = loc[1] + editText.height / 2
                log("READY_FOR_TAP screen=[${loc[0]},${loc[1]}] size=${editText.width}x" +
                        "${editText.height} center=[$cx,$cy] focus=${editText.hasFocus()} " +
                        "immActive=${imm.isActive} accepting=${imm.isAcceptingText()}")
            }, 2000L)

            handler.postDelayed({
                log("AFTER_TAP text='${editText.text}' focus=${editText.hasFocus()} " +
                        "windowFocus=${editText.hasWindowFocus()} immActive=${imm.isActive(editText)} " +
                        "accepting=${imm.isAcceptingText()} ${visibleFrame(editText)}")
            }, 16000L)

            handler.postDelayed({
                log("END text='${editText.text}'")
                allAppLayout.removeView(editText)
                if (grid != null) {
                    val lp = grid.layoutParams as? RelativeLayout.LayoutParams
                    if (lp != null) {
                        lp.topMargin = 0
                        grid.layoutParams = lp
                    }
                }
                popup?.dismiss()
            }, 18000L)
        } catch (t: Throwable) {
            logger.error("probe: run failed", t)
        }
    }

    private fun buildEditText(context: Context, parent: RelativeLayout): EditText {
        val editText = EditText(context)
        editText.hint = "ztool probe"
        editText.isSingleLine = true
        editText.setBackgroundColor(0x33FF4081)
        val lp = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        parent.addView(editText, lp)
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                log("TEXT_CHANGED '${s}'")
            }
        })
        return editText
    }

    private fun visibleFrame(view: View): String {
        val rect = Rect()
        view.getWindowVisibleDisplayFrame(rect)
        return "visibleFrame=[${rect.top},${rect.bottom}] rootH=${view.rootView.height}"
    }

    private fun log(message: String) {
        logger.info("IME_PROBE $message")
    }

    private companion object {
        const val ARM_KEY = "ztool_ime_probe"
    }
}
