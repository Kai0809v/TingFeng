package com.dreamct.tingfeng.ui.home

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import androidx.appcompat.R as AppCompatR
import androidx.core.content.ContextCompat
import com.dreamct.tingfeng.service.TingConfig

/**
 * 通知卡片样式引擎：实现 [HFConfig.NotificationCardStyle] 预留的样式钩子，
 * 把设置页"卡片样式"中保存的配置（[TingConfig]）应用到每个通知卡片。
 *
 * 样式类型（与设置页单选项一一对应）：
 *  - [TYPE_DYNAMIC]  动态颜色：跟随系统主题取 colorPrimary 同色系渐变；
 *  - [TYPE_GRADIENT] 渐变配色：[GRADIENT_PRESETS] 中的预设（设置页以扇形色卡展示）；
 *  - [TYPE_BLUR]     高斯模糊：半透明磨砂近似（真实的壁纸背景模糊需 RenderEffect，
 *                    API 31+ 截取壁纸区域处理，后续实现）；
 *  - [TYPE_LIQUID]   液态玻璃：高透白渐变 + 白描边 + 文字投影；
 *  - [TYPE_CUSTOM]   用户自定义图片（上传指定规格图片）——占位，功能后续开发，
 *                    当前回退为动态颜色。
 *
 * 用法：HomeFragment 中 `HFConfig.cardStyle = CardStyleEngine`，
 * 卡片创建时（[onCardCreated]）自动应用当前配置；设置更改后回到主页时
 * Fragment 视图重建、卡片重新创建，样式随之刷新。
 */
object CardStyleEngine : HFConfig.NotificationCardStyle {

    // ---- 样式类型常量（与 TingConfig 存储值一致） ----
    const val TYPE_DYNAMIC = "dynamic"
    const val TYPE_GRADIENT = "gradient"
    const val TYPE_BLUR = "blur"
    const val TYPE_LIQUID = "liquid"
    const val TYPE_CUSTOM = "custom"

    /** 渐变配色预设：[id] 持久化到 TingConfig，[name] 展示在设置页色卡下方 */
    data class GradientPreset(val id: String, val name: String, val colors: List<Int>)

    /** 渐变配色预设列表（设置页卡片式展示，扇形色板取 [colors]） */
    val GRADIENT_PRESETS = listOf(
        GradientPreset("dawn", "晨曦", listOf(0xFFFF9A8B, 0xFFFF6A88, 0xFFFF99AC).map { it.toInt() }),
        GradientPreset("sakura", "樱粉", listOf(0xFFF78CA0, 0xFFF9748F).map { it.toInt() }),
        GradientPreset("ocean", "海风", listOf(0xFF4FACFE, 0xFF00F2FE).map { it.toInt() }),
        GradientPreset("lime", "青柠", listOf(0xFF43E97B, 0xFF38F9D7).map { it.toInt() }),
        GradientPreset("dusk", "暮色", listOf(0xFFA18CD1, 0xFFFBC2EB).map { it.toInt() }),
        GradientPreset("amber", "琥珀", listOf(0xFFF6D365, 0xFFFDA085).map { it.toInt() })
    )

    /** 按预设 id 查找渐变预设，找不到回退第一个 */
    fun findPreset(id: String): GradientPreset =
        GRADIENT_PRESETS.firstOrNull { it.id == id } ?: GRADIENT_PRESETS.first()

    override fun onCardCreated(card: NotificationCardView) {
        applyStyle(card)
    }

    /** 按当前持久化配置应用卡片样式 */
    fun applyStyle(card: NotificationCardView) {
        when (TingConfig.cardStyleType(card.context)) {
            TYPE_BLUR -> applyBlur(card)
            TYPE_LIQUID -> applyLiquid(card)
            TYPE_GRADIENT ->
                applyGradientColors(card, findPreset(TingConfig.cardGradientId(card.context)).colors)
            // TODO(TYPE_CUSTOM): 用户自定义图片背景（上传指定规格图片），功能后续开发
            else -> applyDynamic(card)
        }
    }

