package com.qimian233.ztool.settingactivity.setting.floatingwindow;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.qimian233.ztool.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FloatingWindow {
    private Context context;
    private WindowManager windowManager;
    private View floatingView;
    private TextView infoText;
    private TextView appNameText;
    private TextView welcomeText;
    private TextView stepText; // 新增：步骤提示文本
    private Handler handler;
    private Runnable updateRunnable;
    private static final long UPDATE_INTERVAL = 1000;
    private String SelectedApp;
    private Button nextButton;
    private Button addActivityButton; // 新增：添加活动按钮
    private CheckBox optionShowEmbeddingDivider;
    private CheckBox optionSkipLetterboxDisplayInfo;
    private CheckBox optionSkipMultiWindowMode;
    private CheckBox optionShowSurfaceViewBackground;
    private CheckBox optionShouldPausePrimaryActivity;
    private LinearLayout optionsLayout;
    private LinearLayout addActivityLayout;
    private TextView addedActivitiesText;
    private VideoView tutorialVideo;
    private LinearLayout videoLayout;
    // 向导状态
    private int currentStep = 0;
    private static final int STEP_SELECT_APP = 0;
    private static final int STEP_SET_MAIN_PAGE = 1;
    private static final int STEP_ADD_ACTIVITIES = 2;
    private static final int STEP_SET_OPTIONS = 3;
    private static final int STEP_COMPLETE = 4;

    // 配置数据
    private String appPackage;
    private String mainActivity;
    private Set<String> activityFromSet; // 使用Set避免重复
    private boolean showEmbeddingDivider = true;
    private boolean skipLetterboxDisplayInfo = false;
    private boolean skipMultiWindowMode = true;
    private boolean showSurfaceViewBackground = false;
    private boolean shouldPausePrimaryActivity = false;

    public FloatingWindow(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.handler = new Handler();
        this.activityFromSet = new HashSet<>();
        initFloatingView();
    }

    private void initFloatingView() {
        LayoutInflater inflater = LayoutInflater.from(context);
        floatingView = inflater.inflate(R.layout.floating_window, null);

        // 初始化视图
        infoText = floatingView.findViewById(R.id.ActivityInfo_text);
        appNameText = floatingView.findViewById(R.id.AppName_text);
        welcomeText = floatingView.findViewById(R.id.welcome_text);
        stepText = floatingView.findViewById(R.id.step_text);
        nextButton = floatingView.findViewById(R.id.next_button);
        videoLayout = floatingView.findViewById(R.id.video_layout);
        tutorialVideo = floatingView.findViewById(R.id.tutorial_video);

        // 初始化新增视图
        addActivityButton = floatingView.findViewById(R.id.add_activity_button);
        addedActivitiesText = floatingView.findViewById(R.id.added_activities_text);
        optionsLayout = floatingView.findViewById(R.id.options_layout);
        addActivityLayout = floatingView.findViewById(R.id.add_activities_layout);
        optionShowEmbeddingDivider = floatingView.findViewById(R.id.option_show_embedding_divider);
        optionSkipLetterboxDisplayInfo = floatingView.findViewById(R.id.option_skip_letterbox_display_info);
        optionSkipMultiWindowMode = floatingView.findViewById(R.id.option_skip_multi_window_mode);
        optionShowSurfaceViewBackground = floatingView.findViewById(R.id.option_show_surface_view_background);
        optionShouldPausePrimaryActivity = floatingView.findViewById(R.id.option_should_pause_primary_activity);

        // 设置选项默认值
        optionShowEmbeddingDivider.setChecked(showEmbeddingDivider);
        optionSkipLetterboxDisplayInfo.setChecked(skipLetterboxDisplayInfo);
        optionSkipMultiWindowMode.setChecked(skipMultiWindowMode);
        optionShowSurfaceViewBackground.setChecked(showSurfaceViewBackground);
        optionShouldPausePrimaryActivity.setChecked(shouldPausePrimaryActivity);

        // 下一步按钮逻辑
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleNextStep();
            }
        });

        // 添加活动按钮逻辑
        addActivityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addCurrentActivity();
            }
        });

        // 设置窗口参数
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        windowManager.addView(floatingView, params);
        startUpdating();
        updateUIForCurrentStep();
    }

    private void handleNextStep() {
        switch (currentStep) {
            case STEP_SELECT_APP:
                // 记录应用包名
                appPackage = getForegroundActivityByShell(true);
                if (appPackage == null || appPackage.equals(getString(R.string.unknown))) {
                    Toast.makeText(context, R.string.cannot_get_app_foreground, Toast.LENGTH_SHORT).show();
                    return;
                }
                SelectedApp = getAppNameFromPackage(context, appPackage);
                currentStep = STEP_SET_MAIN_PAGE;
                break;

            case STEP_SET_MAIN_PAGE:
                // 记录主活动
                mainActivity = getForegroundActivityByShell(false);
                if (mainActivity == null || mainActivity.equals(getString(R.string.unknown))) {
                    Toast.makeText(context, R.string.cannot_get_activity, Toast.LENGTH_SHORT).show();
                    return;
                }
                // 自动添加主活动到映射列表
                activityFromSet.add(mainActivity);
                currentStep = STEP_ADD_ACTIVITIES;
                break;

            case STEP_ADD_ACTIVITIES:
                currentStep = STEP_SET_OPTIONS;
                break;

            case STEP_SET_OPTIONS:
                // 记录选项值
                showEmbeddingDivider = optionShowEmbeddingDivider.isChecked();
                skipLetterboxDisplayInfo = optionSkipLetterboxDisplayInfo.isChecked();
                skipMultiWindowMode = optionSkipMultiWindowMode.isChecked();
                showSurfaceViewBackground = optionShowSurfaceViewBackground.isChecked();
                shouldPausePrimaryActivity = optionShouldPausePrimaryActivity.isChecked();
                currentStep = STEP_COMPLETE;
                break;

            case STEP_COMPLETE:
                generateConfig();
                closeFloatingWindow();
                return;
        }
        updateUIForCurrentStep();
    }

    private void updateUIForCurrentStep() {
        // 隐藏所有可选布局
        addActivityLayout.setVisibility(View.GONE);
        optionsLayout.setVisibility(View.GONE);
        videoLayout.setVisibility(View.GONE);

        // 停止视频播放（如果不是第二步或第三步）
        if (currentStep != STEP_SET_MAIN_PAGE && currentStep != STEP_ADD_ACTIVITIES) {
            stopVideoPlayback();
        }

        switch (currentStep) {
            case STEP_SELECT_APP:
                welcomeText.setText(R.string.welcome_message);
                stepText.setText(R.string.step_1_instruction);
                nextButton.setText(R.string.next_button);
                break;
            case STEP_SET_MAIN_PAGE:
                welcomeText.setText(R.string.set_main_page_title);
                stepText.setText(getString(R.string.step_2_instruction, SelectedApp));
                nextButton.setText(R.string.next_button);
                videoLayout.setVisibility(View.VISIBLE); // 显示视频布局
                startVideoPlayback(R.raw.mainact); // 播放主活动教程视频
                break;
            case STEP_ADD_ACTIVITIES:
                welcomeText.setText(R.string.add_activities_title);
                stepText.setText(R.string.step_3_instruction);
                nextButton.setText(R.string.continue_button);
                addActivityLayout.setVisibility(View.VISIBLE);
                videoLayout.setVisibility(View.VISIBLE);
                startVideoPlayback(R.raw.tutorial); // 播放原有教程视频
                updateAddedActivitiesText();
                break;
            case STEP_SET_OPTIONS:
                welcomeText.setText(R.string.config_options_title);
                stepText.setText(R.string.step_4_instruction);
                nextButton.setText(R.string.finish_config_button);
                optionsLayout.setVisibility(View.VISIBLE);
                break;
            case STEP_COMPLETE:
                welcomeText.setText(R.string.config_complete_title);
                stepText.setText(R.string.step_complete_instruction);
                nextButton.setText(R.string.save_config_button);
                break;
        }
    }

    private String getString(int resId) {
        return context.getString(resId);
    }

    private String getString(int resId, Object... formatArgs) {
        return context.getString(resId, formatArgs);
    }

    private void startVideoPlayback(int videoResId) {
        try {
            String videoPath = "android.resource://" + context.getPackageName() + "/" + videoResId;
            Uri videoUri = Uri.parse(videoPath);
            tutorialVideo.setVideoURI(videoUri);
            tutorialVideo.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    tutorialVideo.start(); // 循环播放
                }
            });
            tutorialVideo.start();
        } catch (Exception e) {
            Log.e("FloatingWindow", "视频播放失败: " + e.getMessage());
        }
    }

    // 停止视频播放
    private void stopVideoPlayback() {
        if (tutorialVideo != null && tutorialVideo.isPlaying()) {
            tutorialVideo.stopPlayback();
        }
    }

    private void addCurrentActivity() {
        String currentActivity = getForegroundActivityByShell(false);
        if (currentActivity != null && !currentActivity.equals(getString(R.string.unknown))) {
            if (activityFromSet.add(currentActivity)) {
                updateAddedActivitiesText();
                Toast.makeText(context, getString(R.string.activity_added, currentActivity), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, R.string.activity_already_added, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(context, R.string.cannot_get_activity, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAddedActivitiesText() {
        String text = getString(R.string.added_activities_count, activityFromSet.size());
        if (!activityFromSet.isEmpty()) {
            text += "\n" + String.join("\n", activityFromSet);
        }
        addedActivitiesText.setText(text);
    }

    private void generateConfig() {
        try {
            JSONObject config = new JSONObject();
            config.put("name", appPackage);
            config.put("mainPage", mainActivity);

            // 构建activityPairs数组
            JSONArray activityPairs = new JSONArray();
            for (String fromActivity : activityFromSet) {
                JSONObject pair = new JSONObject();
                pair.put("from", fromActivity);
                pair.put("to", "*");
                activityPairs.put(pair);
            }
            config.put("activityPairs", activityPairs);

            // 添加选项
            config.put("showEmbeddingDivider", String.valueOf(showEmbeddingDivider));
            config.put("skipLetterboxDisplayInfo", String.valueOf(skipLetterboxDisplayInfo));
            config.put("skipMultiWindowMode", String.valueOf(skipMultiWindowMode));
            config.put("showSurfaceViewBackground", String.valueOf(showSurfaceViewBackground));
            config.put("shouldPausePrimaryActivity", String.valueOf(shouldPausePrimaryActivity));

            // 添加空数组字段
            config.put("forceFullscreenPages", new JSONArray());
            config.put("transActivities", new JSONArray());
            config.put("leftTransActivities", new JSONArray());

            // 输出配置（这里可以保存到文件或发送到其他组件）
            String configJson = config.toString(2);
            Log.d("EmbeddingConfig", "生成的配置:\n" + configJson);
            Toast.makeText(context, R.string.config_generated, Toast.LENGTH_LONG).show();
            saveBase64StringToFile(floatingView.getContext(),configJson,appPackage);

        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(context, R.string.config_generation_error, Toast.LENGTH_SHORT).show();
        }
    }

    public void show() {
        if (floatingView != null) {
            floatingView.setVisibility(View.VISIBLE);
        }
    }

    private void startUpdating() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                String foregroundApp = getForegroundApp();
                String foregroundActivity = getForegroundActivityByShell(true);
                Log.i("EmbeddingConfig", "当前应用: " + foregroundApp);
                String appName = getAppNameFromPackage(floatingView.getContext(), foregroundActivity);

                infoText.setText(getString(R.string.current_activity, foregroundApp));
                appNameText.setText(getString(R.string.app_name_label, appName));

                // 在前步骤3中检查是否在目标应用中
                if (currentStep <= STEP_ADD_ACTIVITIES && SelectedApp != null && !appName.equals(SelectedApp)) {
                    stepText.setText(getString(R.string.return_to_app, SelectedApp));
                    addActivityButton.setVisibility(View.GONE);
                    nextButton.setVisibility(View.GONE);
                } else if (currentStep <= STEP_ADD_ACTIVITIES && SelectedApp != null) {
                    nextButton.setVisibility(View.VISIBLE);
                    if (currentStep == STEP_ADD_ACTIVITIES) {
                        addActivityButton.setVisibility(View.VISIBLE);
                        stepText.setText(R.string.step_3_instruction);
                    } else if (currentStep == STEP_SET_MAIN_PAGE) {
                        stepText.setText(getString(R.string.step_2_instruction, SelectedApp));
                    }
                }

                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        handler.post(updateRunnable);
    }

    private void stopUpdating() {
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    // 以下原有方法完全保持不变
    public static String getAppNameFromPackage(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) {
            return null;
        }
        PackageManager packageManager = context.getPackageManager();
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(applicationInfo);
            return label != null ? label.toString() : null;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getForegroundApp() {
        String activityInfo = getForegroundActivityByShell(false);
        if (activityInfo != null && !activityInfo.equals(getString(R.string.unknown))) {
            return activityInfo;
        }
        return getForegroundPackage();
    }

    private String getForegroundActivityByShell(Boolean OnlyPackageName) {
        try {
            Process process = Runtime.getRuntime().exec("su -c dumpsys activity activities | grep -E \"ResumedActivity|mFocusedActivity\"");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("ResumedActivity") || line.contains("mFocusedActivity")) {
                    Pattern pattern = Pattern.compile("u0\\s+([^/]+)/([^\\s\\},]+)");
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find() && !OnlyPackageName) {
                        String packageName = matcher.group(1);
                        String activityName = matcher.group(2);
                        return packageName + activityName;
                    } else {
                        String packageName = matcher.group(1);
                        return packageName;
                    }
                }
            }
            reader.close();
            return getForegroundActivityByShellAlternative();
        } catch (Exception e) {
            e.printStackTrace();
            return getForegroundActivityByShellAlternative();
        }
    }

    private String getForegroundActivityByShellAlternative() {
        try {
            Process process = Runtime.getRuntime().exec("su -c dumpsys activity top | grep -E \"ACTIVITY\"");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("ACTIVITY")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        return parts[1];
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return getString(R.string.unknown);
    }

    private String getForegroundPackage() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            long time = System.currentTimeMillis();
            List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 5000, time);
            if (stats != null) {
                SortedMap<Long, UsageStats> sortedStats = new TreeMap<>();
                for (UsageStats usageStats : stats) {
                    sortedStats.put(usageStats.getLastTimeUsed(), usageStats);
                }
                if (!sortedStats.isEmpty()) {
                    UsageStats latestStats = sortedStats.get(sortedStats.lastKey());
                    return latestStats.getPackageName();
                }
            }
        }
        return getString(R.string.unknown);
    }

    public boolean saveBase64StringToFile(Context context, String originalString, String PackageName) {
        try {
            // Step 1: 将字符串进行Base64编码
            // 使用UTF-8编码确保字符正确转换，Base64.DEFAULT表示使用默认标志
            String base64String = Base64.encodeToString(originalString.getBytes("UTF-8"), Base64.DEFAULT);

            // Step 2: 获取或创建目标目录（路径为 files/custom_EmbeddingConfig）
            File dir = new File(context.getFilesDir(), "data/custom_EmbeddingConfig");
            if (!dir.exists()) {
                // 创建目录（包括父目录如果不存在）
                boolean dirCreated = dir.mkdirs();
                if (!dirCreated) {
                    // 目录创建失败，返回false
                    return false;
                }
            }

            // Step 3: 生成文件名（使用当前时间戳）
            String fileName = String.valueOf(System.currentTimeMillis());
            File file = new File(dir, fileName + "_" + PackageName);

            // Step 4: 将Base64字符串写入文件
            // 使用FileOutputStream，指定UTF-8编码写入
            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(base64String.getBytes("UTF-8"));
            outputStream.close(); // 关闭流

            return true; // 保存成功
        } catch (IOException e) {
            e.printStackTrace(); // 打印异常日志（实际项目中可使用Log.e）
            return false; // 保存失败
        }
    }

    // 在closeFloatingWindow方法中确保停止视频播放
    public void closeFloatingWindow() {
        stopVideoPlayback();
        stopUpdating();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
            floatingView = null;
        }
    }

    // 在hide方法中也停止视频
    public void hide() {
        if (floatingView != null) {
            floatingView.setVisibility(View.GONE);
        }
        stopVideoPlayback();
        stopUpdating();
    }
}
