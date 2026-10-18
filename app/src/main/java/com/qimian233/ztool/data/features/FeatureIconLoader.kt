package com.qimian233.ztool.data.features

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** Loads a package's launcher icon for a feature card. */
object FeatureIconLoader {

    fun loadPackageIcon(context: Context, packageName: String): Drawable? {
        if (packageName.isBlank()) return null
        return try {
            val packageManager = context.packageManager
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            info.applicationInfo?.loadIcon(packageManager)
        } catch (_: Throwable) {
            null
        }
    }
}
