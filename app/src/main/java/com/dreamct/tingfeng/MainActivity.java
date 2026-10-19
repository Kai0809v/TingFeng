package com.dreamct.tingfeng;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import androidx.fragment.app.Fragment;

import com.dreamct.tingfeng.databinding.ActivityMainBinding;
import com.dreamct.tingfeng.ui.home.CardStyleEngine;
import com.dreamct.tingfeng.ui.home.HomeFragment;
import com.dreamct.tingfeng.ui.navigation.FloatingDockInstaller;
import com.dreamct.tingfeng.ui.navigation.MainPagerAdapter;
import com.dreamct.tingfeng.ui.navigation.TopBarAnimatable;
import com.dreamct.tingfeng.utilities.AppWallpaperApplier;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    //public boolean showStatusbar = false;
    private View Paopao;
    // 声明气泡视图目标宽度，因为在独立的方法中，又要在OnCreate中取值，所以要在这里声明
    int targetWidthOfPaopao;

    // 顶栏入场动画：记录上次页位置（初始 = 冷启动主页），onPageSelected 时
    // 与目标页不同才算真正切页（setCurrentItem 在 callback 注册之前执行，
    // 冷启动回调不会到达本 callback；部分设备还会补发初始位置的回调，
    // 因此不能用「首次回调跳过」标志，必须按位置变化判断）
    private int lastSelectedTopBarPage = MainPagerAdapter.POSITION_HOME;

    // 切页后新页视图尚未创建（懒加载首切）时挂起的待播动画页位置（-1 = 无）
    private int pendingTopBarEntrance = -1;

    /** onSaveInstanceState 保存的最后停留页 key（recreate/旋转后恢复用） */
    private static final String KEY_LAST_PAGE = "last_selected_page";

    /**
     * 刷新主页所有通知卡片样式（设置页样式/滑块变化后调用）：
     * ViewPager2 常驻架构下主页视图不随切页销毁重建，必须主动重应用样式。
     * 遍历 FragmentManager 中所有 fragment（含已 detach 的），对主页视图树
     * 逐个重应用当前配置。
     */
    public void refreshHomeCards() {
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f instanceof HomeFragment && f.getView() != null) {
                CardStyleEngine.refreshAttachedCards(f.getView());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用主题（全局配色）：必须先于 super.onCreate / setContentView 叠加
        // 主题覆盖样式（TingTheme.applyTo 注释说明覆盖范围与切换机制）
        com.dreamct.tingfeng.ui.theme.TingTheme.applyTo(this);
        super.onCreate(savedInstanceState);
        // 沉浸式：状态栏/导航栏透明，壁纸与应用背景直接延伸到系统栏后面。
        // 状态栏图标外观不在此定死：各 Fragment 按顶栏实际背景色自适应
        // （utilities.SystemBars.applyStatusBarIconAppearance），暗色主题下
        // 顶栏为深色 colorSurface → 浅色图标，避免黑底黑图标不可见。
        EdgeToEdge.enable(this, SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));

        binding = ActivityMainBinding.inflate(getLayoutInflater());//绑定布局
        setContentView(binding.getRoot());

        // 主题切换揭示动画：设置页切主题 recreate 后，新首帧以点击点为圆心
        // 圆形展开新主题内容（无 pending 标志则立即返回，无副作用）
        com.dreamct.tingfeng.ui.animation.ThemeRevealAnimator.playIfPending(this);

        // 应用内图片优先；未启用时恢复透出的系统壁纸与其透明度遮罩。
        AppWallpaperApplier.applyConfigured(this);

        // 三个 tab 页常驻 ViewPager2（懒加载：启动仅创建主页，其余页首次切换才建，
        // 之后切换零重建）。禁用滑动手势：页面切换只由底部 Dock 驱动。
        binding.mainPager.setAdapter(new MainPagerAdapter(this));
        binding.mainPager.setUserInputEnabled(false);
        // 起点页：冷启动 = 主页 tab；recreate（切主题）/旋转 = 恢复最后停留页。
        // 必须在 onCreate 内按保存状态显式恢复——ViewPager2 自身的状态恢复
        // （onRestoreInstanceState，在 onCreate 返回后）把 pager 拉回恢复前页面
        // 且该路径不触发 onPageSelected，Dock 组合时机的早晚也不可控；这里
        // setCurrentItem 立即生效且早于 Dock 首帧组合，Dock 主动同步
        // （selectedDestination = pager.currentItem）必读到正确位置，选中态
        // 与页面一致（修复：切主题后界面在设置页、Dock 却选中主页）。
        // 同时同步 lastSelectedTopBarPage，保证恢复后首次切页仍播顶栏入场动画。
        if (savedInstanceState == null) {
            binding.mainPager.setCurrentItem(MainPagerAdapter.POSITION_HOME, false);
        } else {
            int savedPage = savedInstanceState.getInt(KEY_LAST_PAGE, MainPagerAdapter.POSITION_HOME);
            binding.mainPager.setCurrentItem(savedPage, false);
            lastSelectedTopBarPage = savedPage;
        }

        // Fragment 内容避开系统栏：顶部留出状态栏高度（状态栏区域透出壁纸，
        // 与应用界面融合），底部留出导航栏高度；浮动 Dock 自己处理系统栏内边距。
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainPager, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 关闭 pager 内部 RecyclerView 的裁剪：主页顶栏负 margin 越界延伸进
        // 状态栏区域，HomeFragment.setupTopBarImmersion 会沿父链向上关闭裁剪
        // （含此 RecyclerView），但该循环在页面 onViewCreated 才执行，这里
        // 布局后补齐兜底，保证首次进入时越界绘制不被裁。
        binding.mainPager.post(() -> {
            View inner = binding.mainPager.getChildAt(0);
            if (inner instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) inner;
                g.setClipChildren(false);
                g.setClipToPadding(false);
            }
        });

        // Dock 点击 → pager 切页；Dock 选中态由 OnPageChangeCallback 驱动
        FloatingDockInstaller.install(binding.floatingDock, binding.mainPager);

        // 切页顶栏过渡：新页顶栏从屏幕外滑入（页面实现 TopBarAnimatable，
        // translationY 位移不透壁纸）。视图已建则立即播，未建（懒加载首切）
        // 则挂起待 Fragment 视图创建后经 consumePendingTopBarEntrance 领取。
        binding.mainPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                // 与上次页相同 = 冷启动初始/补发回调，不算切页，不播动画
                if (position == lastSelectedTopBarPage) return;
                lastSelectedTopBarPage = position;
                pendingTopBarEntrance = position;
                // FragmentStateAdapter 的 fragment tag 约定为 "f" + itemId（默认 = position）
                Fragment f = getSupportFragmentManager().findFragmentByTag("f" + position);
                if (f instanceof TopBarAnimatable && f.getView() != null) {
                    ((TopBarAnimatable) f).playTopBarEntrance();
                    pendingTopBarEntrance = -1;
                }
            }
        });

        // 返回键：非主页 tab 先回主页 tab，再按系统默认退出
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.mainPager.getCurrentItem() != MainPagerAdapter.POSITION_HOME) {
                    binding.mainPager.setCurrentItem(MainPagerAdapter.POSITION_HOME, false);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        Paopao = binding.statusBar;

        // 初始化气泡视图目标宽度（服务开关时的气泡动画）
        targetWidthOfPaopao = dpToPx(this, 90); // 90dp 转换为像素
//        FrameLayout.LayoutParams paoLayoutParams = (FrameLayout.LayoutParams) Paopao.getLayoutParams();
//        paoLayoutParams.width = 120;

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // 记录最后停留页：recreate（切主题）/旋转后 onCreate 按此恢复起点页。
        // ViewPager2 自身的状态恢复不触发 onPageSelected（Dock 无法被动对齐），
        // 必须自己保存并在 onCreate 显式 setCurrentItem（见 onCreate 注释）
        if (binding != null) {
            outState.putInt(KEY_LAST_PAGE, binding.mainPager.getCurrentItem());
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * Fragment 视图创建晚于切页回调时，领取待播的顶栏入场动画（取走即清）。
     * 懒加载下切页只创建目标页，第一个来领取的即目标页。
     */
    public boolean consumePendingTopBarEntrance() {
        if (pendingTopBarEntrance >= 0) {
            pendingTopBarEntrance = -1;
            return true;
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 后台期间系统壁纸可能被更换：回前台重应用背景（壁纸 bitmap 按系统
        // 壁纸 id 缓存，未更换时零开销命中；应用内壁纸同样有 asset 缓存）
        AppWallpaperApplier.applyConfigured(this);
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
