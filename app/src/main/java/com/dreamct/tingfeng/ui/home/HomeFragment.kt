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
        notificationAdapter = NotificationAdapter() { selectedLog ->
            showNotificationDetail(selectedLog)
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = notificationAdapter
        }

        // 观察数据库变化
        lifecycleScope.launch {
            viewModel.filteredNotifications
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { notifications ->
                    notificationAdapter.submitList(notifications)
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

        btnUp.setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
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
                        mainActivity?.startAnimation()
                        Toast.makeText(requireContext(), "服务已启动", Toast.LENGTH_SHORT).show()
                        // 更新 ViewModel
                        viewModel.updateSwitchState(hasPermission = true, isConnected = true)
                    } else {
                        // 绑定失败，回滚开关状态
                        setSwitchCheckedProgrammatically(false)
                        mainActivity?.stopAnimation()
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

    /** * 原先的 bindNotificationService 已被 performBindWithRetry 替代，
     * 这里保留一个简单的入口供其他地方调用（如果需要）
     */
    private fun bindNotificationService() {
        // 这是一个兼容旧调用的空壳，逻辑已移至 handleUserSwitchChange
        Ting.bindNotificationService(requireContext())
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
}