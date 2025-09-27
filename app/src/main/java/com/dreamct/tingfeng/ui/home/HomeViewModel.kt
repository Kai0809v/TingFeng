package com.dreamct.tingfeng.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamct.tingfeng.data.NotificationLog
import com.dreamct.tingfeng.data.NotifyDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HomeViewModel(private val notifyDao: NotifyDao) : ViewModel() {
    val allNotifications: Flow<List<NotificationLog>> = notifyDao.getAllLogs()

    // 可以添加更多数据库操作方法
    fun deleteNotification(log: NotificationLog) {
        viewModelScope.launch {
            notifyDao.deleteById(log.id)
        }
    }
    /** 插入测试通知数据 */
    fun insertTestNotification(log: NotificationLog) {
        viewModelScope.launch {
            notifyDao.insert(log)
        }
    }
}