package com.qimian233.ztool

import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
                ensureDialog().window?.apply {
                    setLayout(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setGravity(Gravity.CENTER)
                }
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

    @Suppress("unused")
    fun updateMessage(message: String) {
        runOnMain {
            if (showing) {
                this.message = message
            }
        }
    }

    @Suppress("unused")
    fun isShowing(): Boolean = showing

    private fun ensureDialog(): Dialog {
        val existing = dialog
        if (existing != null) return existing

        return createPlatformComposeDialog(context, cancelable = false) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZToolCircularProgressIndicator(modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.width(16.dp))
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
