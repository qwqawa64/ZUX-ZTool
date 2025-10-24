package com.qimian233.ztool;  // 替换为您的实际包名

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.qimian233.ztool.R;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.List;

public class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.ViewHolder> {

    private List<SettingItem> settingsList;

    public SettingsAdapter(List<SettingItem> settingsList) {
        this.settingsList = settingsList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_setting, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SettingItem item = settingsList.get(position);
        holder.settingName.setText(item.getName());
        holder.settingSwitch.setChecked(item.isEnabled());

        holder.settingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 处理设置项开关变化
            //这里是设置界面的逻辑而非功能
            // 例如：保存配置到SharedPreferences
        });
    }

    @Override
    public int getItemCount() {
        return settingsList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView settingName;
        MaterialSwitch settingSwitch;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            settingName = itemView.findViewById(R.id.text_setting_name);
            settingSwitch = itemView.findViewById(R.id.switch_setting);
        }
    }

    // 数据模型类
    public static class SettingItem {
        private String name;
        private boolean enabled;

        public SettingItem(String name, boolean enabled) {
            this.name = name;
            this.enabled = enabled;
        }

        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
