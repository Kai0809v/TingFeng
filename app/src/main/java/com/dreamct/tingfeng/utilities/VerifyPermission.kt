package com.dreamct.tingfeng.utilities

import android.content.Context
import androidx.core.app.NotificationManagerCompat

object VerifyPermission {
    /** 检查当前服务是否已获得通知访问权限*/
    fun hasNotificationAccessPermission(context: Context): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        val packageName = context.packageName
        return enabledListeners.contains(packageName)
    }
}