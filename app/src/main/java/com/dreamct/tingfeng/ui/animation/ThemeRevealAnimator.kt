package com.dreamct.tingfeng.ui.animation

import android.app.Activity
import android.view.View
import android.view.ViewAnimationUtils
import android.view.animation.DecelerateInterpolator
import androidx.core.view.doOnPreDraw
import com.dreamct.tingfeng.service.TingConfig
import kotlin.math.hypot
import kotlin.math.max

/**
 * 主题切换的圆形揭示动画（裁切变换）：
 * 设置页点选新主题时记录被点击行的屏幕坐标（[arm]）→ recreate → 新首帧
 * 以该点为圆心把新主题内容圆形展开，圆外露出 windowBackground（主题底色），
 * 完成旧配色到新配色的过渡。
 *
 * 本目录（ui/animation）统一管理后续加入的动画实现类。
 */
object ThemeRevealAnimator {

    /** 揭示动画时长（ms）：用户要求放慢一秒（480ms + 1000ms） */
    private const val DURATION_MS = 1480L

    /**
     * 记录一次待播放的揭示动画：必须在 recreate 之前调用。
     * [cx]/[cy] 为触发点在屏幕上的坐标（全屏窗口下屏幕坐标即窗口内容坐标，
     * EdgeToEdge 下 content 视图与屏幕原点重合）。
     */
    @JvmStatic
    fun arm(context: android.content.Context, cx: Int, cy: Int) {
        TingConfig.setPendingThemeReveal(context, cx, cy)
    }

    /**
     * MainActivity onCreate（setContentView 之后）调用：有 pending 则在首帧
     * 绘制前对窗口内容视图（android.R.id.content）做圆形揭示，结束后清除
     * 标志（避免之后每次冷启动重播）。
     *
     * reveal 加在 content 上安全：记忆教训中"circular reveal 不能用于含负
     * margin 越界子视图的容器"针对 fragment 级容器（其 bounds top=状态栏高，
     * 顶栏相对它越界会被裁掉）；而 EdgeToEdge 全屏窗口下 content top=0，
     * 顶栏负 margin 部分的最终绘制（y ∈ [0, statusBarTop]）仍落在 content
     * bounds 内，不会被裁切。
     */
    @JvmStatic
    fun playIfPending(activity: Activity) {
        val (rawX, rawY) = TingConfig.pendingThemeReveal(activity) ?: return
        // 播放前先清标志：无论后续是否成功播出，都不允许下次启动重播
        TingConfig.clearPendingThemeReveal(activity)

        val content = activity.findViewById<View>(android.R.id.content)
        // 隐藏内容防止新主题先闪一帧，等首帧绘制前再揭示
        content.visibility = View.INVISIBLE
        content.doOnPreDraw {
            val w = content.width.coerceAtLeast(1)
            val h = content.height.coerceAtLeast(1)
            // 坐标夹回当前窗口范围（旋转/分屏等导致尺寸变化时防越界）
            val cx = rawX.coerceIn(0, w)
            val cy = rawY.coerceIn(0, h)
            // 覆盖全窗口的半径：到最远角点的距离
            val maxRadius = hypot(max(cx, w - cx).toFloat(), max(cy, h - cy).toFloat())
                .coerceAtLeast(1f)
            content.visibility = View.VISIBLE
            ViewAnimationUtils.createCircularReveal(content, cx, cy, 0f, maxRadius).apply {
                duration = DURATION_MS
                interpolator = DecelerateInterpolator(1.6f)
                start()
            }
        }
    }
}
