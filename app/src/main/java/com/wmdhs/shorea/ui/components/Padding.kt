package com.wmdhs.shorea.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 在 Scaffold 的 innerPadding 上再加固定边距。
 *
 * innerPadding 的数值由状态驱动，顶栏折叠时每帧都在变。若在组合阶段调用
 * `calculateTopPadding()` 拼出新的 [PaddingValues]，整个列表会随滚动每帧重组；
 * 这里把读取推迟到列表测量时，只触发重新布局。
 */
internal fun PaddingValues.plus(
    horizontal: Dp = 0.dp,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
): PaddingValues = ExtraPaddingValues(this, horizontal, top, bottom)

@Immutable
private data class ExtraPaddingValues(
    val base: PaddingValues,
    val horizontal: Dp,
    val top: Dp,
    val bottom: Dp,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        base.calculateLeftPadding(layoutDirection) + horizontal

    override fun calculateTopPadding(): Dp = base.calculateTopPadding() + top

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        base.calculateRightPadding(layoutDirection) + horizontal

    override fun calculateBottomPadding(): Dp = base.calculateBottomPadding() + bottom
}
