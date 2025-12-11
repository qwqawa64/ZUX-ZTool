// PackageAdapter.java
package com.qimian233.ztool.settingactivity.setting.magicwindowsearch;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qimian233.ztool.R;

import java.util.List;

public class PackageAdapter extends RecyclerView.Adapter<PackageAdapter.ViewHolder> {
    private final List<PackageInfo> packageList;
    private final OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(PackageInfo packageInfo);
    }

    public PackageAdapter(List<PackageInfo> packageList, OnItemClickListener listener) {
        this.packageList = packageList;
        this.onItemClickListener = listener;
    }

    // 修改PackageAdapter的onCreateViewHolder方法
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.package_item, parent, false);
        return new ViewHolder(view);
    }

    // 修改ViewHolder类
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView packageName;
        public TextView mainActivity;

        public ViewHolder(View view) {
            super(view);
            packageName = view.findViewById(R.id.package_name);
            mainActivity = view.findViewById(R.id.main_activity);
        }
    }

    // 修改onBindViewHolder方法
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PackageInfo packageInfo = packageList.get(position);
        holder.packageName.setText(packageInfo.getName());
        holder.mainActivity.setText(packageInfo.getMainPage());

        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(packageInfo);
            }
        });
    }


    @Override
    public int getItemCount() {
        return packageList.size();
    }
}
