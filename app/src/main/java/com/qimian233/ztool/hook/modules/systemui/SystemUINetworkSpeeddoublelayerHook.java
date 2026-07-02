package com.qimian233.ztool.hook.modules.systemui;

import android.annotation.SuppressLint;
import android.text.Html;

import com.qimian233.ztool.hook.base.BaseHookModule;
import com.qimian233.ztool.hook.base.DexKitHelper;

import io.github.libxposed.api.XposedModuleInterface;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * SystemUI网络速度显示Hook模块
 * 功能：在状态栏显示实时上下行网络速度，支持自定义文本大小和显示格式
 */
@SuppressLint("PrivateApi")
public class SystemUINetworkSpeeddoublelayerHook extends BaseHookModule {

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String NETWORK_SPEED_VIEW_CLASS = "com.android.systemui.zui.NetworkSpeedView";

    // 存储每个实例的上次流量数据
    private final Map<Object, Long> lastRxBytesMap = new WeakHashMap<>();
    private final Map<Object, Long> lastTxBytesMap = new WeakHashMap<>();
    private final Map<Object, Long> lastUpdateTimeMap = new WeakHashMap<>();

    public SystemUINetworkSpeeddoublelayerHook() {}

    @Override
    public String getModuleName() {
        return "systemui_network_speed_doublelayer";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEMUI_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XposedModuleInterface.PackageLoadedParam param) throws Throwable {
        ClassLoader classLoader = param.getDefaultClassLoader();
        String packageName = param.getPackageName();
        if (SYSTEMUI_PACKAGE.equals(packageName)) {
            hookSystemUINetworkSpeed(classLoader);
        }
    }

    private void hookSystemUINetworkSpeed(ClassLoader classLoader) {
        try {
            log("Starting to hook SystemUI NetworkSpeedView");

            // Hook NetworkSpeedView 构造方法
            Constructor<?> ctor = classLoader.loadClass(NETWORK_SPEED_VIEW_CLASS)
                    .getDeclaredConstructor(android.content.Context.class,
                            android.util.AttributeSet.class,
                            int.class);
            this.xposed.hook(ctor).intercept(chain -> {
                chain.proceed();
                initNetworkSpeedView(chain.getThisObject());
                return null;
            });

            // Hook Handler 的 handleMessage 方法
            hookNetworkSpeedHandler(classLoader);

            log("Successfully hooked NetworkSpeedView");

        } catch (Throwable t) {
            logError("Error hooking NetworkSpeedView", t);
        }
    }

    private void initNetworkSpeedView(Object networkSpeedView) {
        try {
            Class<?> cl = networkSpeedView.getClass();
            // 获取初始流量数据
            long initialRxBytes = getTotalRxBytes();
            long initialTxBytes = getTotalTxBytes();

            // 存储初始数据
            lastRxBytesMap.put(networkSpeedView, initialRxBytes);
            lastTxBytesMap.put(networkSpeedView, initialTxBytes);
            lastUpdateTimeMap.put(networkSpeedView, System.currentTimeMillis());

            // 调整文本大小
            try {
                // 获取当前文本大小并增加
                Method getTextSizeMethod = findMethod(cl, "getTextSize");
                Object textSizeResult = getTextSizeMethod.invoke(networkSpeedView);
                float currentTextSize = textSizeResult != null ? (Float) textSizeResult : 8.0f;
                float newTextSize = currentTextSize * 1.1f; // 增加10%

                Method setTextSizeMethod = findMethod(cl, "setTextSize", int.class, float.class);
                setTextSizeMethod.invoke(
                        networkSpeedView, android.util.TypedValue.COMPLEX_UNIT_PX, newTextSize);

                if (DEBUG) {
                    log("Adjusted text size from " + currentTextSize + " to " + newTextSize);
                }
            } catch (Throwable sizeError) {
                logError("Error adjusting text size", sizeError);
            }

            log("Initialized NetworkSpeedView instance");
        } catch (Throwable t) {
            logError("Error initializing NetworkSpeedView", t);
        }
    }

