package com.qimian233.ztool.hook.modules.freeformbar

import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.TextView
import com.qimian233.ztool.hook.base.ModuleLog

/**
 * The all-apps panel's own chrome: a search field pinned to the top of the panel and a hint shown
 * while a query matches nothing.
 *
 * The field is added once, because the panel's view tree is inflated once per service process;
 * every later panel show only clears the query. Each sibling of the field is pushed below it, so
 * the grid and the A–Z rail keep the space they had.
 */
internal class SidebarAllAppsSearch(private val logger: ModuleLog) {

    private var inputField: EditText? = null
    private var emptyView: TextView? = null
    private var onQuery: (String) -> Unit = {}
    private var silent = false

    /** The installed field, for callers that have to keep it out of the panel's hover handling. */
    val view: View? get() = inputField

    fun attach(panel: ViewGroup, query: (String) -> Unit) {
        onQuery = query
        val existing = inputField
        if (existing != null && existing.parent === panel) {
            existing.visibility = View.VISIBLE
            clear()
            return
        }
        val input = existing ?: buildField(panel).also { inputField = it }
        if (input.parent == null) panel.addView(input, 0)
        input.visibility = View.VISIBLE
        pushSiblingsBelow(panel, input)
        installEmptyView(panel)
        clear()
    }

    /** Hides the field without dropping it, so its `layout_below` anchors stay resolvable. */
    fun detach() {
        inputField?.visibility = View.GONE
        setEmpty(false)
    }

    /** Shows or hides the "nothing matched" hint. */
    fun setEmpty(visible: Boolean) {
        emptyView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Clears the query without reporting it; the caller rebuilds the rows for the empty query. */
    fun clear() {
        val input = inputField ?: return
        silent = true
        input.setText("")
        silent = false
    }

    private fun buildField(panel: ViewGroup): EditText {
        val context = panel.context
        val density = context.resources.displayMetrics.density
        val input = EditText(context).apply {
            id = View.generateViewId()
            hint = SidebarStrings.get(
                context, SidebarStrings.STRING_SEARCH_HINT, SidebarStrings.FALLBACK_SEARCH_HINT
            )
            setHintTextColor(HINT_COLOR)
            setTextColor(TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            background = pill(density)
            setPadding(
                (PAD_H_DP * density).toInt(), (PAD_V_DP * density).toInt(),
                (PAD_H_DP * density).toInt(), (PAD_V_DP * density).toInt()
            )
            addTextChangedListener(watcher)
        }
        input.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
            val margin = (MARGIN_DP * density).toInt()
            marginStart = margin
            marginEnd = margin
            bottomMargin = margin
        }
        return input
    }

    /** Everything already in the panel moves under the field instead of behind it. */
    private fun pushSiblingsBelow(panel: ViewGroup, input: View) {
        for (index in 0 until panel.childCount) {
            val child = panel.getChildAt(index)
            if (child === input || child === emptyView) continue
            val params = child.layoutParams as? RelativeLayout.LayoutParams ?: continue
            params.addRule(RelativeLayout.BELOW, input.id)
            child.layoutParams = params
        }
    }

    private fun installEmptyView(panel: ViewGroup) {
        if (emptyView != null) return
        val context = panel.context
        val label = TextView(context).apply {
            text = SidebarStrings.get(
                context, SidebarStrings.STRING_EMPTY, SidebarStrings.FALLBACK_EMPTY
            )
            setTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
            visibility = View.GONE
            isClickable = false
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.CENTER_IN_PARENT) }
        }
        emptyView = label
        panel.addView(label)
    }

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(text: Editable?) {
            if (silent) return
            runCatching { onQuery(text?.toString().orEmpty()) }
                .onFailure { logger.error("all-apps search failed", it) }
        }
    }

    private fun pill(density: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = CORNER_DP * density
        setColor(FIELD_FILL)
    }

    private companion object {
        const val CORNER_DP = 10f
        const val PAD_H_DP = 14f
        const val PAD_V_DP = 8f
        const val MARGIN_DP = 12f
        const val TEXT_SP = 13f
        const val FIELD_FILL = 0x1FFFFFFF
        val TEXT_COLOR = 0xFFFFFFFF.toInt()
        val HINT_COLOR = 0x80FFFFFF.toInt()
    }
}
