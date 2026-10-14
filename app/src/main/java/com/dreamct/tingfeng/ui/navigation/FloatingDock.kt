package com.dreamct.tingfeng.ui.navigation

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

    DisposableEffect(pager) {
        val callback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectedDestination = position
            }
        }
        pager.registerOnPageChangeCallback(callback)
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
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xDDF3F6FF),
                            Color(0xD92A3D61),
                            Color(0xE51A263B)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.12f))
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
                    onClick = {
                        // 瞬切（无平滑滑动）：页面切换保持即时响应
                        pager.setCurrentItem(destination.position, false)
                    }
                )
            }
        }
    }
}

@Composable
private fun DockItem(
    destination: DockDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    val label = stringResource(destination.labelRes)
    val itemShape = RoundedCornerShape(26.dp)
    val itemBackground by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.19f) else Color.Transparent,
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
                if (selected) Color.White else Color.White.copy(alpha = 0.72f)
            ),
            modifier = Modifier.size(23.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = if (selected) 1f else 0.76f),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
