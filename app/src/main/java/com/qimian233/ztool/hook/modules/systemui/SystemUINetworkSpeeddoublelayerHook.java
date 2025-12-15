package com.qimian233.ztool.hook.modules.systemui;

import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * SystemUI网络速度显示Hook模块
 * 功能：在状态栏显示实时上下行网络速度，支持自定义文本大小和显示格式
 */
public class SystemUINetworkSpeeddoublelayerHook extends BaseHookModule {

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String NETWORK_SPEED_VIEW_CLASS = "com.android.systemui.zui.NetworkSpeedView";

    // 存储每个实例的上次流量数据
    private static final String FIELD_LAST_RX_BYTES = "mLastRxBytes";
    private static final String FIELD_LAST_TX_BYTES = "mLastTxBytes";
    private static final String FIELD_LAST_UPDATE_TIME = "mLastUpdateTime";

    @Override
    public String getModuleName() {
        return "systemui_network_speed_doublelayer";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{SYSTEMUI_PACKAGE};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if (SYSTEMUI_PACKAGE.equals(packageName)) {
            hookSystemUINetworkSpeed(lpparam);
        }
    }

    private void hookSystemUINetworkSpeed(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("Starting to hook SystemUI NetworkSpeedView");

            // Hook NetworkSpeedView 构造方法
            XposedHelpers.findAndHookConstructor(NETWORK_SPEED_VIEW_CLASS,
                    lpparam.classLoader,
                    "android.content.Context",
                    "android.util.AttributeSet",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            initNetworkSpeedView(param.thisObject);
                        }
                    });

            // Hook Handler 的 handleMessage 方法
            hookNetworkSpeedHandler(lpparam.classLoader);

            log("Successfully hooked NetworkSpeedView");

        } catch (Throwable t) {
            logError("Error hooking NetworkSpeedView", t);
        }
    }

    private void initNetworkSpeedView(Object networkSpeedView) {
        try {
            // 获取初始流量数据
            long initialRxBytes = getTotalRxBytes();
            long initialTxBytes = getTotalTxBytes();

            // 添加上次接收和发送字节数的字段
            XposedHelpers.setAdditionalInstanceField(networkSpeedView,
                    FIELD_LAST_RX_BYTES, initialRxBytes);
            XposedHelpers.setAdditionalInstanceField(networkSpeedView,
                    FIELD_LAST_TX_BYTES, initialTxBytes);
            XposedHelpers.setAdditionalInstanceField(networkSpeedView,
                    FIELD_LAST_UPDATE_TIME, System.currentTimeMillis());

            // 调整文本大小
            try {
                // 获取当前文本大小并增加
                float currentTextSize = (Float) XposedHelpers.callMethod(
                        networkSpeedView, "getTextSize");
                float newTextSize = currentTextSize * 1.1f; // 增加10%

                XposedHelpers.callMethod(networkSpeedView, "setTextSize",
                        android.util.TypedValue.COMPLEX_UNIT_PX, newTextSize);

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
            // 找到 Handler 类
            Class<?> handlerClass = XposedHelpers.findClass(
                    NETWORK_SPEED_VIEW_CLASS + "$3", classLoader);

            XposedHelpers.findAndHookMethod(handlerClass, "handleMessage",
                    android.os.Message.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object handler = param.thisObject;
                            Object networkSpeedView = XposedHelpers.getObjectField(handler, "this$0");

                            // 获取消息对象
                            Object message = param.args[0];
                            int what = XposedHelpers.getIntField(message, "what");

                            if (what == 10) { // 更新速度的消息
                                handleSpeedUpdate(networkSpeedView, handler);
                                param.setResult(null); // 阻止原始处理
                            } else if (what == 1) { // 格式化显示的消息
                                handleSpeedDisplay(networkSpeedView, message);
                                param.setResult(null); // 阻止原始处理
                            }
                        }
                    });

        } catch (Throwable t) {
            logError("Error hooking NetworkSpeed handler", t);
        }
    }

    private void handleSpeedUpdate(Object networkSpeedView, Object handler) {
        try {
            // 移除之前的消息
            XposedHelpers.callMethod(handler, "removeMessages", 10);

            // 检查是否应该显示网速
            boolean shouldShow = (boolean) XposedHelpers.callMethod(
                    networkSpeedView, "isIconVisible");

            if (!shouldShow) {
                return;
            }

            // 获取当前流量统计
            long currentRxBytes = getTotalRxBytes();
            long currentTxBytes = getTotalTxBytes();
            long currentTime = System.currentTimeMillis();

            // 获取上次的数据
            Long lastRxBytes = (Long) XposedHelpers.getAdditionalInstanceField(
                    networkSpeedView, FIELD_LAST_RX_BYTES);
            Long lastTxBytes = (Long) XposedHelpers.getAdditionalInstanceField(
                    networkSpeedView, FIELD_LAST_TX_BYTES);
            Long lastUpdateTime = (Long) XposedHelpers.getAdditionalInstanceField(
                    networkSpeedView, FIELD_LAST_UPDATE_TIME);

            if (lastRxBytes == null || lastTxBytes == null || lastUpdateTime == null) {
                // 第一次更新，只记录数据不计算速度
                XposedHelpers.setAdditionalInstanceField(networkSpeedView,
                        FIELD_LAST_RX_BYTES, currentRxBytes);
                XposedHelpers.setAdditionalInstanceField(networkSpeedView,
                        FIELD_LAST_TX_BYTES, currentTxBytes);
                XposedHelpers.setAdditionalInstanceField(networkSpeedView,
                        FIELD_LAST_UPDATE_TIME, currentTime);
            } else {
                // 计算时间差（秒）
                long timeDiff = (currentTime - lastUpdateTime) / 1000;
                if (timeDiff > 0) {
                    // 计算上下行速度（字节/秒）
                    long downSpeed = (currentRxBytes - lastRxBytes) / timeDiff;
                    long upSpeed = (currentTxBytes - lastTxBytes) / timeDiff;

                    // 记录调试信息
                    if (DEBUG) {
                        log(String.format("Speed update - downSpeed=%d, upSpeed=%d, timeDiff=%d",
                                downSpeed, upSpeed, timeDiff));
                    }

                    // 发送显示消息
                    Object message = XposedHelpers.callMethod(handler, "obtainMessage");
                    XposedHelpers.setIntField(message, "what", 1);
                    XposedHelpers.setObjectField(message, "obj",
                            new long[]{downSpeed, upSpeed});
                    XposedHelpers.callMethod(handler, "sendMessage", message);

                    // 更新数据
                    XposedHelpers.setAdditionalInstanceField(networkSpeedView,
                            FIELD_LAST_RX_BYTES, currentRxBytes);
                    XposedHelpers.setAdditionalInstanceField(networkSpeedView,
                            FIELD_LAST_TX_BYTES, currentTxBytes);
                    XposedHelpers.setAdditionalInstanceField(networkSpeedView,
                            FIELD_LAST_UPDATE_TIME, currentTime);
                }
            }

            // 安排下一次更新（3秒后）
            XposedHelpers.callMethod(handler, "sendEmptyMessageDelayed", 10, 3000L);

        } catch (Throwable t) {
            logError("Error in speed update", t);
        }
    }

    private void handleSpeedDisplay(Object networkSpeedView, Object message) {
        try {
            long[] speeds = (long[]) XposedHelpers.getObjectField(message, "obj");
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
            XposedHelpers.callMethod(networkSpeedView, "setText",
                    android.text.Html.fromHtml(displayText));

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
            return (Long) XposedHelpers.callStaticMethod(
                    android.net.TrafficStats.class, "getTotalRxBytes");
        } catch (Throwable t) {
            logError("Error getting Rx bytes", t);
            return 0;
        }
    }

    private long getTotalTxBytes() {
        try {
            return (Long) XposedHelpers.callStaticMethod(
                    android.net.TrafficStats.class, "getTotalTxBytes");
        } catch (Throwable t) {
            logError("Error getting Tx bytes", t);
            return 0;
        }
    }
}
