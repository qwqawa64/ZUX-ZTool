package com.qimian233.ztool.hook.modules.systemui;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.widget.TextView;
import com.qimian233.ztool.hook.base.BaseHookModule;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 系统UI网速显示样式Hook模块
 * 修改系统状态栏中的网速显示，使数字部分更大、单位部分更小
 */
public class SystemUINetworkSpeedSIzeHook extends BaseHookModule {

    @Override
    public String getModuleName() {
        return "systemui_network_speed_size";
    }

    @Override
    public String[] getTargetPackages() {
        return new String[]{"com.android.systemui"};
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;

        if ("com.android.systemui".equals(packageName)) {
            hookSystemUI(lpparam);
        }
    }

    private void hookSystemUI(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            log("开始Hook系统UI网速显示");

            // 使用beforeHookedMethod避免递归调用
            XposedHelpers.findAndHookMethod(TextView.class, "setText",
                    CharSequence.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                CharSequence text = (CharSequence) param.args[0];

                                // 检查是否是网络速度显示文本
                                if (isNetworkSpeedText(text)) {
                                    CharSequence styledText = createStyledSpeedText(text.toString());
                                    param.args[0] = styledText; // 直接修改参数
                                    log("成功修改网速显示样式");
                                }
                            } catch (Throwable t) {
                                // 忽略处理过程中的异常
                            }
                        }
                    });

            log("系统UI网速显示Hook成功");
        } catch (Throwable e) {
            logError("系统UI网速显示Hook失败", e);
        }
    }

    /**
     * 检查是否是网速文本
     * 网速文本特征：包含换行符，以下载/上传速度单位结尾
     */
    private boolean isNetworkSpeedText(CharSequence text) {
        if (text == null) return false;
        String str = text.toString();
        return str.contains("\n") &&
                (str.endsWith("K/s") || str.endsWith("M/s") || str.endsWith("G/s") ||
                        str.endsWith("KB/s") || str.endsWith("MB/s") || str.endsWith("GB/s") ||
                        str.endsWith("Kbps") || str.endsWith("Mbps") || str.endsWith("Gbps"));
    }

    /**
     * 创建带样式的网速文本
     * 数字部分1.3倍大小，单位部分0.9倍大小
     */
    private CharSequence createStyledSpeedText(String originalText) {
        if (!originalText.contains("\n")) {
            return originalText;
        }

        String[] parts = originalText.split("\n");
        if (parts.length != 2) {
            return originalText;
        }

        String numberPart = parts[0];
        String unitPart = parts[1];

        SpannableString spannableString = new SpannableString(numberPart + "\n" + unitPart);

        // 设置数字部分相对大小为1.3倍（更大）
        spannableString.setSpan(new RelativeSizeSpan(1.3f),
                0, numberPart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // 设置单位部分相对大小为0.9倍（更小）
        spannableString.setSpan(new RelativeSizeSpan(0.9f),
                numberPart.length() + 1, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return spannableString;
    }
}