    private void hookNetworkSpeedHandler(ClassLoader classLoader) {
        try {
            // 通过 DEXKit 查找 NetworkSpeedView 的内部 Handler 类（替代硬编码 $3）
            Class<?> handlerClass = findHandlerInnerClass(classLoader);

            Method handleMessageMethod = handlerClass.getDeclaredMethod("handleMessage", android.os.Message.class);
            this.xposed.hook(handleMessageMethod).intercept(chain -> {
                Object handler = chain.getThisObject();
                Class<?> handlerCls = handler.getClass();
                java.lang.reflect.Field this0Field = handlerCls.getDeclaredField("this$0");
                this0Field.setAccessible(true);
                Object networkSpeedView = this0Field.get(handler);

                // 获取消息对象
                Object message = chain.getArg(0);
                int what = message.getClass().getDeclaredField("what").getInt(message);

                if (what == 10) { // 更新速度的消息
                    handleSpeedUpdate(networkSpeedView, handler);
                    return null; // 阻止原始处理
                } else if (what == 1) { // 格式化显示的消息
                    handleSpeedDisplay(networkSpeedView, message);
                    return null; // 阻止原始处理
                }
                return chain.proceed();
            });

        } catch (Throwable t) {
            logError("Error hooking NetworkSpeed handler", t);
        }
    }

    private void handleSpeedUpdate(Object networkSpeedView, Object handler) {
        try {
            Class<?> handlerCls = handler.getClass();
            // 移除之前的消息
            findMethod(handlerCls, "removeMessages", int.class).invoke(handler, 10);

            // 检查是否应该显示网速
            Object isIconVisibleResult = networkSpeedView.getClass()
                    .getDeclaredMethod("isIconVisible").invoke(networkSpeedView);
            boolean shouldShow = Boolean.TRUE.equals(isIconVisibleResult);

            if (!shouldShow) {
                return;
            }

            // 获取当前流量统计
            long currentRxBytes = getTotalRxBytes();
            long currentTxBytes = getTotalTxBytes();
            long currentTime = System.currentTimeMillis();

            // 获取上次的数据
            Long lastRxBytes = lastRxBytesMap.get(networkSpeedView);
            Long lastTxBytes = lastTxBytesMap.get(networkSpeedView);
            Long lastUpdateTime = lastUpdateTimeMap.get(networkSpeedView);

            if (lastRxBytes == null || lastTxBytes == null || lastUpdateTime == null) {
                // 第一次更新，只记录数据不计算速度
                lastRxBytesMap.put(networkSpeedView, currentRxBytes);
                lastTxBytesMap.put(networkSpeedView, currentTxBytes);
                lastUpdateTimeMap.put(networkSpeedView, currentTime);
            } else {
                // 计算时间差（秒）
                long timeDiff = (currentTime - lastUpdateTime) / 1000;
                if (timeDiff > 0) {
                    // 计算上下行速度（字节/秒）
                    long downSpeed = (currentRxBytes - lastRxBytes) / timeDiff;
                    long upSpeed = (currentTxBytes - lastTxBytes) / timeDiff;

                    // 记录调试信息
                    if (DEBUG) {
                        log(String.format(Locale.US, "Successfully updated speed - downSpeed=%d, upSpeed=%d, timeDiff=%d",
                                downSpeed, upSpeed, timeDiff));
                    }

                    // 发送显示消息
                    Object message = findMethod(handlerCls, "obtainMessage").invoke(handler);
                    if (message != null) {
                        Class<?> msgCls = message.getClass();
                        msgCls.getDeclaredField("what").setInt(message, 1);
                        msgCls.getDeclaredField("obj").set(message, new long[]{downSpeed, upSpeed});
                        findMethod(handlerCls, "sendMessage", android.os.Message.class)
                                .invoke(handler, message);
                    }

                    // 更新数据
                    lastRxBytesMap.put(networkSpeedView, currentRxBytes);
                    lastTxBytesMap.put(networkSpeedView, currentTxBytes);
                    lastUpdateTimeMap.put(networkSpeedView, currentTime);
                }
            }

            // 安排下一次更新（3秒后）
            findMethod(handlerCls, "sendEmptyMessageDelayed", int.class, long.class)
                    .invoke(handler, 10, 3000L);

        } catch (Throwable t) {
            logError("Error in speed update", t);
        }
    }

