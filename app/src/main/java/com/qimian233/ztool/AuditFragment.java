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
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.qimian233.ztool.audit.LogParser;
import com.qimian233.ztool.audit.LogParser.LogEntry;
import com.qimian233.ztool.audit.LogParser.LogLevel;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 日志审计界面 - 无阴影扁平版 + IME(insets)修复 + 模块显示名修复
 */
public class AuditFragment extends Fragment {

    private static final String TAG = "AuditFragment";

    // Root
    private View rootView;

    // UI组件
    private RecyclerView recyclerView;
    private LogAdapter logAdapter;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private View layoutEmpty;

    // Filters (ExposedDropdownMenu)
    private MaterialAutoCompleteTextView spinnerCategory;
    private MaterialAutoCompleteTextView spinnerModule;
    private MaterialAutoCompleteTextView spinnerLevel;
    private TextInputEditText etSearch;

    private TextView tvStats;
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
    private Map<String, List<String>> modulesByCategory = Collections.emptyMap();

    // Adapter（禁用过滤，避免下拉为空）
    private NoFilterArrayAdapter<String> categoryAdapter;
    private NoFilterArrayAdapter<ModuleOption> moduleAdapter;
    private NoFilterArrayAdapter<String> levelAdapter;

    // 当前选择（模块用 key 存，不用显示文本）
    @Nullable
    private String selectedModuleKey = null;

    // 处理 IME 空白：在本 Fragment 存在期间禁用 adjustResize
    private int oldSoftInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED;

