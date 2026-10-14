package com.dreamct.tingfeng.ui.navigation

/**
 * 主界面三页（统计/主页/设置）顶栏入场动画的统一契约：
 * Dock 切页时由 MainActivity 在 onPageSelected 里触发；页面视图创建晚于
 * 切页回调时，Fragment 在视图创建完成后通过
 * MainActivity.consumePendingTopBarEntrance() 领取待播动画。
 *
 * 实现要求：顶栏卡片背景不透明，动画只允许 translationY 位移
 * （整卡 alpha<1 会离屏合成透出底层壁纸，禁止）。
 */
interface TopBarAnimatable {
    fun playTopBarEntrance()
}
