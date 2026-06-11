package com.qimian233.ztool.ui.components

import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.qimian233.ztool.ui.theme.ZToolTheme

fun createPlatformComposeDialog(
    context: Context,
    cancelable: Boolean = true,
    content: @Composable (Dialog) -> Unit
): Dialog {
    val dialog = Dialog(context)
    val composeView = ComposeView(context).apply {
        bindOwners(context)
        setContent {
            ZToolTheme {
                content(dialog)
            }
        }
    }

    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setContentView(composeView)
    dialog.setCancelable(cancelable)
    dialog.setCanceledOnTouchOutside(cancelable)
    return dialog
}

fun showPlatformComposeDialog(
    context: Context,
    cancelable: Boolean = true,
    width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
    height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    content: @Composable (Dialog) -> Unit
): Dialog {
    return createPlatformComposeDialog(
        context = context,
        cancelable = cancelable,
        content = content
    ).also { dialog ->
        dialog.show()
        dialog.window?.setLayout(width, height)
    }
}

private fun ComposeView.bindOwners(context: Context) {
    (context as? LifecycleOwner)?.let(::setViewTreeLifecycleOwner)
    (context as? ViewModelStoreOwner)?.let(::setViewTreeViewModelStoreOwner)
    (context as? SavedStateRegistryOwner)?.let(::setViewTreeSavedStateRegistryOwner)
}
