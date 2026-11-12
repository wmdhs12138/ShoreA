package com.wmdhs.shorea.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.wmdhs.shorea.R
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val ActionWidth = 92.dp
private val ActionGap = 6.dp
private val ConnectedCorner = 8.dp

/**
 * 左滑露出删除按钮。露出时卡片右侧圆角收窄、与删除按钮形成"连接式"按钮组外观。
 *
 * 同一时间只允许一个条目处于展开状态，由调用方通过 [revealed] 控制。
 *
 * @param content 接收卡片应使用的形状，以及一个包装点击事件的函数：
 *   展开状态下点击卡片只会收起删除按钮，不会打开条目。
 */
@Composable
internal fun SwipeToRevealDelete(
    revealed: Boolean,
    deleteDescription: String,
    onReveal: () -> Unit,
    onConceal: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    highlighted: Boolean = false,
    content: @Composable (shape: Shape, guardClick: (() -> Unit) -> Unit) -> Unit,
) {
    val actionWidthPx = with(LocalDensity.current) { ActionWidth.toPx() }
    var offsetPx by remember { mutableFloatStateOf(0f) }
    val revealFraction = (-offsetPx / actionWidthPx).coerceIn(0f, 1f)
    val restingShape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val cardShape = if (offsetPx == 0f) {
        restingShape
    } else {
        val endCorner = lerp(cornerRadius, ConnectedCorner, revealFraction)
        RoundedCornerShape(
            topStart = cornerRadius,
            topEnd = endCorner,
            bottomEnd = endCorner,
            bottomStart = cornerRadius,
        )
    }
    val draggableState = rememberDraggableState { delta ->
        offsetPx = (offsetPx + delta).coerceIn(-actionWidthPx, 0f)
    }

    suspend fun animateOffsetTo(target: Float) {
        animate(initialValue = offsetPx, targetValue = target) { value, _ -> offsetPx = value }
    }

    LaunchedEffect(revealed) {
        val target = if (revealed) -actionWidthPx else 0f
        if (offsetPx != target) animateOffsetTo(target)
    }

    // 裁剪到自身格子内，左滑的卡片不会盖住相邻列。
    Box(modifier = modifier.fillMaxWidth().clip(restingShape)) {
        // 关闭状态不组合被卡片完全遮住的删除层，降低 Lazy 列表新条目进入时的成本。
        if (offsetPx < 0f || revealed) {
            val coroutineScope = rememberCoroutineScope()
            var deleteRequested by remember { mutableStateOf(false) }
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(ActionWidth - ActionGap)
                        .semantics {
                            if (!revealed) hideFromAccessibility()
                            role = Role.Button
                            contentDescription = deleteDescription
                        }
                        .clickable(enabled = revealed && !deleteRequested) {
                            deleteRequested = true
                            coroutineScope.launch {
                                // 删除前先收起，撤回时条目以初始位置重新进入。
                                animateOffsetTo(0f)
                                onDelete()
                                deleteRequested = false
                            }
                        },
                    shape = RoundedCornerShape(
                        topStart = ConnectedCorner,
                        bottomStart = ConnectedCorner,
                        topEnd = cornerRadius,
                        bottomEnd = cornerRadius,
                    ),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Text("删除", style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                }
            }
        }

        val highlightModifier = if (highlighted) {
            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, restingShape)
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(deleteDescription) {
                            onDelete()
                            true
                        },
                    )
                }
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { onConceal() },
                    onDragStopped = { velocity ->
                        val shouldReveal = velocity < -700f ||
                            (velocity <= 700f && offsetPx <= -actionWidthPx / 2f)
                        animateOffsetTo(if (shouldReveal) -actionWidthPx else 0f)
                        if (shouldReveal) onReveal() else onConceal()
                    },
                )
                .then(highlightModifier),
        ) {
            content(cardShape) { action ->
                val wasOpen = offsetPx < -1f
                onConceal()
                if (!wasOpen) action()
            }
        }
    }
}
