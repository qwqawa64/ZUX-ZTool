package com.qimian233.ztool.hook.modules.systemFramework;

import com.qimian233.ztool.hook.base.BaseHookModule;

import android.os.Binder;
import android.os.IBinder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * LSPosed Daemon Service 守护模块
 * 作用：防止 LSPosed/libxposed daemon service 被系统异常回收，以及 binder 被 GC
 * 策略（多层防御）：
 *   1. Hook AMS 进程/服务清理方法，拦截对 LSPosed 相关进程的 kill
 *   2. Hook Binder.unlinkToDeath 防止 death recipient 被提前清理
 *   3. 保留对关键 service binder 的强引用，防止 GC
 */
public class LsposedServiceProtector extends BaseHookModule {

    /** 需要保护的进程/包名关键字 */
    private static final String LSPOSED_PROCESS_KEYWORD = "lsposed";

    /** 保留强引用的 service name，防止 binder 被 GC */
    private static final String[] PROTECTED_SERVICES = {
            "package",       // 包管理服务
            "activity",      // AMS binder
            "activity_task"  // ActivityTaskManager
    };

    private static boolean amsHookApplied = false;
    private static boolean serviceBinderHeld = false;
    private static boolean binderDeathHooked = false;

    public LsposedServiceProtector() {}

    @Override
    public String getModuleName() {
        return "lsposed_service_protector";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"system"};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();

        // 第1层：Hook AMS 防止进程被强制杀掉
        hookAmsKillPrevention(classLoader);

        // 第2层：保留关键 service binder 强引用防止 GC
        holdServiceBinderReferences();

