package com.qimian233.ztool;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.qimian233.ztool.R;
import java.util.ArrayList;
import java.util.List;

public class SettingsFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // 设置RecyclerView用于显示设置项
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view_settings);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // 这一页是摆设
        List<SettingsAdapter.SettingItem> settingsList = new ArrayList<>();
        settingsList.add(new SettingsAdapter.SettingItem("启用日志", true));
        settingsList.add(new SettingsAdapter.SettingItem("自动启动", false));
        settingsList.add(new SettingsAdapter.SettingItem("备份配置", false));
        // 更多设置项...

        SettingsAdapter adapter = new SettingsAdapter(settingsList);
        recyclerView.setAdapter(adapter);

        return view;
    }
}
