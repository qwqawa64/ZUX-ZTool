package com.qimian233.ztool

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.app.Dialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qimian233.ztool.ui.components.ZToolCircularProgressIndicator
import com.qimian233.ztool.ui.components.createPlatformComposeDialog

class LoadingDialog(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null
    private var showing = false
    private var message by mutableStateOf(context.getString(R.string.loading))

    fun show(message: String) {
        runOnMain {
            this.message = message
            if (!showing) {
                ensureDialog().show()
                ensureDialog().window?.setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                showing = true
            }
        }
    }

    fun show() {
        show(context.getString(R.string.loading))
    }

    fun dismiss() {
        runOnMain {
            if (showing) {
                dialog?.dismiss()
                showing = false
            }
            handler.removeCallbacksAndMessages(null)
            dialog = null
        }
    }

    fun updateMessage(message: String) {
        runOnMain {
            if (showing) {
                this.message = message
            }
        }
    }

    fun isShowing(): Boolean = showing

    private fun ensureDialog(): Dialog {
        val existing = dialog
        if (existing != null) return existing

        return createPlatformComposeDialog(context, cancelable = false) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ZToolCircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }.also {
            dialog = it
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
