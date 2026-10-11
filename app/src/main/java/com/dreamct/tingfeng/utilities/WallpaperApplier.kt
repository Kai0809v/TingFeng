package com.dreamct.tingfeng.utilities

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.ViewGroup
import androidx.annotation.ColorInt

/**
 * 壁纸背景应用器（纯工具类，零第三方依赖，可直接复制到其他项目）。
 *
 * 让系统壁纸透出作为 Activity 背景（效果同桌面启动器），并在根布局铺一层
 * 纯色"遮罩"控制壁纸可见度：调节可见度只改遮罩的 alpha，即时生效、无需
 * 重建窗口；界面组件绘制在遮罩之上，完全不参与透明度调节。
 *
 * ── 复用步骤（三步）──────────────────────────────────────
 * 1. 主题声明（必须，且永远不要在运行时增删该 flag）：
 * ```xml
 * <style name="Theme.App.Wallpaper" parent="你的主题">
 *     <item name="android:windowShowWallpaper">true</item>
 *     <item name="android:windowBackground">@android:color/transparent</item>
 * </style>
 * ```
 *    flag 必须在窗口创建时就存在：
 *      - 运行时增删 FLAG_SHOW_WALLPAPER 会重建窗口 surface 但不清空旧帧，
 *        产生"旧界面不断叠加"的回声 bug；
 *      - 主题声明的 flag 会被启动屏（splash）窗口继承，冷启动直接透出
 *        壁纸，不会出现启动页残影。
 * 2. 在 [Activity.setContentView] 之后调用（幂等，可反复调用）：
 *      Kotlin：`WallpaperApplier.apply(activity, 0.6f)`
 *      Java  ：`WallpaperApplier.apply(activity, 0.6f);`
 * 3. 可见度的持久化由宿主项目自行实现（SharedPreferences 等），
 *    下次启动读回后再次 [apply] 即可；运行中改变可见度也只需再次 [apply]。
 *
 * ── Kotlin / Java 搭配说明 ───────────────────────────────
 * - object + @JvmStatic：Java 侧可直接 `WallpaperApplier.apply(...)` 静态调用，
 *   无需 `WallpaperApplier.INSTANCE.apply(...)` 中转；
 * - @JvmOverloads：为 Java 生成省略默认参数的重载（三参 / 两参版本均可调）；
 * - 实现为 Kotlin 文件，但调用方语言透明：Java 项目把本文件放进源码目录
 *   一起编译后，按上述静态方法调用即可。
 *
 * @param visibility 壁纸可见度 0~1：1 = 完全透出壁纸；0 = 遮罩全不透明（等同关闭功能）
 */
object WallpaperApplier {

    /**
     * 应用壁纸遮罩。
     *
     * @param visibility  壁纸可见度 0~1，超界自动钳制
     * @param scrimColor  遮罩颜色；缺省取主题的 android:colorBackground，
     *                    保证与应用背景风格一致（也可传任意品牌色）
     */
    @JvmStatic
    @JvmOverloads
    fun apply(activity: Activity, visibility: Float, @ColorInt scrimColor: Int = -1) {
        try {
            val resolvedScrim = if (scrimColor != -1) scrimColor else themeBackground(activity)
            val scrim = ColorDrawable(resolvedScrim)
            // 遮罩不透明度 = 1 - 可见度：可见度 1 → 遮罩全透明；可见度 0 → 遮罩全不透明
            scrim.alpha = ((1f - visibility.coerceIn(0f, 1f)) * 255).toInt()
            rootOf(activity)?.background = scrim
        } catch (_: Throwable) {
            // 任何异常都不允许影响宿主 Activity 启动
        }
    }

    /** 根布局（android.R.id.content 的第一个子 View） */
    private fun rootOf(activity: Activity): ViewGroup? {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return content.getChildAt(0) as? ViewGroup
    }

    /** 主题背景色（android:colorBackground），解析失败回退白色 */
    @ColorInt
    private fun themeBackground(activity: Activity): Int {
        val tv = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)
        return if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            tv.type <= TypedValue.TYPE_LAST_COLOR_INT
        ) tv.data else Color.WHITE
    }
}