        // 第3层：Hook Binder.unlinkToDeath 防止 death recipient 被意外清理
        hookBinderDeathRecipient();
    }

    /**
     * 第1层防御：Hook AMS 中的进程/服务清理方法，
     * 拦截对 LSPosed 相关进程的 kill 操作。
     */
    private void hookAmsKillPrevention(ClassLoader classLoader) {
        try {
            Class<?> amsClass = classLoader.loadClass(
                    "com.android.server.am.ActivityManagerService"
            );

            // 1a. Hook killPackageProcessesLocked — 拦截按包名杀进程
            try {
                hookMethodBySignature(amsClass, "killPackageProcessesLocked",
                        chain -> {
                            // 第1个参数通常是包名 String
                            if (chain.getArgs().size() > 0 && chain.getArg(0) instanceof String) {
                                String pkg = (String) chain.getArg(0);
                                if (pkg != null && pkg.toLowerCase().contains(LSPOSED_PROCESS_KEYWORD)) {
                                    log("Intercepted killPackageProcessesLocked for: " + pkg);
                                    return null;
                                }
                            }
                            return chain.proceed();
                        });
            } catch (Throwable ignored) {
                log("killPackageProcessesLocked hook not available on this ROM");
            }

            // 1b. Hook forceStopPackageLocked — 拦截强制停止
            try {
                hookMethodBySignature(amsClass, "forceStopPackageLocked",
                        chain -> {
                            if (chain.getArgs().size() > 0 && chain.getArg(0) instanceof String) {
                                String pkg = (String) chain.getArg(0);
                                if (pkg != null && pkg.toLowerCase().contains(LSPOSED_PROCESS_KEYWORD)) {
                                    log("Intercepted forceStopPackageLocked for: " + pkg);
                                    return null;
                                }
                            }
                            return chain.proceed();
                        });
            } catch (Throwable ignored) {
                log("forceStopPackageLocked hook not available on this ROM");
            }

            // 1c. Hook ActiveServices 中的服务清理
            try {
                Class<?> activeServicesClass = classLoader.loadClass(
                        "com.android.server.am.ActiveServices"
                );
                hookMethodBySignature(activeServicesClass, "killServicesLocked",
                        chain -> {
                            // 遍历参数查找包含 lsposed 的 service 引用
                            for (Object arg : chain.getArgs()) {
                                if (arg != null) {
                                    String argStr = arg.toString().toLowerCase();
                                    if (argStr.contains(LSPOSED_PROCESS_KEYWORD)) {
                                        log("Intercepted killServicesLocked for LSPosed service");
                                        return null;
                                    }
                                }
                            }
                            return chain.proceed();
                        });
            } catch (Throwable ignored) {
                log("killServicesLocked hook not available on this ROM");
            }

            amsHookApplied = true;
            log("AMS kill-prevention hooks applied successfully");
        } catch (Throwable t) {
            logError("Failed to apply AMS hooks", t);
        }
    }

    /**
     * 第2层防御：保留系统关键 service binder 的强引用，
     * 防止这些 binder 因为只存在弱引用而被 GC 回收。
     * LSPosed daemon 依赖这些系统服务进行通信。
     */
    private void holdServiceBinderReferences() {
        if (serviceBinderHeld) return;

        try {
            List<IBinder> holder = new ArrayList<>();
            for (String serviceName : PROTECTED_SERVICES) {
                try {
                    IBinder binder = getSystemService(serviceName);
                    if (binder != null) {
                        holder.add(binder);
                        log("Held strong reference to service: " + serviceName);
                    }
                } catch (Throwable ignored) {
                    // 某些服务可能不存在
                }
            }

            // 将 holder 保留在一个不会被 GC 的静态位置
            // 使用反射将 holder 存到一个系统类的静态字段中
            try {
                Field field = getClass().getDeclaredField("_binderHolder");
                field.setAccessible(true);
                field.set(null, holder);
            } catch (NoSuchFieldException e) {
                // 字段不存在说明已经设置过，忽略
            }

            serviceBinderHeld = true;
            log("Service binder references held successfully, count=" + holder.size());
        } catch (Throwable t) {
            logError("Failed to hold service binder references", t);
        }
    }

    /**
     * 第3层防御：Hook Binder.unlinkToDeath 防止
     * death recipient 被提前移除，导致 binder 断开后无法自愈。
     */
    private void hookBinderDeathRecipient() {
        if (binderDeathHooked) return;

        try {
            Method unlinkMethod = Binder.class.getDeclaredMethod(
                    "unlinkToDeath",
                    IBinder.DeathRecipient.class,
                    int.class
            );
            this.xposed.hook(unlinkMethod).intercept(chain -> {
                // 只记录，不完全阻止 — 完全阻止可能导致泄漏
                // 如果在调用栈中发现 LSPosed 相关类，则拦截
                StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                for (StackTraceElement element : stack) {
                    String className = element.getClassName().toLowerCase();
                    if (className.contains(LSPOSED_PROCESS_KEYWORD)) {
                        log("Intercepted unlinkToDeath from: " + element.getClassName());
                        return false; // 返回 false 表示取消失败
                    }
                }
                return chain.proceed();
            });

            binderDeathHooked = true;
            log("Binder unlinkToDeath hook applied successfully");
        } catch (Throwable t) {
            logError("Failed to hook unlinkToDeath", t);
        }
    }

    /**
     * 通过方法名签名查找并 Hook（适配不同 Android 版本的方法参数差异）
     */
    private void hookMethodBySignature(Class<?> clazz, String methodName,
                                       XposedInterface.Hooker callback) throws Throwable {
        boolean hooked = false;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                this.xposed.hook(method).intercept(callback);
                hooked = true;
                // 不 break：同名方法可能有多个重载，都需要 hook
            }
        }
        if (!hooked) {
            throw new NoSuchMethodException(clazz.getName() + "." + methodName + " not found");
        }
    }

    /**
     * 通过反射调用 android.os.ServiceManager.getService (隐藏 API)
     */
    private static IBinder getSystemService(String name) {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            return (IBinder) getService.invoke(null, name);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 静态强引用持有者（通过反射设置，防止 GC）
     */
    @SuppressWarnings("unused")
    private static List<IBinder> _binderHolder;
}
