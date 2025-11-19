// AuditFragment.java
package com.qimian233.ztool;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.qimian233.ztool.R;
import com.qimian233.ztool.audit.LogParser;
import com.qimian233.ztool.audit.LogParser.LogEntry;
import com.qimian233.ztool.audit.LogParser.LogLevel;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 日志审计界面 - 增强版支持完整模块策略和多文件读取
 */
public class AuditFragment extends Fragment {

    private static final String TAG = "AuditFragment";

    // UI组件
    private RecyclerView recyclerView;
    private LogAdapter logAdapter;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private View layoutEmpty;
    private Spinner spinnerCategory;
    private Spinner spinnerModule;
    private Spinner spinnerLevel;
    private com.google.android.material.textfield.TextInputEditText etSearch;
    private com.google.android.material.button.MaterialButton btnRefresh;
    private com.google.android.material.button.MaterialButton btnClear;
    private com.google.android.material.button.MaterialButton btnStats;
    private TextView tvStats;
    private LinearLayout layoutAdvancedFilters;
    private com.google.android.material.checkbox.MaterialCheckBox cbShowErrors;
    private ExtendedFloatingActionButton fabScrollToTop;
    private AppBarLayout appBarLayout;

    // 数据
    private List<LogEntry> allLogEntries = new ArrayList<>();
    private List<LogEntry> filteredLogEntries = new ArrayList<>();

    // 日志目录
    private File logDir;

