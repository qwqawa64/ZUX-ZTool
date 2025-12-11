package com.qimian233.ztool.hook.modules.packageinstaller;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * 包安装器权限管理Hook模块
 * 强制将权限管理选项设置为"始终允许"，简化用户操作
 */
public class PackageInstallerPermissionHook extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "Always_AllowPermission";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.packageinstaller"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("com.android.packageinstaller".equals(packageName)) {
            hookPackageInstaller(lpparam);
        }
    }

    private void hookPackageInstaller(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 方法1：钩住 startCustomInstallConfirm 方法
            hookStartCustomInstallConfirm(lpparam);

            // 方法2：钩住 PermissionsAdapter 的构造函数
            hookPermissionsAdapterConstructor(lpparam);

            // 方法3：钩住 PermissionsAdapter 的 getCount 方法
            hookPermissionsAdapterGetCount(lpparam);

            // 方法4：钩住 ListView 的 setAdapter 方法
            hookListViewSetAdapter();

            log("Successfully hooked PackageInstaller permission controls");
        } catch (Throwable t) {
            logError("Failed to hook PackageInstaller", t);
        }
    }

    private void hookStartCustomInstallConfirm(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.packageinstaller.PackageInstallerActivityExtra",
                    lpparam.classLoader,
                    "startCustomInstallConfirm",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!isEnabled()) return;

                            Object activityExtra = param.thisObject;
                            Object mAdapter = XposedHelpers.getObjectField(activityExtra, "mAdapter");
                            if (mAdapter == null) return;

                            try {
                                Field[] fields = mAdapter.getClass().getDeclaredFields();
                                ArrayList dataList = null;

                                for (Field field : fields) {
                                    field.setAccessible(true);
                                    if (ArrayList.class.isAssignableFrom(field.getType())) {
                                        Object fieldValue = field.get(mAdapter);
                                        if (fieldValue instanceof ArrayList) {
                                            dataList = (ArrayList<?>) fieldValue;
                                            break;
                                        }
                                    }
                                }

                                if (dataList == null || dataList.isEmpty()) return;

                                Object trustItem = findTrustItem(dataList);
                                if (trustItem == null) return;

                                dataList.clear();
                                dataList.add(trustItem);
                                XposedHelpers.setIntField(mAdapter, "selectId", 2);

                                setPermissionManageType(activityExtra, lpparam.classLoader);
                                XposedHelpers.callMethod(mAdapter, "notifyDataSetChanged");
                                XposedHelpers.setIntField(activityExtra, "mSelectId", 2);

                            } catch (Exception e) {
                                useAlternativeApproach(activityExtra, mAdapter, lpparam.classLoader);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            logError("Failed to hook startCustomInstallConfirm", t);
        }
    }

    private void hookPermissionsAdapterConstructor(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookConstructor(
                    "com.android.packageinstaller.extra.PermissionsAdapter",
                    lpparam.classLoader,
                    android.view.LayoutInflater.class,
                    ArrayList.class,
                    android.os.Handler.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!isEnabled()) return;

                            ArrayList originalList = (ArrayList) param.args[1];
                            if (originalList == null || originalList.isEmpty()) return;

                            Object trustItem = findTrustItem(originalList);
                            if (trustItem != null) {
                                originalList.clear();
                                originalList.add(trustItem);
                            }

                            XposedHelpers.setIntField(param.thisObject, "selectId", 2);
                        }
                    }
            );
        } catch (Throwable t) {
            logError("Failed to hook PermissionsAdapter constructor", t);
        }
    }

    private void hookPermissionsAdapterGetCount(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.packageinstaller.extra.PermissionsAdapter",
                    lpparam.classLoader,
                    "getCount",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!isEnabled()) return;
                            param.setResult(1);
                        }
                    }
            );
        } catch (Throwable t) {
            logError("Failed to hook PermissionsAdapter getCount", t);
        }
    }

    private void hookListViewSetAdapter() {
        try {
            XposedHelpers.findAndHookMethod(
                    android.widget.ListView.class,
                    "setAdapter",
                    android.widget.ListAdapter.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!isEnabled()) return;

                            Object adapter = param.args[0];
                            if (adapter != null && adapter.getClass().getName().contains("PermissionsAdapter")) {
                                XposedHelpers.setIntField(adapter, "selectId", 2);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            logError("Failed to hook ListView setAdapter", t);
        }
    }

    private Object findTrustItem(ArrayList dataList) {
        for (Object item : dataList) {
            try {
                Field indexField = item.getClass().getDeclaredField("index");
                indexField.setAccessible(true);
                int index = (int) indexField.get(item);
                if (index == 2) {
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
            Object appPermsInfoData = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("com.android.packageinstaller.extra.AppPermsInfoData", classLoader),
                    "getInstance",
                    XposedHelpers.getObjectField(activityExtra, "mPkgInfo"),
                    activityExtra,
                    XposedHelpers.getObjectField(activityExtra, "mIntentInfo")
            );

            if (appPermsInfoData != null) {
                XposedHelpers.callMethod(appPermsInfoData, "setPermsManageType", 2);
            }
        } catch (Throwable t) {
            // 忽略错误
        }
    }

    private void useAlternativeApproach(Object activityExtra, Object mAdapter, ClassLoader classLoader) {
        try {
            XposedHelpers.setIntField(mAdapter, "selectId", 2);
            XposedHelpers.setIntField(activityExtra, "mSelectId", 2);
            setPermissionManageType(activityExtra, classLoader);
        } catch (Throwable t) {
            // 忽略所有错误
        }
    }
}
