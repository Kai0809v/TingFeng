package com.dreamct.tingfeng.ui.theme

import android.app.Activity
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import com.dreamct.tingfeng.R
import com.dreamct.tingfeng.service.TingConfig
import com.google.android.material.R as MaterialR
import com.google.android.material.color.DynamicColors

/**
 * 应用主题（全局配色）注册表（单条目 = [TingTheme]，选择入口见
 * SettingsFragment 的主题设置行）。
 *
 * 调色板移植自其他项目的 Compose 主题资源（工作区根目录 theme/ThemePalettes.kt，
 * 五套 Material3 配色 × 亮/暗模式），转为 View 体系的「主题覆盖样式」方案：
 *  - 基础主题（Theme.FoundationX）= 深海蓝调（DeepOcean），色值直接写入
 *    values(-night)/colors.xml 的 md_theme_*，资源名与既有引用保持不变；
 *  - 其余主题（含应用默认「极简白灰」）在 values/theme_palettes.xml 提供同名槽位
 *    色值（wg_/ws_/fm_/sf_/rs_ 前缀），由 theme_overlays.xml 的
 *    ThemeOverlay.AppTheme.* 全量覆盖配色 attr（含 android:windowBackground，
 *    保证切换主题后冷启动第一帧颜色一致；基础主题与白灰的窗口底色同为浅灰/
 *    近黑，启动预览窗口无不协调闪色）；
 *  - 暗色模式继续跟随系统：每个 attr 引用的 @color 通过 values-night 限定符自动切换。
 *
 * 主题覆盖样式的应用时机：每个 Activity 的 onCreate 首行（super 之前）调用
 * [applyTo]——applyStyle 只做加法不可撤销，跨主题切换必须 recreate Activity
 * 让主题从 manifest 重新解析后再叠加当前覆盖。
 *
 * 覆盖范围（全应用生效）：设置页/主页/统计页的 XML 布局与代码动态视图、
 * 底部面板（BottomSheetDialog）、对话框、启动窗口背景（windowBackground）、
 * 底部 Dock 与三页顶栏的三段色相渐变（[hueGradient]，颜色取自主题 attr）；
 * 桌面图标不随主题变化。
 */
