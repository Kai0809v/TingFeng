package com.dreamct.tingfeng;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.dreamct.tingfeng.databinding.ActivityMainBinding;
import com.dreamct.tingfeng.service.TingConfig;
import com.dreamct.tingfeng.ui.navigation.FloatingDockInstaller;
import com.dreamct.tingfeng.utilities.WallpaperApplier;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    //public boolean showStatusbar = false;
    private View Paopao;
    // 声明气泡视图目标宽度，因为在独立的方法中，又要在OnCreate中取值，所以要在这里声明
    int targetWidthOfPaopao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 沉浸式：状态栏/导航栏透明，壁纸与应用背景直接延伸到系统栏后面；
        // 图标固定深色（应用为浅色主题，深色模式适配后再调整）。
        EdgeToEdge.enable(this, SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));

        binding = ActivityMainBinding.inflate(getLayoutInflater());//绑定布局
        setContentView(binding.getRoot());

        // 壁纸 flag 已由主题（Theme.FoundationX.Wallpaper）在窗口创建时声明；
        // 这里只需铺设遮罩层（透明度调节只作用于背景壁纸，不影响界面组件）
        WallpaperApplier.apply(this, TingConfig.wallpaperVisibility(this));
        // Fragment 内容避开系统栏：顶部留出状态栏高度（状态栏区域透出壁纸，
        // 与应用界面融合），底部留出导航栏高度；浮动 Dock 自己处理系统栏内边距。
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_host_fragment_activity_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 初始化气泡视图目标宽度（服务开关时的气泡动画）
        targetWidthOfPaopao = dpToPx(this, 90); // 90dp 转换为像素

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        FloatingDockInstaller.install(binding.floatingDock, navController);

        Paopao = binding.statusBar;
//        FrameLayout.LayoutParams paoLayoutParams = (FrameLayout.LayoutParams) Paopao.getLayoutParams();
//        paoLayoutParams.width = 120;

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
