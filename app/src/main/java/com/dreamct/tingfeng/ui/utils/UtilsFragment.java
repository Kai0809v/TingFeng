package com.dreamct.tingfeng.ui.utils;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.dreamct.tingfeng.R;
import com.dreamct.tingfeng.databinding.FragmentUtilsBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class UtilsFragment extends Fragment {

    private FragmentUtilsBinding binding;
    private Handler handler;
    private Runnable updateTimeRunnable;
    private Runnable midnightUpdateRunnable; // 添加午夜更新Runnable

    // 视图组件
    private TextView currentDateTextView;
    private TextView currentTimeTextView;
    private TextView todayHoursTextView;
    private TextView todayMinutesTextView;
    private TextView todaySecondsTextView;
    private TextView monthDaysTextView;
    private TextView monthPercentTextView;
    private ProgressBar monthProgressBar;
    private TextView yearDaysTextView;
    private TextView yearPercentTextView;
    private ProgressBar yearProgressBar;
    private Button refreshButton;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentUtilsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 初始化视图组件
        initViews(root);

        // 初始化Handler
        handler = new Handler(Looper.getMainLooper());

        // 设置刷新按钮点击事件
        refreshButton.setOnClickListener(v -> updateAllTimeInfo());

        // 立即更新一次时间信息
        updateAllTimeInfo();

        return root;
    }

    private void initViews(View root) {
        currentDateTextView = root.findViewById(R.id.currentDate);
        currentTimeTextView = root.findViewById(R.id.currentTime);
        todayHoursTextView = root.findViewById(R.id.todayHours);
        todayMinutesTextView = root.findViewById(R.id.todayMinutes);
        todaySecondsTextView = root.findViewById(R.id.todaySeconds);
        monthDaysTextView = root.findViewById(R.id.monthDays);
        monthPercentTextView = root.findViewById(R.id.monthPercent);
        monthProgressBar = root.findViewById(R.id.monthProgress);
        yearDaysTextView = root.findViewById(R.id.yearDays);
        yearPercentTextView = root.findViewById(R.id.yearPercent);
        yearProgressBar = root.findViewById(R.id.yearProgress);
        refreshButton = root.findViewById(R.id.refreshButton);
    }

    private void updateAllTimeInfo() {
        // 更新当前日期和时间
        updateCurrentDateTime();

        // 更新今日剩余时间
        updateTodayRemainingTime();

        // 更新本月剩余时间
        updateMonthRemainingTime();

        // 更新今年剩余时间
        updateYearRemainingTime();

        // 设置定时更新（每秒更新一次）
        setupPeriodicUpdates();

        // 设置午夜更新
        setupMidnightUpdate();
    }

    private void updateCurrentDateTime() {
        Calendar calendar = Calendar.getInstance();

        // 格式化日期
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
        String currentDate = dateFormat.format(calendar.getTime());
        currentDateTextView.setText(currentDate);

        // 格式化时间
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTime = timeFormat.format(calendar.getTime());
        currentTimeTextView.setText(currentTime);
    }

    private void updateTodayRemainingTime() {
        Calendar calendar = Calendar.getInstance();
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int currentMinute = calendar.get(Calendar.MINUTE);
        int currentSecond = calendar.get(Calendar.SECOND);

        // 计算今日剩余时间（秒）
        int remainingSeconds = (23 - currentHour) * 3600 + (59 - currentMinute) * 60 + (59 - currentSecond);

        // 转换为小时、分钟、秒
        long hours = TimeUnit.SECONDS.toHours(remainingSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(remainingSeconds) - TimeUnit.HOURS.toMinutes(hours);
        long seconds = remainingSeconds - TimeUnit.HOURS.toSeconds(hours) - TimeUnit.MINUTES.toSeconds(minutes);

        // 更新UI
        todayHoursTextView.setText(String.valueOf(hours));
        todayMinutesTextView.setText(String.valueOf(minutes));
        todaySecondsTextView.setText(String.valueOf(seconds));
    }

    private void updateMonthRemainingTime() {
        Calendar calendar = Calendar.getInstance();
        int currentDay = calendar.get(Calendar.DAY_OF_MONTH);
        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        // 计算本月剩余天数
        int remainingDays = daysInMonth - currentDay;

        // 计算本月进度百分比
        int progressPercent = (int) ((float) currentDay / daysInMonth * 100);

        // 更新UI
        monthDaysTextView.setText(String.valueOf(remainingDays));
        monthPercentTextView.setText(progressPercent + "%");
        monthProgressBar.setProgress(progressPercent);
    }

    private void updateYearRemainingTime() {
        Calendar calendar = Calendar.getInstance();
        int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
        int daysInYear = calendar.getActualMaximum(Calendar.DAY_OF_YEAR);

        // 计算今年剩余天数
        int remainingDays = daysInYear - dayOfYear;

        // 计算今年进度百分比
        int progressPercent = (int) ((float) dayOfYear / daysInYear * 100);

        // 更新UI
        yearDaysTextView.setText(String.valueOf(remainingDays));
        yearPercentTextView.setText(progressPercent + "%");
        yearProgressBar.setProgress(progressPercent);
    }

    private void setupPeriodicUpdates() {
        // 移除之前的回调
        if (updateTimeRunnable != null) {
            handler.removeCallbacks(updateTimeRunnable);
        }

        // 创建新的回调
        updateTimeRunnable = new Runnable() {
            @Override
            public void run() {
                updateCurrentDateTime();
                updateTodayRemainingTime();

                // 每秒更新一次
                handler.postDelayed(this, 1000);
            }
        };

        // 启动定时更新
        handler.post(updateTimeRunnable);
    }

    private void setupMidnightUpdate() {
        // 移除之前的午夜更新回调
        if (midnightUpdateRunnable != null) {
            handler.removeCallbacks(midnightUpdateRunnable);
        }

        // 创建午夜更新回调
        midnightUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateMonthRemainingTime();
                updateYearRemainingTime();

                // 计算到下一个午夜的时间
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);

                long nextMidnight = calendar.getTimeInMillis() - System.currentTimeMillis();

                // 设置下一次午夜更新
                handler.postDelayed(this, nextMidnight);
            }
        };

        // 计算到下一个午夜的时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long midnight = calendar.getTimeInMillis() - System.currentTimeMillis();

        // 启动午夜更新
        handler.postDelayed(midnightUpdateRunnable, midnight);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 恢复定时更新
        if (updateTimeRunnable != null) {
            handler.post(updateTimeRunnable);
        }

        // 恢复午夜更新
        if (midnightUpdateRunnable != null) {
            setupMidnightUpdate();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // 暂停定时更新
        if (updateTimeRunnable != null) {
            handler.removeCallbacks(updateTimeRunnable);
        }

        // 暂停午夜更新
        if (midnightUpdateRunnable != null) {
            handler.removeCallbacks(midnightUpdateRunnable);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 移除所有回调
        if (handler != null) {
            if (updateTimeRunnable != null) {
                handler.removeCallbacks(updateTimeRunnable);
            }
            if (midnightUpdateRunnable != null) {
                handler.removeCallbacks(midnightUpdateRunnable);
            }
        }
        binding = null;
    }
}