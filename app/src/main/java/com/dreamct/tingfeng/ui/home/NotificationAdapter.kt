package com.dreamct.tingfeng.ui.home

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dreamct.tingfeng.R
import com.dreamct.tingfeng.data.NotificationLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通知卡片堆栈适配器（Kotlin）。
 *
 * 交互回调：
 *  - [onItemClick]：点击任意卡片区域打开完整通知详情
 *  - 关闭按钮（onCloseClick）已暂时移除，恢复时取消各处注释即可：
 *    构造参数、ViewHolder.closeButton、onBindViewHolder 中的点击监听，
 *    以及 NotificationCardView 中的按钮构建代码。
 */
class NotificationAdapter(
    private val onItemClick: (NotificationLog) -> Unit
    // 暂时移除删除逻辑，恢复时取消注释（需同步恢复 NotificationCardView 的关闭按钮）：
    // private val onCloseClick: (NotificationLog) -> Unit
) : ListAdapter<NotificationLog, NotificationAdapter.ViewHolder>(NotificationDiffCallback()) {

    class ViewHolder(val card: NotificationCardView) : RecyclerView.ViewHolder(card) {
        val appIcon: ImageView = card.appIcon
        val titleText: TextView = card.titleText
        val timeText: TextView = card.timeText
        val contentText: TextView = card.contentText
        // val closeButton: ImageView = card.closeButton
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val card = NotificationCardView(parent.context).apply {
            // 堆叠间距由布局管理器的 peek 统一控制，垂直边距保持 0
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val marginPx =
                    (HFConfig.cardMarginHorizontalDp * resources.displayMetrics.density).toInt()
                setMargins(marginPx, 0, marginPx, 0)
            }
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = getItem(position)
        val context = holder.itemView.context

        // 应用图标：按包名加载真实图标，失败回退默认图标
        loadAppIcon(context, notification.packageName)?.let {
            holder.appIcon.setImageDrawable(it)
        } ?: holder.appIcon.setImageResource(R.mipmap.ic_launcher)

        holder.titleText.text = notification.title
        holder.timeText.text = formatTimestamp(notification.time)
        holder.contentText.text = notification.content

        holder.itemView.setOnClickListener { onItemClick(notification) }
        // 暂时移除删除逻辑：
        // holder.closeButton.setOnClickListener { onCloseClick(notification) }

        // 预留样式接口：数据绑定时回调（可在设置中自定义格式）
        HFConfig.cardStyle.onCardBound(holder.card, notification)
    }

    /** 按包名加载应用图标（读不到返回 null） */
    private fun loadAppIcon(context: Context, packageName: String): Drawable? = try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (_: Exception) {
        null
    }

    companion object {
        /** 紧凑时间格式：今天 HH:mm，更早 MM/dd HH:mm */
        fun formatTimestamp(timestamp: Long): String {
            val now = Date()
            val date = Date(timestamp)
            val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                .format(now) == SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(date)
            return if (sameDay) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            } else {
                SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(date)
            }
        }
    }
}

/** DiffUtil：按 id 判断同一通知，内容变化时刷新 */
class NotificationDiffCallback : DiffUtil.ItemCallback<NotificationLog>() {
    override fun areItemsTheSame(oldItem: NotificationLog, newItem: NotificationLog): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: NotificationLog, newItem: NotificationLog): Boolean =
        oldItem == newItem
}
