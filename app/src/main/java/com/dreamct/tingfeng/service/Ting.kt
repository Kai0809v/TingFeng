package com.dreamct.tingfeng.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dreamct.tingfeng.R
import com.dreamct.tingfeng.data.AppDatabase
import com.dreamct.tingfeng.data.NotificationLog
import com.dreamct.tingfeng.data.NotifyDao
import com.dreamct.tingfeng.utilities.hasNotificationAccessPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** * 服务端核心修改点：
 * 1. 恢复 startForeground 逻辑，提升进程优先级到最高（Perceptible）。
 * 2. 优化 onListenerDisconnected，增加延迟重连，防止被系统判定为“不良行为”而屏蔽。
 * 3. 配合 UI 层的 toggleNotificationListenerService 确保组件状态切换顺畅。
 */
class Ting : NotificationListenerService() {

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set

        @Volatile
        private var isBinding: Boolean = false

        // 标准请求绑定（温和方式）
        fun bindNotificationService(context: Context) {
            if (!hasNotificationAccessPermission(context)) {
                // 如果没有权限，引导去设置页（这里通常由Activity处理，Service只做简单判断）
                return
            }

            if (isConnected || isBinding) return

            isBinding = true
            try {
                val cn = ComponentName(context, Ting::class.java)
                requestRebind(cn)
                Log.d("TingService", "requestRebind called")
            } catch (e: Exception) {
                Log.e("TingService", "Failed to request rebind", e)
            } finally {
                isBinding = false
            }
        }

        // 强力重连（霸道方式）：由 HomeFragment 在重试失败后调用
        fun toggleNotificationListenerService(context: Context) {
            Log.d("TingService", "执行强力重连：切换组件状态")
            val pm = context.packageManager
            val componentName = ComponentName(context, Ting::class.java)

            // 1. 先禁用组件
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )

            // 2. 再启用组件 (这将强制 System Server 重新识别并绑定服务)
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private val tag = "TingService"
    private lateinit var notifyDao: NotifyDao
    private val notificationChannelId = "ting_service_channel"
    private val notificationId = 101

    // 缓存配置
    private val recentNotifications = mutableSetOf<String>()
    private val CACHE_MAX_SIZE = 1000

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created")

        val db = AppDatabase.getDatabase(applicationContext)
        notifyDao = db.notifyDao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 关键修改：虽然 NLS 不完全遵循这个，但返回 START_STICKY 是好习惯
        return Service.START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(tag, "Listener connected")
        isConnected = true

        // 核心修改 1：连接成功后立即开启前台服务
        // 这将进程优先级提升，防止被 LMK (Low Memory Killer) 杀掉
        // startForegroundService()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(tag, "Listener disconnected")
        isConnected = false

        // 核心修改 2：不要立即重连，使用协程延迟重连
        // 立即重连容易触发系统保护机制（Crash Loop Detection）导致被永久屏蔽
        CoroutineScope(Dispatchers.Main).launch {
            delay(5000) // 冷却 5 秒

            if (!isConnected && hasNotificationAccessPermission(applicationContext)) {
                Log.d(tag, "尝试自动恢复连接...")
                try {
                    requestRebind(ComponentName(applicationContext, Ting::class.java))
                } catch (e: Exception) {
                    Log.e(tag, "自动恢复失败", e)
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 排除自身通知，防止循环记录
        if (sbn.packageName == packageName) return
        process(sbn)
    }

    private fun process(sbn: StatusBarNotification){
        try {
            val notification = sbn.notification
            val extras = notification.extras
            val time = System.currentTimeMillis()
            val title = extras.getString(Notification.EXTRA_TITLE, "")
            val content = extras.getString(Notification.EXTRA_TEXT, "")

            if (title.isNullOrEmpty() && content.isNullOrEmpty()) return

            val appName = try {
                applicationContext.packageManager.getApplicationLabel(
                    applicationContext.packageManager.getApplicationInfo(sbn.packageName, 0)
                ).toString()
            } catch (e: Exception) {
                sbn.packageName
            }

            val notificationLog = NotificationLog(
                title = title ?: "",
                time = time,
                content = content ?: "",
                appName = appName,
                packageName = sbn.packageName,
                intent = null
            )

            jiLu(notificationLog)
        } catch (e: Exception) {
            Log.e(tag, "Error processing notification", e)
        }
    }

    private fun jiLu(log: NotificationLog) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cacheKey = "${log.packageName}:${log.title}:${log.content}".hashCode().toString()

                if (recentNotifications.contains(cacheKey)) return@launch

                val timeWindowStart = log.time - 60_000
                val exists = notifyDao.checkExists(
                    log.title,
                    log.content,
                    log.appName,
                    timeWindowStart,
                    log.time
                ) > 0

                if (!exists) {
                    notifyDao.insert(log)
                    addToMemoryCache(cacheKey)
                    Log.d(tag, "Saved: ${log.title}")
                } else {
                    addToMemoryCache(cacheKey)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error saving", e)
            }
        }
    }

    private fun addToMemoryCache(key: String) {
        synchronized(recentNotifications) {
            recentNotifications.add(key)
            if (recentNotifications.size > CACHE_MAX_SIZE) {
                val itemsToRemove = recentNotifications.take(CACHE_MAX_SIZE / 2)
                recentNotifications.removeAll(itemsToRemove.toSet())
            }
        }
    }

    /**
     * 核心修改 3：必须实现的前台服务逻辑
     * Android 8.0+ 必须创建 NotificationChannel
     */
//    private fun startForegroundService() {
//        try {
//            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                val channel = NotificationChannel(
//                    notificationChannelId,
//                    "通知监听服务状态", // 用户可见的通道名称
//                    NotificationManager.IMPORTANCE_LOW // 低优先级，不发出声音干扰用户
//                ).apply {
//                    description = "保持服务在后台运行以记录通知"
//                    setShowBadge(false)
//                }
//                manager.createNotificationChannel(channel)
//            }
//
//            val notification = NotificationCompat.Builder(this, notificationChannelId)
//                .setContentTitle("听风正在运行")
//                .setContentText("正在记录您的通知历史...")
//                .setSmallIcon(R.drawable.ic_launcher_foreground) // 确保这里有一个有效的图标资源ID
//                .setPriority(NotificationCompat.PRIORITY_LOW)
//                .setOngoing(true) // 设置为常驻通知，不可滑动删除
//                .build()
//
//            // 启动前台服务，ID 不能为 0
//            startForeground(notificationId, notification)
//            Log.d(tag, "前台服务已启动")
//        } catch (e: Exception) {
//            Log.e(tag, "启动前台服务失败", e)
//        }
//    }
}