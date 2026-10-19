package com.dreamct.tingfeng.service

import android.content.Context
import androidx.core.content.edit

object TingConfig {

    private const val PREF_NAME = "ting_config"
    private const val KEY_SHOULD_RESTART = "should_restart"
    private const val KEY_SERVICE_PAUSED = "service_paused"
    private const val KEY_WALLPAPER_ALPHA = "wallpaper_alpha"
    private const val KEY_APP_WALLPAPER_ENABLED = "app_wallpaper_enabled"
    private const val KEY_APP_WALLPAPER_URI = "app_wallpaper_uri"
    private const val KEY_CARD_STYLE_TYPE = "card_style_type"
    private const val KEY_CARD_GRADIENT_ID = "card_gradient_id"
    private const val KEY_CARD_BLUR_FROST = "card_blur_frost"
    private const val KEY_CARD_LIQUID_GLOSS = "card_liquid_gloss"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
    private const val KEY_BLOCKED_KEYWORDS = "blocked_keywords"
    private const val KEY_APP_THEME = "app_theme"
    private const val KEY_PENDING_THEME_REVEAL = "pending_theme_reveal"
    private const val KEY_PENDING_THEME_REVEAL_X = "pending_theme_reveal_x"
    private const val KEY_PENDING_THEME_REVEAL_Y = "pending_theme_reveal_y"

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

    /** 应用内壁纸可见度（0~1，1 = 完全显示壁纸），已钳制到 [MIN, MAX] */
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

    /** 应用主题（全局配色）：值为 TingTheme.id，未选择时为默认深海蓝调 */
    @JvmStatic
    fun setAppTheme(context: Context, value: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_APP_THEME, value) }
    }

    @JvmStatic
    fun appTheme(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_THEME, "plain") ?: "plain"
    }

    @JvmStatic
    fun setPendingThemeReveal(context: Context, cx: Int, cy: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_PENDING_THEME_REVEAL, true)
            putInt(KEY_PENDING_THEME_REVEAL_X, cx)
            putInt(KEY_PENDING_THEME_REVEAL_Y, cy)
        }
    }

    /** 待播放的主题揭示动画触发点（窗口坐标），null = 无待播动画 */
    @JvmStatic
    fun pendingThemeReveal(context: Context): Pair<Int, Int>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING_THEME_REVEAL, false)) return null
        return prefs.getInt(KEY_PENDING_THEME_REVEAL_X, 0) to
            prefs.getInt(KEY_PENDING_THEME_REVEAL_Y, 0)
    }

    @JvmStatic
    fun clearPendingThemeReveal(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_PENDING_THEME_REVEAL, false) }
    }
}