    // Insets 基础值
    private int baseRecyclerPaddingBottom = 0;
    private int baseFabMarginBottom = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_audit, container, false);
        initViews(rootView);
        setupRecyclerView();
        setupFilters();
        setupScrollBehavior();
        setupWindowInsetsFix();
        loadAllLogFiles();
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 避免：键盘隐藏后窗口尺寸不恢复，留下空白（ROM bug）
        Window w = requireActivity().getWindow();
        oldSoftInputMode = w.getAttributes().softInputMode;
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
    }

    @Override
    public void onPause() {
        super.onPause();
        // 恢复 Activity 原来的 softInputMode，避免影响别的页面
        Window w = requireActivity().getWindow();
        w.setSoftInputMode(oldSoftInputMode);
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

        com.google.android.material.button.MaterialButton btnRefresh = view.findViewById(R.id.btn_refresh);
        com.google.android.material.button.MaterialButton btnClear = view.findViewById(R.id.btn_clear);
        com.google.android.material.button.MaterialButton btnStats = view.findViewById(R.id.btn_stats);

        tvStats = view.findViewById(R.id.tv_stats);
        cbShowErrors = view.findViewById(R.id.cb_show_errors);
        fabScrollToTop = view.findViewById(R.id.fab_scroll_to_top);
        appBarLayout = view.findViewById(R.id.app_bar);

        btnRefresh.setOnClickListener(v -> refreshLogs());
        btnClear.setOnClickListener(v -> showClearLogsDialog());
        btnStats.setOnClickListener(v -> showStatistics());

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                applyFilters();
            }
        });

        cbShowErrors.setOnCheckedChangeListener((buttonView, isChecked) -> applyFilters());
        fabScrollToTop.setOnClickListener(v -> scrollToTop());
    }

    /**
     * 解决“键盘隐藏后底部空白” + 让列表/FAB 自动避开键盘覆盖
     */
    private void setupWindowInsetsFix() {
        baseRecyclerPaddingBottom = recyclerView.getPaddingBottom();

        ViewGroup.LayoutParams lp = fabScrollToTop.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            baseFabMarginBottom = ((ViewGroup.MarginLayoutParams) lp).bottomMargin;
        } else {
            baseFabMarginBottom = 0;
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            int bottom = Math.max(sys.bottom, ime.bottom);

            recyclerView.setPadding(
                    recyclerView.getPaddingLeft(),
                    recyclerView.getPaddingTop(),
                    recyclerView.getPaddingRight(),
                    baseRecyclerPaddingBottom + bottom
            );

            ViewGroup.LayoutParams p = fabScrollToTop.getLayoutParams();
            if (p instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) p;
                mlp.bottomMargin = baseFabMarginBottom + bottom;
                fabScrollToTop.setLayoutParams(mlp);
            }

            return insets;
        });
        ViewCompat.requestApplyInsets(rootView);
    }

    private void setupScrollBehavior() {
        appBarLayout.addOnOffsetChangedListener((appBar, verticalOffset) -> {
            if (Math.abs(verticalOffset) >= appBar.getTotalScrollRange()) {
                fabScrollToTop.show();
            } else {
                fabScrollToTop.hide();
            }
        });
        fabScrollToTop.hide();
    }

    private void scrollToTop() {
        appBarLayout.setExpanded(true, true);
        recyclerView.smoothScrollToPosition(0);
    }

    private void setupRecyclerView() {
        logAdapter = new LogAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(logAdapter);
        logAdapter.setOnItemClickListener(this::showLogDetails);
    }

    private void setupFilters() {
        modulesByCategory = LogParser.getModulesByCategory();
        if (modulesByCategory == null) modulesByCategory = Collections.emptyMap();

        // --- Category ---
        List<String> categories = new ArrayList<>();
        categories.add(getString(R.string.all_categories));

        List<String> keys = new ArrayList<>(modulesByCategory.keySet());
        Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);
        categories.addAll(keys);

        categoryAdapter = new NoFilterArrayAdapter<>(
                requireContext(),
                com.google.android.material.R.layout.mtrl_auto_complete_simple_item,
                categories
        );
        spinnerCategory.setAdapter(categoryAdapter);
        spinnerCategory.setThreshold(0);
        spinnerCategory.setShowSoftInputOnFocus(false);
        spinnerCategory.setText(categories.get(0), false);

        spinnerCategory.setOnClickListener(v -> {
            hideImeAndFixInsets();
            spinnerCategory.showDropDown();
        });
        spinnerCategory.setOnItemClickListener((parent, view, position, id) -> {
            updateModuleDropdown();
            applyFilters();
        });

        // --- Level ---
        List<String> levels = Arrays.asList(
                getString(R.string.all_levels),
                "DEBUG", "INFO", "WARN", "ERROR"
        );
        levelAdapter = new NoFilterArrayAdapter<>(
                requireContext(),
                com.google.android.material.R.layout.mtrl_auto_complete_simple_item,
                levels
        );
        spinnerLevel.setAdapter(levelAdapter);
        spinnerLevel.setThreshold(0);
        spinnerLevel.setShowSoftInputOnFocus(false);
        spinnerLevel.setText(levels.get(0), false);

        spinnerLevel.setOnClickListener(v -> {
            hideImeAndFixInsets();
            spinnerLevel.showDropDown();
        });
        spinnerLevel.setOnItemClickListener((parent, view, position, id) -> applyFilters());

        // --- Module (depends on Category) ---
        spinnerModule.setThreshold(0);
        spinnerModule.setShowSoftInputOnFocus(false);
        spinnerModule.setOnClickListener(v -> {
            hideImeAndFixInsets();
            spinnerModule.showDropDown();
        });
        spinnerModule.setOnItemClickListener((parent, view, position, id) -> {
            ModuleOption opt = (ModuleOption) parent.getItemAtPosition(position);
            selectedModuleKey = opt.key; // null = all
            applyFilters();
        });

        updateModuleDropdown();
    }

    private void updateModuleDropdown() {
        List<String> moduleKeys = new ArrayList<>();

        String selectedCategory = getDropdownText(spinnerCategory);
        if (selectedCategory != null
                && !selectedCategory.equals(getString(R.string.all_categories))
                && modulesByCategory.containsKey(selectedCategory)) {
            List<String> list = modulesByCategory.get(selectedCategory);
            if (list != null) moduleKeys.addAll(list);
        } else {
            List<String> all = LogParser.getAvailableModules();
            if (all != null) moduleKeys.addAll(all);
        }

        // 组装显示项：显示名来自 LogParser.getModuleDisplayName（你的数据源）
        List<ModuleOption> options = new ArrayList<>();
        options.add(new ModuleOption(null, getString(R.string.all_modules)));
        for (String key : moduleKeys) {
            String display = LogParser.getModuleDisplayName(key);
            if (display == null || display.trim().isEmpty()) display = key;
            options.add(new ModuleOption(key, display));
        }

        if (moduleAdapter == null) {
            moduleAdapter = new NoFilterArrayAdapter<>(
                    requireContext(),
                    com.google.android.material.R.layout.mtrl_auto_complete_simple_item,
                    options
            );
            spinnerModule.setAdapter(moduleAdapter);
        } else {
            moduleAdapter.replaceAll(options);
        }

        // category 变化时模块默认回到“全部”
        selectedModuleKey = null;
        spinnerModule.setText(options.get(0).label, false);
    }

    private void hideImeAndFixInsets() {
        // 清掉搜索焦点，避免 ROM 抽风导致 IME/insets 状态卡住
        etSearch.clearFocus();

        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(rootView);
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.ime());
        }

        // 强制触发 insets 重新分发（非常关键）
        ViewCompat.requestApplyInsets(rootView);
    }

    @Nullable
    private static String getDropdownText(@NonNull MaterialAutoCompleteTextView v) {
        CharSequence cs = v.getText();
        if (cs == null) return null;
        String s = cs.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private void loadAllLogFiles() {
        showLoading(true);

        new Thread(() -> {
            try {
                logDir = new File(requireContext().getFilesDir(), "Log");

                if (!logDir.exists() || !logDir.isDirectory()) {
                    mainHandler.post(() -> {
                        showEmptyState(getString(R.string.log_directory_not_exists));
                        showLoading(false);
                    });
                    return;
                }

                allLogEntries = LogParser.parseAllLogFiles(logDir);

                if (allLogEntries.isEmpty()) {
                    mainHandler.post(() -> {
                        showEmptyState(getString(R.string.no_log_records_found));
                        showLoading(false);
                    });
                    return;
                }

                final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
                allLogEntries.sort((e1, e2) -> {
                    try {
                        Date date1 = sdf.parse(e1.timestamp);
                        Date date2 = sdf.parse(e2.timestamp);
                        if (date1 == null || date2 == null) return 0;
                        return date2.compareTo(date1);
                    } catch (Exception e) {
                        return String.valueOf(e2.timestamp).compareTo(String.valueOf(e1.timestamp));
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
        if (allLogEntries.isEmpty()) return;

        String selectedCategory = getDropdownText(spinnerCategory);
        if (selectedCategory != null && selectedCategory.equals(getString(R.string.all_categories))) {
            selectedCategory = null;
        }

        LogLevel selectedLevel = LogLevel.UNKNOWN;
        String levelText = getDropdownText(spinnerLevel);
        if (levelText != null && !levelText.equals(getString(R.string.all_levels))) {
            try {
                selectedLevel = LogLevel.valueOf(levelText);
            } catch (IllegalArgumentException ignored) {}
        }

        String searchText = etSearch.getText() != null ? etSearch.getText().toString().trim() : null;
        if (searchText != null && searchText.isEmpty()) searchText = null;

        List<LogEntry> tempFiltered = LogParser.filterEntries(
                allLogEntries,
                selectedModuleKey,     // 这里传 module key（不是显示名）
                selectedLevel,
                searchText,
                selectedCategory
        );

        filteredLogEntries = new ArrayList<>();
        boolean onlyErrors = cbShowErrors.isChecked();
        for (LogEntry entry : tempFiltered) {
            boolean include = !onlyErrors || "true".equals(entry.extractedData.get("is_error"));
            if (include) filteredLogEntries.add(entry);
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

        String stats = getString(
                R.string.stats_format,
                allLogEntries.size(),
                filteredLogEntries.size(),
                totalModules,
                getLogFileCount()
        );
        tvStats.setText(stats);
    }

    private String getLogFileCount() {
        if (logDir == null || !logDir.exists()) return "0";
        File[] logFiles = logDir.listFiles((dir, name) -> name.startsWith("hook_log_") && name.endsWith(".txt"));
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
                            //noinspection ResultOfMethodCallIgnored
                            file.delete();
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
                    Toast.makeText(requireContext(), R.string.clear_logs_success, Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                android.util.Log.e(TAG, "清除日志失败", e);
                mainHandler.post(() -> {
                    showLoading(false);
                    Toast.makeText(requireContext(),
                            getString(R.string.clear_logs_failed) + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
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

        details.append(getString(R.string.full_message_header)).append("\n");
        details.append(entry.getFullMessage()).append("\n\n");

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
     * 禁用 AutoComplete 默认过滤（否则 setText 后会按文本过滤导致列表为空）
     */
    private static class NoFilterArrayAdapter<T> extends ArrayAdapter<T> {
        private final List<T> allItems;

        public NoFilterArrayAdapter(@NonNull Context context, int resource, @NonNull List<T> items) {
            super(context, resource, new ArrayList<>(items));
            this.allItems = new ArrayList<>(items);
        }

        public void replaceAll(@NonNull List<T> items) {
            allItems.clear();
            allItems.addAll(items);
            clear();
            addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = allItems;
                    results.count = allItems.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    // 数据不变，强制刷新即可
                    notifyDataSetChanged();
                }
            };
        }
    }

    private static final class ModuleOption {
        @Nullable final String key;   // 真正用于过滤的 key
        @NonNull final String label;  // 显示名（来自 LogParser 数据源）

        ModuleOption(@Nullable String key, @NonNull String label) {
            this.key = key;
            this.label = label;
        }

        @NonNull
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * 日志适配器
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
            private final TextView tvTime;
            private final TextView tvModule;
            private final TextView tvLevel;
            private final TextView tvMessage;
            private final TextView tvDetails;
            private final View levelIndicator;
            private final ImageView ivStatus;
            private final TextView ivMultiLine;

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
                if (entry.timestamp != null && entry.timestamp.length() >= 12) {
                    tvTime.setText(entry.timestamp.substring(11));
                } else {
                    tvTime.setText("--:--:--");
                }

                // 模块显示：用你的数据源映射（不显示英文 key）
                if (entry.module != null) {
                    String displayModule = LogParser.getModuleDisplayName(entry.module);
                    if (displayModule == null || displayModule.trim().isEmpty()) {
                        displayModule = entry.module;
                    }
                    tvModule.setText(displayModule);
                    tvModule.setVisibility(View.VISIBLE);
                } else {
                    tvModule.setVisibility(View.GONE);
                }

                // 级别显示
                tvLevel.setText(entry.level != null ? entry.level : "?");

                // 消息显示
                String message = entry.getFullMessage();
                if (entry.isMultiLine) {
                    String[] lines = message.split("\n");
                    if (lines.length > 0) {
                        message = lines[0];
                        if (message.length() > 100) message = message.substring(0, 100) + "...";
                        message += " ... [" + (lines.length - 1) + getString(R.string.more_lines_suffix) + "]";
                    }
                } else {
                    if (message.length() > 100) message = message.substring(0, 100) + "...";
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

                setLevelIndicatorColor(entry.logLevel);
                setStatusIcon(entry);
                ivMultiLine.setVisibility(entry.isMultiLine ? View.VISIBLE : View.GONE);
            }

            private void setLevelIndicatorColor(LogLevel level) {
                int color;
                switch (level) {
                    case DEBUG:
                        color = 0xFF2196F3;
                        break;
                    case INFO:
                        color = 0xFF4CAF50;
                        break;
                    case WARN:
                        color = 0xFFFFC107;
                        break;
                    case ERROR:
                        color = 0xFFF44336;
                        break;
                    default:
                        color = 0xFF9E9E9E;
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
