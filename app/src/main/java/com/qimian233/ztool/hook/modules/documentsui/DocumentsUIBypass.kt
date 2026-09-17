package com.qimian233.ztool.hook.modules.documentsui

import android.annotation.SuppressLint
import android.view.View
import android.widget.Button
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Android file picker (DocumentsUI) restriction bypass module.
 * Function: allows the user to select files in restricted directories
 * such as /Android/data.
 */
@SuppressLint("PrivateApi")
class DocumentsUIBypass : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.DOCUMENTS_UI_BYPASS.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.DOCUMENTS_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        logger.debug("Loading DocumentsUI restriction bypass module...")
        hookDocumentInfo(classLoader)
        hookPickFragment(classLoader)
    }

    /**
     * Hook the DocumentInfo class to force-remove the directory tree selection restriction.
     */
    private fun hookDocumentInfo(classLoader: ClassLoader) {
        val documentInfoClass = "com.android.documentsui.base.DocumentInfo"

        try {
            val docInfoClass = classLoader.loadClass(documentInfoClass)

            // Hook the isBlockedFromTree method
            val isBlockedFromTreeMethod = docInfoClass.getDeclaredMethod("isBlockedFromTree")
            hookWithId(
                isBlockedFromTreeMethod,
                "is_blocked_from_tree"
            ) { chain ->
                chain.proceed()
                false
            }
            logger.info("Successfully hooked DocumentInfo.isBlockedFromTree")

            // Optional: try hooking the isBlocked method (exists on some devices or older versions)
            try {
                val isBlockedMethod = docInfoClass.getDeclaredMethod("isBlocked")
                hookWithId(isBlockedMethod, "is_blocked") { chain ->
                    chain.proceed()
                    false
                }
                logger.info("Successfully hooked DocumentInfo.isBlocked")
            } catch (_: Throwable) {
                // The method may not exist; ignore, not logged as a main error
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook DocumentInfo", t)
        }
    }

    /**
     * Hook the PickFragment class to force-enable the pick button and hide the overlay.
     */
    private fun hookPickFragment(classLoader: ClassLoader) {
        val pickFragmentClass = "com.android.documentsui.picker.PickFragment"

        try {
            val pickFragClass = classLoader.loadClass(pickFragmentClass)

            // Hook the updateView method to force-modify control states after UI update
            val updateViewMethod = pickFragClass.getDeclaredMethod("updateView")
            hookWithId(updateViewMethod, "update_view") { chain ->
                val result = chain.proceed()
                val fragment = chain.thisObject

                // 1. Get and enable the mPick button
                try {
                    val mPickField = findField(fragment.javaClass, "mPick") // null-safe
                    val mPick = mPickField.get(fragment)
                    if (mPick is Button) {
                        mPick.isEnabled = true
                    }
                } catch (_: NoSuchFieldError) {
                    // Ignore missing field
                }

                // 2. Get and hide the mPickOverlay overlay
                try {
                    val mPickOverlayField =
                        findField(fragment.javaClass, "mPickOverlay") // null-safe
                    val mPickOverlay = mPickOverlayField.get(fragment)
                    if (mPickOverlay is View) {
                        mPickOverlay.visibility = View.GONE // View.GONE = 8
                    }
                } catch (_: NoSuchFieldError) {
                    // Ignore missing field
                }
                result
            }
            logger.info("Successfully hooked PickFragment.updateView")
        } catch (t: Throwable) {
            logger.error("Failed to hook PickFragment", t)
        }
    }
}
