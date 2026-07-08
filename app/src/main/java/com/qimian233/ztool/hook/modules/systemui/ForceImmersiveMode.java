package com.qimian233.ztool.hook.modules.systemui;

import com.qimian233.ztool.hook.base.BaseHookModule;

import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

/**
 * 强制沉浸式模式 Hook。
 * <p>
 * 通过拦截 SystemUI 的 CommandQueue.setWindowState 方法，
 * 当应用试图显示状态栏/导航栏时，强制将其设为沉浸式（隐藏）状态。
 * 用户仍可通过从顶部/底部滑动手势临时唤出系统栏。
 * </p>
 */
public class ForceImmersiveMode extends BaseHookModule {

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    // 系统栏窗口类型常量
    // TYPE_STATUS_BAR = 1, TYPE_NAVIGATION_BAR = 2
    private static final int WINDOW_STATE_SHOWING = 0;
    private static final int WINDOW_STATE_HIDDEN = 2; // 沉浸式：隐藏但可滑动唤出

    public ForceImmersiveMode() {}

    @Override
    public String getModuleName() {
        return "force_immersive_mode";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEMUI_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        if (!SYSTEMUI_PACKAGE.equals(param.getPackageName())) return;
        log("Loading module ForceImmersiveMode.");
        hookSetWindowState(classLoader);
    }

    private void hookSetWindowState(ClassLoader classLoader) {
        try {
            Class<?> commandQueueClass = classLoader.loadClass(
                    "com.android.systemui.statusbar.CommandQueue");

            // setWindowState(int displayId, int type, int state)
            Method setWindowStateMethod = findMethod(commandQueueClass,
                    "setWindowState", int.class, int.class, int.class);

            this.xposed.hook(setWindowStateMethod).intercept(chain -> {
                java.util.List<Object> args = chain.getArgs();
                int displayId = (int) args.get(0);
                int type = (int) args.get(1);
                int state = (int) args.get(2);

                // 当应用请求显示系统栏（state=0）时，改为沉浸式隐藏（state=2）
                // state=1 是过渡态，state=2 表示已是隐藏态，都不需要再修改
                if (state == WINDOW_STATE_SHOWING) {
                    if (DEBUG) {
                        log("ForceImmersiveMode: intercepting setWindowState("
                                + "displayId=" + displayId
                                + ", type=" + type
                                + ", state=" + state
                                + ") -> forcing state=" + WINDOW_STATE_HIDDEN);
                    }
                    // 使用沉浸式隐藏状态：隐藏但保留滑动手势唤出能力
                    chain.proceed(new Object[]{displayId, type, WINDOW_STATE_HIDDEN});
                } else {
                    chain.proceed();
                }
                return null;
            });

            log("ForceImmersiveMode: CommandQueue.setWindowState hooked successfully.");
        } catch (Exception e) {
            logError("Failed to hook CommandQueue.setWindowState", e);
        }
    }
}
