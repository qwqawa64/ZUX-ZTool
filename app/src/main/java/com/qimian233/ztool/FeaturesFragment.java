package com.qimian233.ztool;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.qimian233.ztool.settingactivity.gametool.GameToolSettngs;
import com.qimian233.ztool.settingactivity.launcher.LauncherSettingsActivity;
import com.qimian233.ztool.settingactivity.ota.OtaSettings;
import com.qimian233.ztool.settingactivity.packageinstaller.packageinstallersettings;
import com.qimian233.ztool.settingactivity.safecenter.SafeCenterSettingsActivity;
import com.qimian233.ztool.settingactivity.setting.SettingsDetailActivity;
import com.qimian233.ztool.settingactivity.systemframework.FrameworkSettingsActivity;
import com.qimian233.ztool.settingactivity.systemui.systemUISettings;

import java.util.ArrayList;
import java.util.List;

public class FeaturesFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_features, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view_features);

        // 1. 获取屏幕适配列数 (Pad适配)
        Context context = requireContext();
        int spanCount = getSpanCount(context);

        // 2. 设置布局管理器 (Grid)
        GridLayoutManager layoutManager = new GridLayoutManager(context, spanCount);
        recyclerView.setLayoutManager(layoutManager);

        // 3. 设置间距 (16dp)，保证在多列模式下美观
        int spacingInPixels = (int) (16 * getResources().getDisplayMetrics().density);
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(spanCount, spacingInPixels, true));

        // 4. 加载数据
        List<FeaturesAdapter.AppItem> appList = loadData(context);
        FeaturesAdapter adapter = new FeaturesAdapter(appList, context);
        recyclerView.setAdapter(adapter);

        return view;
    }

    /**
     * 屏幕宽度检测：
     * < 600dp -> 1列 (手机)
     * 600-840dp -> 2列 (平板/折叠屏)
     * > 840dp -> 3列 (大平板)
     */
    private int getSpanCount(Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        float dpWidth = displayMetrics.widthPixels / displayMetrics.density;

        if (dpWidth >= 840) {
            return 3;
        } else if (dpWidth >= 600) {
            return 2;
        } else {
            return 1;
        }
    }

    private List<FeaturesAdapter.AppItem> loadData(Context context) {
        List<FeaturesAdapter.AppItem> appList = new ArrayList<>();
        // 添加逻辑
        addAppItem(appList, context, R.string.settings_app_name, R.string.settings_app_description, "com.android.settings", SettingsDetailActivity.class);
        addAppItem(appList, context, R.string.game_tool_app_name, R.string.game_tool_app_description, "com.zui.game.service", GameToolSettngs.class);
        addAppItem(appList, context, R.string.system_update_app_name, R.string.system_update_app_description, "com.lenovo.ota", OtaSettings.class);
        addAppItem(appList, context, R.string.package_installer_app_name, R.string.package_installer_app_description, "com.android.packageinstaller", packageinstallersettings.class);
        addAppItem(appList, context, R.string.system_ui_app_name, R.string.system_ui_app_description, "com.android.systemui", systemUISettings.class);
        addAppItem(appList, context, R.string.launcher_app_name, R.string.launcher_app_description, "com.zui.launcher", LauncherSettingsActivity.class);
        addAppItem(appList, context, R.string.system_framework_app_name, R.string.system_framework_app_description, "android", FrameworkSettingsActivity.class);
        addAppItem(appList, context, R.string.safe_center_app_name, R.string.safe_center_app_description, "com.zui.safecenter", SafeCenterSettingsActivity.class);
        return appList;
    }

    private void addAppItem(List<FeaturesAdapter.AppItem> list, Context context, int nameRes, int descRes, String pkg, Class<?> cls) {
        list.add(new FeaturesAdapter.AppItem(getString(nameRes), getString(descRes), pkg, getApplicationIcon(context, pkg), true, cls));
    }

    public Drawable getApplicationIcon(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            if (info != null && info.applicationInfo != null) {
                return info.applicationInfo.loadIcon(pm);
            }
        } catch (Throwable ignore) {}
        return null;
    }

    // 间距装饰器
    public static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int spanCount;
        private final int spacing;
        private final boolean includeEdge;

        public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int column = position % spanCount;
            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount;
                outRect.right = (column + 1) * spacing / spanCount;
                if (position < spanCount) outRect.top = spacing;
                outRect.bottom = spacing;
            } else {
                outRect.left = column * spacing / spanCount;
                outRect.right = spacing - (column + 1) * spacing / spanCount;
                if (position >= spanCount) outRect.top = spacing;
            }
        }
    }
}
