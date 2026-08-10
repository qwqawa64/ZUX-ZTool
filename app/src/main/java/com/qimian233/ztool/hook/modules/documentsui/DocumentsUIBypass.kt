package com.qimian233.ztool.hook.modules.documentsui

import android.annotation.SuppressLint
import android.view.View
import android.widget.Button
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Android文件选择器(DocumentsUI) 限制解除模块
 * 功能：允许用户在/Android/data等受限目录进行选择操作
 */
@SuppressLint("PrivateApi")
class DocumentsUIBypass : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.DOCUMENTS_UI_BYPASS.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.DOCUMENTS_UI.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        logger.debug("开始加载 DocumentsUI 解除限制模块...")
        hookDocumentInfo(classLoader)
        hookPickFragment(classLoader)
    }

    /**
     * Hook DocumentInfo 类，强制解除目录树选择限制
     */
    private fun hookDocumentInfo(classLoader: ClassLoader) {
        val documentInfoClass = "com.android.documentsui.base.DocumentInfo"

        try {
            val docInfoClass = classLoader.loadClass(documentInfoClass)

            // Hook isBlockedFromTree 方法
            val isBlockedFromTreeMethod = docInfoClass.getDeclaredMethod("isBlockedFromTree")
            hookWithId(
                isBlockedFromTreeMethod,
                "is_blocked_from_tree"
            ) { chain ->
                chain.proceed()
                false
            }
            logger.info("成功 Hook DocumentInfo.isBlockedFromTree")

            // 可选：尝试 Hook isBlocked 方法（部分机型或旧版本存在）
            try {
                val isBlockedMethod = docInfoClass.getDeclaredMethod("isBlocked")
                hookWithId(isBlockedMethod, "is_blocked") { chain ->
                    chain.proceed()
                    false
                }
                logger.info("成功 Hook DocumentInfo.isBlocked")
            } catch (_: Throwable) {
                // 方法可能不存在，忽略，不作为主要错误记录
            }
        } catch (t: Throwable) {
            logger.error("Hook DocumentInfo 失败", t)
        }
    }

    /**
     * Hook PickFragment 类，强制启用选择按钮并隐藏遮罩层
     */
    private fun hookPickFragment(classLoader: ClassLoader) {
        val pickFragmentClass = "com.android.documentsui.picker.PickFragment"

        try {
            val pickFragClass = classLoader.loadClass(pickFragmentClass)

            // Hook updateView 方法，在UI更新后强制修改控件状态
            val updateViewMethod = pickFragClass.getDeclaredMethod("updateView")
            hookWithId(updateViewMethod, "update_view") { chain ->
                val result = chain.proceed()
                val fragment = chain.thisObject

                // 1. 获取并启用 mPick 按钮
                try {
                    val mPickField = findField(fragment.javaClass, "mPick") // null-safe
                    val mPick = mPickField.get(fragment)
                    if (mPick is Button) {
                        mPick.isEnabled = true
                    }
                } catch (_: NoSuchFieldError) {
                    // 忽略字段不存在的情况
                }

                // 2. 获取并隐藏 mPickOverlay 覆盖层
                try {
                    val mPickOverlayField =
                        findField(fragment.javaClass, "mPickOverlay") // null-safe
                    val mPickOverlay = mPickOverlayField.get(fragment)
                    if (mPickOverlay is View) {
                        mPickOverlay.visibility = View.GONE // View.GONE = 8
                    }
                } catch (_: NoSuchFieldError) {
                    // 忽略字段不存在的情况
                }
                result
            }
            logger.info("成功 Hook PickFragment.updateView")
        } catch (t: Throwable) {
            logger.error("Hook PickFragment 失败", t)
        }
    }
}
