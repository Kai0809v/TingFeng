package com.dreamct.tingfeng;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationBarView;
import com.dreamct.tingfeng.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private int lastSelectedId = R.id.navigation_home; // 记录上次选中的ID
    //public boolean showStatusbar = false;
    private View Paopao;
    // 声明气泡视图目标宽度，因为在独立的方法中，又要在OnCreate中取值，所以要在这里声明
    int targetWidthOfPaopao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());//绑定布局
        setContentView(binding.getRoot());
        // 处理窗口内边距，需要找到View对象的id，在此处为fragment的id
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_host_fragment_activity_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);//将顶部padding改为了0
            return insets;
        });

        // 获取状态栏高度
        int statusBarHeight = getStatusBarHeight();
        // 设置状态栏背景高度
        FrameLayout statusBarBg = binding.statusBarBg;
        // 初始化气泡视图目标宽度
        targetWidthOfPaopao = dpToPx(this, 90); // 90dp 转换为像素

        assert statusBarBg != null;
        ViewGroup.LayoutParams params = statusBarBg.getLayoutParams();
        params.height = statusBarHeight;
        statusBarBg.setLayoutParams(params);

// 移除原有的 BottomNavigationView 声明，替换为以下代码
        NavigationBarView navBarView; // 使用父类 NavigationBarView 兼容两种导航视图
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 横屏：使用 NavigationRailView (id 为 nav_rail)
            navBarView = findViewById(R.id.nav_rail);
        } else {
            // 竖屏：使用 BottomNavigationView (id 为 nav_view)
            navBarView = findViewById(R.id.nav_view);
        }
        navBarView.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);

        Menu menu = navBarView.getMenu(); // 原 navBarView 替换为 navBarView
        menu.getItem(0).setTitle(null);
        menu.getItem(2).setTitle(null);

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_utils, R.id.navigation_home, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);

        //上面这里定义了NavController，然后下面这里把它和BottomNavigationView绑定起来，
        //这是绑定顶部导航栏的，现在使用无actionbar，再使用这行代码会导致崩溃
        // NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        NavigationUI.setupWithNavController(navBarView, navController);//绑定底部导航栏

        // 添加选中监听器，并添加防止快速重复点击的逻辑
        navBarView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            // 如果点击的是当前已选中的项，则忽略
            if (itemId == lastSelectedId) {
                return false;
            }

            // 更新最后选中的ID
            lastSelectedId = itemId;

            // 重置所有标题
            for (int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setTitle(null);
            }

            // 设置新标题
            if (itemId == R.id.navigation_home) {
                item.setTitle(getString(R.string.title_home));
            } else if (itemId == R.id.navigation_utils) {
                item.setTitle(getString(R.string.title_utils));
            } else if (itemId == R.id.navigation_notifications) {
                item.setTitle(getString(R.string.title_notifications));
            }

            // 执行导航
            return NavigationUI.onNavDestinationSelected(item, navController);
        });

        // 保留目的地监听器（用于处理返回按钮等情况）
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            // 更新最后选中的ID
            lastSelectedId = destination.getId();

            // 重置所有标题
            for (int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setTitle(null);
            }

            // 设置当前标题
            int destId = destination.getId();
            if (destId == R.id.navigation_home) {
                menu.findItem(R.id.navigation_home).setTitle(getString(R.string.title_home));
            } else if (destId == R.id.navigation_utils) {
                menu.findItem(R.id.navigation_utils).setTitle(getString(R.string.title_utils));
            } else if (destId == R.id.navigation_notifications) {
                menu.findItem(R.id.navigation_notifications).setTitle(getString(R.string.title_notifications));
            }
        });


        Paopao = binding.statusBar;
//        FrameLayout.LayoutParams paoLayoutParams = (FrameLayout.LayoutParams) Paopao.getLayoutParams();
//        paoLayoutParams.width = 120;

    }
    // 获取系统状态栏高度的方法
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    // 添加动画方法，添加空检查
    public void startAnimation() {
        if (Paopao != null) {
            ValueAnimator animator = ValueAnimator.ofInt(0,targetWidthOfPaopao);
            animator.setDuration(300);
            animator.addUpdateListener(valueAnimator -> {
                Paopao.getLayoutParams().width = (int) valueAnimator.getAnimatedValue();
                Paopao.requestLayout();
            });
            animator.start();
        }
    }

    public void stopAnimation() {
        if (Paopao != null) {
            ValueAnimator animator = ValueAnimator.ofInt(targetWidthOfPaopao, 0);
            animator.setDuration(300);
            animator.addUpdateListener(valueAnimator -> {
                Paopao.getLayoutParams().width = (int) valueAnimator.getAnimatedValue();
                Paopao.requestLayout();
            });
            animator.start();
        }
    }

    public static int dpToPx(Context context, int px) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(px * density);
    }


}