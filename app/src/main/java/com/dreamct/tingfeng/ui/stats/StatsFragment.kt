package com.dreamct.tingfeng.ui.stats

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.dreamct.tingfeng.MainActivity
import com.dreamct.tingfeng.data.AppDatabase
import com.dreamct.tingfeng.ui.navigation.TopBarAnimatable
import com.dreamct.tingfeng.ui.theme.TingTheme
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 统计页（tab 0，替代已弃用的工具页）：展示近 7 天每个应用平均每天发送通知的
 * 频率（条/天，四舍五入），降序排列。
 *
 * 界面全部用 Kotlin 代码构建（无 XML 布局）。顶栏为悬浮卡片，负 margin 越界
 * 延伸到状态栏后面（与主页顶栏一致），依赖从 fragment 根向上逐层关闭父容器
 * 裁剪（doOnAttach 中执行，覆盖 ViewPager2 视图重建场景）。
 *
 * 加载策略：懒加载（ViewPager2 默认仅当前页创建视图）+ 异步查询（Room 查询
 * 与图标解析均在 Dispatchers.IO，主线程只做列表提交），onResume 时刷新，
 * 页面常驻后每次切回自动拿到最新数据。
 */
class StatsFragment : Fragment(), TopBarAnimatable {

    private companion object {
        /** 统计窗口：近 7 天 */
        private const val WINDOW_DAYS = 7L
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }

    /** 单行数据：应用名 + 包名 + 日均条数 + 应用图标 */
    private data class AppStat(
        val appName: String,
        val packageName: String,
        val perDay: Int,
        val icon: Drawable?
    )

    private var statusBarTop = 0
    private lateinit var topBar: MaterialCardView
    private lateinit var barContent: LinearLayout
    private lateinit var listScroll: NestedScrollView
    private lateinit var rowsContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val root = FrameLayout(ctx).apply {
            clipChildren = false
            clipToPadding = false
        }

