package com.dreamct.tingfeng.ui.navigation

import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.viewpager2.widget.ViewPager2
import com.dreamct.tingfeng.R
import com.google.android.material.R as MaterialR

/** Installs the Compose dock in the existing Activity pager shell. */
object FloatingDockInstaller {
    @JvmStatic
    fun install(composeView: ComposeView, pager: ViewPager2) {
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            FloatingDockContent(pager)
        }
    }
}

private data class DockDestination(
    /** 对应 [MainPagerAdapter] 的页位置 */
    val position: Int,
    val labelRes: Int,
    val iconRes: Int,
    val selectedIconRes: Int
)

private val dockDestinations = listOf(
    DockDestination(
        MainPagerAdapter.POSITION_UTILS,
        R.string.title_statistics,
        R.drawable.time0,
        R.drawable.time1),
    DockDestination(MainPagerAdapter.POSITION_HOME, R.string.title_home, R.drawable.list0, R.drawable.list1),
    DockDestination(
        MainPagerAdapter.POSITION_SETTINGS,
        R.string.settings,
        R.drawable.settings0,
        R.drawable.settings
    )
)

@Composable
private fun FloatingDockContent(pager: ViewPager2) {
    // 选中态跟随 pager 当前页（含 setCurrentItem 程序切换）
    var selectedDestination by remember(pager) {
        mutableIntStateOf(pager.currentItem)
    }

    // 主题化配色：从 Activity 主题 attr 解析三容器色（三段色相渐变）与
    // onPrimaryContainer（图标/文字）。主题切换 = recreate Activity，
    // ComposeView 重建时自然拿到新主题，无需额外监听。
    val context = LocalContext.current
    val gradStart = remember { dockColor(context, MaterialR.attr.colorPrimaryContainer, 0xFFD4E3FF) }
    val gradMid = remember { dockColor(context, MaterialR.attr.colorSecondaryContainer, 0xFFB8EAFF) }
    val gradEnd = remember { dockColor(context, MaterialR.attr.colorTertiaryContainer, 0xFF9EEFFD) }
    val onContainer = remember { dockColor(context, MaterialR.attr.colorOnPrimaryContainer, 0xFF001C39) }

    DisposableEffect(pager) {
        val callback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectedDestination = position
            }
        }
        pager.registerOnPageChangeCallback(callback)
        // 主动对齐一次：recreate（切主题）/旋转时起点页由 MainActivity.onCreate
        // 按保存状态 setCurrentItem（发生在 Dock 组合之前，回调收不到），
        // 组合后读 currentItem 对齐选中态，保证 Dock 与页面一致
        selectedDestination = pager.currentItem
        onDispose {
            pager.unregisterOnPageChangeCallback(callback)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val dockShape = RoundedCornerShape(34.dp)
        Row(
            modifier = Modifier
                .shadow(elevation = 18.dp, shape = dockShape, clip = false)
                .clip(dockShape)
                // 三段色相渐变（primaryContainer→secondaryContainer→tertiaryContainer，
                // 横向流动），与三页顶栏同源（见 TingTheme.hueGradient），随主题亮暗自动切换
                .background(
                    Brush.horizontalGradient(listOf(gradStart, gradMid, gradEnd))
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            onContainer.copy(alpha = 0.35f),
                            onContainer.copy(alpha = 0.10f)
                        )
                    ),
                    shape = dockShape
                )
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockDestinations.forEach { destination ->
                DockItem(
                    destination = destination,
                    selected = selectedDestination == destination.position,
                    onColor = onContainer,
                    onClick = {
                        // 瞬切（无平滑滑动）：页面切换保持即时响应
                        pager.setCurrentItem(destination.position, false)
                    }
                )
            }
        }
    }
}

/** 从主题 attr 解析颜色（字面量或资源引用均支持），失败返回 [fallback] */
private fun dockColor(context: android.content.Context, @AttrRes attr: Int, fallback: Long): Color {
    val tv = TypedValue()
    if (context.theme.resolveAttribute(attr, tv, true)) {
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return Color(tv.data)
        }
        try {
            return Color(androidx.core.content.ContextCompat.getColor(context, tv.resourceId))
        } catch (e: Exception) {
            return Color(fallback)
        }
    }
    return Color(fallback)
}

@Composable
private fun DockItem(
    destination: DockDestination,
    selected: Boolean,
    onColor: Color,
    onClick: () -> Unit
) {
    val label = stringResource(destination.labelRes)
    val itemShape = RoundedCornerShape(26.dp)
    val itemBackground by animateColorAsState(
        targetValue = if (selected) onColor.copy(alpha = 0.14f) else Color.Transparent,
        label = "dockItemBackground"
    )

    Column(
        modifier = Modifier
            .width(68.dp)
            .height(58.dp)
            .clip(itemShape)
            .background(itemBackground)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(
                if (selected) destination.selectedIconRes else destination.iconRes
            ),
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                if (selected) onColor else onColor.copy(alpha = 0.66f)
            ),
            modifier = Modifier.size(23.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = onColor.copy(alpha = if (selected) 1f else 0.72f),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
