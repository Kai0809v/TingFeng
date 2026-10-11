package com.dreamct.tingfeng.service

import android.content.Context
import androidx.core.content.edit

object TingConfig {

    private const val PREF_NAME = "ting_config"
    private const val KEY_SHOULD_RESTART = "should_restart"
    private const val KEY_SERVICE_PAUSED = "service_paused"
    private const val KEY_WALLPAPER_ENABLED = "wallpaper_enabled"
    private const val KEY_WALLPAPER_ALPHA = "wallpaper_alpha"
    private const val KEY_CARD_STYLE_TYPE = "card_style_type"
    private const val KEY_CARD_GRADIENT_ID = "card_gradient_id"

    /** 壁纸可见度范围（0~1）：0 = 壁纸完全隐藏，1 = 壁纸完全显示，无额外阈值 */
    const val MIN_WALLPAPER_ALPHA = 0f

    /** 壁纸可见度上限：完全不遮挡 */
    const val MAX_WALLPAPER_ALPHA = 1.0f

    @JvmStatic
    fun setShouldRestart(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_SHOULD_RESTART, value)
            }
    }

    @JvmStatic
    fun shouldRestart(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOULD_RESTART, false)
    }

    /**
     * 服务监控是否被用户手动暂停（持久化：暂停时服务不记录新通知、不自动重连，
     * 重启应用后仍生效；重新打开开关时清除）。
     */
    @JvmStatic
    fun setServicePaused(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_SERVICE_PAUSED, value)
            }
    }

    @JvmStatic
    fun servicePaused(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_PAUSED, false)
    }

    /** 是否启用"背景使用系统壁纸" */
    @JvmStatic
    fun setWallpaperEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_WALLPAPER_ENABLED, value)
            }
    }

    @JvmStatic
    fun wallpaperEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WALLPAPER_ENABLED, false)
    }

    /** 壁纸可见度（0~1，1 = 完全显示壁纸），已钳制到 [MIN, MAX] */
    @JvmStatic
    fun setWallpaperAlpha(context: Context, value: Float) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                putFloat(KEY_WALLPAPER_ALPHA, value.coerceIn(MIN_WALLPAPER_ALPHA, MAX_WALLPAPER_ALPHA))
            }
    }

    @JvmStatic
    fun wallpaperAlpha(context: Context): Float {
        val stored = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_WALLPAPER_ALPHA, MAX_WALLPAPER_ALPHA)
        // 读取时同样钳制，防止历史数据越界导致界面不可见
        return stored.coerceIn(MIN_WALLPAPER_ALPHA, MAX_WALLPAPER_ALPHA)
    }

    /**
     * 壁纸背景的最终可见度（0~1）：功能关闭时视为 0，启用时为滑杆值。
     * 直接对接 [com.dreamct.tingfeng.utilities.WallpaperApplier].apply(activity, visibility)。
     */
    @JvmStatic
    fun wallpaperVisibility(context: Context): Float =
        if (wallpaperEnabled(context)) wallpaperAlpha(context) else 0f

    // ------------------------- 卡片样式（通知卡片个性化） -------------------------

    /** 卡片样式类型：dynamic / gradient / blur / liquid / custom */
    @JvmStatic
    fun setCardStyleType(context: Context, value: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_CARD_STYLE_TYPE, value)
            }
    }

    @JvmStatic
    fun cardStyleType(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CARD_STYLE_TYPE, "dynamic") ?: "dynamic"
    }

    /** 渐变配色预设 id（cardStyleType == "gradient" 时生效） */
    @JvmStatic
    fun setCardGradientId(context: Context, value: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_CARD_GRADIENT_ID, value)
            }
    }

    @JvmStatic
    fun cardGradientId(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CARD_GRADIENT_ID, "dawn") ?: "dawn"
    }
}
