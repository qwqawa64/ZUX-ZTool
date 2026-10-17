package com.qimian233.ztool.hook.modules.sogouime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.text.Spanned
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.data.sogouime.SogouEnterKeyMode
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Remaps hardware Enter, Shift+Enter and Ctrl+Enter in Sogou OEM IME send fields.
 *
 * See `docs/research/sogou_enter_key_behavior.md` for the host-side analysis.
 */
class SogouEnterKeyHook : AppHookModule() {

    private var consumeEnterUp = false

    private var mfGet: Method? = null
    private var mfInputConnectionGet: Method? = null
    private var composingCacheField: Field? = null
    private var composingQuery: Method? = null
    private var composingProbeAvailable = false

    override fun getModuleName(): String = PreferenceKeys.SOGOU_ENTER_KEY.name

    override fun getTargetPackages(): Array<out String> =
        arrayOf(ScopeKeys.SOGOU_OEM_IME.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        resolveComposingProbe(classLoader)

        try {
            val imeClass = classLoader.loadClass("com.sohu.inputmethod.sogou.SogouIME")
            val onKeyDown = findMethod(
                imeClass,
                "onKeyDown",
                Int::class.javaPrimitiveType,
                KeyEvent::class.java
            )
            val onKeyUp = findMethod(
                imeClass,
                "onKeyUp",
                Int::class.javaPrimitiveType,
                KeyEvent::class.java
            )

            hookWithId(onKeyDown, "sogou_enter_key_down") { chain ->
                val keyCode = chain.args[0] as Int
                val event = chain.args[1] as KeyEvent
                if (keyCode != KeyEvent.KEYCODE_ENTER &&
                    keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER
                ) {
                    return@hookWithId chain.proceed()
                }
                if (event.repeatCount > 0) {
                    logger.trace("Enter repeat: code=$keyCode consume=$consumeEnterUp")
                    return@hookWithId if (consumeEnterUp) true else chain.proceed()
                }

                val service = chain.thisObject as InputMethodService
                val mode = readMode()
                val sendEditor = isSendEditor(service)
                val composing = isComposing(service)
                logger.info(
                    "Enter down: code=$keyCode shift=${event.isShiftPressed} " +
                        "ctrl=${event.isCtrlPressed} alt=${event.isAltPressed} " +
                        "meta=${event.isMetaPressed} ${editorSummary(service)} " +
                        "sendEditor=$sendEditor composing=$composing " +
                        "mode=${mode.storageValue}"
                )
                logger.trace(
                    "Enter editor package=${service.currentInputEditorInfo?.packageName}"
                )

                if (event.isAltPressed || event.isMetaPressed) {
                    logger.info("Enter passed: alt/meta modifier")
                    return@hookWithId chain.proceed()
                }
                if (!sendEditor) {
                    logger.info("Enter passed: not a send editor")
                    return@hookWithId chain.proceed()
                }
                if (composing) {
                    logger.info("Enter passed: composing")
                    return@hookWithId chain.proceed()
                }

                val modified = event.isShiftPressed || event.isCtrlPressed
                val send = if (modified) {
                    mode == SogouEnterKeyMode.EnterNewline
                } else {
                    mode == SogouEnterKeyMode.EnterSend
                }
                logger.info("Enter handled: modified=$modified send=$send")
                performEnter(service, send)
                consumeEnterUp = true
                true
            }

            hookWithId(onKeyUp, "sogou_enter_key_up") { chain ->
                val keyCode = chain.args[0] as Int
                if (keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                ) {
                    if (consumeEnterUp) {
                        logger.debug("Enter up consumed: code=$keyCode")
                        consumeEnterUp = false
                        return@hookWithId true
                    }
                    logger.trace("Enter up passed: code=$keyCode")
                }
                chain.proceed()
            }
            logger.info("SogouEnterKeyHook installed")
        } catch (t: Throwable) {
            logger.error("SogouEnterKeyHook install failed", t)
        }
    }

