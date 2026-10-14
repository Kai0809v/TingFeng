package com.dreamct.tingfeng.ui.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionManager
import com.dreamct.tingfeng.MainActivity
import com.dreamct.tingfeng.R
import com.dreamct.tingfeng.data.AppDatabase
import com.dreamct.tingfeng.data.UserListActivity
import com.dreamct.tingfeng.databinding.FragmentSettingsBinding
import com.dreamct.tingfeng.service.Ting
import com.dreamct.tingfeng.service.TingConfig
import com.dreamct.tingfeng.ui.Author
import com.dreamct.tingfeng.ui.home.CardStyleEngine
import com.dreamct.tingfeng.ui.home.NotificationCardView
import com.dreamct.tingfeng.ui.login.LoginActivity
import com.dreamct.tingfeng.ui.navigation.TopBarAnimatable
import com.dreamct.tingfeng.utilities.AppWallpaperApplier
import com.dreamct.tingfeng.utilities.applyStatusBarIconAppearance
import com.dreamct.tingfeng.utilities.hasNotificationAccessPermission
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment(), TopBarAnimatable {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val tag = "SettingsFragment"
    private var isSettingServiceSwitch = false // 防止程序设置触发监听器

    // 状态栏高度（systemBars.top，含挖孔）：顶栏负 margin 越界延伸进状态栏区域
    private var statusBarTop = 0

    private val appWallpaperPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || !isAdded) return@registerForActivityResult
        val canPersist = runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.isSuccess
        if (!canPersist) {
            Toast.makeText(requireContext(), "无法保留图片访问权限，请重新选择图片", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        TingConfig.setAppWallpaperUri(requireContext(), uri.toString())
        TingConfig.setAppWallpaperEnabled(requireContext(), true)
        _binding?.let { currentBinding ->
            currentBinding.appWallpaperSwitch.isChecked = true
            AppWallpaperApplier.loadPreview(requireActivity(), uri, currentBinding.appWallpaperPreview)
            currentBinding.appWallpaperStatus.text = "已选择图片，点击可更换"
        }
        updateWallpaperSourceControls()
        AppWallpaperApplier.applyConfigured(requireActivity(), Runnable {
            refreshWallpaperSettingsFromConfig()
            refreshStylePreview()
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // ViewModel 仅提供运行提示文案
        val settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        // 顶栏：与统计页/主页同款悬浮圆角卡片（负 margin 越界进状态栏）
        setupTopBar()

        // 状态栏图标颜色按顶栏背景自适应（暗色主题顶栏为深色 → 浅色图标）
        applyStatusBarIconAppearance(
            requireActivity().window,
            requireActivity().window.decorView,
            binding.settingsTopBar
        )

        // ******************************* 组件方法 ******************************************
        // 登录：跳转登录页
        binding.btnLogin.setOnClickListener {
            startActivity(Intent(requireActivity(), LoginActivity::class.java))
        }

        // 关于：跳转作者信息页
        binding.btnInfo.setOnClickListener {
            startActivity(Intent(requireActivity(), Author::class.java))
        }

        // 分享应用：功能后续实现
        binding.btnShare.setOnClickListener {
            // TODO: 实现分享应用
        }

        // 主题设置：功能后续实现
        binding.themeSetting.setOnClickListener {
            // TODO: 实现主题切换（跟随系统 / 浅色 / 深色）
        }

        // ------------------------- 壁纸背景 -------------------------
        // 系统壁纸与应用内壁纸互斥显示；系统壁纸透明度不作用于应用内图片。
        val wallpaperEnabled = TingConfig.wallpaperEnabled(requireContext())
        binding.wallpaperSwitch.isChecked = wallpaperEnabled
        val appWallpaperEnabled = TingConfig.appWallpaperEnabled(requireContext()) &&
                !TingConfig.appWallpaperUri(requireContext()).isNullOrBlank()
        binding.appWallpaperSwitch.isChecked = appWallpaperEnabled
        val appWallpaperUri = TingConfig.appWallpaperUri(requireContext())
        if (appWallpaperUri.isNullOrBlank()) {
            binding.appWallpaperStatus.text = "尚未选择图片"
        } else {
            binding.appWallpaperStatus.text = "已选择图片，点击可更换"
            runCatching {
                AppWallpaperApplier.loadPreview(
                    requireActivity(),
                    android.net.Uri.parse(appWallpaperUri),
                    binding.appWallpaperPreview
                )
            }
        }
        binding.appWallpaperAlphaSlider.value =
            TingConfig.appWallpaperAlpha(requireContext()) * 100f
        updateAppWallpaperAlphaText(binding.appWallpaperAlphaSlider.value)
        updateWallpaperSourceControls()

        binding.wallpaperSwitch.setOnCheckedChangeListener { _, isChecked ->
            TingConfig.setWallpaperEnabled(requireContext(), isChecked)
            updateWallpaperSourceControls()
            AppWallpaperApplier.applyConfigured(requireActivity())
        }
        binding.appWallpaperSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && TingConfig.appWallpaperUri(requireContext()).isNullOrBlank()) {
                binding.appWallpaperSwitch.isChecked = false
                appWallpaperPicker.launch(arrayOf("image/*"))
                return@setOnCheckedChangeListener
            }
            TingConfig.setAppWallpaperEnabled(requireContext(), isChecked)
            updateWallpaperSourceControls()
            AppWallpaperApplier.applyConfigured(requireActivity(), Runnable {
                refreshWallpaperSettingsFromConfig()
            })
        }
        binding.appWallpaperSelect.setOnClickListener {
            appWallpaperPicker.launch(arrayOf("image/*"))
        }

        // 初始化透明度滑杆（0~100，无阈值限制）
        binding.wallpaperAlphaSlider.value =
            TingConfig.wallpaperAlpha(requireContext()) * 100f
        updateAlphaText(binding.wallpaperAlphaSlider.value)
        binding.wallpaperAlphaSlider.addOnChangeListener { _, value, fromUser ->
            updateAlphaText(value)
            if (fromUser) {
                TingConfig.setWallpaperAlpha(requireContext(), value / 100f)
                AppWallpaperApplier.applyConfigured(requireActivity())
            }
        }

        binding.appWallpaperAlphaSlider.addOnChangeListener { _, value, fromUser ->
            updateAppWallpaperAlphaText(value)
            if (fromUser) TingConfig.setAppWallpaperAlpha(requireContext(), value / 100f)
        }
        binding.appWallpaperAlphaSlider.addOnSliderTouchListener(
            object : com.google.android.material.slider.Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) = Unit
                override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                    AppWallpaperApplier.applyConfigured(requireActivity(), Runnable {
                        refreshStylePreview()
                    })
                }
            }
        )

        // ------------------------- 卡片样式（通知卡片个性化） -------------------------
        setupCardStyleSection()

        // ------------------------- 通知过滤（高级设置入口） -------------------------
        setupFilterEntry()

        // ------------------------- 服务监控（由 Home 顶栏迁移） -------------------------
        // 注册监听器前先武装防护标志：导航返回时系统会在 onResume 之前恢复视图状态
        // （开关被重设为离开时的值并触发监听器），若不屏蔽会被误判为用户操作，
        // 导致重复执行绑定流程并弹出"服务已启动" Toast。
        // 标志由 onResume 中的 refreshServiceSwitchState() 解除。
        isSettingServiceSwitch = true
        binding.serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isSettingServiceSwitch) {
                return@setOnCheckedChangeListener
            }
            handleServiceSwitchChange(isChecked)
        }

        // ------------------------- 高级设置（由原 DevSettings 迁移） -------------------------
        // 通知设置：功能后续实现
        binding.notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            // TODO: 实现应用内通知开关
        }

        // 自动重启开关
        binding.configSwitch.isChecked = TingConfig.shouldRestart(requireContext())
        binding.configSwitch.setOnCheckedChangeListener { _, isChecked ->
            TingConfig.setShouldRestart(requireContext(), isChecked)
            Log.d(tag, "重启开关状态：$isChecked")
        }

        // 自动更新：功能后续实现
        binding.autoUpdateSwitch.setOnCheckedChangeListener { _, isChecked ->
            // TODO: 实现自动更新检查
        }

        // 清除记录：确认后异步删除所有通知记录
        binding.notificationData.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除通知记录")
                .setMessage("确定删除所有通知记录吗？")
                .setPositiveButton("删除") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(requireContext()).notifyDao().deleteAll()
                        }
                        Toast.makeText(requireContext(), "通知记录已删除", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 数据使用：跳转用户列表
        binding.dataUsage.setOnClickListener {
            startActivity(Intent(requireActivity(), UserListActivity::class.java))
        }

        // 权限管理：跳转系统通知访问权限设置
        binding.permission.setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        // 运行提示文案
        settingsViewModel.text.observe(viewLifecycleOwner) { binding.tipsText.text = it }

        // 注意：本页顶栏负 margin 越界延伸进状态栏，禁止整树 alpha 淡入
        // （alpha<1 时整树离屏合成透出底层壁纸，状态栏区域闪壁纸色），
        // 原 180ms 整树淡入已随顶栏加入而移除。

        // Dock 切页触发的顶栏入场动画：视图创建晚于切页回调时在此领取待播动画
        if (consumePendingTopBarEntranceFromActivity()) playTopBarEntrance()

        return binding.root
    }

    // ------------------------- 顶栏（统计页/主页同款圆角卡片） -------------------------

    /**
     * 顶栏：悬浮圆角卡片，负 margin 越界延伸到状态栏后面（与统计页一致）。
     * 依赖从 fragment 根向上逐层关闭父容器裁剪（doOnAttach 中执行，覆盖
     * ViewPager2 横竖屏旋转等视图重建场景）；滚动内容经 settingsScroll 的
     * clipToPadding=false 滑到顶栏背后被遮住（顶栏在 FrameLayout 中后添加在上层）。
     */
    private fun setupTopBar() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            statusBarTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            applyTopBarGeometry()
            insets
        }
        binding.root.doOnAttach { v ->
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
        binding.root.doOnLayout { applyTopBarGeometry() }
    }

    /** 顶栏负 margin 越界 + 滚动内容顶部避让（insets 与布局就绪后都会重算） */
    private fun applyTopBarGeometry() {
        if (statusBarTop <= 0) return
        val lp = binding.settingsTopBar.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = -statusBarTop
        binding.settingsTopBar.layoutParams = lp
        // 越界 padding 设在内部内容视图（CardView 的 setPadding 不可靠）
        binding.settingsTopBarContent.setPadding(
            dp(20f), statusBarTop + dp(12f), dp(20f), dp(12f)
        )
        binding.settingsTopBar.doOnLayout { bar ->
            binding.settingsScroll.setPadding(
                binding.settingsScroll.paddingLeft,
                statusBarTop + bar.height + dp(8f),
                binding.settingsScroll.paddingRight,
                binding.settingsScroll.paddingBottom
            )
        }
    }

    /**
     * Dock 切页入场：顶栏从屏幕外（含越界的状态栏部分）滑回原位。
     * 只做 translationY 位移——卡片背景不透明，位移不会透出壁纸。
     */
    override fun playTopBarEntrance() {
        val bar = binding.settingsTopBar
        // 切页瞬间 fragment 视图可能仍 detach（holder 在缓存中），不能用
        // doOnLayout（布局分支跳过时回调永不触发、动画静默丢失）；
        // preDraw 每帧绘制前必触发，切页后首帧瞬移即生效（无闪烁）
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

    /** 从 MainActivity 领取待播的顶栏入场动画（切页回调先于本页视图创建时） */
    private fun consumePendingTopBarEntranceFromActivity(): Boolean {
        val main = activity as? MainActivity ?: return false
        return main.consumePendingTopBarEntrance()
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    ).toInt()

    /** 更新透明度百分比显示 */
    private fun updateAlphaText(value: Float) {
        binding.wallpaperAlphaText.text = "${value.toInt()}%"
    }

    private fun updateAppWallpaperAlphaText(value: Float) {
        binding.appWallpaperAlphaText.text = "${value.toInt()}%"
    }

    private fun updateWallpaperSourceControls() {
        if (_binding == null) return
        val hasAppWallpaper = !TingConfig.appWallpaperUri(requireContext()).isNullOrBlank()
        val appActive = TingConfig.appWallpaperEnabled(requireContext()) && hasAppWallpaper
        val systemEnabled = TingConfig.wallpaperEnabled(requireContext())
        TransitionManager.beginDelayedTransition(binding.root)
        binding.appWallpaperAlphaSection.visibility = if (appActive) View.VISIBLE else View.GONE
        binding.wallpaperAlphaSection.visibility =
            if (systemEnabled && !appActive) View.VISIBLE else View.GONE
        binding.wallpaperSwitch.isEnabled = !appActive
        binding.wallpaperSourceDescription.text = if (appActive) {
            "应用内壁纸已启用，暂时替代系统壁纸"
        } else {
            "将应用背景替换为当前系统壁纸"
        }
    }

    private fun refreshWallpaperSettingsFromConfig() {
        val currentBinding = _binding ?: return
        val uri = TingConfig.appWallpaperUri(requireContext())
        currentBinding.appWallpaperSwitch.isChecked =
            TingConfig.appWallpaperEnabled(requireContext()) && !uri.isNullOrBlank()
        if (uri.isNullOrBlank()) {
            currentBinding.appWallpaperStatus.text = "尚未选择图片"
        }
        updateWallpaperSourceControls()
    }

    // ------------------------- 卡片样式（通知卡片个性化） -------------------------

    /** "卡片样式"面板是否展开 */
    private var cardStyleExpanded = false

    /** 卡片样式实时预览卡（复用 [NotificationCardView]，样式/滑块变化即时应用） */
    private var stylePreviewCard: NotificationCardView? = null

    /**
     * 折叠式"卡片样式"面板：单选样式类型（动态颜色 / 渐变配色 / 纯色半透明 /
     * 自定义图片·开发中）；渐变配色选中时再展开预设色卡列表，
     * 色卡由扇形色板（[GradientSwatchView]）+ 配色名称组成，点击即选用。
     * 配置持久化到 [TingConfig]，由 [CardStyleEngine] 应用到通知卡片；
     * 每次配置变化都会经 [refreshHomeCards] 实时刷新主页卡片。
     */
    private fun setupCardStyleSection() {
        val context = requireContext()

        // 初始化单选状态、渐变色卡列表可见性与摘要文案
        val type = TingConfig.cardStyleType(context)
        binding.rbCardStyleDynamic.isChecked = type == CardStyleEngine.TYPE_DYNAMIC
        binding.rbCardStyleGradient.isChecked = type == CardStyleEngine.TYPE_GRADIENT
        binding.rbCardStyleBlur.isChecked = type == CardStyleEngine.TYPE_BLUR
        binding.rbCardStyleCustom.isChecked = type == CardStyleEngine.TYPE_CUSTOM
        // 自定义图片样式尚未开发，选项禁用
        binding.rbCardStyleCustom.isEnabled = false
        binding.gradientList.visibility =
            if (type == CardStyleEngine.TYPE_GRADIENT) View.VISIBLE else View.GONE
        // 雾度滑块：初始值与可见性（纯色半透明样式选中时展开）
        binding.blurFrostPanel.visibility =
            if (type == CardStyleEngine.TYPE_BLUR) View.VISIBLE else View.GONE
        binding.blurFrostSlider.value =
            (TingConfig.cardBlurFrost(context) * 100f).coerceIn(0f, 100f)
        updateCardStyleSummary(type)

        // 展开 / 折叠（过渡动画 + 箭头旋转指示）
        binding.cardStyleHeader.setOnClickListener {
            cardStyleExpanded = !cardStyleExpanded
            TransitionManager.beginDelayedTransition(binding.root)
            binding.cardStylePanel.visibility =
                if (cardStyleExpanded) View.VISIBLE else View.GONE
            binding.cardStyleChevron.animate()
                .rotation(if (cardStyleExpanded) 180f else 0f)
                .setDuration(200L)
                .start()
        }

        // 单选保存
        binding.cardStyleGroup.setOnCheckedChangeListener { _, checkedId ->
            val newType = when (checkedId) {
                R.id.rb_card_style_gradient -> CardStyleEngine.TYPE_GRADIENT
                R.id.rb_card_style_blur -> CardStyleEngine.TYPE_BLUR
                R.id.rb_card_style_custom -> CardStyleEngine.TYPE_CUSTOM
                else -> CardStyleEngine.TYPE_DYNAMIC
            }
            TingConfig.setCardStyleType(context, newType)
            // 渐变色卡列表在"渐变配色"选项正下方展开 / 折叠（过渡动画）
            TransitionManager.beginDelayedTransition(binding.root)
            binding.gradientList.visibility =
                if (newType == CardStyleEngine.TYPE_GRADIENT) View.VISIBLE else View.GONE
            // 雾度滑块面板跟随样式类型展开 / 折叠
            binding.blurFrostPanel.visibility =
                if (newType == CardStyleEngine.TYPE_BLUR) View.VISIBLE else View.GONE
            updateCardStyleSummary(newType)
            refreshStylePreview()
            refreshHomeCards()
        }

        // 雾度滑块：拖动即保存 + 实时预览
        binding.blurFrostSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            TingConfig.setCardBlurFrost(context, value / 100f)
            if (TingConfig.cardStyleType(context) == CardStyleEngine.TYPE_BLUR) {
                updateCardStyleSummary(CardStyleEngine.TYPE_BLUR)
            }
            refreshStylePreview()
            refreshHomeCards()
        }

        buildGradientCards()
        setupStylePreviewCard()
    }

    /**
     * 卡片样式配置变化后刷新主页已常驻的卡片：
     * ViewPager2 架构下主页视图不随切页销毁重建，样式改动必须主动重应用。
     */
    private fun refreshHomeCards() {
        (activity as? MainActivity)?.refreshHomeCards()
    }

    /** 构建实时预览卡：一张真实 [NotificationCardView]（构造时经 CardStyleEngine 应用当前样式） */
    private fun setupStylePreviewCard() {
        val container = binding.cardStylePreviewContainer
        container.removeAllViews()
        val card = NotificationCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            appIcon.setImageResource(R.mipmap.ic_launcher)
            titleText.text = "通知标题预览"
            timeText.text = "现在"
            contentText.text = "拖动上方滑块或切换样式，卡片效果即时更新。"
            isClickable = false
            isFocusable = false
        }
        container.addView(card)
        stylePreviewCard = card
    }

    /** 重新应用当前样式到预览卡（滑块拖动/样式切换/壁纸变化时调用） */
    private fun refreshStylePreview() {
        stylePreviewCard?.let { CardStyleEngine.applyStyle(it) }
    }

    /** 构建渐变配色预设色卡：扇形色板 + 配色名称，点击选用并高亮 */
    private fun buildGradientCards() {
        val context = requireContext()
        val density = resources.displayMetrics.density
        val row = binding.gradientRow
        row.removeAllViews()

        CardStyleEngine.GRADIENT_PRESETS.forEach { preset ->
            val card = MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (96 * density).toInt(), (108 * density).toInt()
                ).apply { marginEnd = (12 * density).toInt() }
                radius = 14 * density
                cardElevation = 0f
                isClickable = true
                isFocusable = true
                tag = preset.id
            }

            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            val swatch = GradientSwatchView(context).apply {
                colors = preset.colors
                layoutParams = LinearLayout.LayoutParams(
                    (48 * density).toInt(), (48 * density).toInt()
                )
            }
            val name = TextView(context).apply {
                text = preset.name
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(CardStyleEngine.themeColor(
                    context, androidx.appcompat.R.attr.colorPrimary, 0xFF415F91.toInt()
                ))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (8 * density).toInt() }
            }
            content.addView(swatch)
            content.addView(name)
            card.addView(content)

            card.setOnClickListener {
                TingConfig.setCardGradientId(context, preset.id)
                refreshGradientSelection()
                updateCardStyleSummary(CardStyleEngine.TYPE_GRADIENT)
                refreshStylePreview()
                refreshHomeCards()
            }
            row.addView(card)
        }
        refreshGradientSelection()
    }

    /** 按持久化配置刷新色卡选中态描边 */
    private fun refreshGradientSelection() {
        val context = requireContext()
        val selected = TingConfig.cardGradientId(context)
        val primary = CardStyleEngine.themeColor(
            context, androidx.appcompat.R.attr.colorPrimary, 0xFF415F91.toInt()
        )
        val density = resources.displayMetrics.density
        val row = binding.gradientRow
        for (i in 0 until row.childCount) {
            val card = row.getChildAt(i) as MaterialCardView
            val isSelected = card.tag == selected
            card.strokeWidth = ((if (isSelected) 2f else 1f) * density).toInt()
            card.strokeColor = if (isSelected) primary else 0x1F000000
        }
    }

    /** 摘要文案显示当前样式（渐变配色附带配色名称，纯色半透明附带滑块数值） */
    private fun updateCardStyleSummary(type: String) {
        val name = when (type) {
            CardStyleEngine.TYPE_GRADIENT ->
                "渐变 · ${CardStyleEngine.findPreset(TingConfig.cardGradientId(requireContext())).name}"
            CardStyleEngine.TYPE_BLUR ->
                "纯色半透明 · 雾度${(TingConfig.cardBlurFrost(requireContext()) * 100).toInt()}%"
            CardStyleEngine.TYPE_CUSTOM -> "自定义"
            else -> "动态颜色"
        }
        binding.cardStyleSummary.text = "当前：$name"
    }

    // ------------------------- 通知过滤（屏蔽应用 / 关键词） -------------------------

    /** 高级设置中的"通知过滤"入口：点击弹出过滤配置面板 */
    private fun setupFilterEntry() {
        binding.filterEntry.setOnClickListener { showFilterBottomSheet() }
        updateFilterSummary()
    }

    /** 高级设置入口的摘要：展示当前已屏蔽的应用数 / 关键词数 */
    private fun updateFilterSummary() {
        val apps = TingConfig.blockedPackages(requireContext()).size
        val keywords = TingConfig.blockedKeywords(requireContext()).size
        binding.filterSummary.text = when {
            apps == 0 && keywords == 0 -> "屏蔽指定应用与关键词，命中即不记录"
            else -> "已屏蔽 $apps 个应用、$keywords 个关键词"
        }
    }

    /**
     * 通知过滤配置面板（BottomSheetDialog，复用原主页入口的布局）：
     *  - 屏蔽指定应用：多选已安装可启动应用，选中的包名在服务记录通知时直接忽略；
     *  - 关键词屏蔽：标题或内容命中任一关键词（不区分大小写）即不记录。
     * 配置持久化到 [TingConfig]，[Ting] 服务每次收到通知时实时读取，改动即时生效。
     */
    private fun showFilterBottomSheet() {
        val ctx = requireContext()
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
        val sheet = layoutInflater.inflate(R.layout.bottom_sheet_settings, null)
        dialog.setContentView(sheet)
        dialog.behavior.state =
            com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED

        // getStringSet 返回值不可直接修改，必须复制
        val blocked = TingConfig.blockedPackages(ctx).toMutableSet()
        val keywords = TingConfig.blockedKeywords(ctx).toMutableSet()

        val blockedList = sheet.findViewById<LinearLayout>(R.id.layout_blocked_list)!!
        val emptyBlocked = sheet.findViewById<TextView>(R.id.txt_empty_blocked_apps)!!
        val contentBlocked = sheet.findViewById<View>(R.id.content_blocked_apps)!!
        val iconBlocked = sheet.findViewById<ImageView>(R.id.icon_blocked_apps)!!

        val keywordsList = sheet.findViewById<LinearLayout>(R.id.layout_keywords_list)!!
        val emptyKeywords = sheet.findViewById<TextView>(R.id.txt_empty_keywords)!!
        val contentKeywords = sheet.findViewById<View>(R.id.content_keywords)!!
        val iconKeywords = sheet.findViewById<ImageView>(R.id.icon_keywords)!!
        val input = sheet.findViewById<com.google.android.material.textfield.TextInputEditText>(
            R.id.et_keyword
        )!!

        val pad = (12f * resources.displayMetrics.density).toInt()

        /** 展开行/收起行的通用切换（高度 + 淡入淡出过渡，箭头同步旋转）。
         *  注意：content 自身的 visibility 变化要被 Transition 捕获，
         *  beginDelayedTransition 必须作用于其父容器（作用域=场景根）。 */
        fun toggleSection(content: View, icon: ImageView) {
            val expand = content.visibility == View.GONE
            val parent = content.parent as? ViewGroup
            if (parent != null) {
                val transition = androidx.transition.AutoTransition().apply { duration = 220 }
                TransitionManager.beginDelayedTransition(parent, transition)
            }
            content.visibility = if (expand) View.VISIBLE else View.GONE
            icon.animate().rotation(if (expand) 180f else 0f).setDuration(220L).start()
        }

        /** 渲染已屏蔽应用动态行：应用名 + 移除按钮 */
        fun refreshBlockedRows() {
            blockedList.removeAllViews()
            if (blocked.isEmpty()) {
                emptyBlocked.visibility = View.VISIBLE
                blockedList.visibility = View.GONE
                return
            }
            emptyBlocked.visibility = View.GONE
            blockedList.visibility = View.VISIBLE
            for (pkg in blocked.sorted()) {
                val appName = try {
                    ctx.packageManager.getApplicationLabel(
                        ctx.packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                } catch (e: Exception) {
                    pkg
                }
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(pad, pad, pad, pad)
                }
                val nameView = TextView(ctx).apply {
                    text = appName
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val remove = ImageView(ctx).apply {
                    setImageResource(R.drawable.close)
                    setColorFilter(0x8A000000.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
                    setOnClickListener {
                        blocked.remove(pkg)
                        TingConfig.setBlockedPackages(ctx, blocked)
                        refreshBlockedRows()
                        updateFilterSummary()
                    }
                }
                row.addView(nameView)
                row.addView(remove, ViewGroup.LayoutParams(
                    (24f * resources.displayMetrics.density).toInt(),
                    (24f * resources.displayMetrics.density).toInt()
                ))
                blockedList.addView(row)
            }
        }

        /** 渲染已屏蔽关键词动态行：关键词 + 移除按钮 */
        fun refreshKeywordRows() {
            keywordsList.removeAllViews()
            if (keywords.isEmpty()) {
                emptyKeywords.visibility = View.VISIBLE
                keywordsList.visibility = View.GONE
                return
            }
            emptyKeywords.visibility = View.GONE
            keywordsList.visibility = View.VISIBLE
            for (kw in keywords.sorted()) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(pad, pad, pad, pad)
                }
                val kwView = TextView(ctx).apply {
                    text = kw
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val remove = ImageView(ctx).apply {
                    setImageResource(R.drawable.close)
                    setColorFilter(0x8A000000.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
                    setOnClickListener {
                        keywords.remove(kw)
                        TingConfig.setBlockedKeywords(ctx, keywords)
                        refreshKeywordRows()
                        updateFilterSummary()
                    }
                }
                row.addView(kwView)
                row.addView(remove, ViewGroup.LayoutParams(
                    (24f * resources.displayMetrics.density).toInt(),
                    (24f * resources.displayMetrics.density).toInt()
                ))
                keywordsList.addView(row)
            }
        }

        // 展开收起
        sheet.findViewById<View>(R.id.header_blocked_apps).setOnClickListener {
            toggleSection(contentBlocked, iconBlocked)
        }
        sheet.findViewById<View>(R.id.header_keywords).setOnClickListener {
            toggleSection(contentKeywords, iconKeywords)
        }

        // 选择应用：多选已安装、有桌面入口的用户应用（排除自身）
        sheet.findViewById<View>(R.id.btn_block_app).setOnClickListener {
            val pm = ctx.packageManager
            val launchables = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            )
            val items = launchables.mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName) return@mapNotNull null
                val name = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    pkg
                }
                name to pkg
            }.distinctBy { it.second }.sortedBy { it.first.lowercase() }
            if (items.isEmpty()) {
                Toast.makeText(ctx, "未找到可选应用", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val names = items.map { it.first }.toTypedArray()
            val checked = items.map { it.second in blocked }.toBooleanArray()
            MaterialAlertDialogBuilder(ctx)
                .setTitle("选择要屏蔽的应用")
                .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                    if (isChecked) blocked.add(items[which].second)
                    else blocked.remove(items[which].second)
                }
                .setPositiveButton("确定") { _, _ ->
                    TingConfig.setBlockedPackages(ctx, blocked)
                    refreshBlockedRows()
                    updateFilterSummary()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 添加关键词（trim / 非空 / 去重，忽略大小写重复）
        sheet.findViewById<View>(R.id.btn_add_keyword).setOnClickListener {
            val kw = input.text?.toString()?.trim().orEmpty()
            when {
                kw.isEmpty() -> Toast.makeText(ctx, "请输入关键词", Toast.LENGTH_SHORT).show()
                keywords.any { it.equals(kw, ignoreCase = true) } ->
                    Toast.makeText(ctx, "关键词已存在", Toast.LENGTH_SHORT).show()
                else -> {
                    keywords.add(kw)
                    TingConfig.setBlockedKeywords(ctx, keywords)
                    input.setText("")
                    refreshKeywordRows()
                    updateFilterSummary()
                }
            }
        }

        refreshBlockedRows()
        refreshKeywordRows()
        dialog.show()
    }

    // ------------------------- 服务监控逻辑（由 HomeFragment 迁移） -------------------------

    /** onResume 时刷新开关真实状态（权限 + 服务连接 + 手动暂停标志）。 */
    private fun refreshServiceSwitchState() {
        val connected = isNotificationServiceEnabled() && Ting.isConnected &&
                !TingConfig.servicePaused(requireContext())
        isSettingServiceSwitch = true
        binding.serviceSwitch.isChecked = connected
        isSettingServiceSwitch = false
    }

    /**
     * 处理用户手动切换开关的操作（含异步状态检查和失败自动回滚）
     */
    private fun handleServiceSwitchChange(isChecked: Boolean) {
        if (isChecked) {
            // 用户尝试打开：解除持久化的暂停标志
            TingConfig.setServicePaused(requireContext(), false)
            if (!isNotificationServiceEnabled()) {
                // 没有权限，回弹开关
                setServiceSwitchProgrammatically(false)
                showPermissionDialog()
            } else {
                // 有权限，执行绑定流程（包含重试机制）
                lifecycleScope.launch {
                    val success = performBindWithRetry()
                    if (success) {
                        Toast.makeText(requireContext(), "服务已启动", Toast.LENGTH_SHORT).show()
                    } else {
                        // 绑定失败，回滚开关状态
                        setServiceSwitchProgrammatically(false)
                        Toast.makeText(
                            requireContext(),
                            "服务启动失败，请尝试重启应用或重新授权",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } else {
            // 用户手动关闭：持久化暂停（不记录新通知、不自动重连，重启应用后仍生效），
            // NotificationListenerService 本身难以真正"停止"
            TingConfig.setServicePaused(requireContext(), true)
            Toast.makeText(requireContext(), "服务监控已暂停", Toast.LENGTH_SHORT).show()
        }
    }

    /** 程序化设置开关状态，不触发监听器 */
    private fun setServiceSwitchProgrammatically(checked: Boolean) {
        isSettingServiceSwitch = true
        binding.serviceSwitch.isChecked = checked
        isSettingServiceSwitch = false
    }

    /** 执行绑定逻辑，带重试和强力重连机制 */
    private suspend fun performBindWithRetry(): Boolean {
        return withContext(Dispatchers.Main) {
            // 1. 第一次尝试：普通重连
            Ting.bindNotificationService(requireContext())
            if (waitForConnection(2000)) return@withContext true

            // 2. 第二次尝试：强力重连（切换组件状态）
            try {
                Ting.toggleNotificationListenerService(requireContext())
            } catch (e: Exception) {
                Log.e(tag, "强力重连异常", e)
            }
            if (waitForConnection(3000)) return@withContext true

            false
        }
    }

    /** 轮询检查连接状态 */
    private suspend fun waitForConnection(timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (Ting.isConnected) {
                return true
            }
            delay(200)
        }
        return false
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return hasNotificationAccessPermission(requireContext())
    }

    private fun showPermissionDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("需要通知访问权限")
            .setMessage("此功能需要访问通知的权限。请点击确定前往设置页面开启权限。")
            .setPositiveButton("确定") { _, _ ->
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        refreshServiceSwitchState()
        // 恢复状态栏图标外观（按顶栏色自适应，覆盖其他页面对窗口的临时改动）
        if (_binding != null) {
            applyStatusBarIconAppearance(
                requireActivity().window,
                requireActivity().window.decorView,
                binding.settingsTopBar
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stylePreviewCard = null
        _binding = null
    }
}
