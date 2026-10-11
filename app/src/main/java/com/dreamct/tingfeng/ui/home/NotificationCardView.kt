package com.dreamct.tingfeng.ui.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.dreamct.tingfeng.R
import com.google.android.material.card.MaterialCardView

/**
 * 通知卡片视图（通知面板样式，纯 Kotlin 构建，替代 XML）：
 *
 *  ┌───────────────────────────────────────────┐
 *  │ [图标]  通知标题(粗体)           时间  ✕ │  ← 头部行（堆叠条带露出的部分）
 *  │         通知正文第一行……                 │
 *  │         通知正文第二行……                 │
 *  └───────────────────────────────────────────┘
 *
 *  - 左侧应用图标，右侧上方为通知标题、下方为通知正文，右上角时间 + 关闭按钮；
 *  - 所有卡片等高（正文固定 2 行）——堆叠几何依赖等高卡片；
 *  - 头部行高度 ≈ [HFConfig.stackPeekDp] 条带露出高度；
 *  - 不使用 cardElevation 阴影（会污染半透明背景，见 init 内说明），
 *    Z 轴层次感由堆叠缩放与描边体现。
 */
class NotificationCardView(context: Context) : MaterialCardView(context) {

    /** 应用图标（头部行左侧） */
    val appIcon: ImageView

    /** 通知标题（头部行，粗体两行以内） */
    val titleText: TextView

    /** 通知时间（头部行右上角） */
    val timeText: TextView

    // 关闭按钮已暂时移除（删除逻辑保留，见 NotificationAdapter 注释）：
    // val closeButton: ImageView

    /** 通知正文（标题下方，与标题对齐，固定 2 行） */
    val contentText: TextView

    /**
     * 样式容器（承载卡片背景）：[CardStyleEngine] 替换样式时修改它的
     * background，文字颜色通过 [titleText]/[contentText]/[timeText] 调整。
     */
    val styleContainer: LinearLayout

    init {
        radius = dp(HFConfig.cardCornerRadiusDp)
        // 必须保持 0：CardView 的自绘阴影（RoundRectDrawableWithShadow）会把卡片
        // 本体画在一个向内收缩的矩形里、把阴影画在收缩出来的边缘环带内。当
        // setCardBackgroundColor(TRANSPARENT) 且样式容器使用半透明背景（玻璃/磨砂）
        // 时，这圈内置阴影会透出来——卡片边缘发暗、内部显浅，形成"浅色内嵌矩形"。
        // 不透明背景会盖住阴影环带，因此只有半透明样式能看到该问题。
        // 堆叠层次感由 StackedCardLayoutManager 的缩放（stackScaleStep）体现。
        cardElevation = 0f
        // 程序化构建的 MaterialCardView 会套用主题默认 M3 卡片样式，其中自带
        // 12dp contentPadding，把样式容器整体内缩——玻璃类半透明背景与卡片边缘
        // 之间会露出一圈透底空隙（卡片内出现一块颜色更浅的内嵌矩形），必须归零，
        // 让样式容器铺满整张卡片（背景圆角由样式引擎设置为与卡片一致）
        setContentPadding(0, 0, 0, 0)
        preventCornerOverlap = false

        styleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_gradient_blue)
            setPadding(dp(12f).toInt(), dp(10f).toInt(), dp(12f).toInt(), dp(12f).toInt())
        }
        val container = styleContainer

        // ---- 头部行：图标 + 标题 + 时间 + 关闭 ----
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        appIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36f).toInt(), dp(36f).toInt())
            contentDescription = "应用图标"
        }

        titleText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = dp(10f).toInt() }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            minLines = 2
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        timeText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8f).toInt() }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(0xB3FFFFFF.toInt())
        }

        // 关闭按钮已暂时移除，恢复时取消以下注释：
        // closeButton = ImageView(context).apply {
        //     layoutParams = LinearLayout.LayoutParams(dp(28f).toInt(), dp(28f).toInt()).apply {
        //         marginStart = dp(6f).toInt()
        //     }
        //     setImageResource(R.drawable.close)
        //     setColorFilter(0xB3FFFFFF.toInt())
        //     contentDescription = "关闭"
        //     setPadding(dp(4f).toInt(), dp(4f).toInt(), dp(4f).toInt(), dp(4f).toInt())
        //     // 无边框波纹反馈
        //     val tv = TypedValue()
        //     context.theme.resolveAttribute(
        //         android.R.attr.selectableItemBackgroundBorderless, tv, true
        //     )
        //     if (tv.resourceId != 0) setBackgroundResource(tv.resourceId)
        // }

        header.addView(appIcon)
        header.addView(titleText)
        header.addView(timeText)
        // header.addView(closeButton)

        // ---- 正文：与标题列对齐，固定 2 行 → 所有卡片等高 ----
        contentText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(46f).toInt() // 图标 36dp + 间距 10dp
                topMargin = dp(4f).toInt()
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0xF0FFFFFF.toInt())
            minLines = 2
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        container.addView(header)
        container.addView(contentText)
        addView(
            container,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // 预留样式接口：卡片创建时回调（可在设置中自定义样式）
        HFConfig.cardStyle.onCardCreated(this)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
