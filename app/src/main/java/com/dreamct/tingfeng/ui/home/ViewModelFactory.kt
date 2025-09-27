package com.dreamct.tingfeng.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dreamct.tingfeng.data.NotifyDao

class HomeViewModelFactory(private val dao: NotifyDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // 对类型检查，实际上只有HomeViewModel会被创建，不想看到build warning，所以添加了类型检查
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}