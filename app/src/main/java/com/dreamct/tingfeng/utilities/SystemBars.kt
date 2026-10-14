package com.dreamct.tingfeng.utilities

import android.graphics.Color
import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors

/**
 * 状态栏图标外观按指定视图当前实际背景色自适应（主页/搜索页顶栏覆盖状态栏区域）：
 *  - 背景亮度 > 0.5（浅色，如亮色主题的 colorSurface）→ 深色图标；
 *  - 背景亮度 <= 0.5（深色，如暗色主题的 colorSurface）→ 浅色图标。
 *
 * 顶栏颜色来自主题 colorSurface（随亮/暗主题自动切换），因此无需感知当前
 * 主题模式，读一次主题解析值即可在两种模式下都得到可读的状态栏图标。
 * 读取基于 context 主题解析、不依赖布局完成，onViewCreated 即可调用。
 */
fun applyStatusBarIconAppearance(window: Window, decorView: View, backgroundView: View) {
    val background = MaterialColors.getColor(backgroundView, MaterialR.attr.colorSurface)
    WindowCompat.getInsetsController(window, decorView).isAppearanceLightStatusBars =
        Color.luminance(background) > 0.5f
}
