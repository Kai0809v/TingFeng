package com.dreamct.tingfeng.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.dreamct.tingfeng.MainActivity
import com.dreamct.tingfeng.R
import com.dreamct.tingfeng.data.AppDatabase
import com.dreamct.tingfeng.data.NotificationLog
import com.dreamct.tingfeng.databinding.FragmentHomeBinding
import com.dreamct.tingfeng.service.Ting
import com.dreamct.tingfeng.utilities.hasNotificationAccessPermission
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {
    // ********************** ViewBinding **********************
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    // ********************** Component **********************
    private lateinit var appBar :MaterialToolbar
    private lateinit var searchEditText: TextInputEditText
    private lateinit var searchInputLayout: TextInputLayout
    private lateinit var serviceSwitch: MaterialSwitch
    private lateinit var btnTest: MaterialButton
    private lateinit var btnUp: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewModel: HomeViewModel
    private lateinit var notificationAdapter: NotificationAdapter



    // 新增：未读消息计数器
    private var unreadCount = 0
    // 新增：记录上一次列表的最顶端位置，用于判断是否有新消息插入
    private var lastTopPosition = 0
    private val transition = MaterialFadeThrough().apply {
        duration = 300
    }
    private val tag = "HomeFragment"
    private var isSettingProgrammatically = false // 防止程序设置触发监听器

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
        serviceSwitch = binding.serviceSwitch
        recyclerView = binding.cardRecyclerView
        btnTest = binding.test
        btnUp = binding.goUpon
        btnSettings = binding.settings

        // ****************************************************************************
        // 初始化 LayoutManager
        val layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager

// 跟上面的功能是相同的，不过需要在其他地方调用，所以单独弄出来设置
//        recyclerView.apply {
//            layoutManager = LinearLayoutManager(context)
//            adapter = notificationAdapter
//        }


        notificationAdapter = NotificationAdapter() { selectedLog ->
            showNotificationDetail(selectedLog)
        }
        // 在 adapter 初始化后设置
        recyclerView.adapter = notificationAdapter






        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

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
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val firstVisiblePos = layoutManager.findFirstVisibleItemPosition()

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

        // 观察 ViewModel 中的开关状态
        lifecycleScope.launch {
            viewModel.switchState
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { state ->
                    updateSwitchUI(state)
                }
        }

        // 设置开关监听器
        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isSettingProgrammatically) {
                return@setOnCheckedChangeListener
            }
            handleUserSwitchChange(isChecked)
        }

        // 初始状态检查
        checkAndUpdateSwitchState()

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
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val firstVisiblePos = layoutManager.findFirstVisibleItemPosition()

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

    private fun showSettingsBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_settings, null)
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheetDialog.show()
    }

    /**
     * 处理用户手动切换开关的操作
     * 关键修改：增加异步状态检查和自动回滚逻辑
     */
    private fun handleUserSwitchChange(isChecked: Boolean) {
        Log.d(tag, "User manually changed switch to: $isChecked")
        val mainActivity = requireActivity() as? MainActivity

        if (isChecked) {
            // 用户尝试打开
            if (!isNotificationServiceEnabled()) {
                // 没有权限，回弹开关
                setSwitchCheckedProgrammatically(false)
                showPermissionDialog()
            } else {
                // 有权限，执行绑定流程（包含重试机制）
                lifecycleScope.launch {
                    // 1. 尝试绑定
                    val success = performBindWithRetry()

                    if (success) {
                        // 绑定成功
                        //mainActivity?.startAnimation()
                        Toast.makeText(requireContext(), "服务已启动", Toast.LENGTH_SHORT).show()
                        // 更新 ViewModel
                        viewModel.updateSwitchState(hasPermission = true, isConnected = true)
                    } else {
                        // 绑定失败，回滚开关状态
                        setSwitchCheckedProgrammatically(false)
                        //mainActivity?.stopAnimation()
                        Toast.makeText(requireContext(), "服务启动失败，请尝试重启应用或重新授权", Toast.LENGTH_LONG).show()

                        // 失败后，更新ViewModel为未连接
                        viewModel.updateSwitchState(hasPermission = true, isConnected = false)
                    }
                }
            }
        } else {
            // 用户手动关闭
            mainActivity?.stopAnimation()
            Toast.makeText(requireContext(), "服务监控已暂停", Toast.LENGTH_SHORT).show()

            // 注意：NotificationListenerService 很难真正"停止"，
            // 这里主要是逻辑上的停止（不再更新UI/不再记录），或者可以在Service中增加一个静态开关 flag

            viewModel.updateSwitchState(hasPermission = isNotificationServiceEnabled(), isConnected = false)
        }
    }

    /**
     * 辅助方法：程序化设置 Switch 状态，不触发监听器
     */
    private fun setSwitchCheckedProgrammatically(checked: Boolean) {
        isSettingProgrammatically = true
        serviceSwitch.isChecked = checked
        isSettingProgrammatically = false
    }

    /**
     * 执行绑定逻辑，带重试和强力重连机制
     * @return Boolean 是否连接成功
     */
    private suspend fun performBindWithRetry(): Boolean {
        return withContext(Dispatchers.Main) {
            // 1. 第一次尝试：普通重连
            Ting.bindNotificationService(requireContext())

            // 等待检查 (轮询 2秒)
            if (waitForConnection(2000)) return@withContext true

            Log.w(tag, "普通绑定失败，尝试强力重连...")

            // 2. 第二次尝试：强力重连 (切换组件状态)
            // 注意：这需要 Ting Service 中有 toggleNotificationListenerService 方法
            try {
                Ting.toggleNotificationListenerService(requireContext())
            } catch (e: Exception) {
                Log.e(tag, "强力重连异常", e)
            }

            // 再次等待检查 (轮询 3秒，强力重连通常需要更长时间)
            if (waitForConnection(3000)) return@withContext true

            return@withContext false
        }
    }

    /**
     * 轮询检查连接状态
     */
    private suspend fun waitForConnection(timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (Ting.isConnected) {
                return true
            }
            delay(200) // 每200ms检查一次
        }
        return false
    }

    private fun checkAndUpdateSwitchState() {
        val hasPermission = isNotificationServiceEnabled()
        val isConnected = Ting.isConnected
        viewModel.updateSwitchState(hasPermission, isConnected)
    }

    private fun updateSwitchUI(state: HomeViewModel.SwitchState) {
        // 防止程序设置触发监听器
        isSettingProgrammatically = true

        if (state.shouldBeVisible) {
            if (serviceSwitch.visibility != View.VISIBLE) {
                showSwitchWithAnimation()
                viewModel.switchShown.hasShown = false
            }
            // 只有当状态真的改变时才重新设置，避免动画闪烁
            if (serviceSwitch.isChecked != (state.hasPermission && state.isConnected)) {
                serviceSwitch.isChecked = state.hasPermission && state.isConnected
            }
        } else {
            if (serviceSwitch.visibility != View.GONE) {
                if (!viewModel.switchShown.hasShown) {
                    hideSwitchWithAnimation()
                    viewModel.switchShown.hasShown = true
                } else {
                    serviceSwitch.visibility = View.GONE
                }
            }
        }
        isSettingProgrammatically = false
    }

    private fun showSwitchWithAnimation() {
        serviceSwitch.visibility = View.VISIBLE
        serviceSwitch.alpha = 0f
        serviceSwitch.scaleX = 0.8f
        serviceSwitch.scaleY = 0.8f

        serviceSwitch.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .start()
    }

    private fun hideSwitchWithAnimation() {
        serviceSwitch.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(300)
            .withEndAction {
                serviceSwitch.visibility = View.GONE
                serviceSwitch.alpha = 1f
                serviceSwitch.scaleX = 1f
                serviceSwitch.scaleY = 1f
            }
            .start()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return hasNotificationAccessPermission(requireContext())
    }

    private fun showPermissionDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("需要通知访问权限")
            .setMessage("此功能需要访问通知的权限。请点击确定前往设置页面开启权限。")
            .setPositiveButton("确定") { _, _ ->
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                startActivity(intent)
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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
        checkAndUpdateSwitchState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::serviceSwitch.isInitialized) {
            serviceSwitch.setOnCheckedChangeListener(null)
        }
        _binding = null
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