    private void handleSpeedDisplay(Object networkSpeedView, Object message) {
        try {
            long[] speeds = (long[]) message.getClass().getDeclaredField("obj").get(message);
            if (speeds == null || speeds.length != 2) return;

            long downSpeed = speeds[0];
            long upSpeed = speeds[1];

            // 格式化上下行速度
            String downText = formatSpeed(downSpeed);
            String upText = formatSpeed(upSpeed);

            // 创建带有HTML格式的双层显示文本，调整字体大小
            String displayText = "<font size='5'><b>▴ " + upText + "</b></font><br/>" +
                    "<font size='5'><b>▾ " + downText + "</b></font>";

            // 使用HTML格式设置文本
            findMethod(networkSpeedView.getClass(), "setText", CharSequence.class)
                    .invoke(networkSpeedView,
                            android.text.Html.fromHtml(displayText, Html.FROM_HTML_MODE_LEGACY));

        } catch (Throwable t) {
            logError("Error in speed display", t);
        }
    }

    private String formatSpeed(long speedBytes) {
        if (speedBytes <= 0) {
            return "0.00 B/s";
        }

        double speed;
        String unit;

        if (speedBytes >= 1073741824) { // 1 GB
            speed = speedBytes / 1073741824.0;
            unit = "G/s";
        } else if (speedBytes >= 1048576) { // 1 MB
            speed = speedBytes / 1048576.0;
            unit = "M/s";
        } else if (speedBytes >= 1024) { // 1 KB
            speed = speedBytes / 1024.0;
            unit = "K/s";
        } else {
            speed = speedBytes;
            unit = "B/s";
        }

        // 根据速度值选择合适的精度
        String formatPattern;
        if (speed >= 100) {
            formatPattern = "0";
        } else if (speed >= 10) {
            formatPattern = "0.0";
        } else {
            formatPattern = "0.00";
        }

        java.text.DecimalFormat df = new java.text.DecimalFormat(formatPattern);
        return df.format(speed) + " " + unit;
    }

    private long getTotalRxBytes() {
        try {
            Object result = android.net.TrafficStats.class
                    .getDeclaredMethod("getTotalRxBytes").invoke(null);
            return result != null ? (Long) result : 0L;
        } catch (Throwable t) {
            logError("Error getting Rx bytes", t);
            return 0;
        }
    }

    private long getTotalTxBytes() {
        try {
            Object result = android.net.TrafficStats.class
                    .getDeclaredMethod("getTotalTxBytes").invoke(null);
            return result != null ? (Long) result : 0L;
        } catch (Throwable t) {
            logError("Error getting Tx bytes", t);
            return 0;
        }
    }

    /**
     * 通过反射查找 NetworkSpeedView 的内部 Handler 子类。
     * 遍历可能的内部类索引，替代硬编码的 $3。
     */
    private Class<?> findHandlerInnerClass(ClassLoader classLoader) {
        // 先尝试 DEXKit 查找 Handler 子类
        DexKitBridge bridge = DexKitHelper.INSTANCE.getBridgeForClass(
                classLoader, NETWORK_SPEED_VIEW_CLASS);
        if (bridge != null) {
            try {
                java.util.List<ClassData> matches = bridge.findClass(FindClass.create()
                        .searchPackages(SYSTEMUI_PACKAGE)
                        .matcher(ClassMatcher.create()
                                .superClass("android.os.Handler")
                        )
                );
                for (ClassData cd : matches) {
                    String name = cd.getName();
                    if (name.startsWith(NETWORK_SPEED_VIEW_CLASS + "$")) {
                        if (DEBUG) log("DEXKit found Handler inner class: " + name);
                        return classLoader.loadClass(name);
                    }
                }
            } catch (Throwable ignored) {}
        }
        // 回退：遍历常见内部类索引
        for (int i = 1; i <= 10; i++) {
            try {
                Class<?> cls = classLoader.loadClass(NETWORK_SPEED_VIEW_CLASS + "$" + i);
                // 验证是 Handler 子类（有 handleMessage 方法）
                try {
                    cls.getDeclaredMethod("handleMessage", android.os.Message.class);
                    if (DEBUG) log("Found Handler inner class at index " + i);
                    return cls;
                } catch (NoSuchMethodException ignored) {}
            } catch (ClassNotFoundException ignored) {}
        }
        throw new RuntimeException("Cannot find NetworkSpeedView Handler inner class");
    }
}
