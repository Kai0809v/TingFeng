package com.dreamct.tingfeng.ui.home

import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView
import com.dreamct.tingfeng.R
import com.dreamct.tingfeng.data.NotificationLog
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private var notifications: List<NotificationLog>,
    private val onItemClick: (NotificationLog) -> Unit,
//    private val onCopyClick: (String) -> Unit,
//    private val onShareClick: (String) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        val appName: TextView = itemView.findViewById(R.id.notification_app_name)
        val title: TextView = itemView.findViewById(R.id.notification_title)
        val time: TextView = itemView.findViewById(R.id.notification_time)
        val content: TextView = itemView.findViewById(R.id.notification_content)
//        val btnCopy: Button = itemView.findViewById(R.id.btn_copy)
//        val btnShare: Button = itemView.findViewById(R.id.btn_share)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = notifications[position]

        // 应用名
        holder.appName.text = notification.appName

        // 设置通知标题
        holder.title.text = notification.title

        // 格式化并设置时间
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
        val date = Date(notification.time)

        val timeText = if (isToday(notification.time)) {
            "今天 ${timeFormat.format(date)}"
        } else {
            "${dateFormat.format(date)} ${timeFormat.format(date)}"
        }
        holder.time.text = timeText

        // 设置通知内容
        holder.content.text = notification.content

        // 设置点击事件
        holder.itemView.setOnClickListener {
            onItemClick(notification)
        }

        // 设置复制按钮点击事件
//        holder.btnCopy.setOnClickListener {
//            val textToCopy = "${notification.title}\n${notification.content}"
//            onCopyClick(textToCopy)
//        }

        // 设置分享按钮点击事件
//        holder.btnShare.setOnClickListener {
//            val textToShare = "${notification.title}\n${notification.content}"
//            onShareClick(textToShare)
//        }

        // 这里可以添加加载应用图标的逻辑
        // loadAppIcon(notification.packageName, holder.appIcon)
    }

    override fun getItemCount(): Int = notifications.size

    fun updateData(newNotifications: List<NotificationLog>) {
        notifications = newNotifications
        notifyDataSetChanged()
    }

    private fun isToday(time: Long): Boolean {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return time >= today
    }

    // 加载应用图标的方法（预留）
    private fun loadAppIcon(packageName: String, imageView: ImageView) {
        // 这里可以实现在后台加载应用图标的逻辑
        // 暂时使用默认图标
    }
}