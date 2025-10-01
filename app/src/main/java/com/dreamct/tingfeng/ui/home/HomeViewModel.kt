package com.dreamct.tingfeng.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamct.tingfeng.data.NotificationLog
import com.dreamct.tingfeng.data.NotifyDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val notifyDao: NotifyDao) : ViewModel() {

    // --- ⬇️ 以下是新增和修改的部分 ⬇️ ---

    // 1. 用于接收和保存搜索查询词的私有 StateFlow
    private val _searchQuery = MutableStateFlow("")

    // 2. 从数据库获取原始的、完整的通知列表 Flow。
    //    使用 stateIn 将其转换为 "热" 的 StateFlow，以便在多个观察者之间共享，并避免重复查询。
    private val allNotifications: StateFlow<List<NotificationLog>> = notifyDao.getAllLogs().stateIn(
        scope = viewModelScope,
        // 当有订阅者时开始，5秒后如果没有订阅者则停止
        started = SharingStarted.WhileSubscribed(5000L),
        // 初始值为空列表
        initialValue = emptyList()
    )

    // 3. (核心) 结合搜索词和完整列表，生成过滤后的列表 StateFlow
    val filteredNotifications: StateFlow<List<NotificationLog>> =
        _searchQuery.combine(allNotifications) { query, notifications ->
            if (query.isBlank()) {
                notifications // 如果搜索词为空，返回完整列表
            } else {
                // 如果搜索词不为空，执行过滤逻辑
                // 这里会检查 appName, title, content 是否包含关键词（忽略大小写）
                notifications.filter { notification ->
                    notification.appName.contains(query, ignoreCase = true) ||
                            notification.title.contains(query, ignoreCase = true) ||
                            notification.content.contains(query, ignoreCase = true)
                }
            }
        }.stateIn( // 将最终结果也转换为 StateFlow
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    /**
     * 4. 由 Fragment 调用，用于更新搜索关键词
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }


    // --- ⬇️ 以下是原有的方法，保持不变 ⬇️ ---

    fun deleteNotification(log: NotificationLog) {
        viewModelScope.launch {
            notifyDao.deleteById(log.id)
        }
    }

    fun insertTestNotification(log: NotificationLog) {
        viewModelScope.launch {
            notifyDao.insert(log)
        }
    }
}