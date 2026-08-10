package com.qimian233.ztool.hook.modules.packageinstaller

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 禁用应用安装后删除APK提示模块
 * 拦截系统包安装器(com.android.packageinstaller)，修改默认的"安装完成后删除安装包"行为
 * 实现首次安装后默认不勾选删除安装包选项，避免误删安装文件
 */
class PackageInstallerNoDeleteModule : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.PACKAGE_INSTALLER_DISABLE_DELETE.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.PACKAGE_INSTALLER.packageName)

    @Throws(Throwable::class)
    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookPackageInstaller(classLoader)
    }

    /**
     * Hook系统包安装器的核心逻辑
     * 拦截InstallSuccessExtra类的initView方法，修改默认的删除安装包行为。
     * 新版 PackageInstaller 将 CheckBox 改为 initView() 中的局部变量，
     * 且 OnCheckedChangeListener 会直接覆写 mDeleteApk，因此需要：
     * 1. 强制 mDeleteApk = false
     * 2. 通过 findViewById 定位 CheckBox 并替换其 listener，防止用户手动勾选后覆盖布尔值
     * 3. 兜底：Hook clearCachedApkIfNeededAndFinish 再次确保 mDeleteApk = false
     */
    private fun hookPackageInstaller(classLoader: ClassLoader) {
        try {
            logger.info("Starting hook for package installer")

            @SuppressLint("PrivateApi") val installSuccessExtraClass = classLoader.loadClass(
                "com.android.packageinstaller.InstallSuccessExtra"
            )

            // --- Hook 1: initView() — 首次设置 + UI 修复 ---
            val initView = installSuccessExtraClass.getDeclaredMethod("initView")
            val mDeleteApkField = installSuccessExtraClass.getDeclaredField("mDeleteApk")
            mDeleteApkField.isAccessible = true

            hookWithId(initView, "init_view") { chain ->
                val result = chain.proceed()
                if (!isEnabled()) {
                    return@hookWithId result
                }

                try {
                    val instance = chain.thisObject
                    logger.debug("Inside initView method for package installer")

                    // 强制 mDeleteApk = false（无论是否配置变更）
                    mDeleteApkField.setBoolean(instance, false)

                    // 通过 findViewById 定位 CheckBox（新版是局部变量，不能通过字段反射）
                    try {
                        val activity = instance as Activity
                        @SuppressLint("DiscouragedApi") val checkBoxId =
                            activity.resources.getIdentifier(
                                "del_check_box", "id", ScopeKeys.PACKAGE_INSTALLER.packageName
                            )
                        if (checkBoxId != 0) {
                            val view = activity.findViewById<View?>(checkBoxId)
                            if (view is CheckBox) {
                                // 更新 UI 为未勾选状态
                                view.isChecked = false
                                // 替换监听器：防止用户手动勾选后覆盖 mDeleteApk
                                view.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
                                    try {
                                        mDeleteApkField.setBoolean(instance, false)
                                    } catch (_: Throwable) {
                                    }
                                    // 永远显示未勾选
                                    if (isChecked) {
                                        buttonView!!.isChecked = false
                                    }
                                }
                                logger.debug("Successfully updated UI checkbox and replaced listener")
                            }
                        } else {
                            logger.warn("CheckBox resource ID 'del_check_box' not found, may be new version")
                        }
                    } catch (uiError: Throwable) {
                        logger.error("Failed to update checkbox UI", uiError)
                    }
                } catch (t: Throwable) {
                    logger.error("Error in afterHookedMethod for initView", t)
                }
                result
            }

            logger.info("Successfully hooked InstallSuccessExtra.initView()")

            // --- Hook 2: clearCachedApkIfNeededAndFinish() — 兜底防护 ---
            // 该方法在删除线程执行完毕后被调用，或在 onStop 中被调用。
            // 再次确保 mDeleteApk = false，作为多层防护。
            try {
                val clearMethod = installSuccessExtraClass.getDeclaredMethod(
                    "clearCachedApkIfNeededAndFinish"
                )
                hookWithId(clearMethod, "clear") { chain ->
                    if (isEnabled()) {
                        try {
                            mDeleteApkField.setBoolean(chain.thisObject, false)
                        } catch (_: Throwable) {
                        }
                    }
                    chain.proceed()
                }
                logger.info("Successfully hooked InstallSuccessExtra.clearCachedApkIfNeededAndFinish()")
            } catch (t: Throwable) {
                logger.error("Failed to hook clearCachedApkIfNeededAndFinish", t)
            }
        } catch (t: Throwable) {
            logger.error("Failed to initialize package installer hook", t)
        }
    }
}
