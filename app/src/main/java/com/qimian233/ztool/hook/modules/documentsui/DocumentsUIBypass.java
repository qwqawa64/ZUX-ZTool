package com.qimian233.ztool.hook.modules.documentsui;

import android.view.View;
import android.widget.Button;

import com.qimian233.ztool.hook.base.BaseHookModule;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Android文件选择器(DocumentsUI) 限制解除模块
 * 功能：允许用户在/Android/data等受限目录进行选择操作
 */
public class DocumentsUIBypass extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "documents_ui_bypass";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{
                "com.android.documentsui"
        };
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("com.android.documentsui".equals(lpparam.packageName)) {
            if (DEBUG) log("开始加载 DocumentsUI 解除限制模块...");
            hookDocumentInfo(lpparam.classLoader);
            hookPickFragment(lpparam.classLoader);
        }
    }

    /**
     * Hook DocumentInfo 类，强制解除目录树选择限制
     */
    private void hookDocumentInfo(ClassLoader classLoader) {
        final String documentInfoClass = "com.android.documentsui.base.DocumentInfo";

        try {
            // Hook isBlockedFromTree 方法
            XposedHelpers.findAndHookMethod(
                    documentInfoClass,
                    classLoader,
                    "isBlockedFromTree",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // 强制返回 false，允许选择所有目录
                            param.setResult(false);
                        }
                    }
            );
            log("成功 Hook DocumentInfo.isBlockedFromTree");

            // 可选：尝试 Hook isBlocked 方法（部分机型或旧版本存在）
            try {
                XposedHelpers.findAndHookMethod(
                        documentInfoClass,
                        classLoader,
                        "isBlocked",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                param.setResult(false);
                            }
                        }
                );
                log("成功 Hook DocumentInfo.isBlocked");
            } catch (Throwable t) {
                // 方法可能不存在，忽略，不作为主要错误记录
            }

        } catch (Throwable t) {
            logError("Hook DocumentInfo 失败", t);
        }
    }

    /**
     * Hook PickFragment 类，强制启用选择按钮并隐藏遮罩层
     */
    private void hookPickFragment(ClassLoader classLoader) {
        final String pickFragmentClass = "com.android.documentsui.picker.PickFragment";

        try {
            // Hook updateView 方法，在UI更新后强制修改控件状态
            XposedHelpers.findAndHookMethod(
                    pickFragmentClass,
                    classLoader,
                    "updateView",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object fragment = param.thisObject;

                            // 1. 获取并启用 mPick 按钮
                            try {
                                Object mPick = XposedHelpers.getObjectField(fragment, "mPick");
                                if (mPick instanceof Button) {
                                    ((Button) mPick).setEnabled(true);
                                }
                            } catch (NoSuchFieldError e) {
                                // 忽略字段不存在的情况
                            }

                            // 2. 获取并隐藏 mPickOverlay 覆盖层
                            try {
                                Object mPickOverlay = XposedHelpers.getObjectField(fragment, "mPickOverlay");
                                if (mPickOverlay instanceof View) {
                                    ((View) mPickOverlay).setVisibility(View.GONE); // View.GONE = 8
                                }
                            } catch (NoSuchFieldError e) {
                                // 忽略字段不存在的情况
                            }
                        }
                    }
            );
            log("成功 Hook PickFragment.updateView");

        } catch (Throwable t) {
            logError("Hook PickFragment 失败", t);
        }
    }
}
