package com.dreamct.tingfeng.ui.home

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
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
        //return inflater.inflate(R.layout.fragment_home, container, false)
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
            // 点击item的回调处理
            // 示例：显示详情对话框
            showNotificationDetail(selectedLog)
        }

        //notificationAdapter = NotificationAdapter(emptyList())
        recyclerView.adapter = notificationAdapter

        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = notificationAdapter
        }


        // 初始化ViewModel
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        // 观察数据库变化
        // 关键改动 2：观察过滤后的数据流
        lifecycleScope.launch {
            viewModel.filteredNotifications
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { notifications ->
                    // 使用 submitList 来更新数据
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

        // 设置开关监听器（但防止程序设置触发）
        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isSettingProgrammatically) {
                return@setOnCheckedChangeListener
            }
            handleUserSwitchChange(isChecked)
        }

        // 初始状态检查
        checkAndUpdateSwitchState()

        // 关键改动 1：监听搜索框的文本变化
        searchEditText.doOnTextChanged { text, _, _, _ ->
            // 当文本变化时，立即通知 ViewModel
            viewModel.setSearchQuery(text.toString())
        }

        // 初始化开关状态
        //updateSwitchState()

        // **********************Component methods *************************************

        //*************************************************************************************

        btnTest.setOnClickListener {
            insertTestNotification()
        }

        btnUp.setOnClickListener {
            // 平滑滚动到第一个位置（索引0）
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



        //*******************************************************************************
    }

    private fun showSettingsBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_settings, null)
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()

        // 设置展开状态
        bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        // 设置圆角背景
//        bottomSheetDialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
//            ?.setBackgroundResource(R.drawable.bottom_sheet_background)

