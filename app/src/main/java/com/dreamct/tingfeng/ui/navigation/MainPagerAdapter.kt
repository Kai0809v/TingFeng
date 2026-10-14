package com.dreamct.tingfeng.ui.navigation

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.dreamct.tingfeng.ui.home.HomeFragment
import com.dreamct.tingfeng.ui.settings.SettingsFragment
import com.dreamct.tingfeng.ui.stats.StatsFragment

/**
 * 主界面三个 tab 页的常驻适配器（统计 / 主页 / 设置，位置与底部 Dock 一一对应）。
 *
 * 懒加载：FragmentStateAdapter 默认只创建当前页（启动仅主页），其余页在
 * 首次切换到时才实例化视图，创建后随 RecyclerView 常驻复用——之后切换
 * 不再销毁/重建视图树（此前 Navigation replace 每次切换都要重建，实测
 * inflate 50~90ms + setup），切换开销接近零。
 *
 * BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT：仅当前页 RESUMED，切页时旧页
 * onPause、新页 onResume，各页现有 onResume 刷新逻辑（状态徽标、状态栏
 * 图标、服务开关同步）无需改动即继续生效。
 *
 * 注：原工具页（ui/utils/UtilsFragment）已弃用，相关文件保留在仓库中
 * 但不再被本适配器引用。
 */
class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    companion object {
        /** 页位置常量（顺序与 Dock 图标排列一致，主页居中） */
        const val POSITION_UTILS = 0
        const val POSITION_HOME = 1
        const val POSITION_SETTINGS = 2
    }

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment = when (position) {
        POSITION_UTILS -> StatsFragment()
        POSITION_HOME -> HomeFragment()
        POSITION_SETTINGS -> SettingsFragment()
        else -> throw IllegalArgumentException("未知页位置: $position")
    }
}

