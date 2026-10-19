package com.dreamct.tingfeng.ui.home

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dreamct.tingfeng.R
import com.dreamct.tingfeng.MainActivity
import com.dreamct.tingfeng.data.AppDatabase
import com.dreamct.tingfeng.data.NotificationLog
import com.dreamct.tingfeng.databinding.FragmentHomeBinding
import com.dreamct.tingfeng.service.Ting
import com.dreamct.tingfeng.service.TingConfig
import com.dreamct.tingfeng.ui.navigation.TopBarAnimatable
import com.dreamct.tingfeng.ui.theme.TingTheme
import com.dreamct.tingfeng.utilities.AppWallpaperApplier
import com.dreamct.tingfeng.utilities.applyStatusBarIconAppearance
import com.dreamct.tingfeng.utilities.hasNotificationAccessPermission
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment(), TopBarAnimatable {

    private companion object {
        /** 顶栏展开态内容高度（dp）：标题/副标题大字、整体居中 */
        private const val CONTENT_EXPANDED_DP = 128f

        /** 顶栏压缩态内容高度（dp）：标题/副标题小字靠左，等高 Material 顶栏 */
        private const val CONTENT_COLLAPSED_DP = 64f
        private const val TITLE_SP_EXPANDED = 24f
        private const val TITLE_SP_COLLAPSED = 16f
        private const val SUBTITLE_SP_EXPANDED = 15f
        private const val SUBTITLE_SP_COLLAPSED = 12f
    }
    // ********************** ViewBinding **********************
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    // ********************** Component **********************
    private lateinit var titleBlock: View
    private lateinit var barTitle: TextView
    private lateinit var barSubtitle: TextView
    private lateinit var btnTest: MaterialButton
    private lateinit var btnUp: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var floatingTopBar: MaterialCardView
    private lateinit var viewModel: HomeViewModel
    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var stackLayoutManager: StackedCardLayoutManager
    private lateinit var backCallback: OnBackPressedCallback

    // 内嵌搜索态：true = 顶栏显示搜索框、同一列表原地过滤（无页面导航）
    private var searchMode = false

    // 退出搜索态后的首个全量列表恢复并非数据库新增，不计入未读增量
    private var justExitedSearch = false

    // 顶栏沉浸进度缓存（-1 = 尚未初始化，强制首次应用）
    private var topBarProgress = -1f

    // 状态栏高度（systemBars.top，含挖孔），由 WindowInsets 监听器写入；
    // 顶栏融合时卡片需向上延伸这个高度覆盖状态栏区域
    private var statusBarTop = 0



    // 新增：未读消息计数器
    private var unreadCount = 0
    // 新增：记录上一次列表的最顶端位置，用于判断是否有新消息插入
    private var lastTopPosition = 0
    private val tag = "HomeFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 页面架构：主界面三个 tab 页由 MainActivity 的 ViewPager2 常驻承载
        // （懒加载，切页不销毁视图树），无 Navigation 导航栈与 Fragment 转场；
        // 顶栏负 margin 越界延伸进状态栏区域，禁止做整树 alpha 淡入
        // （alpha<1 时整树离屏合成会透出底层壁纸，状态栏区域闪壁纸色）

        // 添加数据库实例获取
        val dao = AppDatabase.getInstance(requireContext()).notifyDao()

        // 使用ViewModelFactory初始化ViewModel（fragment 级：内嵌搜索态复用
        // 主页同一视图树与列表原地过滤，无跨页面共享需求）
        val factory = HomeViewModelFactory(dao)
        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]

        // ********************** ViewBinding *****************************************
        titleBlock = binding.titleBlock
        barTitle = binding.barTitle
        barSubtitle = binding.barSubtitle
        recyclerView = binding.cardRecyclerView
        btnTest = binding.test
        btnUp = binding.goUpon
        floatingTopBar = binding.floatingTopBar

        // 卡片样式引擎：把设置页"卡片样式"选择的样式应用到每个通知卡片
        HFConfig.cardStyle = CardStyleEngine


        // ****************************************************************************
        // 初始化卡片堆栈 LayoutManager（通知面板式 Z 轴遮挡堆叠）
        stackLayoutManager = StackedCardLayoutManager(requireContext())
        recyclerView.layoutManager = stackLayoutManager

        notificationAdapter = NotificationAdapter(
            onItemClick = { log -> handleCardClick(log) }
            // 关闭按钮已暂时移除，删除逻辑保留备用（恢复时同步恢复 Adapter/CardView 注释）：
            // onCloseClick = { log -> viewModel.deleteNotification(log) }
        )
        // 在 adapter 初始化后设置
        recyclerView.adapter = notificationAdapter

        // 堆叠重排时禁用默认条目动画，避免闪烁
        recyclerView.itemAnimator = null

        // 顶栏三段色相渐变（淡化 50%，大面积背景混 colorSurface 防止浓烈染色）：
        // 必须设在顶栏卡片本体上——卡片负 margin 越界覆盖状态栏区域，而内容容器
        // topBarContainer 被压缩动画下移了 statusBarTop 且高度只含内容行，渐变放
        // 它上面会与状态栏区的纯白割裂（另两页渐变载体是含状态栏 padding 的
        // content，无此问题）；与 Dock 同源，见 TingTheme.hueGradient
        binding.floatingTopBar.background = TingTheme.hueGradient(
            requireContext(), dp(24f).toFloat(), horizontal = true, surfaceBlend = 0.5f,
            stroke = true
        )

        // 顶栏沉浸：滚动时渐显背景并与状态栏融为一体
        setupTopBarImmersion()

        // 状态栏图标颜色按顶栏背景自适应（暗色主题顶栏为深色 → 浅色图标）
        applyStatusBarIconAppearance(
            requireActivity().window,
            requireActivity().window.decorView,
            floatingTopBar
        )

        // 栈顶卡片左右滑动消除（复现通知栏滑走通知的效果）
        val swipeCallback = NotificationSwipeCallback(
            layoutManager = stackLayoutManager,
            getItem = { position -> notificationAdapter.currentList.getOrNull(position) },
            onDismiss = { log -> viewModel.deleteNotification(log) }
        )
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)






        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val firstVisibleItemPosition = stackLayoutManager.frontCardPosition

                // 逻辑 A：如果用户手动滑回顶部，清除未读计数
                if (firstVisibleItemPosition == 0) {
                    resetUnreadCount()
                } else {
                    // 逻辑 B：如果用户正在向上滑动（dy < 0），且之前有未读数，尝试递减
                    // 这里做一个简单的估算：每次显示新的 Item 时，未读数 -1
                    // 实际上完全精确匹配比较难，这里用位置差来修正
                    if (unreadCount > 0 && firstVisibleItemPosition < lastTopPosition) {
                        val diff = lastTopPosition - firstVisibleItemPosition
                        updateUnreadCount(unreadCount - diff)
                    }
                }

                // 控制 btnUp 的显示/隐藏（可选，根据需求保留）
                // if (firstVisibleItemPosition > 0) btnUp.show() else btnUp.hide()

                lastTopPosition = firstVisibleItemPosition
            }
        })

        // 观察数据库变化
        lifecycleScope.launch {
            viewModel.filteredNotifications
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { notifications ->
                    // 在提交数据前，判断是否在浏览旧数据
                    // （堆叠布局中 frontCardPosition 等价于原来的 firstVisibleItemPosition）
                    val firstVisiblePos = stackLayoutManager.frontCardPosition

                    // 如果当前不在顶部（firstVisiblePos > 0），且新数据来了
                    // 注意：这里需要根据具体的 ListAdapter 差异比对逻辑。
                    // 简单做法：如果列表不在顶部，且新列表比旧列表长（或者只是判定为新数据），则增加计数
                    // 因为 submitList 是异步的，这里做一个假设：
                    // 只有当用户没有浏览最顶部时，才累加计数。

                    // 退出搜索态后的首个全量恢复并非数据库新增，跳过未读增量
                    if (!justExitedSearch && firstVisiblePos > 0 && notifications.isNotEmpty()) {
                        // 这是一个简化逻辑：假设每次 collect 都是因为插入了一条新数据
                        // 严谨的做法是在 Adapter 的 DataObserver 中做，但这里更简单
                        val previousCount = notificationAdapter.itemCount
                        val newCount = notifications.size
                        if (newCount > previousCount) {
                            val addedCount = newCount - previousCount
                            updateUnreadCount(unreadCount + addedCount)
                        }
                    }
                    justExitedSearch = false

                    notificationAdapter.submitList(notifications) {
                        // 数据更新完成后，如果之前在顶部，保持在顶部（避免被新消息顶下去）
                        if (firstVisiblePos == 0) {
                            recyclerView.scrollToPosition(0)
                        }
                    }

                    // 搜索空态：有关键词但过滤结果为空
                    updateSearchEmptyState(notifications.isEmpty())
                }
        }

        // 观察 ViewModel 中的开关状态（服务开关已迁移至设置页）

        // **********************Component methods *************************************
        btnTest.setOnClickListener {
            insertTestNotification()
        }

        // 智能智能回到顶部
        btnUp.setOnClickListener {
            val firstVisiblePos = stackLayoutManager.frontCardPosition

            // 阈值：超过 20 条视为“太远”
            val jumpThreshold = 20

            if (firstVisiblePos > jumpThreshold) {
                // 策略：先瞬移到第 20 条，再平滑滚动到 0
                // 这样既能看到滚动的动画效果，又不会等太久
                recyclerView.scrollToPosition(jumpThreshold)
                recyclerView.post {
                    recyclerView.smoothScrollToPosition(0)
                }
            } else {
                // 距离近，直接平滑滚动
                recyclerView.smoothScrollToPosition(0)
            }

            // 点击后必然回到顶部，清除计数
            resetUnreadCount()
        }

        // 内嵌搜索态：返回键在搜索态下退出搜索（而非离开应用）
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = exitSearchMode()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        // 点击右上角搜索按钮：即时切换搜索态（标题 ↔ 搜索框，search ↔ close 图标互切），
        // 同一列表原地过滤，零导航、零新视图树
        binding.barSearchButton.setOnClickListener {
            if (searchMode) exitSearchMode() else enterSearchMode()
        }

        // 输入实时过滤（内存过滤，微秒级，无需防抖/分页）
        binding.searchEditText.doOnTextChanged { text, _, _, _ ->
            viewModel.setSearchQuery(text?.toString()?.trim().orEmpty())
        }

        // Dock 切页触发的顶栏入场动画：视图创建晚于切页回调时在此领取待播动画
        val main = activity as? MainActivity
        if (main?.consumePendingTopBarEntrance() == true) playTopBarEntrance()
    }

    // ------------------------- 内嵌搜索态 -------------------------

    /** 进入搜索态：顶栏标题切换为搜索框，聚焦弹键盘，返回键拦截启用 */
    private fun enterSearchMode() {
        searchMode = true
        titleBlock.visibility = View.GONE
        binding.searchInputLayout.visibility = View.VISIBLE
        binding.barSearchButton.setImageResource(R.drawable.close)
        backCallback.isEnabled = true
        // 视图刚从 GONE 变 VISIBLE，post 到完成布局后再弹软键盘
        binding.searchEditText.requestFocus()
        binding.searchEditText.post {
            if (_binding == null) return@post
            val imm = requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** 退出搜索态：清空关键词恢复全量列表，顶栏切回标题 */
    private fun exitSearchMode() {
        searchMode = false
        backCallback.isEnabled = false
        justExitedSearch = true
        viewModel.setSearchQuery("")
        binding.searchEditText.setText("")
        binding.searchEditText.clearFocus()
        binding.searchEmptyView.visibility = View.GONE
        binding.searchInputLayout.visibility = View.GONE
        titleBlock.visibility = View.VISIBLE
        binding.barSearchButton.setImageResource(R.drawable.search)
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
        // 搜索期间可能滚动过（顶栏被压缩且标题对齐未更新），恢复后按当前进度重算几何
        applyTopBarGeometry(topBarProgress.coerceIn(0f, 1f))
    }

    /** 搜索空态：搜索态且有关键词但过滤结果为空时显示提示 */
    private fun updateSearchEmptyState(resultEmpty: Boolean) {
        val b = _binding ?: return
        b.searchEmptyView.visibility =
            if (searchMode && !b.searchEditText.text.isNullOrBlank() && resultEmpty)
                View.VISIBLE else View.GONE
    }

    // ------------------------- 顶栏沉浸（滚动渐显 + 与状态栏融合） -------------------------

    /**
     * 沉浸式顶栏（全顶栏样式 + 滚动压缩）：
     *  - 顶栏常驻全宽矩形、从屏幕最顶端开始，状态栏背景即顶栏背景（负上边距
     *    越出 fragment 上边界，前提是整条父链关闭裁剪）；
     *  - 滚动时高度压缩（128dp → 64dp）、字号缩小、标题/副标题由居中转靠左，
     *    由滚动偏移直接驱动（约 96dp 滑动完成），可逆、无跳变；
     *  - 搜索按钮固定右上角，不参与压缩。
     */
    private fun setupTopBarImmersion() {
        // 状态栏高度就绪后记录（供几何插值使用），并同步列表顶部留白与顶栏几何
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyInsets(insets)
            insets
        }
        // fragment 视图在 onViewCreated 时还未 attach 到窗口，此刻请求重新
        // 分发是 no-op；而首次 insets 分发又发生在 attach 之前、已错过 →
        // 该竞态是否被其他分发"救回"因设备而异。双保险根治：
        // ① attach 后直接主动查询根 insets（不依赖任何事件分发的时序）；
        // ② 再请求一次完整分发，覆盖后续的主题/布局变化。
        binding.root.doOnAttach {
            ViewCompat.getRootWindowInsets(it)?.let(::applyInsets)
            ViewCompat.requestApplyInsets(it)
        }
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateTopBarImmersive()
            }
        })
        updateTopBarImmersive()
        // 顶栏卡片需越出 fragment 根布局的上边界延伸到状态栏区域：
        // 从 fragment 根向上逐层关闭父容器的裁剪（XML 静态声明在部分层级
        // 会被运行时覆盖或未生效，这里统一强制，覆盖到窗口内容视图为止）。
        // 放在 doOnAttach 里执行：ViewPager2 下 fragment 视图重建（如横竖屏
        // 旋转）时 onViewCreated 可能早于 addView（parent=null，循环空转），
        // attach 后父链（holder → pager 内部 RecyclerView → pager → 根容器）
        // 必然完整，且新 holder 均为默认裁剪开启的新实例，必须重新关闭。
        binding.root.doOnAttach { root ->
            var clipParent = root.parent as? ViewGroup
            while (clipParent != null) {
                clipParent.clipChildren = false
                clipParent.clipToPadding = false
                clipParent = clipParent.parent as? ViewGroup
            }
            val rootGroup = root as ViewGroup
            rootGroup.clipChildren = false
            rootGroup.clipToPadding = false
        }
        // 布局几何就绪后立即应用顶栏初始几何，避免首帧使用 XML 回退值
        binding.root.doOnLayout { applyTopBarGeometry(topBarProgress.coerceIn(0f, 1f)) }
    }

    /** insets 就绪：记录状态栏高度，并同步列表顶部留白与顶栏几何 */
    private fun applyInsets(insets: WindowInsetsCompat) {
        statusBarTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        // 列表内容避开展开态顶栏（状态栏 + 16dp 间距 + 展开高度 + 8dp 呼吸）
        recyclerView.setPadding(
            recyclerView.paddingLeft,
            (statusBarTop + dp(16f) + dp(CONTENT_EXPANDED_DP) + dp(8f)).toInt(),
            recyclerView.paddingRight,
            recyclerView.paddingBottom
        )
        applyTopBarGeometry(topBarProgress.coerceIn(0f, 1f))
    }

    /**
     * 顶栏几何插值（全顶栏样式，progress: 0 = 展开态, 1 = 压缩态）：
     *  - 顶栏常驻全宽矩形、从屏幕最顶端开始（卡片上边距 = -状态栏高度，
     *    天然覆盖状态栏区域）；内容容器同步下移状态栏高度，内容行钉在状态栏之下；
     *  - 内容高度：128dp（展开，大字居中）→ 64dp（压缩，小字靠左），
     *    卡片（wrap_content）随之变矮；
     *  - 字号：标题 24sp → 16sp，副标题 15sp → 12sp；
     *  - 水平对齐：标题/副标题居中（平移量随进度归零）→ 靠左 16dp；
     *  - 下边缘线常显，始终贴住卡片底边。
     * 注意：负上边距要求整条父链关闭裁剪（fragment 根布局与 nav_host 的
     * clipChildren/clipToPadding 均为 false），否则延伸进状态栏的部分被裁掉。
     */
    private fun applyTopBarGeometry(progress: Float) {
        val lp = floatingTopBar.layoutParams as ViewGroup.MarginLayoutParams
        lp.leftMargin = 0
        lp.rightMargin = 0
        // 顶栏常驻覆盖状态栏：卡片顶边钉在屏幕最顶端（负边距越出 fragment 上边界）
        lp.topMargin = -statusBarTop
        floatingTopBar.layoutParams = lp

        // 搜索按钮钉在内容行顶部（相对内容行 12dp）：位置不随压缩变化，
        // 且始终在状态栏触摸区之外（落在状态栏区域内会被状态栏窗口拦截点击）。
        // 注意按钮的参照系是 topBarContainer（其顶部已在状态栏高度处），
        // 这里不能再叠加 statusBarTop，否则双重偏移
        val btnLp = binding.barSearchButton.layoutParams as ViewGroup.MarginLayoutParams
        btnLp.topMargin = dp(12f).toInt()
        binding.barSearchButton.layoutParams = btnLp

        // 内容高度：展开 → 压缩；容器下移抵消卡片上移，内容行钉在状态栏之下
        val contentH = lerpDp(CONTENT_EXPANDED_DP, CONTENT_COLLAPSED_DP, progress)
        val containerLp = binding.topBarContainer.layoutParams as ViewGroup.MarginLayoutParams
        containerLp.topMargin = statusBarTop
        containerLp.height = contentH
        binding.topBarContainer.layoutParams = containerLp

        // 字号：大 → 小（展开态空间充裕，压缩态紧凑）
        barTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, lerpSp(TITLE_SP_EXPANDED, TITLE_SP_COLLAPSED, progress))
        barSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, lerpSp(SUBTITLE_SP_EXPANDED, SUBTITLE_SP_COLLAPSED, progress))

        // 水平对齐：展开居中 → 压缩靠左。本帧先按当前宽度估算，
        // 布局完成后（post）再用新鲜宽度重算——滚动停止后不再有几何调用，
        // 若只用滞后宽度，静止的顶栏会残留旧宽度算出的平移（标题溢出）
        val remain = 1f - progress
        applyBarTextAlignment(remain)
        titleBlock.post { if (_binding != null) applyBarTextAlignment(remain) }
    }

    /** dp 线性插值 */
    private fun lerpDp(from: Float, to: Float, progress: Float): Int =
        (dp(from) + (dp(to) - dp(from)) * progress).toInt()

    /** sp 线性插值 */
    private fun lerpSp(from: Float, to: Float, progress: Float): Float =
        from + (to - from) * progress

    /**
     * 标题/副标题水平对齐：展开态居中（translationX 补偿）→ 压缩态靠左（0）。
     * 宽度取视图自身 width（wrap_content = 文本实际宽），布局完成后始终有效；
     * GONE 期间（搜索态）跳过，避免用 0 宽度算出错误平移污染恢复后的位置。
     */
    private fun applyBarTextAlignment(remain: Float) {
        if (titleBlock.visibility != View.VISIBLE) return
        val usable = binding.topBarContainer.width - dp(32f)  // 容器宽 - 两侧 16dp padding
        if (usable <= 0) return
        barTitle.translationX = ((usable - barTitle.width) / 2f).coerceAtLeast(0f) * remain
        barSubtitle.translationX = ((usable - barSubtitle.width) / 2f).coerceAtLeast(0f) * remain
    }

    /** 按当前滚动偏移更新顶栏压缩进度（0 = 展开态，1 = 压缩态） */
    private fun updateTopBarImmersive() {
        val progress = (recyclerView.computeVerticalScrollOffset() / dp(96f))
            .coerceIn(0f, 1f)
        if (progress == topBarProgress) return
        topBarProgress = progress
        // 顶栏压缩过渡（高度、字号、对齐）
        applyTopBarGeometry(progress)
    }

    // ------------------------- 顶栏入场动画（Dock 切页过渡） -------------------------

    /**
     * Dock 切页入场：顶栏从屏幕外（含越界的状态栏部分）滑回原位。
     * 只做 translationY 位移——卡片背景不透明，位移不会透出壁纸；
     * 整卡 alpha<1 会离屏合成透出底层壁纸，禁止。
     */
    override fun playTopBarEntrance() {
        val bar = floatingTopBar
        // 切页瞬间 fragment 视图可能仍 detach（holder 在缓存中），不能用
        // doOnLayout：视图重新放置时若尺寸未变且无 FORCE_LAYOUT，View.layout
        // 会跳过 onLayoutChange，挂起的回调永不触发、动画静默丢失（实测二次
        // 切页复现）；preDraw 每帧绘制前必触发，切页后首帧瞬移即生效（无闪烁）
        bar.doOnPreDraw {
            if (_binding == null) return@doOnPreDraw
            // 先瞬移到屏幕外（首帧绘制前生效），下一帧再滑回原位
            bar.translationY = -(bar.height + statusBarTop).toFloat()
            bar.post {
                if (_binding == null) return@post
                bar.animate().translationY(0f).setDuration(240L)
                    .setInterpolator(DecelerateInterpolator(1.6f))
                    .start()
            }
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    /** Clicking any notification card opens its full details. */
    private fun handleCardClick(log: NotificationLog) {
        showNotificationDetail(log)
    }

    private fun showNotificationDetail(log: NotificationLog) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(log.appName)
            .setMessage("${log.title}\n - ${log.content}")
            .setPositiveButton("关闭", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回时刷新服务状态徽标（与设置页"服务监控"开关联动）
        updateServiceStatusBadge()
        // 从设置页等页面返回时恢复状态栏图标外观（按顶栏色自适应，
        // 覆盖其他页面对窗口状态栏外观的临时改动）
        if (_binding != null) {
            AppWallpaperApplier.applyConfigured(requireActivity(), Runnable {
                val contentRoot = requireActivity().findViewById<View>(android.R.id.content)
                if (contentRoot != null) CardStyleEngine.refreshAttachedCards(contentRoot)
            })
            applyStatusBarIconAppearance(
                requireActivity().window,
                requireActivity().window.decorView,
                floatingTopBar
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 顶栏"历史通知记录"旁的服务运行状态文本（🟢 运行中 / 🔴 未运行）。
     * 判定与设置页"服务监控"开关完全一致：通知访问权限 + 服务连接（[Ting.isConnected]）。
     * 实现为标题内联 span（紧随标题、自动跟随布局），状态部分缩小字号。
     */
    private fun updateServiceStatusBadge() {
        val b = _binding ?: return
        // 判定与设置页"服务监控"开关一致：权限 + 连接 + 未被手动暂停（持久化）
        val running = hasNotificationAccessPermission(requireContext()) && Ting.isConnected &&
                !TingConfig.servicePaused(requireContext())
        val title = "历史通知记录"
        val status = if (running) "🟢 运行中" else "🔴 未运行"
        val separator = "   "
        val text = SpannableString("$title$separator$status")
        text.setSpan(
            RelativeSizeSpan(0.62f),
            title.length + separator.length,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        b.barTitle.text = text
    }

    private fun insertTestNotification() {
        val testLog = NotificationLog(
            time = System.currentTimeMillis(),
            title = "欢迎使用听风",
            content = "这是一条自动生成的测试通知内容",
            appName = "测试应用",
            packageName = "com.example.test",
        )
        viewModel.insertTestNotification(testLog)
    }

    private fun updateUnreadCount(newCount: Int) {
        unreadCount = if (newCount < 0) 0 else newCount
        updateUnreadUI()
    }

    private fun resetUnreadCount() {
        unreadCount = 0
        updateUnreadUI()
    }

    private fun updateUnreadUI() {
        if (unreadCount > 0) {
            // 有未读消息：显示数字，改变图标样式（可选）
            btnUp.text = if (unreadCount > 99) "99+" else unreadCount.toString()
            // 如果 btnUp 是 icon-only 模式，需要确保 text 可见
            // 建议在 layout xml 中设置 btnUp 为 iconGravity="textStart"
            btnUp.setIconResource(R.drawable.up1) // 保持向上箭头
            // 可以改变背景色提示用户，例如变成强调色
            // btnUp.setBackgroundColor(...)
        } else {
            // 无未读消息：清空文字，只显示图标
            btnUp.text = ""
            btnUp.setIconResource(R.drawable.up1)
        }
    }
}
