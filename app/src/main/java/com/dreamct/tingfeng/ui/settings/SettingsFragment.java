package com.dreamct.tingfeng.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dreamct.tingfeng.databinding.FragmentSettingsBinding;
import com.dreamct.tingfeng.ui.Author;
import com.dreamct.tingfeng.ui.login.LoginActivity;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;//绑定，根据对应xml文件自动生成的，fragment_settings-->FragmentSettings+Binding

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        SettingsViewModel settingsViewModel =
                new ViewModelProvider(this).get(SettingsViewModel.class);

        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 绑定按钮点击事件
        binding.btnLogin.setOnClickListener(v -> {
            Intent login = new Intent(getActivity(), LoginActivity.class);
            startActivity(login);
        });

        binding.btnInfo.setOnClickListener(v -> {
            Intent info = new Intent(getActivity(), Author.class);
            startActivity(info);
        });

        binding.btnDev.setOnClickListener(v -> {
            Intent dev = new Intent(getActivity(), DevSettings.class);
            startActivity(dev);
        });

        final TextView textView = binding.textSettings;
        settingsViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}