package com.dreamct.tingfeng.ui.home

import android.graphics.Canvas
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dreamct.tingfeng.data.NotificationLog
import kotlin.math.abs

/**
 * 顶层卡片滑动消除回调 —— 复现通知栏"滑走通知"的效果：
 *
 *  - 仅允许滑动当前堆叠在最上层的卡片（与系统通知栏一致）；
 *  - 滑动时卡片水平位移并逐渐透明；
 *  - 松手越过阈值后从数据库删除该条记录，后层卡片自动补位。
 *
 * @param layoutManager 堆叠布局管理器（用于判断顶层卡片）
 * @param getItem        由适配器提供指定位置的数据
 * @param onDismiss      用户确认消除时的回调（执行删除）
 */
class NotificationSwipeCallback(
    private val layoutManager: StackedCardLayoutManager,
    private val getItem: (Int) -> NotificationLog?,
    private val onDismiss: (NotificationLog) -> Unit
) : ItemTouchHelper.Callback() {

    /** 滑动超出该比例后松手即消除 */
    private val escapeRatio = 0.5f

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.bindingAdapterPosition
        val swipeFlags = if (position == layoutManager.frontCardPosition) {
            ItemTouchHelper.START or ItemTouchHelper.END
        } else {
            0 // 非顶层卡片不允许滑动，点击层叠边缘可先滚动置顶
        }
        return makeMovementFlags(0, swipeFlags)
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = escapeRatio

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        getItem(position)?.let(onDismiss)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val width = itemView.width.toFloat()
        if (width > 0 && actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            // 越滑越透明，模拟通知被"淡出"消除
            val progress = (abs(dX) / width).coerceIn(0f, 1f)
            itemView.alpha = 1f - progress / HFConfig.swipeAlphaFactor
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ) {
        // 恢复透明度（滑动被取消回弹时）
        viewHolder.itemView.alpha = 1f
        viewHolder.itemView.translationX = 0f
        super.clearView(recyclerView, viewHolder)
    }
}
