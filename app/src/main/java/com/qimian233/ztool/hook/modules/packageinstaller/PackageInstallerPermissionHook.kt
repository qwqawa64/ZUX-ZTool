package com.qimian233.ztool.hook.modules.packageinstaller

import android.annotation.SuppressLint
import android.os.Handler
import android.view.LayoutInflater
import android.widget.ListAdapter
import android.widget.ListView
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * 包安装器权限管理Hook模块
 * 强制将权限管理选项设置为"始终允许"，简化用户操作
 */
@SuppressLint("PrivateApi")
class PackageInstallerPermissionHook : AppHookModule() {
    override fun getModuleName(): String = PreferenceKeys.ALWAYS_ALLOW_PERMISSION.name

    override fun getTargetPackages(): Array<String> = arrayOf(ScopeKeys.PACKAGE_INSTALLER.packageName)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        hookPackageInstaller(classLoader)
    }

    private fun hookPackageInstaller(classLoader: ClassLoader) {
        try {
            // 方法1：钩住 startCustomInstallConfirm 方法
            hookStartCustomInstallConfirm(classLoader)

            // 方法2：钩住 PermissionsAdapter 的构造函数
            hookPermissionsAdapterConstructor(classLoader)

            // 方法3：钩住 PermissionsAdapter 的 getCount 方法
            hookPermissionsAdapterGetCount(classLoader)

            // 方法4：钩住 ListView 的 setAdapter 方法
            hookListViewSetAdapter()

            logger.info("Successfully hooked PackageInstaller permission controls")
        } catch (t: Throwable) {
            logger.error("Failed to hook PackageInstaller", t)
        }
    }

    private fun hookStartCustomInstallConfirm(classLoader: ClassLoader) {
        try {
            val activityExtraClass = classLoader.loadClass(
                "com.android.packageinstaller.PackageInstallerActivityExtra"
            )
            val startCustomInstallConfirm =
                activityExtraClass.getDeclaredMethod("startCustomInstallConfirm")
            hookWithId(
                startCustomInstallConfirm,
                "start_custom_install_confirm"
            ) { chain ->
                val result = chain.proceed()
                if (!isEnabled()) return@hookWithId result

                val activityExtra = chain.thisObject
                val mAdapterField = activityExtra.javaClass.getDeclaredField("mAdapter")
                mAdapterField.isAccessible = true
                val mAdapter = mAdapterField.get(activityExtra) ?: return@hookWithId result

                try {
                    val fields = mAdapter.javaClass.declaredFields
                    var dataList: ArrayList<Any>? = null

                    for (field in fields) {
                        field.isAccessible = true
                        if (ArrayList::class.java.isAssignableFrom(field.type)) {
                            val fieldValue = field.get(mAdapter)
                            if (fieldValue is ArrayList<*>) {
                                @Suppress("UNCHECKED_CAST") val list = fieldValue as ArrayList<Any>?
                                dataList = list
                                break
                            }
                        }
                    }

                    if (dataList.isNullOrEmpty()) return@hookWithId result

                    val trustItem = findTrustItem(dataList) ?: return@hookWithId result

                    dataList.clear()
                    dataList.add(trustItem)

                    val selectIdField = mAdapter.javaClass.getDeclaredField("selectId")
                    selectIdField.isAccessible = true
                    selectIdField.setInt(mAdapter, 2)

                    setPermissionManageType(activityExtra, classLoader)

                    val notifyMethod =
                        mAdapter.javaClass.getDeclaredMethod("notifyDataSetChanged")
                    notifyMethod.invoke(mAdapter)

                    val mSelectIdField = activityExtra.javaClass.getDeclaredField("mSelectId")
                    mSelectIdField.isAccessible = true
                    mSelectIdField.setInt(activityExtra, 2)
                } catch (_: Exception) {
                    useAlternativeApproach(activityExtra, mAdapter, classLoader)
                }
                result
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook startCustomInstallConfirm", t)
        }
    }

    private fun hookPermissionsAdapterConstructor(classLoader: ClassLoader) {
        try {
            val permissionsAdapterClass = classLoader.loadClass(
                "com.android.packageinstaller.extra.PermissionsAdapter"
            )
            val ctor: Constructor<*> = permissionsAdapterClass.getDeclaredConstructor(
                LayoutInflater::class.java,
                ArrayList::class.java,
                Handler::class.java
            )
            hookWithId(ctor, "ctor") { chain ->
                chain.proceed()
                if (!isEnabled()) return@hookWithId null

                @Suppress("UNCHECKED_CAST") val originalList = chain.args[1] as ArrayList<Any>
                if (originalList.isEmpty()) return@hookWithId null

                val trustItem = findTrustItem(originalList)
                if (trustItem != null) {
                    originalList.clear()
                    originalList.add(trustItem)
                }

                val selectIdField = chain.thisObject.javaClass.getDeclaredField("selectId")
                selectIdField.isAccessible = true
                selectIdField.setInt(chain.thisObject, 2)
                null
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook PermissionsAdapter constructor", t)
        }
    }

    private fun hookPermissionsAdapterGetCount(classLoader: ClassLoader) {
        try {
            val permissionsAdapterClass = classLoader.loadClass(
                "com.android.packageinstaller.extra.PermissionsAdapter"
            )
            val getCount = permissionsAdapterClass.getDeclaredMethod("getCount")
            hookWithId(getCount, "get_count") { chain ->
                if (!isEnabled()) return@hookWithId chain.proceed()
                chain.proceed()
                1
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook PermissionsAdapter getCount", t)
        }
    }

    private fun hookListViewSetAdapter() {
        try {
            val setAdapter = ListView::class.java.getDeclaredMethod(
                "setAdapter", ListAdapter::class.java
            )
            hookWithId(setAdapter, "set_adapter") { chain ->
                if (!isEnabled()) return@hookWithId chain.proceed()
                val adapter = chain.args[0]
                if (adapter != null && adapter.javaClass.name.contains("PermissionsAdapter")) {
                    val selectIdField = adapter.javaClass.getDeclaredField("selectId")
                    selectIdField.isAccessible = true
                    selectIdField.setInt(adapter, 2)
                }
                chain.proceed()
            }
        } catch (t: Throwable) {
            logger.error("Failed to hook ListView setAdapter", t)
        }
    }

    private fun findTrustItem(dataList: ArrayList<*>): Any? {
        for (item in dataList) {
            try {
                val indexField = item.javaClass.getDeclaredField("index")
                indexField.isAccessible = true
                val indexObj = indexField.get(item)
                if (indexObj is Int && indexObj == 2) {
                    return item
                }
            } catch (_: Exception) {
                // 忽略错误，继续查找
            }
        }
        return null
    }

    private fun setPermissionManageType(activityExtra: Any, classLoader: ClassLoader) {
        try {
            val appPermsInfoDataClass = classLoader.loadClass(
                "com.android.packageinstaller.extra.AppPermsInfoData"
            )

            val mPkgInfoField = activityExtra.javaClass.getDeclaredField("mPkgInfo")
            mPkgInfoField.isAccessible = true

            val mIntentInfoField = activityExtra.javaClass.getDeclaredField("mIntentInfo")
            mIntentInfoField.isAccessible = true

            // Find getInstance method with 3 params
            var getInstanceMethod: Method? = null
            for (m in appPermsInfoDataClass.declaredMethods) {
                if (m.name == "getInstance" && m.parameterTypes.size == 3) {
                    getInstanceMethod = m
                    break
                }
            }
            if (getInstanceMethod == null) return
            getInstanceMethod.isAccessible = true

            val appPermsInfoData = getInstanceMethod.invoke(
                null,
                mPkgInfoField.get(activityExtra),
                activityExtra,
                mIntentInfoField.get(activityExtra)
            )

            if (appPermsInfoData != null) {
                // Find setPermsManageType method with 1 param
                for (m in appPermsInfoData.javaClass.declaredMethods) {
                    if (m.name == "setPermsManageType" && m.parameterTypes.size == 1) {
                        m.isAccessible = true
                        m.invoke(appPermsInfoData, 2)
                        break
                    }
                }
            }
        } catch (_: Throwable) {
            // 忽略错误
        }
    }

    private fun useAlternativeApproach(
        activityExtra: Any,
        mAdapter: Any,
        classLoader: ClassLoader
    ) {
        try {
            val selectIdField = mAdapter.javaClass.getDeclaredField("selectId")
            selectIdField.isAccessible = true
            selectIdField.setInt(mAdapter, 2)

            val mSelectIdField = activityExtra.javaClass.getDeclaredField("mSelectId")
            mSelectIdField.isAccessible = true
            mSelectIdField.setInt(activityExtra, 2)

            setPermissionManageType(activityExtra, classLoader)
        } catch (_: Throwable) {
            // 忽略所有错误
        }
    }
}
