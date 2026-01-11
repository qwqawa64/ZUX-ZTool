package com.qimian233.ztool;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;
import com.qimian233.ztool.service.LogServiceManager;
import com.qimian233.ztool.utils.CountdownDialog;

public class MainActivity extends AppCompatActivity implements HomeFragment.EnvironmentStateListener,
        LogServiceManager.ServiceStatusListener, CountdownDialog.OnCountdownFinishListener {

    private BottomNavigationView bottomNav;
    private NavController navController;

    private boolean isEnvironmentReady = false;
    private long lastClickTime = 0;
    private static final long CLICK_INTERVAL = 300; // 限制点击间隔为300ms

    // 用于保存导航状态的键
    private static final String KEY_CURRENT_DESTINATION = "current_destination";
    private static final String KEY_ENVIRONMENT_READY = "environment_ready";
    private int currentDestinationId = R.id.homeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        // 恢复保存的状态
        if (savedInstanceState != null) {
            currentDestinationId = savedInstanceState.getInt(KEY_CURRENT_DESTINATION, R.id.homeFragment);
            isEnvironmentReady = savedInstanceState.getBoolean(KEY_ENVIRONMENT_READY, false);
        }

        // 设置状态栏和导航栏样式
        setupSystemBars();

        setContentView(R.layout.activity_main);

        // 设置服务状态监听器
        LogServiceManager.setServiceStatusListener(this);

        // 初始化底部导航栏
        bottomNav = findViewById(R.id.bottom_navigation);

        // 设置导航控制器
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        assert navHostFragment != null;
        navController = navHostFragment.getNavController();

        // 设置导航目的地变化监听器
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            // 更新当前目的地
            currentDestinationId = destination.getId();

            // 如果环境不齐全且尝试导航到非首页，则强制返回首页
            if (!isEnvironmentReady && destination.getId() != R.id.homeFragment) {
                navController.navigate(R.id.homeFragment);
            }
        });

        // 根据环境状态更新导航栏
        updateBottomNavigation(isEnvironmentReady);

        // 如果环境已就绪且存在保存的目的地，则导航到该目的地
        if (savedInstanceState == null && isEnvironmentReady && currentDestinationId != R.id.homeFragment) {
            navigateToSavedDestination();
        }

        SharedPreferences utils = getSharedPreferences("ZToolPrefs", Context.MODE_PRIVATE);
        if (utils.getBoolean("isFirstLaunch", true)) {
            CountdownDialog.Builder dialog = new CountdownDialog.Builder(navHostFragment.requireContext(), this);
            dialog.setTitle(getString(R.string.agreement_title));
            dialog.setMessage(getString(R.string.agreement_text));
            dialog.setCancelable(false);
            dialog.setCountdownSeconds(30);
            dialog.setNegativeText(getString(R.string.agreement_dismiss));
            dialog.setPositiveText(getString(R.string.agreement_confirm));
            dialog.setOnCountdownFinishListener(this);
            dialog.build().show();
        }

        // 应用启动时尝试重启日志服务
        LogServiceManager.restartServiceIfNeeded(this);
    }

    @Override
    public void onPositiveButtonClick() {
        SharedPreferences utils = getSharedPreferences("ZToolPrefs", Context.MODE_PRIVATE);
        utils.edit().putBoolean("isFirstLaunch", false).apply();
        android.widget.Toast.makeText(this,
                getString(R.string.user_confirm_agreement),
                android.widget.Toast.LENGTH_SHORT)
                .show();
    }

    @Override
    public void onNegativeButtonClick() {
        android.widget.Toast.makeText(this,
                        getString(R.string.user_dismiss_agreement),
                        android.widget.Toast.LENGTH_SHORT)
                .show();
        System.exit(0);
    }

    @Override
    public void onCountdownFinished() {}

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存当前目的地和环境状态
        outState.putInt(KEY_CURRENT_DESTINATION, currentDestinationId);
        outState.putBoolean(KEY_ENVIRONMENT_READY, isEnvironmentReady);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理监听器
        LogServiceManager.setServiceStatusListener(null);
    }

    /**
     * 服务状态监听器实现
     */
    @Override
    public void onServiceStarted() {
        runOnUiThread(() -> Toast.makeText(this,
                getString(R.string.log_service_started),
                Toast.LENGTH_SHORT).
                show());
    }

    @Override
    public void onServiceStopped() {
        runOnUiThread(() -> Toast.makeText(this,
                getString(R.string.log_service_stopped),
                Toast.LENGTH_SHORT)
                .show());
    }

    @Override
    public void onServiceRestartFailed() {
        runOnUiThread(() -> Toast.makeText(this,
                getString(R.string.log_service_require_manual_restart),
                Toast.LENGTH_LONG)
                .show());
    }

    /**
     * 导航到保存的目的地
     */
    private void navigateToSavedDestination() {
        if (currentDestinationId != R.id.homeFragment) {
            // 使用 NavOptions 来避免重复添加相同的目标到返回栈
            NavOptions navOptions = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .build();

            try {
                navController.navigate(currentDestinationId, null, navOptions);
            } catch (IllegalArgumentException e) {
                // 如果目标ID无效，导航到首页
                navController.navigate(R.id.homeFragment);
            }
        }
    }

    private void setupSystemBars() {
        Window window = getWindow();

        // 启用全屏布局，但保留系统栏
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // 设置状态栏和导航栏透明
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        // 创建 WindowInsetsControllerCompat
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(window, window.getDecorView());

        // 根据主题设置状态栏图标颜色（浅色主题用深色图标，深色主题用浅色图标）
        boolean isDarkTheme = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        // 设置状态栏图标颜色
        windowInsetsController.setAppearanceLightStatusBars(!isDarkTheme);

        // 设置导航栏图标颜色
        windowInsetsController.setAppearanceLightNavigationBars(!isDarkTheme);
    }

    private void updateBottomNavigation(boolean environmentReady) {
        if (bottomNav == null) return;
        if (environmentReady) {
            bottomNav.setVisibility(View.VISIBLE);
            bottomNav.setEnabled(true);
            bottomNav.setOnItemSelectedListener(null);
            NavigationUI.setupWithNavController(bottomNav, navController);
            bottomNav.setOnItemSelectedListener(item -> {
                long currentTime = System.currentTimeMillis();
                // 【防抖】如果点击太快，直接忽略
                if (currentTime - lastClickTime < CLICK_INTERVAL) {
                    return false;
                }
                lastClickTime = currentTime;
                int itemId = item.getItemId();
                androidx.navigation.NavDestination currentDestination = navController.getCurrentDestination();
                // 判空保护 + 避免重复点击当前页刷新 (如果不需要刷新当前页的话)
                if (currentDestination != null && currentDestination.getId() == itemId) {
                    return false;
                }
                // 构造动画配置
                NavOptions.Builder builder = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setRestoreState(true)
                        .setEnterAnim(R.anim.nav_enter)
                        .setExitAnim(R.anim.nav_exit)
                        .setPopEnterAnim(R.anim.nav_pop_enter)
                        .setPopExitAnim(R.anim.nav_pop_exit);
                // 获取首页ID (StartDestination)
                int startDestinationId = navController.getGraph().getStartDestinationId();
                builder.setPopUpTo(startDestinationId, false, true);
                try {
                    navController.navigate(itemId, null, builder.build());
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            });
            // 4. 同步 UI 状态
            if (navController.getCurrentDestination() != null) {
                int currentId = navController.getCurrentDestination().getId();
                if (bottomNav.getSelectedItemId() != currentId) {
                    bottomNav.setSelectedItemId(currentId);
                }
            }
        } else {
            // 环境不齐全处理... (保持不变)
            bottomNav.setVisibility(View.GONE);
            bottomNav.setEnabled(false);
            if (navController.getCurrentDestination() != null &&
                    navController.getCurrentDestination().getId() != R.id.homeFragment) {
                navController.navigate(R.id.homeFragment);
            }
            currentDestinationId = R.id.homeFragment;
        }
    }
    @Override
    public void onEnvironmentStateChanged(boolean environmentReady) {
        boolean previousState = this.isEnvironmentReady;
        this.isEnvironmentReady = environmentReady;

        updateBottomNavigation(environmentReady);

        // 如果环境从不就绪变为就绪，尝试恢复到之前保存的目的地
        if (!previousState && environmentReady && currentDestinationId != R.id.homeFragment) {
            navigateToSavedDestination();
        }
    }
}
