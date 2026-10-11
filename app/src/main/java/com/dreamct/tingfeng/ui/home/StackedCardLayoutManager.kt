package com.dreamct.tingfeng.ui.home

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

/**
 * Notification list with a fixed bottom stack and an expanded list above it.
 * During a scroll, the front card of the stack travels into the last expanded row;
 * the cards behind it advance by one peek height, like a rotating stack.
 */
class StackedCardLayoutManager(context: Context) : RecyclerView.LayoutManager() {

    private val density = context.resources.displayMetrics.density
    private var scrollOffset = 0
    private var pendingPosition = RecyclerView.NO_POSITION
    private var viewportHeight = 0
    private var cardHeight = 1
    private var expandedCount = 1
    private var expandedStep = 1
    private var foldTop = 0

    /** Position of the first expanded card, used by click and swipe actions. */
    var frontCardPosition: Int = 0
        private set

    private val spacingPx: Int
        get() = (HFConfig.expandedCardSpacingDp * density).toInt().coerceAtLeast(0)

    private val peekPx: Int
        get() = (HFConfig.stackPeekDp * density).toInt().coerceAtLeast(1)

    /** 顶部裁剪线（RecyclerView 绝对坐标）：卡片超出此线的部分不绘制。 */
    private val topClipPx: Int
        get() = (HFConfig.topClipDp * density).toInt()

    /** 顶部裁剪线（相对 paddingTop 的坐标，placements 使用同一坐标系）。 */
    private val topClipRel: Int
        get() = topClipPx - paddingTop

    /** 复用的裁剪矩形，避免每帧分配。 */
    private val clipRect = Rect()

