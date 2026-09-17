package com.qimian233.ztool.utils

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Queries Lenovo PC flash firmware information.
 *
 * All network requests run on [Dispatchers.IO]; callers must invoke [queryFirmware]
 * from a coroutine.
 */
class GetPCFlashFirmware {

    /**
     * Query firmware info, returning a six-element array (download URL, password,
     * platform, flashing method, first upload time, last update time);
     * returns null on failure.
     */
    suspend fun queryFirmware(sn: String): Array<String?>? = withContext(Dispatchers.IO) {
        if (sn.isEmpty()) {
            Log.w(TAG, "error: please provide a device serial number as argument")
            return@withContext null
        }
        Log.d(TAG, "serial number: $sn")

        try {
            val mtm = getMTM(sn)
            if (mtm.isNullOrEmpty()) {
                Log.w(TAG, "error: failed to get MTM parameter")
                return@withContext null
            }
            Log.d(TAG, "MTM parameter: $mtm")

            val packageInfo = getDownloadPackageInfo(mtm)
            if (!packageInfo.isNullOrEmpty()) {
                packageInfo
            } else {
                Log.w(TAG, "error: empty download URL")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "exception while querying firmware", e)
            null
        }
    }

    companion object {
        private const val TAG = "LenovoFirmwareQuery"

        private val MTM_PATTERN = Regex("\"MTM\":\"([^\"]+)\"")

        /**
         * Fetch machine info and extract the MTM parameter
         */
        private fun getMTM(sn: String): String? {
            return try {
                val urlStr = "https://ptstpd.lenovo.com.cn/home/ConfigurationQuery/getMachineSequenceInfo?MachineNo=" +
                        URLEncoder.encode(sn, "UTF-8")
                Log.d(TAG, "query URL: $urlStr")
                val response = sendGetRequest(urlStr)
                Log.d(TAG, "MTM fetched successfully")
                extractMTM(response)
            } catch (e: Exception) {
                Log.w(TAG, "error while fetching MTM: " + e.message)
                null
            }
        }

        /**
         * Fetch the flash package info using MTM
         */
        private fun getDownloadPackageInfo(mtm: String): Array<String?>? {
            return try {
                val jsonBody = "{\"mtm\":\"$mtm\"}"

                val response = sendPostRequest(jsonBody)
                arrayOf(
                    extractEverything(response, "download_url"), // Download URL
                    "FC(fv:SknR", // Password, found in official tool
                    extractEverything(response, "platform"), // Platform type, Qualcomm or MTK
                    extractEverything(response, "flashing_machine_method"), // Method
                    extractEverything(response, "add_time"), // Uncertain, maybe first upload time?
                    extractEverything(response, "upd_time") // Last update time
                )
            } catch (e: Exception) {
                Log.w(TAG, "error while fetching download URL: " + e.message)
                null
            }
        }

        /**
         * Send a GET request
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
         * Send a POST request
         */
        private fun sendPostRequest(jsonBody: String): String {
            val url = URL("https://ptstpd.lenovo.com.cn/home/ConfigurationQuery/getPadFlashingMachine")
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
         * Read the HTTP response
         */
        private fun readResponse(conn: HttpURLConnection): String {
            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP request failed, response code: $responseCode")
                throw IOException("HTTP request failed, response code: $responseCode")
            }
            Log.d(TAG, "HTTP request succeeded, response code: $responseCode")
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
                Log.w(TAG, "error while reading response: $e")
                throw e
            }
            // Log.d(TAG, "response content: " + response)
            return response.toString()
        }

        /**
         * Extract the MTM parameter from the JSON response
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
