package com.dreamct.tingfeng.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {

    private val _text = MutableLiveData(
        "为了更稳定的运行\n" +
                "建议在设置-耗电管理中允许应用后台行为\n" +
                "耗电量并不高\n" +
                "建议开启通知栏常驻增强稳定性（待完成）"
    )

    val text: LiveData<String> = _text
}
