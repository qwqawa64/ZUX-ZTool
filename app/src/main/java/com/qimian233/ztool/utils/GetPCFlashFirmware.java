package com.qimian233.ztool.utils;

import android.os.AsyncTask;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.regex.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/** @noinspection deprecation*/
public class GetPCFlashFirmware {

    private static final String TAG = "LenovoFirmwareQuery";

    // 异步查询固件信息
    public void queryFirmwareAsync(String sn, OnFirmwareQueryListener listener) {
        new QueryFirmwareTask(listener).execute(sn);
    }

    /** @noinspection deprecation*/ // 异步任务类
    private static class QueryFirmwareTask extends AsyncTask<String, Void, String[]> {
        private final OnFirmwareQueryListener listener;

        public QueryFirmwareTask(OnFirmwareQueryListener listener) {
            this.listener = listener;
        }

        @Override
        protected String[] doInBackground(String... params) {
            String sn = params[0];
            if (sn == null || sn.isEmpty()) {
                Log.w(TAG, "错误: 请提供设备序列号作为参数");
                return null;
            }
            Log.d(TAG, "获取到序列号: " + sn);

            try {
                String mtm = getMTM(sn);
                if (mtm == null || mtm.isEmpty()) {
                    Log.w(TAG, "错误: 无法获取MTM参数");
                    return null;
                }
                Log.d(TAG,"获取到的MTM参数：" + mtm);

                String[] packageInfo = getDownloadPackageInfo(mtm);
                if (packageInfo != null && packageInfo.length > 0) {
                    return packageInfo;
                } else {
                    Log.w(TAG, "错误: 空下载链接");
                    return null;
                }
            } catch (Exception e) {
                Log.e(TAG, "查询固件时发生异常", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String[] result) {
            if (listener != null) {
                listener.onFirmwareQueryResult(result);
            }
        }
    }

    // 回调接口
    public interface OnFirmwareQueryListener {
        void onFirmwareQueryResult(String[] result);
    }

    /**
     * 获取机器信息并提取MTM参数
     */
    private static String getMTM(String sn) {
        try {
            String urlStr = "https://ptstpd.lenovo.com.cn/home/ConfigurationQuery/getMachineSequenceInfo?MachineNo="+
                    URLEncoder.encode(sn, "UTF-8");
            Log.d(TAG,"查询链接：" + urlStr);
            String response = sendGetRequest(urlStr);
            Log.d(TAG,"成功获取到MTM");
            return extractMTM(response);

        } catch (Exception e) {
            Log.w(TAG, "获取MTM时发生错误: " + e.getMessage());
            return null;
        }
    }

    /**
     * 使用MTM获取刷机包信息
     */
    private static String[] getDownloadPackageInfo(String mtm) {
        try {
            String urlStr = "https://ptstpd.lenovo.com.cn/home/ConfigurationQuery/getPadFlashingMachine";
            String jsonBody = "{\"mtm\":\"" + mtm + "\"}";

            String response = sendPostRequest(urlStr, jsonBody);
            return new String[]{
                    extractEverything(response, "download_url"), // Download URL
                    "FC(fv:SknR", // Password, found in official tool
                    extractEverything(response, "platform"), // Platform type, Qualcomm or MTK
                    extractEverything(response, "flashing_machine_method"), // Method
                    extractEverything(response, "add_time"), // Uncertain, maybe first upload time?
                    extractEverything(response, "upd_time") // Last update time
            };

        } catch (Exception e) {
            Log.w(TAG, "获取下载链接时发生错误: " + e.getMessage());
            return null;
        }
    }

    /**
     * 发送GET请求
     */
    private static String sendGetRequest(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        return readResponse(conn);
    }

    /**
     * 发送POST请求
     */
    private static String sendPostRequest(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        try (OutputStream os = conn.getOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8")) {
            osw.write(jsonBody);
            osw.flush();
        }

        return readResponse(conn);
    }

    /**
     * 读取HTTP响应
     */
    private static String readResponse(HttpURLConnection conn) throws IOException {
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP请求失败，响应码: " + responseCode);
                throw new IOException("HTTP请求失败，响应码: " + responseCode);
            }
            Log.d(TAG, "HTTP请求成功，响应码：" + responseCode);
            StringBuilder response = new StringBuilder();
            try (InputStream is = conn.getInputStream();
                 InputStreamReader isr = new InputStreamReader(is, "UTF-8");
                 BufferedReader br = new BufferedReader(isr)) {

                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            } catch (Exception e) {
                Log.w(TAG, "读取响应时发生错误: " + e);
                throw e;
            }
            // Log.d(TAG, "读取到的响应内容：" + response);
            return response.toString();

    }

    /**
     * 从JSON响应中提取MTM参数
     */
    private static String extractMTM(String jsonResponse) {
        Pattern pattern = Pattern.compile("\"MTM\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(jsonResponse);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String extractEverything(String jsonResponse, String key) {
        try {
            Gson gson = new Gson();
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
            JsonArray dataArray = response.getAsJsonArray("data");

            if (dataArray != null && !dataArray.isEmpty()) {
                JsonObject firmwareInfo = dataArray.get(0).getAsJsonObject();
                return firmwareInfo.get(key).getAsString();
            }
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Failed to parse assigned content", e);
        }
        return null;
    }
}
