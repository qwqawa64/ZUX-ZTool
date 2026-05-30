package com.qimian233.ztool.utils

import android.content.Context
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.app.Dialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.R
import com.qimian233.ztool.ui.components.createPlatformComposeDialog

class CountdownDialog private constructor(
    private val context: Context,
    private val title: String,
    private val message: String,
    private val countdownSeconds: Int,
    private val positiveText: String,
    private val negativeText: String,
    private val cancelable: Boolean,
    private val listener: OnCountdownFinishListener?
) {
    private val handler = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null
    private var countDownTimer: CountDownTimer? = null
    private var remainingSeconds by mutableIntStateOf(countdownSeconds.coerceAtLeast(0))
    private var positiveEnabled by mutableStateOf(countdownSeconds <= 0)
    private var finishedCallbackSent = false

    interface OnCountdownFinishListener {
        fun onCountdownFinished()
        fun onPositiveButtonClick()
        fun onNegativeButtonClick()
    }

    class Builder(
        private val context: Context,
        private var listener: OnCountdownFinishListener?
    ) {
        private var title = context.getString(R.string.confirm)
        private var message = context.getString(R.string.customizedConfirmWithCountdown, context.getString(R.string.confirm), 10)
        private var countdownSeconds = 10
        private var positiveText = context.getString(R.string.confirm)
        private var negativeText = context.getString(R.string.cancel)
        private var cancelable = true

        fun setTitle(title: String): Builder {
            this.title = title
            return this
        }

        fun setMessage(message: String): Builder {
            this.message = message
            return this
        }

        fun setCountdownSeconds(seconds: Int) {
            countdownSeconds = seconds
        }

        fun setOnCountdownFinishListener(listener: OnCountdownFinishListener?) {
            this.listener = listener
        }

        fun setPositiveText(text: String) {
            positiveText = text
        }

        fun setNegativeText(text: String) {
            negativeText = text
        }

        fun setCancelable(cancelable: Boolean) {
            this.cancelable = cancelable
        }

        fun build(): CountdownDialog {
            return CountdownDialog(
                context = context,
                title = title,
                message = message,
                countdownSeconds = countdownSeconds,
                positiveText = positiveText,
                negativeText = negativeText,
                cancelable = cancelable,
                listener = listener
            )
        }
    }

    fun show() {
        runOnMain {
            val dialog = ensureDialog()
            if (!dialog.isShowing) {
                remainingSeconds = countdownSeconds.coerceAtLeast(0)
                positiveEnabled = countdownSeconds <= 0
                finishedCallbackSent = false
                dialog.show()
                dialog.window?.setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                startCountdown()
            }
        }
    }

    fun dismiss() {
        runOnMain {
            countDownTimer?.cancel()
            countDownTimer = null
            dialog?.takeIf { it.isShowing }?.dismiss()
        }
    }

    private fun ensureDialog(): Dialog {
        dialog?.let { return it }

        return createPlatformComposeDialog(context, cancelable = cancelable) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(weight = 1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                listener?.onNegativeButtonClick()
                                dismiss()
                            }
                        ) {
                            Text(negativeText)
                        }
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Button(
                            enabled = positiveEnabled,
                            onClick = {
                                listener?.onPositiveButtonClick()
                                dismiss()
                            }
                        ) {
                            Text(positiveButtonLabel())
                        }
                    }
                }
            }
        }.also { platformDialog ->
                platformDialog.setOnDismissListener {
                    countDownTimer?.cancel()
                    countDownTimer = null
                }
                dialog = platformDialog
            }
    }

    private fun startCountdown() {
        countDownTimer?.cancel()
        if (countdownSeconds <= 0) {
            notifyCountdownFinished()
            return
        }

        countDownTimer = object : CountDownTimer(countdownSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = (millisUntilFinished / 1000L).toInt() + 1
            }

            override fun onFinish() {
                remainingSeconds = 0
                positiveEnabled = true
                notifyCountdownFinished()
            }
        }.also { it.start() }
    }

    private fun positiveButtonLabel(): String {
        return if (positiveEnabled) {
            positiveText
        } else {
            context.getString(R.string.customizedConfirmWithCountdown, positiveText, remainingSeconds)
        }
    }

    private fun notifyCountdownFinished() {
        if (!finishedCallbackSent) {
            finishedCallbackSent = true
            listener?.onCountdownFinished()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            handler.post(block)
        }
    }
}
