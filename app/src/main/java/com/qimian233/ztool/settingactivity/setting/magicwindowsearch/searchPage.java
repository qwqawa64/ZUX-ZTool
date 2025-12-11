package com.qimian233.ztool.settingactivity.setting.magicwindowsearch;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.qimian233.ztool.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class searchPage extends AppCompatActivity {

    private CardView resultCard;
    private JSONObject embedding_config;
    private final List<PackageInfo> searchResults = new ArrayList<>();
    private PackageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search_page);

        Toolbar toolbar = findViewById(R.id.toolbar_strategy_search);
        TextView tips = findViewById(R.id.tips_search_package);
        resultCard = findViewById(R.id.ResultsCard);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 初始化RecyclerView
        RecyclerView recyclerView = findViewById(R.id.recyclerview_results);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PackageAdapter(searchResults, this::showPackageDetails);
        recyclerView.setAdapter(adapter);

        try {
            embedding_config = new JSONObject(readFile("/data/system/zui/embedding/embedding_config.json"));
            int count = embedding_config.getJSONArray("packages").length();
            tips.setText(getString(R.string.module_config_tips, count));
        } catch (JSONException e) {
            try {
                embedding_config = new JSONObject(loadJsonFromAsset("embedding/embedding_config.json"));
            } catch (JSONException ex) {
                tips.setText(R.string.config_not_exists_tips);
                return;
            }
            tips.setText(R.string.official_config_tips);
        }

        TextInputEditText searchEditText = findViewById(R.id.edittext_search_package);
        MaterialButton searchButton = findViewById(R.id.button_search);

        searchButton.setOnClickListener(v -> {
            String packageName = Objects.requireNonNull(searchEditText.getText()).toString().trim();
            if (packageName.isEmpty()) {
                // 可以添加提示输入不能为空
                return;
            }

            performSearch(packageName);
        });
    }

    private void performSearch(String keyword) {
        searchResults.clear();

        try {
            JSONArray packages = embedding_config.getJSONArray("packages");
            for (int i = 0; i < packages.length(); i++) {
                JSONObject packageObj = packages.getJSONObject(i);
                String name = packageObj.optString("name", "");

                // 在包名中搜索关键字（不区分大小写）
                if (name.toLowerCase().contains(keyword.toLowerCase())) {
                    PackageInfo packageInfo = new PackageInfo(packageObj);
                    searchResults.add(packageInfo);
                }
            }

            // 更新UI
            adapter.notifyDataSetChanged();

            if (searchResults.isEmpty()) {
                resultCard.setVisibility(View.GONE);
                // 可以显示没有找到结果的提示
            } else {
                resultCard.setVisibility(View.VISIBLE);
            }

        } catch (JSONException e) {
            e.printStackTrace();
            // 处理搜索异常
        }
    }

    private void showPackageDetails(PackageInfo packageInfo) {
        StringBuilder details = new StringBuilder();

        // 构建详细信息
        details.append(getString(R.string.package_info_header)).append("\n\n");
        details.append(getString(R.string.app_package_name)).append(packageInfo.getName()).append("\n\n");
        details.append(getString(R.string.main_activity_info)).append(packageInfo.getMainPage()).append("\n\n");

        // 活动对信息
        List<ActivityPair> activityPairs = packageInfo.getActivityPairs();
        if (!activityPairs.isEmpty()) {
            details.append(getString(R.string.activity_pairs_info)).append("\n");
            for (ActivityPair pair : activityPairs) {
                details.append(getString(R.string.activity_pair_format, pair.getFrom(), pair.getTo())).append("\n");
            }
            details.append("\n");
        }

        // 强制全屏页面
        List<String> forceFullscreenPages = packageInfo.getForceFullscreenPages();
        if (!forceFullscreenPages.isEmpty()) {
            details.append(getString(R.string.force_fullscreen_pages)).append("\n");
            for (String page : forceFullscreenPages) {
                details.append(getString(R.string.list_item_format, page)).append("\n");
            }
            details.append("\n");
        }

        // 透明活动
        List<String> transActivities = packageInfo.getTransActivities();
        if (!transActivities.isEmpty()) {
            details.append(getString(R.string.transparent_activities)).append("\n");
            for (String activity : transActivities) {
                details.append(getString(R.string.list_item_format, activity)).append("\n");
            }
            details.append("\n");
        }

        // 左侧透明活动
        List<String> leftTransActivities = packageInfo.getLeftTransActivities();
        if (!leftTransActivities.isEmpty()) {
            details.append(getString(R.string.left_transparent_activities)).append("\n");
            for (String activity : leftTransActivities) {
                details.append(getString(R.string.list_item_format, activity)).append("\n");
            }
            details.append("\n");
        }

        // 其他配置信息
        details.append(getString(R.string.split_screen_config_header)).append("\n\n");
        details.append(getString(R.string.adjust_window_ratio)).append(packageInfo.getShowEmbeddingDivider()).append("\n");
        details.append(getString(R.string.skip_multi_window_mode)).append(packageInfo.getSkipMultiWindowMode()).append("\n");
        details.append(getString(R.string.skip_letterbox_display)).append(packageInfo.getSkipLetterboxDisplayInfo()).append("\n");
        details.append(getString(R.string.show_surface_view_bg)).append(packageInfo.getShowSurfaceViewBackground()).append("\n");
        details.append(getString(R.string.pause_primary_activity)).append(packageInfo.getShouldPausePrimaryActivity()).append("\n");

        // 创建MaterialAlertDialog
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.parallel_window_details_title)
                .setMessage(details.toString())
                .setPositiveButton(R.string.close_button, null)
                .show();
    }

    // 以下保持原有的readFile和loadJsonFromAsset方法不变
    static public String readFile(String filePath) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
            DataInputStream inputStream = new DataInputStream(process.getInputStream());

            outputStream.writeBytes("cat " + filePath + "\n");
            outputStream.writeBytes("exit\n");
            outputStream.flush();

            StringBuilder result = new StringBuilder();
            String line;
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }

            process.waitFor();
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    public String loadJsonFromAsset(String jsName) {
        try {
            InputStream is = getAssets().open(jsName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            Log.e("readerror", "Error reading JS file: " + jsName, e);
            return null;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
