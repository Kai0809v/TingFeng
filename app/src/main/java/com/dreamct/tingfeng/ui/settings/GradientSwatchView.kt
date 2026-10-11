package com.dreamct.tingfeng.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * 渐变配色扇形色板：把一组颜色按数量等分为扇形，从圆心向外绘制成色环，
 * 用于设置页"卡片样式 → 渐变配色"的预设预览（扇形颜色即渐变用色，
 * 色卡下方展示配色名称）。
 */
class GradientSwatchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 扇形颜色列表（按顺序绘制），空列表回退灰色 */
    var colors: List<Int> = listOf(0xFF9E9E9E.toInt())
        set(value) {
            field = value.ifEmpty { listOf(0xFF9E9E9E.toInt()) }
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
        strokeWidth = dp(1.5f)
    }

    private val arcBounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = colors.size
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(2f)
        if (radius <= 0f) return
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 每个颜色一个扇形，从 12 点方向起顺时针均分
        val sweep = 360f / n
        colors.forEachIndexed { index, color ->
            fillPaint.color = color
            canvas.drawArc(arcBounds, -90f + index * sweep, sweep, true, fillPaint)
        }
        // 白色外环收边，浅色背景下保持色板轮廓清晰
        canvas.drawCircle(cx, cy, radius - ringPaint.strokeWidth / 2f, ringPaint)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
