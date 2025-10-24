package com.qimian233.ztool;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class LoadingDialog {
    private AlertDialog dialog;
    private TextView tvDescription;
    private ProgressBar progressBar;
    private final Context context;
    private boolean isShowing = false;

    public LoadingDialog(Context context) {
        this.context = context;
        initDialog();
    }

    private void initDialog() {
        // 使用MaterialAlertDialogBuilder创建对话框
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);

        // 加载自定义布局
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_loading, null);

        // 初始化视图
        progressBar = dialogView.findViewById(R.id.progress_bar);
        tvDescription = dialogView.findViewById(R.id.tv_description);

        builder.setView(dialogView);

        // 设置对话框不可关闭
        dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
    }

    /**
     * 显示加载对话框
     * @param message 初始描述文本
     */
    public void show(String message) {
        if (dialog != null && !isShowing) {
            tvDescription.setText(message);
            dialog.show();
            isShowing = true;
        }
    }

    /**
     * 显示加载对话框（使用默认文本）
     */
    public void show() {
        show("加载中...");
    }

    /**
     * 隐藏对话框
     */
    public void dismiss() {
        if (dialog != null && isShowing) {
            // 确保在主线程中执行
            if (Looper.myLooper() == Looper.getMainLooper()) {
                dialog.dismiss();
            } else {
                new Handler(Looper.getMainLooper()).post(() -> dialog.dismiss());
            }
            isShowing = false;
        }
    }

    /**
     * 动态更新描述文本
     * @param message 新的描述文本
     */
    public void updateMessage(String message) {
        if (tvDescription != null && isShowing) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                tvDescription.setText(message);
            } else {
                new Handler(Looper.getMainLooper()).post(() -> tvDescription.setText(message));
            }
        }
    }

    /**
     * 检查对话框是否正在显示
     */
    public boolean isShowing() {
        return isShowing;
    }

    /**
     * 设置对话框是否可取消
     */
    public void setCancelable(boolean cancelable) {
        if (dialog != null) {
            dialog.setCancelable(cancelable);
        }
    }
}
