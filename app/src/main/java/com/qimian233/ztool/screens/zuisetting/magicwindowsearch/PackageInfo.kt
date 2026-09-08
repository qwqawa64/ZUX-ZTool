package com.qimian233.ztool.screens.zuisetting.magicwindowsearch

import org.json.JSONException
import org.json.JSONObject

class PackageInfo @Throws(JSONException::class) constructor(jsonObject: JSONObject) {
    val name: String = jsonObject.optString("name", "")
    val mainPage: String = jsonObject.optString("mainPage", "")

    // 解析activityPairs
    val activityPairs: List<ActivityPair> = buildList {
        if (jsonObject.has("activityPairs")) {
            val pairsArray = jsonObject.getJSONArray("activityPairs")
            for (i in 0 until pairsArray.length()) {
                val pairObj = pairsArray.getJSONObject(i)
                add(ActivityPair(pairObj.optString("from", ""), pairObj.optString("to", "")))
            }
        }
    }

    // 解析forceFullscreenPages
    val forceFullscreenPages: List<String> = buildList {
        if (jsonObject.has("forceFullscreenPages")) {
            val fullscreenArray = jsonObject.getJSONArray("forceFullscreenPages")
            for (i in 0 until fullscreenArray.length()) {
                add(fullscreenArray.getString(i))
            }
        }
    }

    // 解析transActivities
    val transActivities: List<String> = buildList {
        if (jsonObject.has("transActivities")) {
            val transArray = jsonObject.getJSONArray("transActivities")
            for (i in 0 until transArray.length()) {
                add(transArray.getString(i))
            }
        }
    }

    // 解析leftTransActivities
    val leftTransActivities: List<String> = buildList {
        if (jsonObject.has("leftTransActivities")) {
            val leftTransArray = jsonObject.getJSONArray("leftTransActivities")
            for (i in 0 until leftTransArray.length()) {
                add(leftTransArray.getString(i))
            }
        }
    }

    // 解析其他字符串字段
    val showEmbeddingDivider: String = jsonObject.optString("showEmbeddingDivider", "未设置")
    val skipLetterboxDisplayInfo: String = jsonObject.optString("skipLetterboxDisplayInfo", "未设置")
    val skipMultiWindowMode: String = jsonObject.optString("skipMultiWindowMode", "未设置")
    val showSurfaceViewBackground: String = jsonObject.optString("showSurfaceViewBackground", "未设置")
    val shouldPausePrimaryActivity: String = jsonObject.optString("shouldPausePrimaryActivity", "未设置")
}