//        // 设置展开/收起功能
//        setupExpandableSections(view, bottomSheetDialog)
//
//        // 设置按钮点击事件
//        setupButtonClicks(view, bottomSheetDialog)
    }

    /*
    private fun setupExpandableSections(view: View, dialog: BottomSheetDialog) {
        // 已屏蔽的应用展开/收起
        val headerBlockedApps = view.findViewById<LinearLayout>(R.id.header_blocked_apps)
        val contentBlockedApps = view.findViewById<LinearLayout>(R.id.content_blocked_apps)
        val iconBlockedApps = view.findViewById<ShapeableImageView>(R.id.icon_blocked_apps)

        headerBlockedApps.setOnClickListener {
            val isExpanded = contentBlockedApps.visibility == View.VISIBLE
            contentBlockedApps.visibility = if (isExpanded) View.GONE else View.VISIBLE
            iconBlockedApps.rotation = if (isExpanded) 0f else 180f
        }

        // 已屏蔽的关键词展开/收起
        val headerKeywords = view.findViewById<LinearLayout>(R.id.header_keywords)
        val contentKeywords = view.findViewById<LinearLayout>(R.id.content_keywords)
        val iconKeywords = view.findViewById<ShapeableImageView>(R.id.icon_keywords)

        headerKeywords.setOnClickListener {
            val isExpanded = contentKeywords.visibility == View.VISIBLE
            contentKeywords.visibility = if (isExpanded) View.GONE else View.VISIBLE
            iconKeywords.rotation = if (isExpanded) 0f else 180f
        }
    }

    private fun setupButtonClicks(view: View, dialog: BottomSheetDialog) {
        // 屏蔽应用按钮
        view.findViewById<MaterialButton>(R.id.btn_block_app).setOnClickListener {
            // 实现选择应用逻辑
            showAppSelectionDialog()
        }

        // 添加关键词按钮
        view.findViewById<MaterialButton>(R.id.btn_add_keyword).setOnClickListener {
            val keyword = view.findViewById<TextInputEditText>(R.id.et_keyword).text.toString().trim()
            if (keyword.isNotEmpty()) {
                addKeyword(keyword)
                view.findViewById<TextInputEditText>(R.id.et_keyword).text?.clear()
            } else {
                Toast.makeText(requireContext(), "请输入关键词", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAppSelectionDialog() {
        // 实现应用选择对话框
        // 这里可以启动一个 Activity 或显示另一个 Dialog 来选择应用
    }

    private fun addKeyword(keyword: String) {
        // 实现添加关键词逻辑
        // 更新 UI 显示已添加的关键词
    }
    */

    /**
     * 处理用户手动切换开关的操作
     * 这个方法只在用户主动点击开关时调用，程序设置开关状态时不会调用
     */
    private fun handleUserSwitchChange(isChecked: Boolean) {
        Log.d(tag, "User manually changed switch to: $isChecked")

        val mainActivity = requireActivity() as? MainActivity

        if (isChecked) {
            // 用户手动打开开关
            if (!isNotificationServiceEnabled()) {
                // 没有权限，显示对话框
                showPermissionDialog()
                // 注意：这里不能直接设置开关状态，因为对话框是异步的
                // 状态会在用户返回后通过 onResume 更新
            } else {
                // 有权限，绑定服务
                bindNotificationService()
                // 绑定成功，更新UI
                mainActivity?.startAnimation()
                Toast.makeText(requireContext(), "服务状态: ${Ting.isConnected}", Toast.LENGTH_SHORT).show()

                // 更新 ViewModel 状态
                val hasPermission = isNotificationServiceEnabled()
                val isConnected = Ting.isConnected
                viewModel.updateSwitchState(hasPermission, isConnected)
            }
        } else {
            // 用户手动关闭开关
            mainActivity?.stopAnimation()
            Toast.makeText(requireContext(), "服务已停止", Toast.LENGTH_SHORT).show()

            // 更新 ViewModel 状态
            val hasPermission = isNotificationServiceEnabled()
            val isConnected = false // 用户主动关闭，认为服务断开
            viewModel.updateSwitchState(hasPermission, isConnected)
        }
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
            // 需要显示开关
            if (serviceSwitch.visibility != View.VISIBLE) {
                showSwitchWithAnimation()
                viewModel.switchShown.hasShown = false
            }
            serviceSwitch.isChecked = state.hasPermission && state.isConnected
        } else {
            // 需要隐藏开关
            if (serviceSwitch.visibility != View.GONE) {
                // 只有当开关之前没有显示过时才展示动画隐藏
                if (!viewModel.switchShown.hasShown) {
                    hideSwitchWithAnimation()
                    viewModel.switchShown.hasShown = true
                }else{serviceSwitch.visibility = View.GONE}//否则直接隐藏
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
                // 恢复属性
                serviceSwitch.alpha = 1f
                serviceSwitch.scaleX = 1f
                serviceSwitch.scaleY = 1f
            }
            .start()
    }
    private fun isNotificationServiceEnabled(): Boolean {
        return try {
            val enabledListeners = Settings.Secure.getString(
                requireContext().contentResolver,
                "enabled_notification_listeners"
            )
            enabledListeners?.contains(requireContext().packageName) == true
        } catch (e: Exception) {
            Log.e(tag, "Error checking notification service status", e)
            false
        }
    }

    private fun showPermissionDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("需要通知访问权限")
            .setMessage("此功能需要访问通知的权限。请点击确定前往设置页面开启权限。")
            .setPositiveButton("确定") { _, _ ->
                // 跳转到通知权限设置页面
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                startActivity(intent)
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun bindNotificationService() {
        try {
            // 添加服务状态检查
            if (!Ting.isConnected) {
                // 这个服务既是系统管理的，再次启动并不及时
//                val intent = Intent(requireContext(), Ting::class.java)
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                    requireContext().startForegroundService(intent)
//                } else {
//                    requireContext().startService(intent)
//                }
                // 尝试手动绑定
                Ting.bindNotificationService(requireContext())

                view?.postDelayed({
                    if (!Ting.isConnected) {
                        // 仍未连接，提示用户
                        Toast.makeText(requireContext(), "哦豁，Status:${Ting.isConnected}", Toast.LENGTH_SHORT).show()
                    }
                }, 1000)
                
            }
            // 延迟检查服务状态
//            view?.postDelayed({
//                updateSwitchState()
//            }, 1000)
        } catch (e: Exception) {
            Log.e(tag, "Failed to start service", e)
            serviceSwitch.isChecked = false
        }
    }

    /** 服务解绑，因为这个服务是系统管理的，无法停止 */
    private fun unbindNotificationService() {
        try {

        } catch (e: Exception) {
            Log.e(tag, "Failed to unbind service", e)
        }
    }

    private fun showNotificationDetail(log: NotificationLog) {
        MaterialAlertDialogBuilder(requireContext())
            //.setTitle(log.appName + "\n- " + log.title)
            .setTitle(log.appName)
            .setMessage("${log.title}\n - ${log.content}")
            .setPositiveButton("关闭", null)
            .show()
    }


    override fun onResume() {
        super.onResume()
        // 每次可见时检查状态（比如用户从权限设置返回）
        checkAndUpdateSwitchState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::serviceSwitch.isInitialized) {
            serviceSwitch.setOnCheckedChangeListener(null)
        }

        _binding = null
    }

    /** 发送测试通知 */

    /** 插入测试通知数据 */
    private fun insertTestNotification() {
        val testLog = NotificationLog(
            //id = 0, // Room会自动生成ID
            time = System.currentTimeMillis(),
            title = "欢迎使用听风",
            content = "这是一条自动生成的测试通知内容",
            appName = "测试应用",
            packageName = "com.example.test",

            //isActive = true
        )

        viewModel.insertTestNotification(testLog)
    }


}