    // 处理UI更新
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 模块分类数据
    private Map<String, List<String>> modulesByCategory;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_audit, container, false);
        initViews(view);
        setupRecyclerView();
        setupFilters();
        setupScrollBehavior();
        loadAllLogFiles();
        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_view_logs);
        progressBar = view.findViewById(R.id.progress_bar);
        tvEmpty = view.findViewById(R.id.tv_empty);
        layoutEmpty = view.findViewById(R.id.layout_empty);
        spinnerCategory = view.findViewById(R.id.spinner_category);
        spinnerModule = view.findViewById(R.id.spinner_module);
        spinnerLevel = view.findViewById(R.id.spinner_level);
        etSearch = view.findViewById(R.id.et_search);
        btnRefresh = view.findViewById(R.id.btn_refresh);
        btnClear = view.findViewById(R.id.btn_clear);
        btnStats = view.findViewById(R.id.btn_stats);
        tvStats = view.findViewById(R.id.tv_stats);
        layoutAdvancedFilters = view.findViewById(R.id.layout_advanced_filters);
        cbShowErrors = view.findViewById(R.id.cb_show_errors);
        fabScrollToTop = view.findViewById(R.id.fab_scroll_to_top);
        appBarLayout = view.findViewById(R.id.app_bar);

        // 设置刷新按钮点击事件
        btnRefresh.setOnClickListener(v -> refreshLogs());

        // 设置清除按钮点击事件
        btnClear.setOnClickListener(v -> showClearLogsDialog());

        // 设置统计按钮点击事件
        btnStats.setOnClickListener(v -> showStatistics());

        // 设置搜索框文本变化监听
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                applyFilters();
            }
        });

        // 设置高级筛选选项监听
        cbShowErrors.setOnCheckedChangeListener((buttonView, isChecked) -> applyFilters());

        // 设置返回顶部按钮点击事件
        fabScrollToTop.setOnClickListener(v -> scrollToTop());
    }

    private void setupScrollBehavior() {
        // 监听滚动状态，控制返回顶部按钮的显示/隐藏
        appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                // 当AppBar完全折叠时显示返回顶部按钮
                if (Math.abs(verticalOffset) >= appBarLayout.getTotalScrollRange()) {
                    fabScrollToTop.show();
                } else {
                    fabScrollToTop.hide();
                }
            }
        });

        // 初始隐藏返回顶部按钮
        fabScrollToTop.hide();
    }

    private void scrollToTop() {
        // 展开AppBarLayout
        appBarLayout.setExpanded(true, true);

        // 滚动RecyclerView到顶部
        if (recyclerView.getLayoutManager() != null) {
            recyclerView.getLayoutManager().scrollToPosition(0);
        }
    }

    private void setupRecyclerView() {
        logAdapter = new LogAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(logAdapter);

        // 添加点击事件支持查看详情
        logAdapter.setOnItemClickListener(entry -> showLogDetails(entry));
    }

    private void setupFilters() {
        // 获取模块分类数据
        modulesByCategory = LogParser.getModulesByCategory();
        // 类别过滤器
        List<String> categories = new ArrayList<>();
        categories.add(getString(R.string.all_categories));
        categories.addAll(modulesByCategory.keySet());
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, categories);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);
        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateModuleSpinner();
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        // 初始化模块过滤器
        updateModuleSpinner();
        // 级别过滤器
        List<String> levels = Arrays.asList(getString(R.string.all_levels), "INFO", "ERROR");
        ArrayAdapter<String> levelAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, levels);
        levelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLevel.setAdapter(levelAdapter);
        spinnerLevel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateModuleSpinner() {
        List<String> modules = new ArrayList<>();
        modules.add(getString(R.string.all_modules));
        String selectedCategory = (String) spinnerCategory.getSelectedItem();
        if (selectedCategory != null && !selectedCategory.equals(getString(R.string.all_categories)) && modulesByCategory.containsKey(selectedCategory)) {
            modules.addAll(modulesByCategory.get(selectedCategory));
        } else {
            // 显示所有模块
            modules.addAll(LogParser.getAvailableModules());
        }
        ArrayAdapter<String> moduleAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, modules);
        moduleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModule.setAdapter(moduleAdapter);
        spinnerModule.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadAllLogFiles() {
        showLoading(true);

        new Thread(() -> {
            try {
                // 获取日志目录
                logDir = new File(requireContext().getFilesDir(), "Log");

                if (!logDir.exists() || !logDir.isDirectory()) {
                    mainHandler.post(() -> {
                        showEmptyState(getString(R.string.log_directory_not_exists));
                        showLoading(false);
                    });
                    return;
                }

                // 解析所有日志文件
                allLogEntries = LogParser.parseAllLogFiles(logDir);

                if (allLogEntries.isEmpty()) {
                    mainHandler.post(() -> {
                        showEmptyState(getString(R.string.no_log_records_found));
                        showLoading(false);
                    });
                    return;
                }

                // 按时间倒序排列（最新的在前）
                Collections.sort(allLogEntries, (e1, e2) -> {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                        Date date1 = sdf.parse(e1.timestamp);
                        Date date2 = sdf.parse(e2.timestamp);
                        return date2.compareTo(date1); // 降序
                    } catch (Exception e) {
                        return e2.timestamp.compareTo(e1.timestamp);
                    }
                });

                mainHandler.post(() -> {
                    applyFilters();
                    updateStats();
                    showLoading(false);
                });

            } catch (Exception e) {
                android.util.Log.e(TAG, "加载日志文件失败", e);
                mainHandler.post(() -> {
                    showEmptyState(getString(R.string.load_logs_failed) + e.getMessage());
                    showLoading(false);
                });
            }
        }).start();
    }

    private void applyFilters() {
        if (allLogEntries.isEmpty()) {
            return;
        }
        String selectedCategory = spinnerCategory.getSelectedItemPosition() == 0 ?
                null : (String) spinnerCategory.getSelectedItem();
        String selectedModule = spinnerModule.getSelectedItemPosition() == 0 ?
                null : (String) spinnerModule.getSelectedItem();
        LogLevel selectedLevel = LogLevel.UNKNOWN;
        if (spinnerLevel.getSelectedItemPosition() > 0) {
            String levelStr = (String) spinnerLevel.getSelectedItem();
            try {
                selectedLevel = LogLevel.valueOf(levelStr);
            } catch (IllegalArgumentException e) {
                selectedLevel = LogLevel.UNKNOWN;
            }
        }
        String searchText = etSearch.getText().toString().trim();
        if (searchText.isEmpty()) {
            searchText = null;
        }

        // 应用高级筛选
        filteredLogEntries = new ArrayList<>();
        List<LogEntry> tempFiltered = LogParser.filterEntries(
                allLogEntries, selectedModule, selectedLevel, searchText, selectedCategory);

        // 应用错误/成功筛选
        for (LogEntry entry : tempFiltered) {
            boolean include = true;

            if (cbShowErrors.isChecked() && !"true".equals(entry.extractedData.get("is_error"))) {
                include = false;
            }

            if (include) {
                filteredLogEntries.add(entry);
            }
        }

        logAdapter.setLogEntries(filteredLogEntries);
        updateStats();

        if (filteredLogEntries.isEmpty()) {
            showEmptyState(getString(R.string.no_matching_log_records));
        } else {
            hideEmptyState();
        }
    }

    private void updateStats() {
        Map<String, Integer> moduleStats = LogParser.getModuleStats(allLogEntries);
        int totalModules = moduleStats.size();

        String stats = getString(R.string.stats_format,
                allLogEntries.size(), filteredLogEntries.size(), totalModules, getLogFileCount());
        tvStats.setText(stats);
    }

    private String getLogFileCount() {
        if (logDir == null || !logDir.exists()) {
            return "0";
        }
        File[] logFiles = logDir.listFiles((dir, name) ->
                name.startsWith("hook_log_") && name.endsWith(".txt"));
        return logFiles != null ? String.valueOf(logFiles.length) : "0";
    }

    private void refreshLogs() {
        loadAllLogFiles();
    }

    private void showClearLogsDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_logs_title)
                .setMessage(R.string.clear_logs_message)
                .setPositiveButton(R.string.clear_button, (dialog, which) -> clearAllLogs())
                .setNegativeButton(R.string.restart_no, null)
                .show();
    }

    private void clearAllLogs() {
        showLoading(true);

        new Thread(() -> {
            try {
                if (logDir != null && logDir.exists()) {
                    File[] logFiles = logDir.listFiles((dir, name) ->
                            name.startsWith("hook_log_") && name.endsWith(".txt"));

                    if (logFiles != null) {
                        for (File file : logFiles) {
                            if (file.delete()) {
                                android.util.Log.d(TAG, "删除日志文件: " + file.getName());
                            }
                        }
                    }
                }

                mainHandler.post(() -> {
                    allLogEntries.clear();
                    filteredLogEntries.clear();
                    logAdapter.setLogEntries(filteredLogEntries);
                    updateStats();
                    showEmptyState(getString(R.string.logs_cleared_message));
                    showLoading(false);

                    // 显示清除成功提示
                    Toast.makeText(requireContext(), R.string.clear_logs_success, Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                android.util.Log.e(TAG, "清除日志失败", e);
                mainHandler.post(() -> {
                    showLoading(false);
                    Toast.makeText(requireContext(), getString(R.string.clear_logs_failed) + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showStatistics() {
        Map<String, Integer> moduleStats = LogParser.getModuleStats(allLogEntries);
        Map<String, Object> errorStats = LogParser.getErrorStats(allLogEntries);

        StringBuilder statsMessage = new StringBuilder();
        statsMessage.append(getString(R.string.log_statistics_header)).append("\n\n");
        statsMessage.append(getString(R.string.total_logs)).append(allLogEntries.size()).append("\n");
        statsMessage.append(getString(R.string.total_modules)).append(moduleStats.size()).append("\n");
        statsMessage.append(getString(R.string.total_errors)).append(errorStats.get("total_errors")).append("\n");
        statsMessage.append(getString(R.string.log_files_count)).append(getLogFileCount()).append(getString(R.string.log_files_unit)).append("\n\n");

        statsMessage.append(getString(R.string.module_statistics_header)).append("\n");
        for (Map.Entry<String, Integer> entry : moduleStats.entrySet()) {
            String moduleName = LogParser.getModuleDisplayName(entry.getKey());
            statsMessage.append(moduleName).append(": ").append(entry.getValue()).append(getString(R.string.log_count_unit)).append("\n");
        }

        // 显示统计对话框
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.log_statistics_title)
                .setMessage(statsMessage.toString())
                .setPositiveButton(R.string.restart_yes, null)
                .show();
    }

    private void showLogDetails(LogEntry entry) {
        StringBuilder details = new StringBuilder();
        details.append(getString(R.string.log_detail_time)).append(entry.timestamp).append("\n");
        details.append(getString(R.string.log_detail_module)).append(LogParser.getModuleDisplayName(entry.module)).append("\n");
        details.append(getString(R.string.log_detail_level)).append(entry.level).append("\n");
        details.append(getString(R.string.log_detail_tag)).append(entry.tag).append("\n");
        details.append(getString(R.string.log_detail_pid)).append(entry.pid).append("\n");
        details.append(getString(R.string.log_detail_mode)).append(entry.mode).append("\n");
        details.append(getString(R.string.log_detail_function)).append(entry.function != null ? entry.function : getString(R.string.none)).append("\n");
        details.append(getString(R.string.log_detail_multiline)).append(entry.isMultiLine ? getString(R.string.yes) : getString(R.string.no)).append("\n\n");

        // 显示完整消息（包含多行）
        details.append(getString(R.string.full_message_header)).append("\n");
        details.append(entry.getFullMessage()).append("\n\n");

        // 提取的数据
        if (!entry.extractedData.isEmpty()) {
            details.append(getString(R.string.extracted_data_header)).append("\n");
            for (Map.Entry<String, String> data : entry.extractedData.entrySet()) {
                details.append(data.getKey()).append(": ").append(data.getValue()).append("\n");
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.log_detail_title)
                .setMessage(details.toString())
                .setPositiveButton(R.string.copy_button, (dialog, which) -> copyToClipboard(details.toString()))
                .setNegativeButton(R.string.close_button, null)
                .show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(R.string.log_content), text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            recyclerView.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.GONE);
        }
    }

    private void showEmptyState(String message) {
        tvEmpty.setText(message);
        layoutEmpty.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }

    private void hideEmptyState() {
        layoutEmpty.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
    }

    /**
     * 增强的日志适配器 - 支持点击查看详情和多行显示
     */
    private class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

        private List<LogEntry> logEntries = new ArrayList<>();
        private OnItemClickListener onItemClickListener;

        public void setLogEntries(List<LogEntry> entries) {
            this.logEntries = entries != null ? entries : new ArrayList<>();
            notifyDataSetChanged();
        }

        public void setOnItemClickListener(OnItemClickListener listener) {
            this.onItemClickListener = listener;
        }

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_log_entry, parent, false);
            return new LogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            LogEntry entry = logEntries.get(position);
            holder.bind(entry);

            // 设置点击事件
            holder.itemView.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(entry);
                }
            });
        }

        @Override
        public int getItemCount() {
            return logEntries.size();
        }

        class LogViewHolder extends RecyclerView.ViewHolder {
            private TextView tvTime;
            private TextView tvModule;
            private TextView tvLevel;
            private TextView tvMessage;
            private TextView tvDetails;
            private View levelIndicator;
            private ImageView ivStatus;
            private TextView ivMultiLine;

            public LogViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTime = itemView.findViewById(R.id.tv_time);
                tvModule = itemView.findViewById(R.id.tv_module);
                tvLevel = itemView.findViewById(R.id.tv_level);
                tvMessage = itemView.findViewById(R.id.tv_message);
                tvDetails = itemView.findViewById(R.id.tv_details);
                levelIndicator = itemView.findViewById(R.id.level_indicator);
                ivStatus = itemView.findViewById(R.id.iv_status);
                ivMultiLine = itemView.findViewById(R.id.iv_multiline);
            }

            public void bind(LogEntry entry) {
                // 时间显示
                if (entry.timestamp != null) {
                    String displayTime = entry.timestamp.substring(11); // 只显示时间部分
                    tvTime.setText(displayTime);
                } else {
                    tvTime.setText("--:--:--");
                }

                // 模块显示
                if (entry.module != null) {
                    String displayModule = LogParser.getModuleDisplayName(entry.module);
                    tvModule.setText(displayModule);
                    tvModule.setVisibility(View.VISIBLE);
                } else {
                    tvModule.setVisibility(View.GONE);
                }

                // 级别显示
                String levelText = entry.level != null ? entry.level : "?";
                tvLevel.setText(levelText);

                // 消息显示 - 如果是多行日志，显示第一行并添加省略号
                String message = entry.getFullMessage();
                if (entry.isMultiLine) {
                    // 对于多行日志，只显示第一行
                    String[] lines = message.split("\n");
                    if (lines.length > 0) {
                        message = lines[0];
                        if (message.length() > 100) {
                            message = message.substring(0, 100) + "...";
                        }
                        message += " ... [" + (lines.length - 1) + getString(R.string.more_lines_suffix) + "]";
                    }
                } else {
                    // 单行日志正常截取
                    if (message.length() > 100) {
                        message = message.substring(0, 100) + "...";
                    }
                }
                tvMessage.setText(message);

                // 详细信息
                StringBuilder details = new StringBuilder();
                if (entry.tag != null && !entry.tag.equals("ZToolXposedModule")) {
                    details.append("Tag: ").append(entry.tag);
                }
                if (entry.pid != -1) {
                    if (details.length() > 0) details.append(" | ");
                    details.append("PID: ").append(entry.pid);
                }
                if (entry.mode != null) {
                    if (details.length() > 0) details.append(" | ");
                    details.append("Mode: ").append(entry.mode);
                }
                if (entry.function != null) {
                    if (details.length() > 0) details.append(" | ");
                    details.append("Function: ").append(entry.function);
                }

                if (details.length() > 0) {
                    tvDetails.setText(details.toString());
                    tvDetails.setVisibility(View.VISIBLE);
                } else {
                    tvDetails.setVisibility(View.GONE);
                }

                // 设置级别指示器颜色
                setLevelIndicatorColor(entry.logLevel);

                // 设置状态图标
                setStatusIcon(entry);

                // 设置多行图标
                if (entry.isMultiLine) {
                    ivMultiLine.setVisibility(View.VISIBLE);
                } else {
                    ivMultiLine.setVisibility(View.GONE);
                }
            }

            private void setLevelIndicatorColor(LogLevel level) {
                int color;
                switch (level) {
                    case VERBOSE:
                        color = 0xFF9E9E9E; // 灰色
                        break;
                    case DEBUG:
                        color = 0xFF2196F3; // 蓝色
                        break;
                    case INFO:
                        color = 0xFF4CAF50; // 绿色
                        break;
                    case WARN:
                        color = 0xFFFFC107; // 黄色
                        break;
                    case ERROR:
                        color = 0xFFF44336; // 红色
                        break;
                    default:
                        color = 0xFF9E9E9E; // 灰色
                }
                levelIndicator.setBackgroundColor(color);
            }

            private void setStatusIcon(LogEntry entry) {
                if ("true".equals(entry.extractedData.get("is_error"))) {
                    ivStatus.setImageResource(R.drawable.ic_error);
                    ivStatus.setVisibility(View.VISIBLE);
                } else if ("true".equals(entry.extractedData.get("is_success"))) {
                    ivStatus.setImageResource(R.drawable.ic_success);
                    ivStatus.setVisibility(View.VISIBLE);
                } else {
                    ivStatus.setVisibility(View.GONE);
                }
            }
        }
    }

    interface OnItemClickListener {
        void onItemClick(LogEntry entry);
    }
}
