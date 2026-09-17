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
 * Auto-accepts the PC→phone file transfer confirmation dialog of Super
 * Interconnect (FileUnion).
 * <p>
 * After FileConnectionConfirmActivity.onCreate completes, the accept logic is
 * triggered directly through the ViewModel, so the dialog is auto-confirmed as
 * soon as it appears, without requiring the user to tap manually.
 * </p>
 * <p>
 * Target class/field/method names are pre-computed via the DexKit offline
 * index (MobileDesktopDexIndexer); if the index is missing, hardcoded names
 * for the current version are used as fallback.
 * </p>
 */
class AutoAcceptFileTransferHook : AppHookModule() {

    override fun getModuleName(): String = PreferenceKeys.AUTO_ACCEPT_FILE_TRANSFER.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.MOBILE_DESKTOP.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

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
                // Retry with fallback name "c"
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
                                liveData.javaClass.getDeclaredMethod(methodName, Any::class.java)
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
     * Finds an update method by parameter signature in the LiveData/MutableLiveData
     * class hierarchy. After obfuscation setValue → l and postValue → i; both have
     * the signature (Object)void. Used only as a fallback when the offline index is
     * missing (values are pre-computed with DexKit at index time).
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
        private const val FALLBACK_VM_FIELD = "a"
        private const val FALLBACK_ACCEPTED_FIELD = "d"
        private const val FALLBACK_LIVE_DATA_FIELD = "b"
    }
}
