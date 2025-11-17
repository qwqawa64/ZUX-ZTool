package com.qimian233.ztool;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FeaturesAdapter extends RecyclerView.Adapter<FeaturesAdapter.ViewHolder> {

    private List<AppItem> appList;
    private Context context;

    public FeaturesAdapter(List<AppItem> appList, Context context) {
        this.appList = appList;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feature, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppItem item = appList.get(position);
        holder.appName.setText(item.getName());
        holder.appDesc.setText(item.getDescription());

        // 设置动态获取的图标
        Drawable icon = item.getIconDrawable();
        if (icon != null) {
            holder.appIcon.setImageDrawable(icon);
        }

        // 点击整个item进入详细设置
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, item.getTargetActivity());
            intent.putExtra("app_name", item.getName());
            intent.putExtra("app_package", item.getPackageName());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName, appDesc;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.image_app_icon);
            appName = itemView.findViewById(R.id.text_app_name);
            appDesc = itemView.findViewById(R.id.text_app_desc);
        }
    }

    // 数据模型类
    public static class AppItem {
        private final String name;
        private final String description;
        private final String packageName;
        private final Drawable iconDrawable; // 改为Drawable类型
        private boolean enabled;
        private final Class<?> targetActivity;

        public AppItem(String name, String description, String packageName,
                       Drawable iconDrawable, boolean enabled, Class<?> targetActivity) {
            this.name = name;
            this.description = description;
            this.packageName = packageName;
            this.iconDrawable = iconDrawable;
            this.enabled = enabled;
            this.targetActivity = targetActivity;
        }

        // Getter和Setter方法
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getPackageName() { return packageName; }
        public Drawable getIconDrawable() { return iconDrawable; } // 返回Drawable
        public boolean isEnabled() { return enabled; }
        public Class<?> getTargetActivity() { return targetActivity; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
