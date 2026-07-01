package com.qimian233.ztool.hook.modules.documentsui;

import android.view.View;
import android.widget.Button;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Android文件选择器(DocumentsUI) 限制解除模块
 * 功能：允许用户在/Android/data等受限目录进行选择操作
 */
public class DocumentsUIBypass extends BaseHookModule {

    public DocumentsUIBypass() {}

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
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if ("com.android.documentsui".equals(packageName)) {
            if (DEBUG) log("开始加载 DocumentsUI 解除限制模块...");
            hookDocumentInfo(classLoader);
            hookPickFragment(classLoader);
        }
    }

    /**
     * Hook DocumentInfo 类，强制解除目录树选择限制
     */
    private void hookDocumentInfo(ClassLoader classLoader) {
        final String documentInfoClass = "com.android.documentsui.base.DocumentInfo";

        try {
            Class<?> docInfoClass = classLoader.loadClass(documentInfoClass);

            // Hook isBlockedFromTree 方法
            Method isBlockedFromTreeMethod = docInfoClass.getDeclaredMethod("isBlockedFromTree");
            this.xposed.hook(isBlockedFromTreeMethod).intercept(chain -> {
                chain.proceed();
                // 强制返回 false，允许选择所有目录
                return false;
            });
            log("成功 Hook DocumentInfo.isBlockedFromTree");

            // 可选：尝试 Hook isBlocked 方法（部分机型或旧版本存在）
            try {
                Method isBlockedMethod = docInfoClass.getDeclaredMethod("isBlocked");
                this.xposed.hook(isBlockedMethod).intercept(chain -> {
                    chain.proceed();
                    return false;
                });
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
            Class<?> pickFragClass = classLoader.loadClass(pickFragmentClass);

            // Hook updateView 方法，在UI更新后强制修改控件状态
            Method updateViewMethod = pickFragClass.getDeclaredMethod("updateView");
            this.xposed.hook(updateViewMethod).intercept(chain -> {
                Object result = chain.proceed();
                Object fragment = chain.getThisObject();

                // 1. 获取并启用 mPick 按钮
                try {
                    Field mPickField = findField(fragment.getClass(), "mPick");
                    if (mPickField != null) {
                        mPickField.setAccessible(true);
                        Object mPick = mPickField.get(fragment);
                        if (mPick instanceof Button) {
                            ((Button) mPick).setEnabled(true);
                        }
                    }
                } catch (NoSuchFieldError e) {
                    // 忽略字段不存在的情况
                }

                // 2. 获取并隐藏 mPickOverlay 覆盖层
                try {
                    Field mPickOverlayField = findField(fragment.getClass(), "mPickOverlay");
                    if (mPickOverlayField != null) {
                        mPickOverlayField.setAccessible(true);
                        Object mPickOverlay = mPickOverlayField.get(fragment);
                        if (mPickOverlay instanceof View) {
                            ((View) mPickOverlay).setVisibility(View.GONE); // View.GONE = 8
                        }
                    }
                } catch (NoSuchFieldError e) {
                    // 忽略字段不存在的情况
                }

                return result;
            });
            log("成功 Hook PickFragment.updateView");

        } catch (Throwable t) {
            logError("Hook PickFragment 失败", t);
        }
    }
}
