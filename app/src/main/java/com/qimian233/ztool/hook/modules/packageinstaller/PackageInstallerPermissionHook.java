package com.qimian233.ztool.hook.modules.packageinstaller;

import com.qimian233.ztool.hook.base.BaseHookModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * 包安装器权限管理Hook模块
 * 强制将权限管理选项设置为"始终允许"，简化用户操作
 */
public class PackageInstallerPermissionHook extends BaseHookModule {

    public PackageInstallerPermissionHook() {}

    @Override
    public String getModuleName() {
        return "Always_AllowPermission";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.packageinstaller"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if ("com.android.packageinstaller".equals(packageName)) {
            hookPackageInstaller(classLoader);
        }
    }

    private void hookPackageInstaller(ClassLoader classLoader) {
        try {
            // 方法1：钩住 startCustomInstallConfirm 方法
            hookStartCustomInstallConfirm(classLoader);

            // 方法2：钩住 PermissionsAdapter 的构造函数
            hookPermissionsAdapterConstructor(classLoader);

            // 方法3：钩住 PermissionsAdapter 的 getCount 方法
            hookPermissionsAdapterGetCount(classLoader);

            // 方法4：钩住 ListView 的 setAdapter 方法
            hookListViewSetAdapter();

            log("Successfully hooked PackageInstaller permission controls");
        } catch (Throwable t) {
            logError("Failed to hook PackageInstaller", t);
        }
    }

    private void hookStartCustomInstallConfirm(ClassLoader classLoader) {
        try {
            Class<?> activityExtraClass = classLoader.loadClass(
                    "com.android.packageinstaller.PackageInstallerActivityExtra");
            Method startCustomInstallConfirm = activityExtraClass.getDeclaredMethod("startCustomInstallConfirm");
            hookWithId(startCustomInstallConfirm, "start_custom_install_confirm", chain -> {
                Object result = chain.proceed();
                if (!isEnabled()) return result;

                Object activityExtra = chain.getThisObject();
                Field mAdapterField = activityExtra.getClass().getDeclaredField("mAdapter");
                mAdapterField.setAccessible(true);
                Object mAdapter = mAdapterField.get(activityExtra);
                if (mAdapter == null) return result;

                try {
                    Field[] fields = mAdapter.getClass().getDeclaredFields();
                    ArrayList<Object> dataList = null;

                    for (Field field : fields) {
                        field.setAccessible(true);
                        if (ArrayList.class.isAssignableFrom(field.getType())) {
                            Object fieldValue = field.get(mAdapter);
                            if (fieldValue instanceof ArrayList) {
                                @SuppressWarnings("unchecked")
                                ArrayList<Object> list = (ArrayList<Object>) fieldValue;
                                dataList = list;
                                break;
                            }
                        }
                    }

                    if (dataList == null || dataList.isEmpty()) return result;

                    Object trustItem = findTrustItem(dataList);
                    if (trustItem == null) return result;

                    dataList.clear();
                    dataList.add(trustItem);

                    Field selectIdField = mAdapter.getClass().getDeclaredField("selectId");
                    selectIdField.setAccessible(true);
                    selectIdField.setInt(mAdapter, 2);

                    setPermissionManageType(activityExtra, classLoader);

                    Method notifyMethod = mAdapter.getClass().getDeclaredMethod("notifyDataSetChanged");
                    notifyMethod.invoke(mAdapter);

                    Field mSelectIdField = activityExtra.getClass().getDeclaredField("mSelectId");
                    mSelectIdField.setAccessible(true);
                    mSelectIdField.setInt(activityExtra, 2);

                } catch (Exception e) {
                    useAlternativeApproach(activityExtra, mAdapter, classLoader);
                }

                return result;
            });
        } catch (Throwable t) {
            logError("Failed to hook startCustomInstallConfirm", t);
        }
    }

    private void hookPermissionsAdapterConstructor(ClassLoader classLoader) {
        try {
            Class<?> permissionsAdapterClass = classLoader.loadClass(
                    "com.android.packageinstaller.extra.PermissionsAdapter");
            Constructor<?> ctor = permissionsAdapterClass.getDeclaredConstructor(
                    android.view.LayoutInflater.class,
                    ArrayList.class,
                    android.os.Handler.class);
            hookWithId(ctor, "ctor", chain -> {
                chain.proceed();
                if (!isEnabled()) return null;

                @SuppressWarnings("unchecked")
                ArrayList<Object> originalList = (ArrayList<Object>) chain.getArg(1);
                if (originalList == null || originalList.isEmpty()) return null;

                Object trustItem = findTrustItem(originalList);
                if (trustItem != null) {
                    originalList.clear();
                    originalList.add(trustItem);
                }

                Field selectIdField = chain.getThisObject().getClass().getDeclaredField("selectId");
                selectIdField.setAccessible(true);
                selectIdField.setInt(chain.getThisObject(), 2);

                return null;
            });
        } catch (Throwable t) {
            logError("Failed to hook PermissionsAdapter constructor", t);
        }
    }

    private void hookPermissionsAdapterGetCount(ClassLoader classLoader) {
        try {
            Class<?> permissionsAdapterClass = classLoader.loadClass(
                    "com.android.packageinstaller.extra.PermissionsAdapter");
            Method getCount = permissionsAdapterClass.getDeclaredMethod("getCount");
            hookWithId(getCount, "get_count", chain -> {
                if (!isEnabled()) return chain.proceed();
                chain.proceed();
                return 1;
            });
        } catch (Throwable t) {
            logError("Failed to hook PermissionsAdapter getCount", t);
        }
    }

    private void hookListViewSetAdapter() {
        try {
            Method setAdapter = android.widget.ListView.class.getDeclaredMethod(
                    "setAdapter", android.widget.ListAdapter.class);
            hookWithId(setAdapter, "set_adapter", chain -> {
                if (!isEnabled()) return chain.proceed();

                Object adapter = chain.getArg(0);
                if (adapter != null && adapter.getClass().getName().contains("PermissionsAdapter")) {
                    Field selectIdField = adapter.getClass().getDeclaredField("selectId");
                    selectIdField.setAccessible(true);
                    selectIdField.setInt(adapter, 2);
                }

                return chain.proceed();
            });
        } catch (Throwable t) {
            logError("Failed to hook ListView setAdapter", t);
        }
    }

    private Object findTrustItem(ArrayList<?> dataList) {
        for (Object item : dataList) {
            try {
                Field indexField = item.getClass().getDeclaredField("index");
                indexField.setAccessible(true);
                Object indexObj = indexField.get(item);
                if (indexObj instanceof Integer && (Integer) indexObj == 2) {
                    return item;
                }
            } catch (Exception e) {
                // 忽略错误，继续查找
            }
        }
        return null;
    }

    private void setPermissionManageType(Object activityExtra, ClassLoader classLoader) {
        try {
            Class<?> appPermsInfoDataClass = classLoader.loadClass(
                    "com.android.packageinstaller.extra.AppPermsInfoData");

            Field mPkgInfoField = activityExtra.getClass().getDeclaredField("mPkgInfo");
            mPkgInfoField.setAccessible(true);

            Field mIntentInfoField = activityExtra.getClass().getDeclaredField("mIntentInfo");
            mIntentInfoField.setAccessible(true);

            // Find getInstance method with 3 params
            Method getInstanceMethod = null;
            for (Method m : appPermsInfoDataClass.getDeclaredMethods()) {
                if (m.getName().equals("getInstance") && m.getParameterTypes().length == 3) {
                    getInstanceMethod = m;
                    break;
                }
            }
            if (getInstanceMethod == null) return;
            getInstanceMethod.setAccessible(true);

            Object appPermsInfoData = getInstanceMethod.invoke(null,
                    mPkgInfoField.get(activityExtra),
                    activityExtra,
                    mIntentInfoField.get(activityExtra));

            if (appPermsInfoData != null) {
                // Find setPermsManageType method with 1 param
                for (Method m : appPermsInfoData.getClass().getDeclaredMethods()) {
                    if (m.getName().equals("setPermsManageType") && m.getParameterTypes().length == 1) {
                        m.setAccessible(true);
                        m.invoke(appPermsInfoData, 2);
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            // 忽略错误
        }
    }

    private void useAlternativeApproach(Object activityExtra, Object mAdapter, ClassLoader classLoader) {
        try {
            Field selectIdField = mAdapter.getClass().getDeclaredField("selectId");
            selectIdField.setAccessible(true);
            selectIdField.setInt(mAdapter, 2);

            Field mSelectIdField = activityExtra.getClass().getDeclaredField("mSelectId");
            mSelectIdField.setAccessible(true);
            mSelectIdField.setInt(activityExtra, 2);

            setPermissionManageType(activityExtra, classLoader);
        } catch (Throwable t) {
            // 忽略所有错误
        }
    }
}