    private fun resolveComposingProbe(classLoader: ClassLoader) {
        try {
            val mfClass = classLoader.loadClass("defpackage.MF")
            mfGet = mfClass.getMethod("V")
            mfInputConnectionGet = mfClass.getMethod("z")
            val inputConnectionClass = classLoader.loadClass("defpackage.Cha")
            composingCacheField = inputConnectionClass.getDeclaredField("e").apply {
                isAccessible = true
            }
            composingQuery = composingCacheField?.type?.getDeclaredMethod("j")?.apply {
                isAccessible = true
            }
            composingProbeAvailable = true
            logger.info("Composing probe resolved")
        } catch (t: Throwable) {
            mfGet = null
            mfInputConnectionGet = null
            composingCacheField = null
            composingQuery = null
            composingProbeAvailable = false
            logger.warn(
                "Composing probe unavailable, falling back to spans: " +
                    t.javaClass.simpleName
            )
        }
    }

    private fun isComposing(service: InputMethodService): Boolean {
        // Sogou's cached InputConnection exposes its composing range; fall back to app spans.
        if (composingProbeAvailable) {
            try {
                val mf = mfGet?.invoke(null)
                val inputConnection = mfInputConnectionGet?.invoke(mf)
                val cache = composingCacheField?.get(inputConnection)
                val composing = composingQuery?.invoke(cache) as? Boolean
                logger.debug("Composing probe: wrapper=$composing")
                if (composing == true) return true
            } catch (t: Throwable) {
                logger.debug("Composing probe failed: ${t.javaClass.simpleName}")
            }
        }

        return try {
            val inputConnection = service.currentInputConnection
            if (inputConnection == null) {
                logger.debug("Composing span probe: no input connection")
                return false
            }
            val text = inputConnection.getExtractedText(
                ExtractedTextRequest().apply { flags = 0 },
                0
            )?.text as? Spanned
            if (text == null) {
                logger.debug("Composing span probe: extracted text is not spanned")
                return false
            }
            val composing = text.getSpans(0, text.length, Any::class.java).any {
                it.javaClass.name.contains("ComposingText")
            }
            logger.debug("Composing span probe: composing=$composing")
            composing
        } catch (t: Throwable) {
            logger.debug("Composing span probe failed: ${t.javaClass.simpleName}")
            false
        }
    }

    private fun editorSummary(service: InputMethodService): String {
        val info = service.currentInputEditorInfo ?: return "editor=null"
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        return "action=$action inputType=${info.inputType}"
    }

    private fun isSendEditor(service: InputMethodService): Boolean {
        val info = service.currentInputEditorInfo ?: return false
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if (action == EditorInfo.IME_ACTION_SEND) return true
        if (action == EditorInfo.IME_ACTION_SEARCH ||
            action == EditorInfo.IME_ACTION_GO ||
            action == EditorInfo.IME_ACTION_NEXT ||
            action == EditorInfo.IME_ACTION_DONE
        ) {
            return false
        }
        val packageName = info.packageName ?: return false
        // Mirror Sogou's own send scope for chat editors whose imeOptions is not SEND.
        if (packageName != "com.tencent.mm" &&
            packageName != "com.tencent.mobileqq" &&
            packageName != "com.tencent.tim"
        ) {
            return false
        }
        return (info.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
    }

    private fun performEnter(service: InputMethodService, send: Boolean) {
        val inputConnection = service.currentInputConnection ?: return
        if (send) {
            inputConnection.performEditorAction(EditorInfo.IME_ACTION_SEND)
        } else {
            inputConnection.commitText("\r\n", 1)
        }
    }

    private fun readMode(): SogouEnterKeyMode = try {
        SogouEnterKeyMode.fromStorage(
            remotePreferences.getString(
                PreferenceKeys.SOGOU_ENTER_KEY_MODE.name,
                PreferenceKeys.SOGOU_ENTER_KEY_MODE.default
            )
        )
    } catch (_: Throwable) {
        SogouEnterKeyMode.EnterSend
    }
}
