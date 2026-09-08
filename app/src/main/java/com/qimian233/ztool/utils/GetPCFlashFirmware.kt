package com.qimian233.ztool.utils

import android.os.AsyncTask
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Suppress("DEPRECATION")
class GetPCFlashFirmware {

    // 异步查询固件信息
    fun queryFirmwareAsync(sn: String, listener: OnFirmwareQueryListener) {
        QueryFirmwareTask(listener).execute(sn)
    }

    // 异步任务类
    private class QueryFirmwareTask(val listener: OnFirmwareQueryListener) :
        AsyncTask<String, Void, Array<String?>?>() {

        override fun doInBackground(vararg params: String): Array<String?>? {
            val sn = params[0]
            if (sn.isEmpty()) {
                Log.w(TAG, "错误: 请提供设备序列号作为参数")
                return null
            }
            Log.d(TAG, "获取到序列号: $sn")

            return try {
                val mtm = getMTM(sn)
                if (mtm.isNullOrEmpty()) {
                    Log.w(TAG, "错误: 无法获取MTM参数")
                    return null
                }
                Log.d(TAG, "获取到的MTM参数：$mtm")

                val packageInfo = getDownloadPackageInfo(mtm)
                if (packageInfo != null && packageInfo.isNotEmpty()) {
                    packageInfo
                } else {
                    Log.w(TAG, "错误: 空下载链接")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "查询固件时发生异常", e)
                null
            }
        }

        override fun onPostExecute(result: Array<String?>?) {
            listener.onFirmwareQueryResult(result)
        }
    }

    // 回调接口
    fun interface OnFirmwareQueryListener {
        fun onFirmwareQueryResult(result: Array<String?>?)
    }

    companion object {
        private const val TAG = "LenovoFirmwareQuery"

        private val MTM_PATTERN = Regex("\"MTM\":\"([^\"]+)\"")

        /**
         * 获取机器信息并提取MTM参数
         */
        private fun getMTM(sn: String): String? {
            return try {
                val urlStr = "https://ptstpd.lenovo.com.cn/home/ConfigurationQuery/getMachineSequenceInfo?MachineNo=" +
                        URLEncoder.encode(sn, "UTF-8")
                Log.d(TAG, "查询链接：$urlStr")
                val response = sendGetRequest(urlStr)
                Log.d(TAG, "成功获取到MTM")
                extractMTM(response)
            } catch (e: Exception) {
                Log.w(TAG, "获取MTM时发生错误: " + e.message)
                null
            }
        }

        /**
         * 使用MTM获取刷机包信息
         */
        private fun getDownloadPackageInfo(mtm: String): Array<String?>? {
            return try {
                val urlStr = "https://ptstpd.lenovo.com.cn/home/ConfigurationQuery/getPadFlashingMachine"
                val jsonBody = "{\"mtm\":\"" + mtm + "\"}"

                val response = sendPostRequest(urlStr, jsonBody)
                arrayOf(
                    extractEverything(response, "download_url"), // Download URL
                    "FC(fv:SknR", // Password, found in official tool
                    extractEverything(response, "platform"), // Platform type, Qualcomm or MTK
                    extractEverything(response, "flashing_machine_method"), // Method
                    extractEverything(response, "add_time"), // Uncertain, maybe first upload time?
                    extractEverything(response, "upd_time") // Last update time
                )
            } catch (e: Exception) {
                Log.w(TAG, "获取下载链接时发生错误: " + e.message)
                null
            }
        }

        /**
         * 发送GET请求
         */
        private fun sendGetRequest(urlStr: String): String {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            return readResponse(conn)
        }

        /**
         * 发送POST请求
         */
        private fun sendPostRequest(urlStr: String, jsonBody: String): String {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            conn.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { osw ->
                    osw.write(jsonBody)
                    osw.flush()
                }
            }

            return readResponse(conn)
        }

        /**
         * 读取HTTP响应
         */
        private fun readResponse(conn: HttpURLConnection): String {
            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP请求失败，响应码: $responseCode")
                throw IOException("HTTP请求失败，响应码: $responseCode")
            }
            Log.d(TAG, "HTTP请求成功，响应码：$responseCode")
            val response = StringBuilder()
            try {
                conn.inputStream.use { inputStream ->
                    InputStreamReader(inputStream, "UTF-8").use { isr ->
                        BufferedReader(isr).use { br ->
                            var line: String?
                            while (br.readLine().also { line = it } != null) {
                                response.append(line)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "读取响应时发生错误: $e")
                throw e
            }
            // Log.d(TAG, "读取到的响应内容：" + response)
            return response.toString()
        }

        /**
         * 从JSON响应中提取MTM参数
         */
        private fun extractMTM(jsonResponse: String): String? {
            return MTM_PATTERN.find(jsonResponse)?.groupValues?.get(1)
        }

        private fun extractEverything(jsonResponse: String, key: String): String? {
            return try {
                val response = Gson().fromJson(jsonResponse, JsonObject::class.java)
                val dataArray = response.getAsJsonArray("data")

                if (dataArray != null && dataArray.size() > 0) {
                    val firmwareInfo = dataArray.get(0).asJsonObject
                    return firmwareInfo.get(key)!!.asString
                }
                null
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "Failed to parse assigned content", e)
                null
            }
        }
    }
}
