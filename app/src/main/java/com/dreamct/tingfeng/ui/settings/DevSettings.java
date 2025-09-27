package com.dreamct.tingfeng.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dreamct.tingfeng.R;
import com.dreamct.tingfeng.data.AppDatabase;
import com.dreamct.tingfeng.data.UserListActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DevSettings extends AppCompatActivity {

    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dev_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        findViewById(R.id.data_usage).setOnClickListener(v -> {
            Intent users = new Intent(this, UserListActivity.class);
            startActivity(users);
        });
        findViewById(R.id.notification_data).setOnClickListener(v -> {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle("删除通知记录")
                    .setMessage("确定删除所有通知记录吗？")
                    .setPositiveButton("删除", (dialog, which) -> {

                        // 异步删除通知记录,数据库操作不要在主线程中进行
                        new Thread(()-> {
                            db = AppDatabase.getInstance(this);
                            db.notifyDao().deleteAll();
                        }).start();
                        Toast.makeText(this, "通知记录已删除", Toast.LENGTH_SHORT).show();

                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        findViewById(R.id.permission).setOnClickListener(v->{
            Intent listen = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivity(listen);
        });
    }
}