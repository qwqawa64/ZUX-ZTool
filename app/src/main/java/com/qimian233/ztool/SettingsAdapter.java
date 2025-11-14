package com.qimian233.ztool;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

public class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.ViewHolder> {

    public static class SettingItem {
        private String title;
        private boolean isSwitch;
        private boolean switchState;
        private String description;

        public SettingItem(String title, boolean isSwitch) {
            this.title = title;
            this.isSwitch = isSwitch;
            this.switchState = false;
            this.description = "";
        }

        public SettingItem(String title, boolean isSwitch, boolean switchState) {
            this.title = title;
            this.isSwitch = isSwitch;
            this.switchState = switchState;
            this.description = "";
        }

        public SettingItem(String title, boolean isSwitch, boolean switchState, String description) {
            this.title = title;
            this.isSwitch = isSwitch;
            this.switchState = switchState;
            this.description = description;
        }

        // Getters and setters
        public String getTitle() { return title; }
        public boolean isSwitch() { return isSwitch; }
        public boolean getSwitchState() { return switchState; }
        public void setSwitchState(boolean state) { this.switchState = state; }
        public String getDescription() { return description; }
    }

    private List<SettingItem> settingsList;
    private OnSettingChangeListener listener;

    public interface OnSettingChangeListener {
        void onSwitchChanged(String settingTitle, boolean isChecked);
        void onItemClicked(String settingTitle);
    }

    public SettingsAdapter(List<SettingItem> settingsList) {
        this.settingsList = settingsList;
    }

    public void setOnSettingChangeListener(OnSettingChangeListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_setting, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SettingItem item = settingsList.get(position);
        holder.titleTextView.setText(item.getTitle());

        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            holder.descriptionTextView.setText(item.getDescription());
            holder.descriptionTextView.setVisibility(View.VISIBLE);
        } else {
            holder.descriptionTextView.setVisibility(View.GONE);
        }

        if (item.isSwitch()) {
            holder.settingSwitch.setVisibility(View.VISIBLE);
            holder.settingSwitch.setChecked(item.getSwitchState());
            holder.settingSwitch.setOnCheckedChangeListener(null); // 先清除监听器避免重复触发
            holder.settingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    item.setSwitchState(isChecked);
                    if (listener != null) {
                        listener.onSwitchChanged(item.getTitle(), isChecked);
                    }
                }
            });
            holder.itemView.setOnClickListener(null); // 禁用item点击，只使用switch
        } else {
            holder.settingSwitch.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onItemClicked(item.getTitle());
                    }
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return settingsList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView titleTextView;
        public TextView descriptionTextView;
        public MaterialSwitch settingSwitch;

        public ViewHolder(View view) {
            super(view);
            titleTextView = view.findViewById(R.id.setting_title);
            descriptionTextView = view.findViewById(R.id.setting_description);
            settingSwitch = view.findViewById(R.id.setting_switch);
        }
    }
}
