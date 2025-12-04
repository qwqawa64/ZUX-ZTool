package com.qimian233.ztool;

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

public class MainActivity extends AppCompatActivity implements HomeFragment.EnvironmentStateListener,
        LogServiceManager.ServiceStatusListener {

    private BottomNavigationView bottomNav;
    private NavController navController;
    private boolean isEnvironmentReady = false;

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

        // 如果环境就绪，尝试恢复到之前的目的地
        if (isEnvironmentReady && currentDestinationId != R.id.homeFragment) {
            navigateToSavedDestination();
        }

        // 应用启动时尝试重启日志服务
        LogServiceManager.restartServiceIfNeeded(this);
    }

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
        runOnUiThread(() -> Toast.makeText(this, "日志服务已启动", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onServiceStopped() {
        runOnUiThread(() -> Toast.makeText(this, "日志服务已停止", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onServiceRestartFailed() {
        runOnUiThread(() -> Toast.makeText(this, "日志服务自动重启失败，请手动启动", Toast.LENGTH_LONG).show());
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

    // 其余方法保持不变...
    // setupSystemBars(), updateBottomNavigation(), onEnvironmentStateChanged() 等

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
            // 环境齐全：显示并启用底部导航栏
            bottomNav.setVisibility(View.VISIBLE);
            bottomNav.setEnabled(true);

            // 重新设置导航控制器
            NavigationUI.setupWithNavController(bottomNav, navController);

            // 更新底部导航栏的选中状态
            if (currentDestinationId != R.id.homeFragment) {
                // 延迟执行以确保UI已更新
                bottomNav.post(() -> {
                    try {
                        // 尝试设置选中的菜单项
                        for (int i = 0; i < bottomNav.getMenu().size(); i++) {
                            if (bottomNav.getMenu().getItem(i).getItemId() == currentDestinationId) {
                                bottomNav.setSelectedItemId(currentDestinationId);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // 忽略设置选中项时的异常
                    }
                });
            }
        } else {
            // 环境不齐全：隐藏并禁用底部导航栏
            bottomNav.setVisibility(View.GONE);
            bottomNav.setEnabled(false);

            // 停留在首页
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
