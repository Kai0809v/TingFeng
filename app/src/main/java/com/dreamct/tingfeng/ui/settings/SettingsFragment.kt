package com.dreamct.tingfeng.ui.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionManager
import com.dreamct.tingfeng.R
import com.dreamct.tingfeng.data.AppDatabase
import com.dreamct.tingfeng.data.UserListActivity
import com.dreamct.tingfeng.databinding.FragmentSettingsBinding
import com.dreamct.tingfeng.service.Ting
import com.dreamct.tingfeng.service.TingConfig
import com.dreamct.tingfeng.ui.Author
import com.dreamct.tingfeng.ui.home.CardStyleEngine
import com.dreamct.tingfeng.ui.login.LoginActivity
import com.dreamct.tingfeng.utilities.WallpaperApplier
import com.dreamct.tingfeng.utilities.hasNotificationAccessPermission
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val tag = "SettingsFragment"
    private var isSettingServiceSwitch = false // 防止程序设置触发监听器

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // ViewModel 仅提供运行提示文案
        val settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

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
        // 初始化开关状态（读取持久化配置，下次启动依然生效）；
        // 透明度调节区域仅在功能启用后显示
        val wallpaperEnabled = TingConfig.wallpaperEnabled(requireContext())
        binding.wallpaperSwitch.isChecked = wallpaperEnabled
        binding.wallpaperAlphaSection.visibility =
            if (wallpaperEnabled) View.VISIBLE else View.GONE
        binding.wallpaperSwitch.setOnCheckedChangeListener { _, isChecked ->
            TingConfig.setWallpaperEnabled(requireContext(), isChecked)
            // 展开 / 折叠过渡动画
            TransitionManager.beginDelayedTransition(binding.root)
            binding.wallpaperAlphaSection.visibility =
                if (isChecked) View.VISIBLE else View.GONE
            activity?.let { WallpaperApplier.apply(it, TingConfig.wallpaperVisibility(it)) }
        }

        // 初始化透明度滑杆（0~100，无阈值限制）
        binding.wallpaperAlphaSlider.value =
            TingConfig.wallpaperAlpha(requireContext()) * 100f
        updateAlphaText(binding.wallpaperAlphaSlider.value)
        binding.wallpaperAlphaSlider.addOnChangeListener { _, value, fromUser ->
            updateAlphaText(value)
            if (fromUser) {
                TingConfig.setWallpaperAlpha(requireContext(), value / 100f)
                WallpaperApplier.apply(requireActivity(), TingConfig.wallpaperVisibility(requireActivity()))
            }
        }

        // ------------------------- 卡片样式（通知卡片个性化） -------------------------
        setupCardStyleSection()

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

        return binding.root
    }

    /** 更新透明度百分比显示 */
    private fun updateAlphaText(value: Float) {
        binding.wallpaperAlphaText.text = "${value.toInt()}%"
    }

    // ------------------------- 卡片样式（通知卡片个性化） -------------------------

    /** "卡片样式"面板是否展开 */
    private var cardStyleExpanded = false

    /**
     * 折叠式"卡片样式"面板：单选样式类型（动态颜色 / 渐变配色 / 高斯模糊 /
     * 液态玻璃 / 自定义图片·开发中）；渐变配色选中时再展开预设色卡列表，
     * 色卡由扇形色板（[GradientSwatchView]）+ 配色名称组成，点击即选用。
     * 配置持久化到 [TingConfig]，由 [CardStyleEngine] 应用到通知卡片。
     */
    private fun setupCardStyleSection() {
        val context = requireContext()

        // 初始化单选状态、渐变色卡列表可见性与摘要文案
        val type = TingConfig.cardStyleType(context)
        binding.rbCardStyleDynamic.isChecked = type == CardStyleEngine.TYPE_DYNAMIC
        binding.rbCardStyleGradient.isChecked = type == CardStyleEngine.TYPE_GRADIENT
        binding.rbCardStyleBlur.isChecked = type == CardStyleEngine.TYPE_BLUR
        binding.rbCardStyleLiquid.isChecked = type == CardStyleEngine.TYPE_LIQUID
        binding.rbCardStyleCustom.isChecked = type == CardStyleEngine.TYPE_CUSTOM
        // 自定义图片样式尚未开发，选项禁用
        binding.rbCardStyleCustom.isEnabled = false
        binding.gradientList.visibility =
            if (type == CardStyleEngine.TYPE_GRADIENT) View.VISIBLE else View.GONE
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
                R.id.rb_card_style_liquid -> CardStyleEngine.TYPE_LIQUID
                R.id.rb_card_style_custom -> CardStyleEngine.TYPE_CUSTOM
                else -> CardStyleEngine.TYPE_DYNAMIC
            }
            TingConfig.setCardStyleType(context, newType)
            // 渐变色卡列表在"渐变配色"选项正下方展开 / 折叠（过渡动画）
            TransitionManager.beginDelayedTransition(binding.root)
            binding.gradientList.visibility =
                if (newType == CardStyleEngine.TYPE_GRADIENT) View.VISIBLE else View.GONE
            updateCardStyleSummary(newType)
        }

        buildGradientCards()
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

    /** 摘要文案显示当前样式（渐变配色附带配色名称） */
    private fun updateCardStyleSummary(type: String) {
        val name = when (type) {
            CardStyleEngine.TYPE_GRADIENT ->
                "渐变 · ${CardStyleEngine.findPreset(TingConfig.cardGradientId(requireContext())).name}"
            CardStyleEngine.TYPE_BLUR -> "高斯模糊"
            CardStyleEngine.TYPE_LIQUID -> "液态玻璃"
            CardStyleEngine.TYPE_CUSTOM -> "自定义"
            else -> "动态颜色"
        }
        binding.cardStyleSummary.text = "当前：$name"
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
