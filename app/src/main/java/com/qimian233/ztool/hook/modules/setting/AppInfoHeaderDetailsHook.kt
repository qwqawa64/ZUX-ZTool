package com.qimian233.ztool.hook.modules.setting

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.data.keys.ScopeKeys
import com.qimian233.ztool.hook.base.AppHookModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects

@SuppressLint("PrivateApi", "DiscouragedApi")
class AppInfoHeaderDetailsHook : AppHookModule() {

    private var systemLanguage = Locale.getDefault().language
    private val displayStringsCn = arrayOf("包名", "首次安装", "最后更新", "安装自", "已复制到剪贴板", "未知")
    private val displayStringsAlternative = arrayOf(
        "Package Name", "First Installed", "Last Updated", "Source", "Copied to clipboard", "Unknown"
    )

    private fun getDisplayString(stringIndex: Int): String {
        return if (stringIndex <= 3) {
            if (systemLanguage == "zh") displayStringsCn[stringIndex] + ": "
            else displayStringsAlternative[stringIndex] + ": "
        } else {
            if (systemLanguage == "zh") displayStringsCn[stringIndex]
            else displayStringsAlternative[stringIndex]
        }
    }

    override fun getModuleName(): String = PreferenceKeys.APP_DETAILS.name

    override fun getTargetPackages(): Array<String> = arrayOf(TARGET_PACKAGE)

    override fun handleLoadPackage(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        val packageName = param.packageName
        if (TARGET_PACKAGE != packageName) {
            return
        }

        val appEntryClass = try {
            classLoader.loadClass(APP_ENTRY_CLASS)
        } catch (_: ClassNotFoundException) {
            null
        }
        if (appEntryClass == null) {
            logger.warn("AppEntry class not found, skip app info header hook.")
            return
        }

        val m: Method = classLoader
            .loadClass(CONTROLLER_CLASS)
            .getDeclaredMethod("setAppLabelAndIcon", PackageInfo::class.java, appEntryClass)
        hookWithId(m, "hook_76") { chain ->
            val result = chain.proceed()
            try {
                val pkgInfo = chain.args[0] as? PackageInfo
                if (pkgInfo == null || pkgInfo.applicationInfo == null) {
                    return@hookWithId result
                }

                val mContextField: Field = findField(chain.thisObject.javaClass, "mContext")
                mContextField.isAccessible = true
                val context = mContextField.get(chain.thisObject) as Context

                val mHeaderField: Field = findField(chain.thisObject.javaClass, "mHeader")
                mHeaderField.isAccessible = true
                val headerPreference = mHeaderField.get(chain.thisObject)

                val summaryView = findSummaryView(context, headerPreference)
                if (summaryView == null) {
                    logger.warn("entity_header_summary not found.")
                    return@hookWithId result
                }

                val appInfo = buildAppInfo(summaryView.context, pkgInfo)
                if (TextUtils.isEmpty(appInfo)) {
                    return@hookWithId result
                }

                val originalSummary = summaryView.text
                val displayText = mergeSummary(originalSummary, appInfo)
                summaryView.setSingleLine(false)
                summaryView.maxLines = Integer.MAX_VALUE
                summaryView.text = displayText
                summaryView.setOnLongClickListener { v ->
                    copyToClipboard(v.context, displayText)
                    true
                }
            } catch (t: Throwable) {
                logger.error("Failed to update app info header summary", t)
            }
            result
        }
        logger.info("Hooked AppHeaderViewPreferenceController#setAppLabelAndIcon.")
    }

    private fun findSummaryView(context: Context?, headerPreference: Any?): TextView? {
        if (context == null || headerPreference == null) {
            return null
        }

        val summaryId = context.resources.getIdentifier(
            "entity_header_summary", "id", TARGET_PACKAGE
        )
        if (summaryId == 0) {
            return null
        }

        try {
            val findViewById: Method =
                headerPreference.javaClass.getDeclaredMethod("findViewById", Int::class.javaPrimitiveType)
            val headerView = findViewById.invoke(headerPreference, summaryId) as View
            if (headerView is TextView) {
                return headerView
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun buildAppInfo(context: Context, pkgInfo: PackageInfo): String {
        systemLanguage = Locale.getDefault().language
        val appInfo: ApplicationInfo = pkgInfo.applicationInfo!!
        return getDisplayString(0) + pkgInfo.packageName +
            '\n' + "minSDK " +
            appInfo.minSdkVersion +
            " / target " +
            appInfo.targetSdkVersion +
            '\n' + getDisplayString(1) + formatTime(pkgInfo.firstInstallTime) +
            '\n' + getDisplayString(2) + formatTime(pkgInfo.lastUpdateTime) +
            '\n' + getDisplayString(3) + getInstallSource(context, pkgInfo.packageName)
    }

    private fun mergeSummary(originalSummary: CharSequence?, appInfo: String): String {
        systemLanguage = Locale.getDefault().language
        if (TextUtils.isEmpty(originalSummary)) {
            return appInfo
        }
        val original = originalSummary.toString()
        if (original.contains(getDisplayString(0)) && original.contains(getDisplayString(3))) {
            return appInfo
        }
        return original + "\n" + appInfo
    }

    private fun formatTime(timeMillis: Long): String {
        if (timeMillis <= 0L) {
            systemLanguage = Locale.getDefault().language
            return getDisplayString(5)
        }
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(timeMillis))
    }

    private fun getInstallSource(context: Context, packageName: String): String {
        try {
            val pm = context.packageManager
            val source: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sourceInfo: InstallSourceInfo = pm.getInstallSourceInfo(packageName)
                firstNonEmpty(
                    sourceInfo.installingPackageName,
                    sourceInfo.initiatingPackageName,
                    sourceInfo.originatingPackageName
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
            if (TextUtils.isEmpty(source)) {
                systemLanguage = Locale.getDefault().language
                return getDisplayString(5)
            }

            val label = getApplicationLabel(pm, source!!)
            if (!TextUtils.isEmpty(label)) {
                return label.toString() + " (" + source + ")"
            }
            return source
        } catch (t: Throwable) {
            systemLanguage = Locale.getDefault().language
            return getDisplayString(5)
        }
    }

    private fun getApplicationLabel(pm: PackageManager, packageName: String): CharSequence? {
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0))
        } catch (_: Throwable) {
            null
        }
    }

    private fun firstNonEmpty(first: String?, second: String?, third: String?): String? {
        if (!TextUtils.isEmpty(first)) {
            return first
        }
        if (!TextUtils.isEmpty(second)) {
            return second
        }
        return if (TextUtils.isEmpty(third)) null else third
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("app_info", text))
            systemLanguage = Locale.getDefault().language
            Toast.makeText(context, getDisplayString(4), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val TARGET_PACKAGE = ScopeKeys.SETTINGS.packageName
        private const val CONTROLLER_CLASS =
            "com.android.settings.applications.appinfo.AppHeaderViewPreferenceController"
        private const val APP_ENTRY_CLASS =
            "com.android.settingslib.applications.ApplicationsState\$AppEntry"
    }
}