    /** 动态颜色：跟随系统主题 colorPrimary 的同色系渐变 */
    private fun applyDynamic(card: NotificationCardView) {
        val primary = themeColor(card.context, AppCompatR.attr.colorPrimary, 0xFF415F91.toInt())
        applyGradientColors(card, listOf(primary, blend(primary, Color.WHITE, 0.35f)))
    }

    /** 渐变背景 + 浅色文字 */
    private fun applyGradientColors(card: NotificationCardView, colors: List<Int>) {
        card.setCardBackgroundColor(Color.TRANSPARENT)
        card.styleContainer.background = buildDrawable(card, colors.toIntArray(), strokeColor = 0)
        setTextColors(card, light = true, shadow = false)
    }

    /** 高斯模糊（磨砂玻璃近似）：半透明白 + 深色文字 */
    private fun applyBlur(card: NotificationCardView) {
        card.setCardBackgroundColor(Color.TRANSPARENT)
        card.styleContainer.background = buildDrawable(
            card,
            intArrayOf(0xE8FFFFFF.toInt(), 0xD2FFFFFF.toInt()),
            strokeColor = 0x1F1B1B1F.toInt()
        )
        setTextColors(card, light = false)
    }

    /** 液态玻璃：高透白渐变 + 白描边 + 浅色文字投影 */
    private fun applyLiquid(card: NotificationCardView) {
        card.setCardBackgroundColor(Color.TRANSPARENT)
        card.styleContainer.background = buildDrawable(
            card,
            intArrayOf(0x66FFFFFF.toInt(), 0x2EFFFFFF.toInt(), 0x14FFFFFF.toInt()),
            strokeColor = 0x59FFFFFF.toInt()
        )
        setTextColors(card, light = true, shadow = true)
    }

    /** 构建圆角渐变背景（圆角与卡片一致；strokeColor 传 0 表示不描边） */
    private fun buildDrawable(view: View, colors: IntArray, strokeColor: Int): GradientDrawable {
        val drawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
        drawable.cornerRadius = view.dp(HFConfig.cardCornerRadiusDp)
        if (strokeColor != 0) {
            drawable.setStroke(view.dp(1f).toInt(), strokeColor)
        }
        return drawable
    }

    /** 按背景明暗调整卡片文字颜色（浅色文字可选投影） */
    private fun setTextColors(card: NotificationCardView, light: Boolean, shadow: Boolean = false) {
        if (light) {
            card.titleText.setTextColor(Color.WHITE)
            card.contentText.setTextColor(0xF0FFFFFF.toInt())
            card.timeText.setTextColor(0xB3FFFFFF.toInt())
            if (shadow) {
                card.titleText.setShadowLayer(2f, 0f, 1f, 0x66000000.toInt())
            } else {
                card.titleText.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }
        } else {
            card.titleText.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            card.titleText.setTextColor(0xDE1B1B1F.toInt())
            card.contentText.setTextColor(0xE61B1B1F.toInt())
            card.timeText.setTextColor(0x611B1B1F.toInt())
        }
    }

    /** 颜色插值：ratio = 0 返回 [color]，1 返回 [other] */
    private fun blend(color: Int, other: Int, ratio: Float): Int = Color.argb(
        255,
        lerp(Color.red(color), Color.red(other), ratio),
        lerp(Color.green(color), Color.green(other), ratio),
        lerp(Color.blue(color), Color.blue(other), ratio)
    )

    private fun lerp(a: Int, b: Int, ratio: Float): Int =
        (a * (1f - ratio) + b * ratio).toInt().coerceIn(0, 255)

    /**
     * 解析主题属性颜色（属性值可能是字面量或资源引用），
     * 解析失败返回 [fallback]。避免直接引用 material R（IDE 索引问题）。
     */
    fun themeColor(context: Context, attr: Int, fallback: Int): Int {
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

    private fun View.dp(v: Float): Float = v * resources.displayMetrics.density
}
