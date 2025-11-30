package com.qimian233.ztool.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.qimian233.ztool.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppChooserDialog {

    public interface AppSelectionCallback {
        void onSelected(List<AppInfo> selectedApps);
        void onCancel();
    }

    public static class AppInfo {
        private String packageName;
        private String appName;
        private Drawable appIcon;
        private boolean isSelected;

        public AppInfo(String packageName, String appName, Drawable appIcon) {
            this.packageName = packageName;
            this.appName = appName;
            this.appIcon = appIcon;
            this.isSelected = false;
        }

        public String getPackageName() { return packageName; }
        public String getAppName() { return appName; }
        public Drawable getAppIcon() { return appIcon; }
        public boolean isSelected() { return isSelected; }
        public void setSelected(boolean selected) { isSelected = selected; }
    }

    private static class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {
        private List<AppInfo> appList;
        private List<AppInfo> filteredList;
        private Set<String> selectedPackages;

        public AppAdapter(List<AppInfo> appList) {
            this.appList = appList;
            this.filteredList = new ArrayList<>(appList);
            this.selectedPackages = new HashSet<>();
            // 初始化 selectedPackages 基于 appList 中的选中状态
            for (AppInfo app : appList) {
                if (app.isSelected()) {
                    selectedPackages.add(app.getPackageName());
                }
            }
        }

        @NonNull
        @Override
        public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_choice, parent, false);
            return new AppViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
            AppInfo appInfo = filteredList.get(position);
            holder.bind(appInfo);
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        public void filter(String query) {
            filteredList.clear();
            if (query == null || query.isEmpty()) {
                filteredList.addAll(appList);
            } else {
                String lowerCaseQuery = query.toLowerCase();
                for (AppInfo app : appList) {
                    if (app.getAppName().toLowerCase().contains(lowerCaseQuery) ||
                            app.getPackageName().toLowerCase().contains(lowerCaseQuery)) {
                        filteredList.add(app);
                    }
                }
            }
            notifyDataSetChanged();
        }

        public List<AppInfo> getSelectedApps() {
            List<AppInfo> selected = new ArrayList<>();
            for (AppInfo app : appList) {
                if (app.isSelected()) {
                    selected.add(app);
                }
            }
            return selected;
        }

        class AppViewHolder extends RecyclerView.ViewHolder {
            private ImageView appIcon;
            private TextView appName;
            private TextView packageName;
            private CheckBox checkBox;

            public AppViewHolder(@NonNull View itemView) {
                super(itemView);
                appIcon = itemView.findViewById(R.id.app_icon);
                appName = itemView.findViewById(R.id.app_name);
                packageName = itemView.findViewById(R.id.package_name);
                checkBox = itemView.findViewById(R.id.checkbox);
            }

            public void bind(AppInfo appInfo) {
                appIcon.setImageDrawable(appInfo.getAppIcon());
                appName.setText(appInfo.getAppName());
                packageName.setText(appInfo.getPackageName());
                checkBox.setChecked(appInfo.isSelected());

                itemView.setOnClickListener(v -> {
                    boolean newState = !appInfo.isSelected();
                    appInfo.setSelected(newState);
                    checkBox.setChecked(newState);

                    if (newState) {
                        selectedPackages.add(appInfo.getPackageName());
                    } else {
                        selectedPackages.remove(appInfo.getPackageName());
                    }
                });

                checkBox.setOnClickListener(v -> {
                    boolean newState = checkBox.isChecked();
                    appInfo.setSelected(newState);

                    if (newState) {
                        selectedPackages.add(appInfo.getPackageName());
                    } else {
                        selectedPackages.remove(appInfo.getPackageName());
                    }
                });
            }
        }
    }

    // 原有方法：不传入已勾选包名，保持兼容
    public static void show(Context context,
                            List<String> packageNames,
                            AppSelectionCallback callback) {
        show(context, packageNames, null, null, callback);
    }

    // 原有方法：传入标题，不传入已勾选包名
    public static void show(Context context,
                            List<String> packageNames,
                            String title,
                            AppSelectionCallback callback) {
        show(context, packageNames, null, title, callback);
    }

    // 新方法：传入已勾选包名列表
    public static void show(Context context,
                            List<String> packageNames,
                            List<String> selectedPackageNames,
                            AppSelectionCallback callback) {
        show(context, packageNames, selectedPackageNames, null, callback);
    }

    // 新方法：传入已勾选包名列表和标题
    public static void show(Context context,
                            List<String> packageNames,
                            List<String> selectedPackageNames,
                            String title,
                            AppSelectionCallback callback) {

        // 创建加载中对话框
        AlertDialog loadingDialog = new MaterialAlertDialogBuilder(context)
                .setTitle("加载中...")
                .setMessage("正在获取应用信息")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            List<AppInfo> appInfoList = new ArrayList<>();
            PackageManager pm = context.getPackageManager();

            for (String packageName : packageNames) {
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    String appName = pm.getApplicationLabel(appInfo).toString();
                    Drawable appIcon = pm.getApplicationIcon(appInfo);

                    AppInfo app = new AppInfo(packageName, appName, appIcon);
                    // 根据 selectedPackageNames 设置选中状态
                    if (selectedPackageNames != null && selectedPackageNames.contains(packageName)) {
                        app.setSelected(true);
                    }
                    appInfoList.add(app);
                } catch (PackageManager.NameNotFoundException e) {
                    // 包名不存在，跳过
                }
            }

            handler.post(() -> {
                loadingDialog.dismiss();
                showAppSelectionDialog(context, appInfoList, title, callback);
            });
        });
    }

    private static void showAppSelectionDialog(Context context,
                                               List<AppInfo> appInfoList,
                                               String title,
                                               AppSelectionCallback callback) {

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_app_chooser, null);

        RecyclerView recyclerView = dialogView.findViewById(R.id.recycler_view);
        EditText searchEditText = dialogView.findViewById(R.id.search_edit_text);

        // 动态设置 RecyclerView 高度
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int screenHeight = displayMetrics.heightPixels;
        int dialogMaxHeight = (int) (screenHeight * 0.6); // 屏幕高度的60%

        ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
        params.height = dialogMaxHeight;
        recyclerView.setLayoutParams(params);

        AppAdapter adapter = new AppAdapter(appInfoList);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);

        // 其余代码保持不变...
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setPositiveButton(context.getString(R.string.restart_yes), (dialog, which) -> {
                    if (callback != null) {
                        callback.onSelected(adapter.getSelectedApps());
                    }
                })
                .setNegativeButton(context.getString(R.string.restart_no), (dialog, which) -> {
                    if (callback != null) {
                        callback.onCancel();
                    }
                })
                .setOnCancelListener(dialog -> {
                    if (callback != null) {
                        callback.onCancel();
                    }
                });

        if (title != null) {
            builder.setTitle(title);
        }

        AlertDialog dialog = builder.create();

        // 设置对话框窗口属性，确保在横屏时也能正确显示
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        dialog.show();
    }
}
