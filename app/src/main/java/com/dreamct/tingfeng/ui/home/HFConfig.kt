package com.dreamct.tingfeng.ui.home

import android.view.View
import com.dreamct.tingfeng.data.NotificationLog

/**
 * HomeFragment 界面配置中心。
 *
 * 集中管理"通知面板式"堆叠卡片栈的参数，并预留样式定制接口，
 * 后续接入"设置"页面时，只需修改这里的值或替换 [cardStyle] 实现即可，
 * 不需要改动布局管理器和适配器代码。
 */
object HFConfig {

    // ********************** 堆叠卡片栈参数 **********************

    /** Card-to-card reveal strip height in the folded stack, in dp. */
    var stackPeekDp: Float = 28f

    /** Folded stack viewport height as a fraction of one card's height. */
    var stackFoldHeightRatio: Float = 1.1f

    /** Number of staggered layers shown before deeper cards are fully covered. */
    var stackVisibleLayerCount: Int = 2

    /** Fixed gap between fully expanded cards. */
    var expandedCardSpacingDp: Float = 6f

    /** 每深入一层的宽度缩放量（0.04 = 每层窄 4%）。 */
    var stackScaleStep: Float = 0.04f

    /** 后层卡片的最小缩放比例。 */
    var stackMinScale: Float = 0.72f

    /**
     * 顶部裁剪线（相对 RecyclerView 顶边的距离，单位 dp）：
     * 卡片滑出时超过该线的部分不再绘制（视觉上被悬浮顶栏遮住），
     * 整体越过该线后才被回收。默认 8dp = 悬浮顶栏的上边缘。
     */
    var topClipDp: Float = 8f

    /** 卡片圆角半径，单位 dp。 */
    var cardCornerRadiusDp: Float = 16f

    /** 卡片水平外边距，单位 dp。 */
    var cardMarginHorizontalDp: Int = 14

    /** 卡片垂直外边距（堆叠间距由 peek 控制，保持 0）。 */
    var cardMarginVerticalDp: Int = 0

    /** 滑动消除时透明度衰减系数（1.0 = 滑出一半时完全透明）。 */
    var swipeAlphaFactor: Float = 1.2f

    // ********************** 样式预留接口 **********************

    /**
     * 卡片样式钩子。所有方法默认空实现，用于后续在设置中自定义格式。
     *
     * 使用方式（示例，后续在设置页面接入）：
     * ```
     * HFConfig.cardStyle = object : NotificationCardStyle {
     *     override fun onCardCreated(card: NotificationCardView) {
     *         // 例如：换背景、换圆角、换字体
     *     }
     * }
     * ```
     */
    interface NotificationCardStyle {
        /** 卡片刚创建时调用（每个 ViewHolder 只调用一次）。 */
        fun onCardCreated(card: NotificationCardView) {}

        /** 卡片绑定数据时调用（每次刷新数据都会调用）。 */
        fun onCardBound(card: NotificationCardView, log: NotificationLog) {}

        /**
         * 堆叠层级效果应用后调用。
         * @param card      卡片视图
         * @param stackIndex 堆叠层级，0 为最顶层（完全展示的卡片）
         */
        fun onApplyStackEffect(card: View, stackIndex: Int) {}
    }

    /** 当前生效的卡片样式，默认为空实现。后续由设置页面替换。 */
    var cardStyle: NotificationCardStyle = object : NotificationCardStyle {}
}
