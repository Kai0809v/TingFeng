package com.dreamct.tingfeng.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.content.Intent
import androidx.room.TypeConverters

@Entity(tableName = "notification_logs")
data class NotificationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long, // 时间（推荐使用System.currentTimeMillis()）
    val title: String, // 通知标题
    val content: String, // 通知内容
    val appName: String, // 应用名
    val packageName: String, // 发送应用包名
    val intent: String? = null // 点击通知后的意图
    // 我感觉没必要加@ColumnInfo
)