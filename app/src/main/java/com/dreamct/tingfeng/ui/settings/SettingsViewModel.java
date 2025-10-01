package com.dreamct.tingfeng.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SettingsViewModel extends ViewModel {

    private final MutableLiveData<String> mText;
    public SettingsViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("应用想要稳定运行，须在设置-耗电管理中允许应用后台行为\n" +
                "耗电量并不高");
    }

    public LiveData<String> getText() {
        return mText;
    }
}