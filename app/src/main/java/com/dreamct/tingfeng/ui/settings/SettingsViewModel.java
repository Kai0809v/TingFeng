package com.dreamct.tingfeng.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SettingsViewModel extends ViewModel {

    private final MutableLiveData<String> mText;
    public SettingsViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("为了更稳定的运行\n" +
                "        建议在设置-耗电管理中允许应用后台行为\n" +
                "        耗电量并不高\n" +
                "        在转为前台服务前，未必管用");
    }

    public LiveData<String> getText() {
        return mText;
    }
}