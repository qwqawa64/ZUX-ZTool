package com.qimian233.ztool.hook.modules.setting

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
 * 锁屏 OwnerInfo 更新核心逻辑（OwnerInfoHook 拆分后的共享类）。
 * <p>
 * 由 [OwnerInfoSettingsHook] 与 [OwnerInfoSystemHook] 双侧共用：从 API 拉取
 * 每日一言并写入锁屏 OwnerInfo。构造注入 [xposed] 与 [logger]，两侧 Hook
 * 在各自回调阶段各建实例。
 * </p>
 */
class OwnerInfoUpdater(
    private val xposed: XposedInterface,
    private val logger: ModuleLog
) {

    private var apiUrl: String? = null
    private var cachedContent = ""

    /**
     * 更新 OwnerInfo（启动新线程获取 API 数据，避免阻塞调用线程）。
     */
    fun updateOwnerInfo(context: Any?, classLoader: ClassLoader) {
        Thread {
            try {
                apiUrl = getString(PreferenceKeys.API_URL.name)
                // 处理可能的URL协议保存问题，这里添加补全协议的逻辑
                if (apiUrl != null && apiUrl!!.isNotEmpty()) {
                    if (!apiUrl!!.startsWith("http://") && !apiUrl!!.startsWith("https://") &&
                        !apiUrl!!.startsWith("Https://") && !apiUrl!!.startsWith("Http://")
                    ) {
                        apiUrl = "https://" + apiUrl
                    }
                } else {
                    logger.warn("API_URL配置为空，使用默认值")
                    apiUrl = "https://api.example.com" // 设置一个默认URL
                }
                val content = fetchContentFromAPI()
                if (content != null && content != cachedContent) {
                    cachedContent = content
                    logger.debug("从API获取新内容: $content")
                    setOwnerInfoContent(content, context, classLoader)
                } else if (content == null) {
                    logger.warn("从API获取内容失败$apiUrl")
                } else {
                    logger.debug("内容未变化，跳过更新")
                }
            } catch (e: Exception) {
                logger.error("updateOwnerInfo线程出错", e)
            }
        }.start()
    }

    private fun fetchContentFromAPI(): String? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(apiUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "OwnerInfoHook/1.0")

            val responseCode = connection.responseCode
            logger.debug("API响应码: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream: InputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }

                val rawResponse = response.toString()
                logger.debug("API原始响应: $rawResponse") // 记录原始响应用于调试

                return parseContentFromJson(rawResponse)
            } else {
                // 读取错误流获取更多信息
                val errorStream = connection.errorStream
                if (errorStream != null) {
                    val reader = BufferedReader(InputStreamReader(errorStream))
                    val errorResponse = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        errorResponse.append(line)
                    }
                    logger.debug("API错误响应: $errorResponse")
                }
                logger.debug("HTTP错误响应: $responseCode")
            }
        } catch (e: Exception) {
            logger.error("获取API数据时出错", e)
        } finally {
            connection?.disconnect()
        }
        return "If you see this message, your API is broken, check your settings and Internet connection, then restart com.android.settings"
    }

    private fun parseContentFromJson(jsonString: String): String {
        return try {
            // 使用正则表达式匹配content字段，处理转义字符
            val regular = getString(PreferenceKeys.REGULAR.name)
            // 增加对表达式为空的保护：如果正则表达式为null或空，则跳过匹配
            if (regular.isEmpty()) {
                return jsonString
            }
            val pattern = Pattern.compile(regular)
            val matcher = pattern.matcher(jsonString)

            if (matcher.find()) {
                var content = matcher.group(1) ?: return jsonString
                // 处理转义字符（如\"转换为"）
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
                logger.warn("JSON中未找到content字段")
                jsonString
            }
        } catch (e: Exception) {
            logger.error("解析JSON时出错", e)
            jsonString
        }
    }

    /**
     * 设置 OwnerInfo 内容（确保在主线程执行设置操作）。
     */
    private fun setOwnerInfoContent(content: String, context: Any?, classLoader: ClassLoader) {
        try {
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                try {
                    logger.debug("设置OwnerInfo内容: $content")

                    // 方法1: 通过LockPatternUtils
                    try {
                        val lockPatternUtils = getObject(context, classLoader)

                        // 先启用OwnerInfo
                        val setEnabled: Method = lockPatternUtils.javaClass
                            .getDeclaredMethod("setOwnerInfoEnabled", Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                        setEnabled.invoke(lockPatternUtils, true, 0)
                        // 设置OwnerInfo内容
                        val setOwnerInfo: Method = lockPatternUtils.javaClass
                            .getDeclaredMethod("setOwnerInfo", String::class.java, Int::class.javaPrimitiveType)
                        setOwnerInfo.invoke(lockPatternUtils, content, 0)

                        logger.debug("通过LockPatternUtils成功更新OwnerInfo")
                        return@post
                    } catch (t: Throwable) {
                        logger.error("通过LockPatternUtils更新失败", t)
                    }

                    // 方法2: 通过ILockSettings服务
                    try {
                        val serviceManagerClass = classLoader.loadClass("android.os.ServiceManager")
                        val getServiceMethod: Method =
                            serviceManagerClass.getDeclaredMethod("getService", String::class.java)
                        val lockSettingsService = getServiceMethod.invoke(null, "lock_settings")

                        if (lockSettingsService != null) {
                            // 启用OwnerInfo
                            val setBooleanMethod: Method = lockSettingsService.javaClass
                                .getDeclaredMethod("setBoolean", String::class.java, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                            setBooleanMethod.invoke(
                                lockSettingsService,
                                "lock_screen_owner_info_enabled", true, 0
                            )
                            // 设置内容
                            val setStringMethod: Method = lockSettingsService.javaClass
                                .getDeclaredMethod("setString", String::class.java, String::class.java, Int::class.javaPrimitiveType)
                            setStringMethod.invoke(
                                lockSettingsService,
                                "lock_screen_owner_info", content, 0
                            )

                            logger.debug("通过ILockSettings成功更新OwnerInfo")
                            return@post
                        }
                    } catch (t: Throwable) {
                        logger.error("通过ILockSettings更新失败", t)
                    }

                    // 方法3: 直接调用SettingsProvider（备用方法）
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
                            logger.debug("通过SettingsProvider成功更新OwnerInfo")
                        }
                    } catch (t: Throwable) {
                        logger.error("通过SettingsProvider更新失败", t)
                    }
                } catch (t: Throwable) {
                    logger.error("设置OwnerInfo内容失败", t)
                }
            }
        } catch (t: Throwable) {
            logger.error("提交到主Handler失败", t)
        }
    }

    private fun getObject(context: Any?, classLoader: ClassLoader): Any {
        return try {
            val lockPatternUtilsClass = classLoader.loadClass(
                "com.android.internal.widget.LockPatternUtils"
            )

            val lockPatternUtils: Any
            if (context is Context) {
                // 从Context创建LockPatternUtils实例
                val ctor: Constructor<*> = lockPatternUtilsClass.getDeclaredConstructor(Context::class.java)
                lockPatternUtils = ctor.newInstance(context)
            } else {
                // 使用默认构造函数
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
