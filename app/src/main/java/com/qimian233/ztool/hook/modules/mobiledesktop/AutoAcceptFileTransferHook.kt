package com.qimian233.ztool.hook.modules.mobiledesktop

import android.os.Bundle
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.dexindex.base.DexIndexConstants
import com.qimian233.ztool.hook.base.AppHookModule
import com.qimian233.ztool.hook.base.DexIndexStore
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 自动接受超级互联 PC→手机 文件互传确认弹窗。
 * <p>
 * 在 FileConnectionConfirmActivity.onCreate 完成后直接通过 ViewModel
 * 触发接受逻辑，实现弹窗出现即自动确认，无需用户手动点击。
 * </p>
 * <p>
 * 目标类/字段/方法名通过 DexKit 离线索引（MobileDesktopDexIndexer）预计算，
 * 索引缺失时回退硬编码名称（c/d/b 及继承链签名查找）。
 * </p>
 */
class AutoAcceptFileTransferHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.AUTO_ACCEPT_FILE_TRANSFER.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.MOBILE_DESKTOP.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        // ── 从离线索引读取混淆类名/字段名/方法名（handleLoadPackage 阶段，勿在 lambda 内做 IO） ──
        val vmFieldName = DexIndexStore.string(
            xposed, ScopeKeys.MOBILE_DESKTOP.packageName,
            DexIndexConstants.ModuleKeys.AUTO_ACCEPT_FILE_TRANSFER,
            DexIndexConstants.Keys.VM_FIELD_NAME
        ) ?: FALLBACK_VM_FIELD
        val acceptedFieldName = DexIndexStore.string(
            xposed, ScopeKeys.MOBILE_DESKTOP.packageName,
            DexIndexConstants.ModuleKeys.AUTO_ACCEPT_FILE_TRANSFER,
            DexIndexConstants.Keys.ACCEPTED_FIELD_NAME
        ) ?: FALLBACK_ACCEPTED_FIELD
        val liveDataFieldName = DexIndexStore.string(
            xposed, ScopeKeys.MOBILE_DESKTOP.packageName,
            DexIndexConstants.ModuleKeys.AUTO_ACCEPT_FILE_TRANSFER,
            DexIndexConstants.Keys.LIVE_DATA_FIELD_NAME
        ) ?: FALLBACK_LIVE_DATA_FIELD
        val liveDataUpdateMethodName = DexIndexStore.string(
            xposed, ScopeKeys.MOBILE_DESKTOP.packageName,
            DexIndexConstants.ModuleKeys.AUTO_ACCEPT_FILE_TRANSFER,
            DexIndexConstants.Keys.LIVE_DATA_UPDATE_METHOD
        )

        try {
            val activityClass = classLoader.loadClass(TARGET_CLASS)

            val vmField = try {
                activityClass.getDeclaredField(vmFieldName)
            } catch (_: NoSuchFieldException) {
                // 用回退名称 "c" 再试
                activityClass.getDeclaredField(FALLBACK_VM_FIELD)
            }
            vmField.isAccessible = true
            val vmClass = vmField.type

            val finalVmFieldName = vmField.name

            hookWithId(activityClass.getDeclaredMethod("onCreate", Bundle::class.java), "on_create") { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject
                try {
                    val vmF: Field = activityClass.getDeclaredField(finalVmFieldName)
                    vmF.isAccessible = true
                    val viewModel = vmF.get(activity)
                    if (viewModel == null) {
                        logger.warn("ViewModel is null, skip auto-accept")
                        return@hookWithId result
                    }

                    val vmCls = viewModel.javaClass

                    val acceptedF = try {
                        vmCls.getDeclaredField(acceptedFieldName)
                    } catch (_: NoSuchFieldException) {
                        vmCls.getDeclaredField(FALLBACK_ACCEPTED_FIELD)
                    }
                    acceptedF.isAccessible = true
                    acceptedF.setBoolean(viewModel, true)

                    val liveDataF = try {
                        vmCls.getDeclaredField(liveDataFieldName)
                    } catch (_: NoSuchFieldException) {
                        vmCls.getDeclaredField(FALLBACK_LIVE_DATA_FIELD)
                    }
                    liveDataF.isAccessible = true
                    val liveData = liveDataF.get(viewModel)
                    if (liveData != null) {
                        val updateMethod = liveDataUpdateMethodName?.let { methodName ->
                            try {
                                liveData.javaClass.getDeclaredMethod(methodName, Object::class.java)
                            } catch (_: NoSuchMethodException) {
                                null
                            }
                        } ?: findLiveDataUpdateMethod(liveData.javaClass)
                        if (updateMethod != null) {
                            updateMethod.invoke(liveData, java.lang.Boolean.TRUE)
                            logger.debug(
                                "Auto-accepted file transfer [vm=$finalVmFieldName, " +
                                    "accepted=${acceptedF.name}, ld=${liveDataF.name}]"
                            )
                        } else {
                            logger.warn("Cannot find LiveData update method, skip")
                        }
                    } else {
                        logger.warn("LiveData field is null, skip")
                    }
                } catch (t: Throwable) {
                    logger.error("Failed to auto-accept file transfer", t)
                }
                result
            }
            logger.info("Installed hook for auto-accept file transfer")
        } catch (t: Throwable) {
            logger.error("Failed to install auto-accept file transfer hook", t)
        }
    }

    /**
     * 在 LiveData/MutableLiveData 类层次中按参数签名查找更新方法。
     * 混淆后 setValue → l, postValue → i，二者签名均为 (Object)void。
     * 仅作为离线索引缺失时的回退（索引期已用 DexKit 预计算）。
     */
    private fun findLiveDataUpdateMethod(cls: Class<*>): Method? {
        var current: Class<*>? = cls
        while (current != null && current != Any::class.java) {
            for (m in current.declaredMethods) {
                val params = m.parameterTypes
                if (params.size == 1 && params[0] == Any::class.java
                    && m.returnType == Void.TYPE
                ) {
                    m.isAccessible = true
                    return m
                }
            }
            current = current.superclass
        }
        return null
    }

    companion object {
        private const val TARGET_CLASS =
            "com.motorola.mobiledesktop.files.pc2phone.FileConnectionConfirmActivity"
        private const val FALLBACK_VM_FIELD = "c"
        private const val FALLBACK_ACCEPTED_FIELD = "d"
        private const val FALLBACK_LIVE_DATA_FIELD = "b"
    }
}
