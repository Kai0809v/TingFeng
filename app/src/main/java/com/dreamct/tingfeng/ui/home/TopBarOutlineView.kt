package com.dreamct.tingfeng.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.android.material.card.MaterialCardView
import androidx.core.graphics.withClip

/**
 * 顶栏轮廓层：绘制悬浮顶栏的圆角描边，支持"由上往下逐渐消失"的擦除动画。
 *
 * 放置在 MaterialCardView 内部（Container 之后）覆盖整个顶栏：
 *  - [outlineProgress] = 0：完整轮廓；
 *  - [outlineProgress] → 1：轮廓顶部先被擦除、逐渐向下推进，完全消失。
 * 顶栏上移与状态栏融合时，上半段轮廓先消失（该区域已被背景覆盖），
 * 视觉上呈现"轮廓线由上往下逐渐消失"的过渡效果。
 *
 * 不响应触摸事件（普通 View），不影响顶栏内容交互。
 */
class TopBarOutlineView : View {

    /** XML 膨胀必需的构造器 */
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    /** 代码构建用 */
    constructor(context: Context) : super(context)

    /** 轮廓擦除进度：0 = 完整轮廓，1 = 完全消失 */
    var outlineProgress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** 轮廓颜色：由外部注入主题色（避免代码直接引用 material R；默认半透明黑仅作回退） */
    var outlineColor: Int
        get() = paint.color
        set(value) {
            paint.color = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x1F000000
    }

    private val bounds = RectF()
    private var cornerRadius = dp(28f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 圆角与所在卡片保持一致
        cornerRadius = (parent as? MaterialCardView)?.radius ?: dp(28f)
        val inset = paint.strokeWidth / 2f
        bounds.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (outlineProgress >= 1f || width == 0 || height == 0) return
        // 每帧读取父卡片当前圆角（过渡时圆角同步收窄，轮廓始终贴合卡片形状）
        val radius = (parent as? MaterialCardView)?.radius ?: cornerRadius
        // 擦除线自上而下推进：裁掉轮廓顶部，只绘制擦除线以下的部分
        canvas.withClip(0f, outlineProgress * height, width.toFloat(), height.toFloat()) {
            drawRoundRect(bounds, radius, radius, paint)
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
