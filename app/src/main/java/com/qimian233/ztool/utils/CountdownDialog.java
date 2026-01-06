package com.qimian233.ztool.utils;

import android.content.Context;
import android.os.CountDownTimer;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.qimian233.ztool.R;

public class CountdownDialog {
    private AlertDialog dialog;
    private CountDownTimer countDownTimer;

    public interface OnCountdownFinishListener {
        void onCountdownFinished();
        void onPositiveButtonClick();
        void onNegativeButtonClick();
    }

    public static class Builder {
        private final Context context;
        private String title = "确认操作";
        private String message = "请在倒计时结束后确认";
        private int countdownSeconds = 10;
        private OnCountdownFinishListener listener;
        private String positiveText = "确认";
        private String negativeText = "取消";
        private boolean cancelable = true;

        public Builder(Context context, OnCountdownFinishListener listener) {
            this.context = context;
            this.listener = listener;
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        public void setCountdownSeconds(int seconds) {
            this.countdownSeconds = seconds;
        }

        public void setOnCountdownFinishListener(OnCountdownFinishListener listener) {
            this.listener = listener;
        }

        public void setPositiveText(String text) {
            this.positiveText = text;
        }

        public void setNegativeText(String text) {
            this.negativeText = text;
        }

        public void setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
        }

        public CountdownDialog build() {
            CountdownDialog countdownDialog = new CountdownDialog();

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(String.format(
                            context.getString(R.string.customizedConfirmWithCountdown),
                            positiveText,
                            countdownSeconds),
                            null)
                    .setNegativeButton(negativeText, null)
                    .setCancelable(cancelable);

            countdownDialog.dialog = builder.create();

            countdownDialog.dialog.setOnShowListener(dialogInterface -> {

                // 让取消按钮也能使用callback来调用其他函数，而非简单的dismiss();
                final Button negativeButton = countdownDialog.dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                negativeButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onNegativeButtonClick();
                    }
                    countdownDialog.dismiss();
                });

                final Button positiveButton = countdownDialog.dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                positiveButton.setEnabled(false);

                countdownDialog.countDownTimer = new CountDownTimer(countdownSeconds * 1000L, 1000L) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        int seconds = (int) (millisUntilFinished / 1000);
                        positiveButton.setText(String.format(context.getString(
                                R.string.customizedConfirmWithCountdown),
                                positiveText,
                                seconds + 1));
                        countdownDialog.dialog.setMessage(message);
                    }

                    @Override
                    public void onFinish() {
                        positiveButton.setEnabled(true);
                        positiveButton.setText(positiveText);

                        // 通知倒计时结束
                        if (listener != null) {
                            listener.onCountdownFinished();
                        }

                        // 设置确认按钮点击事件
                        positiveButton.setOnClickListener(v -> {
                            if (listener != null) {
                                listener.onPositiveButtonClick();
                            }
                            countdownDialog.dialog.dismiss();
                        });
                    }
                };

                countdownDialog.countDownTimer.start();

                // 对话框关闭时取消计时器
                countdownDialog.dialog.setOnDismissListener(dialogInterface1 -> {
                    if (countdownDialog.countDownTimer != null) {
                        countdownDialog.countDownTimer.cancel();
                    }
                });
            });

            return countdownDialog;
        }
    }

    public void show() {
        if (dialog != null && !dialog.isShowing()) {
            dialog.show();
        }
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
