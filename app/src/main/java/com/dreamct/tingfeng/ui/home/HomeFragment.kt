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
import com.dreamct.tingfeng.data.AppDatabase
import com.dreamct.tingfeng.data.NotificationLog
import com.dreamct.tingfeng.databinding.FragmentHomeBinding
import com.dreamct.tingfeng.service.Ting
import com.google.android.material.appbar.MaterialToolbar
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
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewModel: HomeViewModel
    private lateinit var notificationAdapter: NotificationAdapter


    private val transition = MaterialFadeThrough().apply {
        duration = 300
    }
    private val tag = "HomeFragment"

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

        // 关键改动 1：监听搜索框的文本变化
        searchEditText.doOnTextChanged { text, _, _, _ ->
            // 当文本变化时，立即通知 ViewModel
            viewModel.setSearchQuery(text.toString())
        }

        // 初始化开关状态
        updateSwitchState()

        // **********************Component methods *************************************
        // 设置开关监听
        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            Log.d(tag, "Switch changed to: $isChecked")

            val mainActivity = requireActivity() as? MainActivity

            if (isChecked) {
                // 如果用户打开开关，检查权限
                if (!isNotificationServiceEnabled()) {
                    // 没有权限，显示对话框
                    showPermissionDialog()
                    // 暂时不改变开关状态，等用户处理完权限后再决定
                    serviceSwitch.isChecked = false
                } else {
                    // 有权限，绑定服务
                    bindNotificationService()
                    // 绑定成功，更新UI
                    mainActivity?.startAnimation()
                    Toast.makeText(requireContext(), "Status:${Ting.isConnected}", Toast.LENGTH_SHORT).show()
                }

            } else {
                // 用户关闭开关，解绑服务

                mainActivity?.stopAnimation()

            }
        }
        //*************************************************************************************

        btnTest.setOnClickListener {
            insertTestNotification()
        }

        btnUp.setOnClickListener {
            // 平滑滚动到第一个位置（索引0）
            recyclerView.smoothScrollToPosition(0)
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

    private fun updateSwitchState() {
        val hasPermission = isNotificationServiceEnabled()
        val isConnected = Ting.isConnected

        Log.d(tag, "Update switch state: hasPermission=$hasPermission, isServiceRunning=$isConnected")

        // 只有同时有权限且服务正在运行时，开关才打开
        serviceSwitch.isChecked = hasPermission && isConnected

//        val mainActivity = requireActivity() as? MainActivity
//        mainActivity?.let {
//            if(isConnected) {
//                it.startAnimation()
//            }else{
//                it.stopAnimation()
//            }
//        }

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
            view?.postDelayed({
                updateSwitchState()
            }, 1000)
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
        // 当Fragment恢复时，更新开关状态
        if (::serviceSwitch.isInitialized) {
            updateSwitchState()
        }
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