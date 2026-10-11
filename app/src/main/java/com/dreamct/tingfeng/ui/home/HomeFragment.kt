package com.dreamct.tingfeng.ui.home

import android.os.Bundle
import android.graphics.drawable.ColorDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.dreamct.tingfeng.R
import com.dreamct.tingfeng.data.AppDatabase
import com.dreamct.tingfeng.data.NotificationLog
import com.dreamct.tingfeng.databinding.FragmentHomeBinding
import com.dreamct.tingfeng.service.Ting
import com.dreamct.tingfeng.service.TingConfig
import com.dreamct.tingfeng.utilities.hasNotificationAccessPermission
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    // ********************** ViewBinding **********************
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    // ********************** Component **********************
    private lateinit var appBar :MaterialToolbar
    private lateinit var searchEditText: TextInputEditText
    private lateinit var searchInputLayout: TextInputLayout
    private lateinit var btnTest: MaterialButton
    private lateinit var btnUp: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var appbarScrim: View
    private lateinit var appbarDivider: View
    private lateinit var topBarOutline: TopBarOutlineView
    private lateinit var floatingTopBar: MaterialCardView
    private lateinit var viewModel: HomeViewModel
    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var stackLayoutManager: StackedCardLayoutManager

    // 顶栏沉浸进度缓存（-1 = 尚未初始化，强制首次应用）
    private var topBarProgress = -1f



    // 新增：未读消息计数器
    private var unreadCount = 0
    // 新增：记录上一次列表的最顶端位置，用于判断是否有新消息插入
    private var lastTopPosition = 0
    private val transition = MaterialFadeThrough().apply {
        duration = 300
    }
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

        // 添加数据库实例获取
        val dao = AppDatabase.getInstance(requireContext()).notifyDao()

        // 使用ViewModelFactory初始化ViewModel
        val factory = HomeViewModelFactory(dao)
        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]

        // ********************** ViewBinding *****************************************
        appBar = binding.topAppBar
        searchEditText = binding.searchEditText
        searchInputLayout = binding.searchInputLayout
        recyclerView = binding.cardRecyclerView
        btnTest = binding.test
        btnUp = binding.goUpon
        btnSettings = binding.settings
        appbarScrim = binding.appbarScrim
        appbarDivider = binding.appbarDivider
        topBarOutline = binding.topBarOutline
        // 轮廓颜色直接取自下边缘线的背景（主题色已在 XML 解析，避免引用 material R）
        (appbarDivider.background as? ColorDrawable)?.let {
            topBarOutline.outlineColor = it.color
        }
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

        // 顶栏沉浸：滚动时渐显背景并与状态栏融为一体
        setupTopBarImmersion()

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

                    if (firstVisiblePos > 0 && notifications.isNotEmpty()) {
                        // 这是一个简化逻辑：假设每次 collect 都是因为插入了一条新数据
                        // 严谨的做法是在 Adapter 的 DataObserver 中做，但这里更简单
                        val previousCount = notificationAdapter.itemCount
                        val newCount = notifications.size
                        if (newCount > previousCount) {
                            val addedCount = newCount - previousCount
                            updateUnreadCount(unreadCount + addedCount)
                        }
                    }

                    notificationAdapter.submitList(notifications) {
                        // 数据更新完成后，如果之前在顶部，保持在顶部（避免被新消息顶下去）
                        if (firstVisiblePos == 0) {
                            recyclerView.scrollToPosition(0)
                        }
                    }
                }
        }

        // 观察 ViewModel 中的开关状态（服务开关已迁移至设置页）

        // 监听搜索框的文本变化
        searchEditText.doOnTextChanged { text, _, _, _ ->
            viewModel.setSearchQuery(text.toString())
        }

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

        btnSettings.setOnClickListener {
            showSettingsBottomSheet()
        }

        // 点击导航图标 -> 切换到搜索栏
        appBar.setNavigationOnClickListener {
            TransitionManager.beginDelayedTransition(binding.topBarContainer, transition)
            appBar.visibility = View.GONE
            searchInputLayout.visibility = View.VISIBLE
            searchInputLayout.requestFocus()
        }

        // 搜索栏的起始图标点击事件 -> 切换到应用栏
        searchInputLayout.setStartIconOnClickListener {
            appBar.visibility = View.VISIBLE
            searchInputLayout.visibility = View.GONE
        }
    }
    // ------------------------- 顶栏沉浸（滚动渐显 + 与状态栏融合） -------------------------

    /**
     * 沉浸式顶栏（简单过渡）：
     *  1. 静态：实色圆角顶栏贴于状态栏下沿，TopBarOutlineView 绘制完整轮廓；
     *  2. 滚动中：scrim（覆盖状态栏 + 顶栏区域）渐显为 surface 色连成整块，
     *     轮廓自上而下逐段擦除——上半段先"融入"状态栏；
     *  3. 融合后：轮廓完全消失，过渡段末尾只渐显一条下边缘线。
     * 整个过程由滚动偏移直接驱动（约 96dp 滑动完成），可逆、无跳变。
     */
    private fun setupTopBarImmersion() {
        // 状态栏高度就绪后定位 scrim（负向平移进入状态栏区域）与下边缘线
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            appbarScrim.translationY = -top.toFloat()
            appbarScrim.layoutParams.height = top + topBarPillBottom()
            appbarDivider.translationY = (topBarPillBottom() - 1).toFloat()
            appbarScrim.requestLayout()
            insets
        }
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateTopBarImmersive()
            }
        })
        updateTopBarImmersive()
        // 布局完成后立即应用顶栏初始几何（居中胶囊），避免首帧使用 XML 回退边距
        binding.root.doOnLayout { applyTopBarGeometry(topBarProgress.coerceIn(0f, 1f)) }
    }

    /** 悬浮顶栏底部在 fragment 坐标系中的位置（工具栏高度） */
    private fun topBarPillBottom(): Int {
        val tv = TypedValue()
        val barHeight = if (requireContext().theme.resolveAttribute(
                android.R.attr.actionBarSize, tv, true
            )
        ) {
            TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        } else {
            dp(56f).toInt()
        }
        return barHeight
    }

    /**
     * 顶栏几何插值（progress: 0 = 悬浮胶囊, 1 = 全宽矩形顶栏）：
     *  - 左右边距：父容器宽度各 20%（居中胶囊）→ 0（全宽），胶囊边缘向两侧扩展，
     *    左上角随之上移——视觉上即"胶囊向左上移动展开成矩形顶栏"；
     *  - 上边距：16dp（状态栏下沿之下）→ 0（贴住状态栏）；
     *  - 圆角：28dp（胶囊）→ 0（矩形）；
     *  - 轮廓：由上往下逐段擦除（TopBarOutlineView）。
     * 修改初始圆角时需同步 fragment_home.xml 的 app:cardCornerRadius。
     */
    private fun applyTopBarGeometry(progress: Float) {
        val parentView = floatingTopBar.parent as? ViewGroup ?: return
        if (parentView.width == 0) return
        val lp = floatingTopBar.layoutParams as ViewGroup.MarginLayoutParams
        val remain = 1f - progress
        val sideMargin = (parentView.width * 0.2f * remain).toInt()
        lp.leftMargin = sideMargin
        lp.rightMargin = sideMargin
        lp.topMargin = (dp(16f) * remain).toInt()
        floatingTopBar.layoutParams = lp
        floatingTopBar.radius = dp(28f) * remain
        topBarOutline.outlineProgress = progress
    }

    /** 按当前滚动偏移更新顶栏沉浸进度（0 = 悬浮胶囊，1 = 融合为矩形顶栏） */
    private fun updateTopBarImmersive() {
        val progress = (recyclerView.computeVerticalScrollOffset() / dp(96f))
            .coerceIn(0f, 1f)
        if (progress == topBarProgress) return
        topBarProgress = progress
        // 胶囊 → 矩形顶栏的几何过渡（边距、圆角、轮廓擦除）
        applyTopBarGeometry(progress)
        // scrim 渐显：状态栏 + 顶栏区域连成整块背景
        appbarScrim.alpha = progress
        // 融合后期（70% 之后）只渐显一条下边缘线
        appbarDivider.alpha = ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    /**展示设置底部弹窗 */
    private fun showSettingsBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_settings, null)
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheetDialog.show()
    }

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
        b.topAppBar.title = text
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