        // ---- 列表：一个大卡片装全部行（设置页"关于"同款结构），行间分隔线 ----
        // 滚动容器在下层：上划时内容滑到顶栏背后，被顶栏遮住
        listScroll = NestedScrollView(ctx).apply {
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        rowsContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        listScroll.addView(MaterialCardView(ctx).apply {
            // 与设置页卡片同款：colorSurface 背景 + colorOutlineVariant 1dp 描边
            radius = dp(16f).toFloat()
            cardElevation = dp(1f).toFloat()
            setCardBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurface))
            strokeColor = themeColor(com.google.android.material.R.attr.colorOutlineVariant)
            strokeWidth = dp(1f)
            addView(rowsContainer)
        }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(16f), 0, dp(16f), 0) })
        root.addView(listScroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // ---- 顶栏：悬浮卡片，负 margin 越界延伸进状态栏（insets 就绪后设置） ----
        // 注意：CardView 重写了 setPadding（转发到内部 contentPadding），越界的
        // 状态栏避让 padding 必须设在内部内容视图上，不能设在卡片自身
        topBar = MaterialCardView(ctx).apply {
            radius = dp(24f).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurface))
            clipChildren = false
            clipToPadding = false
        }
        barContent = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            // 顶栏三段色相渐变（淡化 50%）：content 含状态栏避让 padding、铺满整卡，
            // 与底部 Dock、其余两页顶栏同源（见 TingTheme.hueGradient surfaceBlend）
            background = TingTheme.hueGradient(
                ctx, dp(24f).toFloat(), horizontal = true, surfaceBlend = 0.5f,
                stroke = true
            )
        }
        barContent.addView(TextView(ctx).apply {
            text = "统计"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
        })
        barContent.addView(TextView(ctx).apply {
            text = "近 $WINDOW_DAYS 天各应用通知频率"
            textSize = 13f
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2f) })
        topBar.addView(barContent)
        root.addView(topBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // insets：记录状态栏高度 → 顶栏负 margin 越界 + 列表顶部避让
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            statusBarTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            applyTopBarGeometry()
            insets
        }
        ViewCompat.requestApplyInsets(root)

        // 越界绘制：从 fragment 根向上逐层关闭父容器裁剪（attach 后父链必然完整，
        // 覆盖 ViewPager2 横竖屏旋转等视图重建场景）
        root.doOnAttach { v ->
            var parent = v.parent as? ViewGroup
            while (parent != null) {
                parent.clipChildren = false
                parent.clipToPadding = false
                parent = parent.parent as? ViewGroup
            }
            // ViewPager2 下视图 attach 可能错过首次 WindowInsets 分发（主页同类坑）：
            // 强制重分发一次（listener 会设置 statusBarTop 并重算几何）
            ViewCompat.requestApplyInsets(v)
        }
        root.doOnLayout { applyTopBarGeometry() }

        // Dock 切页触发的顶栏入场动画：视图创建晚于切页回调时在此领取待播动画
        val main = activity as? MainActivity
        if (main?.consumePendingTopBarEntrance() == true) playTopBarEntrance()

        return root
    }

    /**
     * Dock 切页入场：顶栏从屏幕外（含越界的状态栏部分）滑回原位。
     * 只做 translationY 位移——卡片背景不透明，位移不会透出壁纸；
     * 整卡 alpha<1 会离屏合成透出底层壁纸，禁止。
     */
    override fun playTopBarEntrance() {
        // 切页瞬间 fragment 视图可能仍 detach（holder 在缓存中），不能用
        // doOnLayout：视图重新放置时若尺寸未变且无 FORCE_LAYOUT，View.layout
        // 会跳过 onLayoutChange，挂起的回调永不触发、动画静默丢失（实测二次
        // 切页复现）；preDraw 每帧绘制前必触发，切页后首帧瞬移即生效（无闪烁）
        topBar.doOnPreDraw { bar ->
            // 先瞬移到屏幕外（首帧绘制前生效），下一帧再滑回原位
            bar.translationY = -(bar.height + statusBarTop).toFloat()
            bar.post {
                bar.animate().translationY(0f).setDuration(240L)
                    .setInterpolator(DecelerateInterpolator(1.6f))
                    .start()
            }
        }
    }

    /** 顶栏负 margin 越界 + 列表顶部避让（insets 与布局就绪后都会重算） */
    private fun applyTopBarGeometry() {
        if (statusBarTop <= 0) return
        val lp = topBar.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = -statusBarTop
        // 越界 padding 设在内部内容视图（CardView 的 setPadding 不可靠）
        barContent.setPadding(
            dp(20f),
            statusBarTop + dp(12f),
            dp(20f),
            dp(12f)
        )
        topBar.layoutParams = lp
        topBar.doOnLayout { bar ->
            // 底部 padding 用 Dock 内容避让值（设置页同源）：clipToPadding=false，
            // 内容可滚进 padding 区，滚到底时卡片末尾项目停在 Dock 上方不被遮挡
            val dockInset = resources.getDimensionPixelSize(
                com.dreamct.tingfeng.R.dimen.floating_dock_content_inset
            )
            listScroll.setPadding(
                0,
                statusBarTop + bar.height + dp(8f),
                0,
                dockInset
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    /** 异步统计近 7 天数据：IO 线程查询+聚合+图标解析，主线程提交列表 */
    private fun refreshStats() {
        if (!isAdded) return
        val ctx = requireContext()
        val owner = viewLifecycleOwner
        owner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val dao = AppDatabase.getInstance(ctx).notifyDao()
                val now = System.currentTimeMillis()
                val logs = dao.getLogsByTimeRange(now - WINDOW_DAYS * DAY_MS, now).first()
                val pm = ctx.packageManager
                logs.groupBy { it.packageName }.mapNotNull { (pkg, list) ->
                    val perDay = (list.size.toDouble() / WINDOW_DAYS).roundToInt()
                    val appName = list.firstOrNull()?.appName?.takeIf { it.isNotBlank() }
                        ?: runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        }.getOrDefault(pkg)
                    val icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
                    AppStat(appName, pkg, perDay, icon)
                }.sortedByDescending { it.perDay }
            }
            // 主线程重建行容器（大卡片内动态行，数据量小无需 Diff）
            rebuildRows(result)
        }
    }

    /** 重建大卡片内的行容器：每应用一行，行间 1dp 分隔线（设置页"关于"同款） */
    private fun rebuildRows(items: List<AppStat>) {
        rowsContainer.removeAllViews()
        if (items.isEmpty()) {
            rowsContainer.addView(TextView(requireContext()).apply {
                text = "近 $WINDOW_DAYS 天暂无通知数据"
                textSize = 14f
                setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(dp(16f), dp(20f), dp(16f), dp(20f))
            })
            return
        }
        items.forEachIndexed { index, item ->
            rowsContainer.addView(buildRow(item))
            if (index < items.lastIndex) {
                // 分隔线避开行内图标，与文字左对齐（16dp padding + 40dp 图标 + 12dp 间距）
                rowsContainer.addView(View(requireContext()).apply {
                    setBackgroundColor(themeColor(com.google.android.material.R.attr.colorOutlineVariant))
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)
                ).apply { marginStart = dp(68f) })
            }
        }
    }

    /** 单行：图标 + 应用名 + "大约每天 n 条"（padding 16dp，与设置页行一致） */
    private fun buildRow(item: AppStat): View {
        val ctx = requireContext()
        val name = TextView(ctx).apply {
            text = item.appName
            textSize = 16f
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
        }
        val freq = TextView(ctx).apply {
            text = "大约每天 ${item.perDay} 条"
            textSize = 13f
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
        }
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(12f) }
            addView(name)
            addView(freq, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2f) })
        }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            addView(ImageView(ctx).apply {
                setImageDrawable(item.icon)
            }, LinearLayout.LayoutParams(dp(40f), dp(40f)))
            addView(texts)
        }
        return content
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    ).toInt()

    private fun themeColor(attr: Int): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
