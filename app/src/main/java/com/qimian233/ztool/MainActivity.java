package com.qimian233.ztool;

import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;

public class MainActivity extends AppCompatActivity implements HomeFragment.EnvironmentStateListener {

    private BottomNavigationView bottomNav;
    private NavController navController;
    private boolean isEnvironmentReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        // 设置状态栏和导航栏样式
        setupSystemBars();

        setContentView(R.layout.activity_main);

        // 初始化底部导航栏
        bottomNav = findViewById(R.id.bottom_navigation);

        // 设置导航控制器
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();

        // 初始状态下，假设环境不齐全，禁用导航栏
        updateBottomNavigation(false);

        // 设置导航目的地变化监听器，防止在环境不齐全时跳转到其他页面
        navController.addOnDestinationChangedListener(new NavController.OnDestinationChangedListener() {
            @Override
            public void onDestinationChanged(@NonNull NavController controller,
                                             @NonNull NavDestination destination,
                                             @Nullable Bundle arguments) {
                // 如果环境不齐全且尝试导航到非首页，则强制返回首页
                if (!isEnvironmentReady && destination.getId() != R.id.homeFragment) {
                    navController.navigate(R.id.homeFragment);
                }
            }
        });
    }

    /**
     * 设置状态栏和导航栏样式
     */
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

    /**
     * 根据环境状态更新底部导航栏
     */
    private void updateBottomNavigation(boolean environmentReady) {
        if (bottomNav == null) return;

        if (environmentReady) {
            // 环境齐全：显示并启用底部导航栏
            bottomNav.setVisibility(View.VISIBLE);
            bottomNav.setEnabled(true);

            // 重新设置导航控制器
            NavigationUI.setupWithNavController(bottomNav, navController);
        } else {
            // 环境不齐全：隐藏并禁用底部导航栏
            bottomNav.setVisibility(View.GONE);
            bottomNav.setEnabled(false);

            // 停留在首页
            if (navController.getCurrentDestination() != null &&
                    navController.getCurrentDestination().getId() != R.id.homeFragment) {
                navController.navigate(R.id.homeFragment);
            }
        }
    }

    /**
     * 实现环境状态监听器接口
     */
    @Override
    public void onEnvironmentStateChanged(boolean environmentReady) {
        this.isEnvironmentReady = environmentReady;
        updateBottomNavigation(environmentReady);
    }
}
