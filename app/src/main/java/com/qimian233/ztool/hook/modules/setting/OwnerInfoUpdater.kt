package com.qimian233.ztool.hook.modules.setting

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.qimian233.ztool.data.keys.PreferenceKeys
import com.qimian233.ztool.hook.base.ModuleLog
import io.github.libxposed.api.XposedInterface
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Lock screen OwnerInfo update core logic (shared class split from OwnerInfoHook).
 * <p>
 * Used by both [OwnerInfoSettingsHook] and OwnerInfoSystemHook: fetches the daily
 * quote from the API and writes it to the lock screen OwnerInfo. [xposed] and
 * [logger] are constructor-injected; each side builds its own instance in its
 * own callback phase.
 * </p>
 */
@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class OwnerInfoUpdater(
    private val xposed: XposedInterface,
    private val logger: ModuleLog
) {

    private var apiUrl: String? = null
    private var cachedContent = ""

    /**
     * Updates OwnerInfo (starts a new thread to fetch API data, avoiding blocking the calling thread).
     */
    fun updateOwnerInfo(context: Any?, classLoader: ClassLoader) {
        Thread {
            try {
                apiUrl = getString(PreferenceKeys.API_URL.name)
                // Handle possible URL protocol saving issues by completing the protocol here
                if (apiUrl != null && apiUrl!!.isNotEmpty()) {
                    if (!apiUrl!!.startsWith("http://") && !apiUrl!!.startsWith("https://") &&
                        !apiUrl!!.startsWith("Https://") && !apiUrl!!.startsWith("Http://")
                    ) {
                        apiUrl = "https://$apiUrl"
                    }
                } else {
                    // Fall back to the default quote API when not configured
                    apiUrl = PreferenceKeys.API_URL.default
                }
                val content = fetchContentFromAPI()
                if (content != cachedContent) {
                    cachedContent = content
                    logger.debug("New content fetched from API: $content")
                    setOwnerInfoContent(content, context, classLoader)
                } else {
                    logger.debug("Content unchanged, skipping update")
                }
            } catch (e: Exception) {
                logger.error("Error in updateOwnerInfo thread", e)
            }
        }.start()
    }

    private fun fetchContentFromAPI(): String {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(apiUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "OwnerInfoHook/1.0")

            val responseCode = connection.responseCode
            logger.debug("API response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream: InputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }

                val rawResponse = response.toString()
                logger.debug("Raw API response: $rawResponse") // Log raw response for debugging

                return parseContentFromJson(rawResponse)
            } else {
                // Read the error stream for more details
                val errorStream = connection.errorStream
                if (errorStream != null) {
                    val reader = BufferedReader(InputStreamReader(errorStream))
                    val errorResponse = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        errorResponse.append(line)
                    }
                    logger.debug("API error response: $errorResponse")
                }
                logger.debug("HTTP error response: $responseCode")
            }
        } catch (e: Exception) {
            logger.error("Error fetching API data", e)
        } finally {
            connection?.disconnect()
        }
        return "If you see this message, your API is broken, check your settings and Internet connection, then restart com.android.settings"
    }

    private fun parseContentFromJson(jsonString: String): String {
        return try {
            // Regex to match the content field, handling escape characters; fall back to the default pattern if not configured
            val regular = getString(PreferenceKeys.REGULAR.name)
                .ifEmpty { PreferenceKeys.REGULAR.default }
            // Guard against an empty expression: skip matching if the regex is null or empty
            val pattern = Pattern.compile(regular)
            val matcher = pattern.matcher(jsonString)

            if (matcher.find()) {
                var content = matcher.group(1) ?: return jsonString
                // Handle escape characters (e.g. \" converted to ")
                content = content
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\/", "/")
                    .replace("\\b", "\b")
                    .replace("\\f", "\u000C")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                content
            } else {
                logger.warn("content field not found in JSON")
                jsonString
            }
        } catch (e: Exception) {
            logger.error("Error parsing JSON", e)
            jsonString
        }
    }

    /**
     * Sets the OwnerInfo content (ensures the write runs on the main thread).
     */
    private fun setOwnerInfoContent(content: String, context: Any?, classLoader: ClassLoader) {
        try {
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                try {
                    logger.debug("Setting OwnerInfo content: $content")

                    // Method 1: via LockPatternUtils
                    try {
                        val lockPatternUtils = getObject(context, classLoader)

                        // Enable OwnerInfo first
                        val setEnabled: Method = lockPatternUtils.javaClass
                            .getDeclaredMethod("setOwnerInfoEnabled", Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        setEnabled.invoke(lockPatternUtils, true, 0)
                        // Set OwnerInfo content
                        val setOwnerInfo: Method = lockPatternUtils.javaClass
                            .getDeclaredMethod("setOwnerInfo", String::class.java, Int::class.javaPrimitiveType)
                        setOwnerInfo.invoke(lockPatternUtils, content, 0)

                        logger.debug("OwnerInfo updated successfully via LockPatternUtils")
                        return@post
                    } catch (t: Throwable) {
                        logger.error("Failed to update via LockPatternUtils", t)
                    }

                    // Method 2: via the ILockSettings service
                    try {
                        val serviceManagerClass = classLoader.loadClass("android.os.ServiceManager")
                        val getServiceMethod: Method =
                            serviceManagerClass.getDeclaredMethod("getService", String::class.java)
                        val lockSettingsService = getServiceMethod.invoke(null, "lock_settings")

                        if (lockSettingsService != null) {
                            // Enable OwnerInfo
                            val setBooleanMethod: Method = lockSettingsService.javaClass
                                .getDeclaredMethod("setBoolean", String::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                            setBooleanMethod.invoke(
                                lockSettingsService,
                                "lock_screen_owner_info_enabled", true, 0
                            )
                            // Set content
                            val setStringMethod: Method = lockSettingsService.javaClass
                                .getDeclaredMethod("setString", String::class.java, String::class.java, Int::class.javaPrimitiveType)
                            setStringMethod.invoke(
                                lockSettingsService,
                                "lock_screen_owner_info", content, 0
                            )

                            logger.debug("OwnerInfo updated successfully via ILockSettings")
                            return@post
                        }
                    } catch (t: Throwable) {
                        logger.error("Failed to update via ILockSettings", t)
                    }

                    // Method 3: call SettingsProvider directly (fallback)
                    try {
                        if (context is Context) {
                            Settings.Secure.putString(
                                context.contentResolver,
                                "lock_screen_owner_info_enabled", "1"
                            )
                            Settings.Secure.putString(
                                context.contentResolver,
                                "lock_screen_owner_info", content
                            )
                            logger.debug("OwnerInfo updated successfully via SettingsProvider")
                        }
                    } catch (t: Throwable) {
                        logger.error("Failed to update via SettingsProvider", t)
                    }
                } catch (t: Throwable) {
                    logger.error("Failed to set OwnerInfo content", t)
                }
            }
        } catch (t: Throwable) {
            logger.error("Failed to post to main Handler", t)
        }
    }

    private fun getObject(context: Any?, classLoader: ClassLoader): Any {
        return try {
            val lockPatternUtilsClass = classLoader.loadClass(
                "com.android.internal.widget.LockPatternUtils"
            )

            val lockPatternUtils: Any
            if (context is Context) {
                // Create a LockPatternUtils instance from the Context
                val ctor: Constructor<*> = lockPatternUtilsClass.getDeclaredConstructor(Context::class.java)
                lockPatternUtils = ctor.newInstance(context)
            } else {
                // Use the default constructor
                val ctor: Constructor<*> = lockPatternUtilsClass.getDeclaredConstructor()
                lockPatternUtils = ctor.newInstance()
            }
            lockPatternUtils
        } catch (t: Throwable) {
            throw RuntimeException("Failed to create LockPatternUtils", t)
        }
    }

    private fun getString(key: String): String {
        return xposed.getRemotePreferences(PREFS_NAME).getString(key, "")!!
    }

    private companion object {
        const val PREFS_NAME = "xposed_module_config"
    }
}
