package com.qimian233.ztool;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.qimian233.ztool.service.LogServiceManager;
import java.util.ArrayList;
import java.util.List;

public class SettingsFragment extends Fragment implements SettingsAdapter.OnSettingChangeListener {

    private SettingsAdapter adapter;
    private List<SettingsAdapter.SettingItem> settingsList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // 初始化设置列表
        settingsList = new ArrayList<>();

        // 日志采集服务开关
        boolean isLogServiceEnabled = LogServiceManager.isServiceEnabled(requireContext());
        settingsList.add(new SettingsAdapter.SettingItem(
                "日志采集服务",
                true,
                isLogServiceEnabled,
                "开启后将在后台收集Hook模块的运行日志，保存到应用私有目录"
        ));

        // 其他设置项
        settingsList.add(new SettingsAdapter.SettingItem("自动启动", true, false));
        settingsList.add(new SettingsAdapter.SettingItem("备份配置", false));
        settingsList.add(new SettingsAdapter.SettingItem("关于", false));

        // 设置RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view_settings);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new SettingsAdapter(settingsList);
        adapter.setOnSettingChangeListener(this);
        recyclerView.setAdapter(adapter);

        return view;
    }

    @Override
    public void onSwitchChanged(String settingTitle, boolean isChecked) {
        switch (settingTitle) {
            case "日志采集服务":
                handleLogServiceSwitch(isChecked);
                break;
            case "自动启动":
                // 处理自动启动开关
                break;
            // 其他开关处理...
        }
    }

    @Override
    public void onItemClicked(String settingTitle) {
        switch (settingTitle) {
            case "备份配置":
                // 处理备份配置点击
                break;
            case "关于":
                // 处理关于点击
                break;
            // 其他点击处理...
        }
    }

    /**
     * 处理日志服务开关状态变化
     */
    private void handleLogServiceSwitch(boolean isEnabled) {
        if (isEnabled) {
            // 启动日志采集服务
            LogServiceManager.startLogService(requireContext());
            showToast("日志采集服务已启动");
        } else {
            // 停止日志采集服务
            LogServiceManager.stopLogService(requireContext());
            showToast("日志采集服务已停止");
        }

        // 更新设置项状态
        updateSettingItemState("日志采集服务", isEnabled);
    }

    /**
     * 更新指定设置项的状态
     */
    private void updateSettingItemState(String title, boolean state) {
        for (SettingsAdapter.SettingItem item : settingsList) {
            if (item.getTitle().equals(title)) {
                item.setSwitchState(state);
                break;
            }
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * 显示Toast消息
     */
    private void showToast(String message) {
        if (getContext() != null) {
            android.widget.Toast.makeText(getContext(), message, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次进入设置页面时更新开关状态
        refreshSwitchStates();
    }

    /**
     * 刷新所有开关状态
     */
    private void refreshSwitchStates() {
        // 更新日志服务开关状态
        boolean isLogServiceEnabled = LogServiceManager.isServiceEnabled(requireContext());
        updateSettingItemState("日志采集服务", isLogServiceEnabled);
    }
}
