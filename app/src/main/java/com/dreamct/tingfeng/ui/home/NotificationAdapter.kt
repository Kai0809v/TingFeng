package com.dreamct.tingfeng.ui.home

import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dreamct.tingfeng.R
import com.dreamct.tingfeng.data.NotificationLog
import java.util.Date
import java.util.Locale

// --- ⬇️ 1. 创建 DiffUtil.ItemCallback ⬇️ ---
class NotificationDiffCallback : DiffUtil.ItemCallback<NotificationLog>() {
    /**
     * 判断是否是同一个 item
     * 通常使用数据的唯一 ID
     */
    override fun areItemsTheSame(oldItem: NotificationLog, newItem: NotificationLog): Boolean {
        return oldItem.id == newItem.id
    }

    /**
     * 判断同一个 item 的内容是否发生了变化
     */
    override fun areContentsTheSame(oldItem: NotificationLog, newItem: NotificationLog): Boolean {
        // Kotlin 的 data class 会自动生成 equals 方法，可以直接比较
        return oldItem == newItem
    }
}


// --- ⬇️ 2. 修改 Adapter 继承自 ListAdapter ⬇️ ---
class NotificationAdapter(
    private val onItemClick: (NotificationLog) -> Unit
) : ListAdapter<NotificationLog, NotificationAdapter.ViewHolder>(NotificationDiffCallback()) {

    // ViewHolder 类保持不变
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        val appName: TextView = itemView.findViewById(R.id.notification_app_name)
        val title: TextView = itemView.findViewById(R.id.notification_title)
        val time: TextView = itemView.findViewById(R.id.notification_time)
        val content: TextView = itemView.findViewById(R.id.notification_content)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // --- ⬇️ 3. 使用 getItem(position) 获取数据 ⬇️ ---
        val notification = getItem(position)

        // 应用名
        holder.appName.text = notification.appName
        // 设置通知标题
        holder.title.text = notification.title
        // 格式化并设置时间
        holder.time.text = formatTimestamp(notification.time)
        // 设置通知内容
        holder.content.text = notification.content
        // 设置点击事件
        holder.itemView.setOnClickListener {
            onItemClick(notification)
        }
    }

    // --- ⬇️ 4. 移除 getItemCount() 和 updateData() ⬇️ ---
    // ListAdapter 会自动处理 getItemCount()
    // public fun submitList(list: List<NotificationLog>?) 是 ListAdapter 自带的更新方法

    private fun formatTimestamp(time: Long): String {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
        val date = Date(time)

        return if (isToday(time)) {
            "今天 ${timeFormat.format(date)}"
        } else {
            "${dateFormat.format(date)} ${timeFormat.format(date)}"
        }
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
}