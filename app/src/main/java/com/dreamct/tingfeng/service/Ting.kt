package com.dreamct.tingfeng.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresPermission
import com.dreamct.tingfeng.data.AppDatabase
import com.dreamct.tingfeng.data.NotificationLog
import com.dreamct.tingfeng.data.NotifyDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.log

/** 此服务继承于NotificationListenerService，服务交给系统控制 */
class Ting : NotificationListenerService() {

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set

        fun bindNotificationService(context: Context) {
            try {
                val cn = ComponentName(context, Ting::class.java)
                requestRebind(cn)

                Log.d("TingService", "requestRebind called")
            } catch (e: Exception) {
                Log.e("TingService", "Failed to request rebind", e)
            }
        }

    }

    private val tag = "TingService"
    private lateinit var notifyDao: NotifyDao
    private val notificationChannelId = "ting_service_channel"
    private val notificationId = 101

    // 在类中添加内存缓存
    private val recentNotifications = mutableSetOf<String>()
    private  val CACHE_MAX_SIZE = 1000

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created")

        // 初始化数据库
        val db = AppDatabase.getDatabase(applicationContext)
        notifyDao = db.notifyDao()

        // 启动为前台服务
        //startForegroundService()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(tag, "Listener connected")
        isConnected = true
        // 确保服务在前台运行
        //startForegroundService()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {

        process(sbn)

    }

    private fun process(sbn: StatusBarNotification){
        try {
            // 提取通知信息
            val notification = sbn.notification
            val extras = notification.extras

            val time = System.currentTimeMillis()
            val title = extras.getString(Notification.EXTRA_TITLE, "")
            val appName = applicationContext.packageManager.getApplicationLabel(
                applicationContext.packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
            val content = extras.getString(Notification.EXTRA_TEXT, "")
            val packageName = sbn.packageName

            // 空值检查：当标题和内容都为空时不记录
            if (title.isNullOrEmpty() && content.isNullOrEmpty()) {
                return
            }


            // 创建通知记录对象
            val notificationLog = NotificationLog(
                title = title ?: "",
                time = time,
                content = content ?: "",
                appName = appName,
                packageName = packageName,

                intent = null // 暂时不处理intent
            )

            jiLu(notificationLog)
        } catch (e: Exception) {
            Log.e(tag, "Error processing notification", e)
        }
    }
    /** 新增的独立插入方法 后台线程 */
    private fun jiLu(log: NotificationLog) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 生成缓存键（包名+标题+内容的哈希）
                val cacheKey = "${log.packageName}:${log.title}:${log.content}".hashCode().toString()

                // 先检查内存缓存（快速去重）
                if (recentNotifications.contains(cacheKey)) {
                    Log.d(tag, "Duplicate notification skipped (memory cache): ${log.title}")
                    return@launch
                }

                // 内存缓存中没有，再检查数据库
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
                    // 添加到内存缓存
                    addToMemoryCache(cacheKey)
                    Log.d(tag, "Notification saved: ${log.title}")
                } else {
                    Log.d(tag, "Duplicate notification skipped: ${log.title}")
                    addToMemoryCache(cacheKey) // 即使是重复的也缓存，避免重复查询
                }
            } catch (e: Exception) {
                Log.e(tag, "Error saving notification", e)
            }
        }
    }

    /** 添加到内存缓存 */
    private fun addToMemoryCache(key: String) {
        synchronized(recentNotifications) {
            recentNotifications.add(key)
            // 限制缓存大小
            if (recentNotifications.size > CACHE_MAX_SIZE) {
                val itemsToRemove = recentNotifications.take(CACHE_MAX_SIZE / 2)
                recentNotifications.removeAll(itemsToRemove.toSet())
            }
        }
    }


    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(tag, "Listener disconnected")

        isConnected = false
        if (TingConfig.shouldRestart(applicationContext)) {
            Log.d(tag, "尝试重新绑定")
            // 尝试重新连接
            try {
                requestRebind(ComponentName(this, Ting::class.java))
            } catch (e: Exception) {
                Log.e(tag, "Failed to rebind", e)
                // 计划稍后重新启动服务
                //scheduleServiceRestart()
            }

        }else{
            Log.d(tag, "不尝试重新绑定")
        }
    }

    /**
    @SuppressLint("ForegroundServiceType")
    private fun startForegroundService() {
        // 创建通知渠道 (Android 8.0+ 需要)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "通知记录服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "正在后台记录系统通知"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // 创建前台服务通知
        val notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("通知记录服务运行中")
            .setContentText("正在记录系统通知")
            .setSmallIcon(R.drawable.notification) // 您需要提供这个图标
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()


        // 启动前台服务
        startForeground(notificationId, notification)

    }*/


    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    @SuppressLint("ServiceCast")
    private fun scheduleServiceRestart() {
        // 改用显式Intent启动方式
        val restartIntent = Intent(this, Ting::class.java).apply {
            action = "RESTART_SERVICE"
            `package` = packageName  // 添加包名限制
        }

        // 使用正确的PendingIntent flag
        val pendingIntent = PendingIntent.getService(
            applicationContext,
            0,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 添加SDK版本检查
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 5000,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 5000,
                pendingIntent
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Service destroyed")
        // 计划重新启动服务
        //scheduleServiceRestart()
    }
}