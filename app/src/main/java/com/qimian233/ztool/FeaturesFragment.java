package com.qimian233.ztool;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_features, container, false);

        // 设置RecyclerView用于显示应用列表
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view_features);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // 添加设置到选项列表
        List<FeaturesAdapter.AppItem> appList = new ArrayList<>();
        appList.add(new FeaturesAdapter.AppItem(
                getString(R.string.settings_app_name),
                getString(R.string.settings_app_description),
                "com.android.settings",
                getApplicationIcon(requireContext(), "com.android.settings"),
                true,
                SettingsDetailActivity.class
        ));
        //添加游戏助手
        appList.add(new FeaturesAdapter.AppItem(
                getString(R.string.game_tool_app_name),
                getString(R.string.game_tool_app_description),
                "com.zui.game.service",
                getApplicationIcon(requireContext(), "com.zui.game.service"),
                true,
                GameToolSettngs.class
        ));
        //添加系统OTA
        appList.add(new FeaturesAdapter.AppItem(
                getString(R.string.system_update_app_name),
                getString(R.string.system_update_app_description),
                "com.lenovo.ota",
                getApplicationIcon(requireContext(), "com.lenovo.ota"),
                true,
                OtaSettings.class
        ));
        //添加软件包安装程序
        appList.add(new FeaturesAdapter.AppItem(
                getString(R.string.package_installer_app_name),
                getString(R.string.package_installer_app_description),
                "com.android.packageinstaller",
                getApplicationIcon(requireContext(), "com.android.packageinstaller"),
                true,
                packageinstallersettings.class
        ));
        // 添加系统界面
        appList.add(new FeaturesAdapter.AppItem(
                getString(R.string.system_ui_app_name),
                getString(R.string.system_ui_app_description),
                "com.android.systemui",
                getApplicationIcon(requireContext(), "com.android.systemui"),
                true,
                systemUISettings.class
        ));

        // 添加启动器设置
        appList.add(new FeaturesAdapter.AppItem(
                getString(R.string.launcher_app_name),
                getString(R.string.launcher_app_description),
                "com.zui.launcher",
                getApplicationIcon(requireContext(), "com.zui.launcher"),
                true,
                LauncherSettingsActivity.class
        ));

        appList.add(new FeaturesAdapter.AppItem(
                getString(R.string.system_framework_app_name),
                getString(R.string.system_framework_app_description),
                "android",
                getApplicationIcon(requireContext(), "android"),
                true,
                FrameworkSettingsActivity.class
        ));

        appList.add(new FeaturesAdapter.AppItem(
                getString(R.string.safe_center_app_name),
                getString(R.string.safe_center_app_description),
                "com.zui.safecenter",
                getApplicationIcon(requireContext(), "com.zui.safecenter"),
                true,
                SafeCenterSettingsActivity.class
        ));

        // 可以添加更多应用项
        // appList.add(new FeaturesAdapter.AppItem(...));

        FeaturesAdapter adapter = new FeaturesAdapter(appList, requireContext());
        recyclerView.setAdapter(adapter);

        return view;
    }

    /**
     * 根据包名获取应用图标
     */
    public Drawable getApplicationIcon(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        PackageManager pm = context.getPackageManager();
        PackageInfo packageInfo = getPackageInfo(context, packageName);
        if (packageInfo != null) {
            assert packageInfo.applicationInfo != null;
            return packageInfo.applicationInfo.loadIcon(pm);
        }
        return null;
    }

    /**
     * 根据包名获取 PackageInfo
     */
    public PackageInfo getPackageInfo(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
        } catch (Throwable ignore) {
            // 处理异常，可能是包名不存在等情况
        }
        return null;
    }
}