enum class TingTheme(
    val id: String,
    val displayName: String,
    /** 选择面板中的说明文案 */
    val hint: String,
    /** 主题覆盖样式资源；0 = 基础主题（深海蓝调，无需叠加），-1 = 动态取色 */
    @StyleRes val overlayRes: Int,
    /** 选择面板的色板预览（亮色模式 primary / tertiary / secondary） */
    val swatches: List<Int>,
) {
    PLAIN(
        "plain", "极简白灰", "纯净白灰，无渐变，应用默认",
        R.style.ThemeOverlay_AppTheme_Plain,
        listOf(0xFF454746.toInt(), 0xFF565656.toInt(), 0xFF5E5E5E.toInt())
    ),
    DEEP_OCEAN(
        "deep_ocean", "深海蓝调", "沉静蓝青，深邃沉稳",
        0, listOf(0xFF005CB1.toInt(), 0xFF006874.toInt(), 0xFF006780.toInt())
    ),
    SOFT_FOCUS(
        "soft_focus", "默认紫韵", "柔和紫罗兰，轻盈浪漫",
        R.style.ThemeOverlay_AppTheme_SoftFocus,
        listOf(0xFF6B4EFF.toInt(), 0xFFFF8C61.toInt(), 0xFF2DD4BF.toInt())
    ),
    WARM_SUNSET(
        "warm_sunset", "暖色日落", "暖调橙棕，落日余晖",
        R.style.ThemeOverlay_AppTheme_WarmSunset,
        listOf(0xFF8C5000.toInt(), 0xFF7A5900.toInt(), 0xFF9C4325.toInt())
    ),
    FRESH_MINT(
        "fresh_mint", "清新薄荷", "清爽绿调，自然通透",
        R.style.ThemeOverlay_AppTheme_FreshMint,
        listOf(0xFF006C4C.toInt(), 0xFF3F6374.toInt(), 0xFF4A6357.toInt())
    ),
    SOFT_ROSE(
        "soft_rose", "柔粉玫瑰", "温柔粉调，轻盈柔和",
        R.style.ThemeOverlay_AppTheme_SoftRose,
        listOf(0xFF9C3B5E.toInt(), 0xFF8B4F28.toInt(), 0xFF8C4A5E.toInt())
    ),
    DYNAMIC(
        "dynamic", "动态取色", "跟随系统壁纸配色（Android 12+，低版本回退深海蓝调）",
        -1, listOf(0xFF6750A4.toInt(), 0xFF7D5260.toInt(), 0xFF625B71.toInt())
    );

    companion object {
        const val DEFAULT_ID = "plain"

        fun fromId(id: String?): TingTheme =
            entries.firstOrNull { it.id == id } ?: PLAIN

        /** 当前选中的主题（未选择/未知 id 回退极简白灰） */
        fun current(context: Context): TingTheme =
            fromId(TingConfig.appTheme(context))

        /**
         * 在 Activity 的 onCreate 首行（super 之前）调用，把选中主题的
         * 覆盖样式叠加到 Activity 主题上：
         *  - 固定调色板：叠加对应 ThemeOverlay.AppTheme.*；
         *  - 动态取色：用 Material 的 DynamicColors API 叠加动态颜色覆盖
         *    （仅 Android 12+ 生效，低版本 API 内部自动跳过，回退基础主题）；
         *  - 深海蓝调（基础主题）：不叠加任何样式。
         */
        @JvmStatic
        fun applyTo(activity: Activity) {
            when (val overlay = current(activity).overlayRes) {
                0 -> Unit
                -1 -> DynamicColors.applyToActivityIfAvailable(activity)
                else -> activity.theme.applyStyle(overlay, true)
            }
        }

        /** 解析主题属性颜色（属性值可能是字面量或资源引用），失败返回 [fallback] */
        private fun resolveColor(context: Context, @AttrRes attr: Int, fallback: Int): Int {
            val tv = TypedValue()
            if (!context.theme.resolveAttribute(attr, tv, true)) return fallback
            return if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                tv.data
            } else {
                try {
                    ContextCompat.getColor(context, tv.resourceId)
                } catch (e: Exception) {
                    fallback
                }
            }
        }

        /**
         * 三段色相渐变背景：primaryContainer → secondaryContainer → tertiaryContainer。
         * 三容器色在每套调色板中本就沿品牌色相排列（如深海蓝调的 蓝→蓝青→青），
         * 三段插值即得自然的色相流动；亮暗模式由主题 attr 自动切换。
         * 用于底部 Dock、三页顶栏、主题选择面板预览条等点缀性背景。
         *
         * @param cornerRadiusPx 圆角半径（0 = 直角）
         * @param horizontal true = 水平流动（左→右），false = 垂直（上→下）
         * @param surfaceBlend 与 colorSurface 的混合比例（0 = 原容器色饱和浓烈，
         *   0.5 = 对半淡化）。顶栏等大面积背景用 0.5 淡雅不抢戏，Dock/预览条
         *   等小面积焦点用 0 保持浓淡对比。混合而非半透明：不透明渐变在任何
         *   底色上表现一致（半透明叠卡片底/页面底会得出两种颜色）
         */
        @JvmStatic
        fun hueGradient(
            context: Context,
            cornerRadiusPx: Float = 0f,
            horizontal: Boolean = true,
            surfaceBlend: Float = 0f,
            /** 描边（默认关闭）：用 colorOutlineVariant 画 1dp 弱轮廓，与设置页/
             *  统计页大卡片同源。必须画在渐变 drawable 边缘——主页顶栏渐变直接
             *  覆盖卡片本体背景，MaterialCardView 自带描边会被盖掉 */
            stroke: Boolean = false
        ): GradientDrawable {
            val surface = resolveColor(context, MaterialR.attr.colorSurface, 0xFFFFFFFF.toInt())
            fun blend(color: Int): Int =
                if (surfaceBlend <= 0f) color
                else android.graphics.Color.rgb(
                    (android.graphics.Color.red(color) * (1 - surfaceBlend) + android.graphics.Color.red(surface) * surfaceBlend).toInt(),
                    (android.graphics.Color.green(color) * (1 - surfaceBlend) + android.graphics.Color.green(surface) * surfaceBlend).toInt(),
                    (android.graphics.Color.blue(color) * (1 - surfaceBlend) + android.graphics.Color.blue(surface) * surfaceBlend).toInt()
                )
            val colors = intArrayOf(
                blend(resolveColor(context, MaterialR.attr.colorPrimaryContainer, 0xFFD4E3FF.toInt())),
                blend(resolveColor(context, MaterialR.attr.colorSecondaryContainer, 0xFFB8EAFF.toInt())),
                blend(resolveColor(context, MaterialR.attr.colorTertiaryContainer, 0xFF9EEFFD.toInt()))
            )
            return GradientDrawable(
                if (horizontal) GradientDrawable.Orientation.LEFT_RIGHT
                else GradientDrawable.Orientation.TOP_BOTTOM,
                colors
            ).apply {
                if (cornerRadiusPx > 0f) cornerRadius = cornerRadiusPx
                if (stroke) {
                    setStroke(
                        context.resources.displayMetrics.density.toInt().coerceAtLeast(1),
                        resolveColor(context, MaterialR.attr.colorOutlineVariant, 0xFFC6C6C6.toInt())
                    )
                }
            }
        }
    }
}
