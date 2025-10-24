// PackageInfo.java
package com.qimian233.ztool.settingactivity.setting.magicwindowsearch;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PackageInfo {
    private String name;
    private String mainPage;
    private List<ActivityPair> activityPairs;
    private List<String> forceFullscreenPages;
    private List<String> transActivities;
    private List<String> leftTransActivities;
    private String showEmbeddingDivider;
    private String skipLetterboxDisplayInfo;
    private String skipMultiWindowMode;
    private String showSurfaceViewBackground;
    private String shouldPausePrimaryActivity;

    public PackageInfo(JSONObject jsonObject) throws JSONException {
        this.name = jsonObject.optString("name", "");
        this.mainPage = jsonObject.optString("mainPage", "");

        // 解析activityPairs
        this.activityPairs = new ArrayList<>();
        if (jsonObject.has("activityPairs")) {
            JSONArray pairsArray = jsonObject.getJSONArray("activityPairs");
            for (int i = 0; i < pairsArray.length(); i++) {
                JSONObject pairObj = pairsArray.getJSONObject(i);
                activityPairs.add(new ActivityPair(
                        pairObj.optString("from", ""),
                        pairObj.optString("to", "")
                ));
            }
        }

        // 解析forceFullscreenPages
        this.forceFullscreenPages = new ArrayList<>();
        if (jsonObject.has("forceFullscreenPages")) {
            JSONArray fullscreenArray = jsonObject.getJSONArray("forceFullscreenPages");
            for (int i = 0; i < fullscreenArray.length(); i++) {
                forceFullscreenPages.add(fullscreenArray.getString(i));
            }
        }

        // 解析transActivities
        this.transActivities = new ArrayList<>();
        if (jsonObject.has("transActivities")) {
            JSONArray transArray = jsonObject.getJSONArray("transActivities");
            for (int i = 0; i < transArray.length(); i++) {
                transActivities.add(transArray.getString(i));
            }
        }

        // 解析leftTransActivities
        this.leftTransActivities = new ArrayList<>();
        if (jsonObject.has("leftTransActivities")) {
            JSONArray leftTransArray = jsonObject.getJSONArray("leftTransActivities");
            for (int i = 0; i < leftTransArray.length(); i++) {
                leftTransActivities.add(leftTransArray.getString(i));
            }
        }

        // 解析其他字符串字段
        this.showEmbeddingDivider = jsonObject.optString("showEmbeddingDivider", "未设置");
        this.skipLetterboxDisplayInfo = jsonObject.optString("skipLetterboxDisplayInfo", "未设置");
        this.skipMultiWindowMode = jsonObject.optString("skipMultiWindowMode", "未设置");
        this.showSurfaceViewBackground = jsonObject.optString("showSurfaceViewBackground", "未设置");
        this.shouldPausePrimaryActivity = jsonObject.optString("shouldPausePrimaryActivity", "未设置");
    }

    // Getter方法
    public String getName() { return name; }
    public String getMainPage() { return mainPage; }
    public List<ActivityPair> getActivityPairs() { return activityPairs; }
    public List<String> getForceFullscreenPages() { return forceFullscreenPages; }
    public List<String> getTransActivities() { return transActivities; }
    public List<String> getLeftTransActivities() { return leftTransActivities; }
    public String getShowEmbeddingDivider() { return showEmbeddingDivider; }
    public String getSkipLetterboxDisplayInfo() { return skipLetterboxDisplayInfo; }
    public String getSkipMultiWindowMode() { return skipMultiWindowMode; }
    public String getShowSurfaceViewBackground() { return showSurfaceViewBackground; }
    public String getShouldPausePrimaryActivity() { return shouldPausePrimaryActivity; }
}
