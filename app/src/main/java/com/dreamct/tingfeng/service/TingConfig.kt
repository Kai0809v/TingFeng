package com.dreamct.tingfeng.service

import android.content.Context
import androidx.core.content.edit

object TingConfig {

    private const val PREF_NAME = "ting_config"
    private const val KEY_SHOULD_RESTART = "should_restart"
    private const val KEY_SERVICE_PAUSED = "service_paused"
    private const val KEY_WALLPAPER_ENABLED = "wallpaper_enabled"
    private const val KEY_WALLPAPER_ALPHA = "wallpaper_alpha"
    private const val KEY_APP_WALLPAPER_ENABLED = "app_wallpaper_enabled"
    private const val KEY_APP_WALLPAPER_URI = "app_wallpaper_uri"
    private const val KEY_APP_WALLPAPER_ALPHA = "app_wallpaper_alpha"
    private const val KEY_CARD_STYLE_TYPE = "card_style_type"
    private const val KEY_CARD_GRADIENT_ID = "card_gradient_id"
    private const val KEY_CARD_BLUR_FROST = "card_blur_frost"
    private const val KEY_CARD_LIQUID_GLOSS = "card_liquid_gloss"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
    private const val KEY_BLOCKED_KEYWORDS = "blocked_keywords"

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

    /** 是否用用户选择的图片替代系统壁纸作为应用背景。 */
    @JvmStatic
    fun setAppWallpaperEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_APP_WALLPAPER_ENABLED, value) }
    }

    @JvmStatic
    fun appWallpaperEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_WALLPAPER_ENABLED, false)

    /** 由系统文档选择器返回并持久授权的图片 URI。 */
    @JvmStatic
    fun setAppWallpaperUri(context: Context, value: String?) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                if (value == null) remove(KEY_APP_WALLPAPER_URI)
                else putString(KEY_APP_WALLPAPER_URI, value)
            }
    }

    @JvmStatic
    fun appWallpaperUri(context: Context): String? =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_WALLPAPER_URI, null)

    /** 应用内壁纸的不透明度（0~1），独立于系统壁纸透明度。 */
    @JvmStatic
    fun setAppWallpaperAlpha(context: Context, value: Float) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(KEY_APP_WALLPAPER_ALPHA, value.coerceIn(0f, 1f)) }
    }

    @JvmStatic
    fun appWallpaperAlpha(context: Context): Float =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_APP_WALLPAPER_ALPHA, 1f).coerceIn(0f, 1f)

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

    /** 纯色半透明雾度（0~1）：0 = 清透，1 = 重雾度（cardStyleType == "blur" 时生效） */
    @JvmStatic
    fun setCardBlurFrost(context: Context, value: Float) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit {
                putFloat(KEY_CARD_BLUR_FROST, value.coerceIn(0f, 1f))
            }
    }

    @JvmStatic
    fun cardBlurFrost(context: Context): Float {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_CARD_BLUR_FROST, 0.65f).coerceIn(0f, 1f)
    }

    // ------------------------- 通知过滤 -------------------------

    /** 屏蔽名单中的应用包名：服务记录通知时直接忽略 */
    @JvmStatic
    fun setBlockedPackages(context: Context, value: Set<String>) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putStringSet(KEY_BLOCKED_PACKAGES, value) }
    }

    @JvmStatic
    fun blockedPackages(context: Context): Set<String> {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
    }

    /** 屏蔽的关键词：标题或内容命中即不记录 */
    @JvmStatic
    fun setBlockedKeywords(context: Context, value: Set<String>) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putStringSet(KEY_BLOCKED_KEYWORDS, value) }
    }

    @JvmStatic
    fun blockedKeywords(context: Context): Set<String> {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_BLOCKED_KEYWORDS, emptySet()) ?: emptySet()
    }
}
