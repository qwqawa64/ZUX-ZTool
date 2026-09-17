package com.qimian233.ztool.hook.modules.sogouime

import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method

/**
 * Forced half-width punctuation output for the Lenovo OEM Sogou IME.
 *
 * The OEM IME hardcodes its fullwidth punctuation mapping (no user-facing switch),
 * so ASCII math/tech keys like # * - + = come out as fullwidth ＃＊－＋＝ in Chinese
 * mode. PC IME convention (Rime half_shape, MS Pinyin) keeps those half-width;
 * this hook restores that behavior for a user-configurable symbol list.
 *
 * Ported from BetterZUIKey-SogouOEMExt (GPL-3.0, author-granted): it rewrites
 * text at the commit boundary instead of the IME internals, which keeps the hook
 * surface on stable system classes only (no obfuscated IME symbols).
 *
 * Scope is the IME process only — sensitive apps (banking/games/IM) are never
 * injected.
 */
class HalfWidthPunctHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.HALF_WIDTH_PUNCT.name

    override fun getTargetPackages(): Array<out String> =
        arrayOf(ScopeKeys.SOGOU_OEM_IME.packageName)

    // Rewritten on every handleLoadPackage (hot reload / config change re-entry)
    private var punctTable: Map<Char, Char> = emptyMap()

    override fun handleLoadPackage(param: PackageLoadedParam) {
        punctTable = buildPunctTable(loadSignsConfig())

        val icClass: Class<*> = try {
            param.defaultClassLoader.loadClass("android.inputmethodservice.RemoteInputConnection")
        } catch (_: ClassNotFoundException) {
            // Newer Android builds expose InputConnection wrappers under a different
            // implementation name; try the generic interface path before giving up.
            try {
                param.defaultClassLoader.loadClass(
                    "android.view.inputmethod.InputConnectionDefaultWrapper"
                )
            } catch (_: ClassNotFoundException) {
                logger.warn("RemoteInputConnection not found, hook skipped")
                return
            }
        }

        try {
            val commitText: Method = findMethod(
                icClass, "commitText", CharSequence::class.java, Int::class.javaPrimitiveType
            )
            hookWithId(commitText, "half_width_punct_commit") { chain ->
                val arg0 = chain.args.getOrNull(0) as? CharSequence ?: return@hookWithId chain.proceed()
                val rewritten = rewrite(arg0) ?: return@hookWithId chain.proceed()
                val args = chain.args.toMutableList()
                args[0] = rewritten
                logger.debug("punct: $arg0 -> $rewritten")
                chain.proceed(args.toTypedArray())
            }
            logger.info("HalfWidthPunctHook installed, table=$punctTable")
        } catch (t: Throwable) {
            logger.error("HalfWidthPunctHook install failed", t)
        }
    }

    private fun loadSignsConfig(): String = try {
        remotePreferences.getString(
            PreferenceKeys.HALF_WIDTH_PUNCT_SIGNS.name,
            PreferenceKeys.HALF_WIDTH_PUNCT_SIGNS.default
        ) ?: PreferenceKeys.HALF_WIDTH_PUNCT_SIGNS.default
    } catch (_: Throwable) {
        PreferenceKeys.HALF_WIDTH_PUNCT_SIGNS.default
    }

    /**
     * Rewrites a commit only when it is a single character or an auto-paired
     * couple (both sides in the table). Sentence commits and pastes pass
     * through untouched, so user-authored Chinese text keeps its width.
     */
    private fun rewrite(text: CharSequence): String? {
        val len = text.length
        if (len == 1) {
            val mapped = punctTable[text[0]] ?: return null
            return mapped.toString()
        }
        if (len == 2) {
            val openMapped = punctTable[text[0]] ?: return null
            val closeMapped = punctTable[text[1]] ?: return null
            if (openMapped == text[0] && closeMapped == text[1]) return null
            return "$openMapped$closeMapped"
        }
        return null
    }

    companion object {
        // Pure CJK punctuation has no canonical ASCII counterpart and is never
        // converted (PC IME convention). 、 is excluded too: the OEM IME emits it
        // for both / and \, so a 1:1 mapping is ambiguous.
        private const val NON_CONVERTIBLE = "。，、；：？！“”‘’《》〈〉「」『』【】〔〕（）—…·～￥"

        // Bracket/quote pairs: converting one side auto-extends to the other so
        // the IME's auto-pair never produces mixed-width couples like （)
        private val PAIRS = charArrayOf(
            '(', ')', '[', ']', '{', '}', '<', '>',
            '（', '）', '［', '］', '｛', '｝', '＜', '＞', '《', '》'
        )

        /**
         * Parses the user-facing sign list into a fullwidth→ASCII map.
         * Every input char is normalized to its canonical (ASCII) form first,
         * so pasting ＃ or # selects the same rule; the table keys are the
         * fullwidth forms the IME actually commits, plus ASCII no-ops removed.
         */
        fun buildPunctTable(rawConfig: String): Map<Char, Char> {
            val selected = LinkedHashSet<Char>()
            for (c in rawConfig) {
                val canonical = canonicalize(c) ?: continue
                selected.add(canonical)
            }
            // Auto-extend pairs: selecting one side selects both
            for (i in PAIRS.indices step 2) {
                if (PAIRS[i] in selected) selected.add(PAIRS[i + 1])
                if (PAIRS[i + 1] in selected) selected.add(PAIRS[i])
            }
            val table = HashMap<Char, Char>()
            for (ascii in selected) {
                if (ascii in NON_CONVERTIBLE) continue
                val fullwidth = if (ascii in '!'..'~') (ascii.code + 0xFEE0).toChar() else null
                table[fullwidth ?: ascii] = ascii
            }
            return table
        }

        /** Fullwidth ASCII block (FF01–FF5E) → canonical ASCII; anything else kept only if ASCII printable. */
        private fun canonicalize(c: Char): Char? {
            if (c.code in 0xFF01..0xFF5E) return (c.code - 0xFEE0).toChar()
            if (c in '!'..'~') return c
            return null
        }
    }
}