    private val step: Int
        get() = expandedStep.coerceAtLeast(1)

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams =
        RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )

    override fun canScrollVertically(): Boolean = true

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (state.itemCount == 0) {
            removeAndRecycleAllViews(recycler)
            scrollOffset = 0
            expandedCount = 1
            frontCardPosition = 0
            return
        }
        if (width == 0 || height == 0) return

        // 视口取 RecyclerView 全高（忽略底部 Dock 遮挡 inset）：
        // 折叠堆的卡片主体可延伸到浮动 Dock 背后（配合 clipToPadding=false 绘入 padding 区）。
        // 列表末尾的安全间距由折叠区几何保证：折叠区高度 = 1.1 × 卡高（> Dock 实际高度），
        // 滚动到底时最后一张展开卡的底边仍高出 Dock 顶部约 1.1×卡高 + 间距 - 88dp 的冗余。
        viewportHeight = (height - paddingTop).coerceAtLeast(0)

        // Cards have a fixed height, so measure one item and use it for all stack geometry.
        val probe = recycler.getViewForPosition(0)
        measureChildWithMargins(probe, 0, 0)
        cardHeight = getDecoratedMeasuredHeight(probe).coerceAtLeast(1)
        removeAndRecycleView(probe, recycler)

        val collapsedHeight = (cardHeight * HFConfig.stackFoldHeightRatio).toInt()
        val upperHeight = (viewportHeight - collapsedHeight).coerceAtLeast(0)
        val nominalStep = (cardHeight + spacingPx).coerceAtLeast(1)
        val capacity = (upperHeight / nominalStep).coerceAtLeast(1)
        expandedCount = min(state.itemCount, capacity).coerceAtLeast(1)
        // If everything fits above, use the full viewport instead of reserving an empty stack.
        foldTop = if (state.itemCount > expandedCount) upperHeight else viewportHeight
        // Keep the configured spacing between rows.
        expandedStep = nominalStep

        if (pendingPosition != RecyclerView.NO_POSITION) {
            scrollOffset = pendingPosition.coerceAtLeast(0) * step
            pendingPosition = RecyclerView.NO_POSITION
        }
        scrollOffset = scrollOffset.coerceIn(0, maxScroll(state.itemCount))

        detachAndScrapAttachedViews(recycler)
        val placements = placementsFor(scrollOffset, state.itemCount)
        frontCardPosition = firstExpandedPosition(scrollOffset, state.itemCount)

        // Add deeper cards first. The expanded/front cards are therefore drawn above them.
        placements.sortedByDescending { it.position }.forEach { placement ->
            val child = recycler.getViewForPosition(placement.position)
            addView(child)
            measureChildWithMargins(child, 0, 0)
            layoutChild(child, placement)
        }
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        if (dy == 0 || childCount == 0 || state.itemCount == 0) return 0

        val target = (scrollOffset + dy).coerceIn(0, maxScroll(state.itemCount))
        val consumed = target - scrollOffset
        if (consumed == 0) return 0

        scrollOffset = target
        frontCardPosition = firstExpandedPosition(scrollOffset, state.itemCount)
        val placements = placementsFor(scrollOffset, state.itemCount)
        val wanted = placements.associateBy { it.position }
        val attached = HashMap<Int, View>()

        // Keep existing holders alive while scrolling; only recycle cards that leave the viewport.
        for (index in childCount - 1 downTo 0) {
            val child = getChildAt(index) ?: continue
            val position = getPosition(child)
            if (position !in wanted) {
                removeAndRecycleViewAt(index, recycler)
            } else {
                attached[position] = child
            }
        }

        placements.sortedByDescending { it.position }.forEach { placement ->
            val child = attached[placement.position] ?: run {
                val newChild = recycler.getViewForPosition(placement.position)
                var insertAt = childCount
                for (index in 0 until childCount) {
                    val existingPosition = getPosition(getChildAt(index) ?: continue)
                    if (existingPosition < placement.position) {
                        insertAt = index
                        break
                    }
                }
                addView(newChild, insertAt)
                measureChildWithMargins(newChild, 0, 0)
                newChild
            }
            layoutChild(child, placement)
        }
        return consumed
    }

    private data class Placement(
        val position: Int,
        val top: Int,
        val scale: Float,
        val stackLevel: Int
    )

    private fun maxScroll(itemCount: Int): Int =
        (itemCount - expandedCount).coerceAtLeast(0) * step

    private fun firstExpandedPosition(offset: Int, itemCount: Int): Int =
        (offset / step).coerceIn(0, (itemCount - 1).coerceAtLeast(0))

    private fun placementsFor(offset: Int, itemCount: Int): List<Placement> {
        if (itemCount <= 0) return emptyList()

        val unit = offset / step
        val fraction = (offset % step).toFloat() / step
        val expandedTop = if (itemCount > expandedCount) {
            (foldTop - expandedCount * step).coerceAtLeast(0)
        } else {
            0
        }
        val lastExpandedTop = expandedTop + (expandedCount - 1) * step
        val placements = ArrayList<Placement>(expandedCount + 8)

        // 离场卡（position < unit）：已滚出展开区、正从悬浮顶栏后方滑出的卡片。
        // 保持附着并继续滑动，直到整体越过顶部裁剪线（顶栏上缘）才回收——
        // 配合 layoutChild 的 clipBounds 实现"超过顶栏的部分消失、
        // 未超过的被顶栏遮住"的平滑离场，避免卡片中途突然消失。
        for (position in (unit - 1) downTo 0) {
            val unitsGone = unit - position
            val top = (expandedTop - (unitsGone + fraction) * step).toInt()
            if (top + cardHeight <= topClipRel) break
            placements += Placement(position, top, 1f, 0)
        }

        // Expanded cards use identical width and spacing. They slide upward as a group.
        val expandedEnd = min(unit + expandedCount, itemCount)
        for (position in unit until expandedEnd) {
            val row = position - unit
            val top = expandedTop + row * step - (fraction * step).toInt()
            if (top >= viewportHeight) break
            // 顶边裁剪线判定：底边仍低于裁剪线才布局（否则越过线的部分直接消失）。
            if (top + cardHeight > topClipRel) {
                placements += Placement(position, top, 1f, 0)
            }
        }

        // Folded cards sit behind the last expanded card, instead of starting at the bottom
        // edge of the list. This overlap is what makes the next card emerge from behind it.
        // Each folded layer reveals a progressively smaller strip. The next card stays
        // directly behind the second visible layer, so it is uncovered gradually as that
        // layer advances during the scroll instead of entering from the viewport bottom.
        val visibleLayers = HFConfig.stackVisibleLayerCount.coerceAtLeast(1)
        val stackEnd = min(itemCount, expandedEnd + visibleLayers + 1)
        for (position in expandedEnd until stackEnd) {
            val stackIndex = position - expandedEnd
            val depth = stackIndex + 1
            val reveal = revealHeight(depth)
            val visibleDepth = min(depth, visibleLayers)
            val stackOffset = cumulativeReveal(visibleDepth)
            val top = if (depth <= visibleLayers) {
                lastExpandedTop + stackOffset - (fraction * reveal).toInt()
            } else {
                // Keep one fully covered card behind the final visible layer. Moving that
                // layer upward uncovers this card's edge continuously during the gesture.
                lastExpandedTop + stackOffset
            }
            if (top >= viewportHeight) break
            if (top + cardHeight <= 0) continue

            val scale = 1f - (depth - fraction) * HFConfig.stackScaleStep
            val boundedScale = scale.coerceAtLeast(HFConfig.stackMinScale)
            placements += Placement(
                position = position,
                top = top,
                scale = boundedScale,
                stackLevel = stackIndex + 1
            )
        }
        return placements
    }

    /** The exposed strip halves with each deeper layer (28dp, 14dp, 7dp, ...). */
    private fun revealHeight(depth: Int): Int {
        val divisor = 1 shl (depth - 1).coerceIn(0, 20)
        return (peekPx / divisor).coerceAtLeast(1)
    }

    private fun cumulativeReveal(depth: Int): Int {
        var total = 0
        for (layer in 1..depth) total += revealHeight(layer)
        return total
    }

    private fun layoutChild(child: View, placement: Placement) {
        val params = child.layoutParams as RecyclerView.LayoutParams
        // layoutDecoratedWithMargins expects the outer bounds, including margins.
        // The measured decorated width excludes margins, so add them back before placing;
        // otherwise the right margin is effectively applied twice and cards shift left.
        val outerWidth = getDecoratedMeasuredWidth(child) + params.leftMargin + params.rightMargin
        val childHeight = getDecoratedMeasuredHeight(child)
        layoutDecoratedWithMargins(
            child,
            paddingLeft,
            paddingTop + placement.top,
            paddingLeft + outerWidth,
            paddingTop + placement.top + childHeight
        )
        child.scaleX = placement.scale
        child.scaleY = placement.scale
        child.translationX = 0f
        // 顶部裁剪：滑出到顶栏上缘以上的部分不再绘制（该区域通常已被
        // 悬浮顶栏遮住，裁剪是为了避免卡片在顶栏上方 8dp 边距缝隙露边）。
        // 注意需在 layoutDecorated 之后读取 child.width/height。
        val childTop = paddingTop + placement.top
        if (childTop < topClipPx) {
            clipRect.set(0, topClipPx - childTop, child.width, child.height)
            child.clipBounds = clipRect
        } else {
            child.clipBounds = null
        }
        HFConfig.cardStyle.onApplyStackEffect(child, placement.stackLevel)
    }

    override fun scrollToPosition(position: Int) {
        pendingPosition = position
        requestLayout()
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int
    ) {
        val target = position.coerceAtLeast(0) * step
        recyclerView.smoothScrollBy(0, target - scrollOffset)
    }

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int =
        max(maxScroll(state.itemCount) + height, height)

    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int = height

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int = scrollOffset

    fun scrollToTop() {
        pendingPosition = 0
        requestLayout()
    }
}
