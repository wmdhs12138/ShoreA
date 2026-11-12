package com.wmdhs.shorea.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.wmdhs.shorea.ui.detail.CompoundDetailScreen
import com.wmdhs.shorea.ui.editor.CompoundEditorScreen
import com.wmdhs.shorea.ui.editor.InspectionEditorScreen
import com.wmdhs.shorea.ui.home.HomeScreen
import com.wmdhs.shorea.ui.home.ManualHomeItem
import com.wmdhs.shorea.ui.navigation.CompoundDetailRoute
import com.wmdhs.shorea.ui.navigation.CompoundEditorRoute
import com.wmdhs.shorea.ui.navigation.HomeRoute
import com.wmdhs.shorea.ui.navigation.InspectionEditorRoute
import com.wmdhs.shorea.ui.theme.ShoreATheme
import com.wmdhs.shorea.ui.transfer.TransferHost
import kotlinx.coroutines.launch

/** 各页面 Scaffold 共用的 Snackbar 宿主，页面切换时提示（含"撤回"）不会丢失。 */
internal val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("SnackbarHostState not provided")
}

@Composable
internal fun ShoreAApp(viewModel: ManualViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShoreATheme(dynamicColor = state.preferences.dynamicColor) {
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(viewModel, snackbarHostState) {
            viewModel.messages.collect { message ->
                launch {
                    // 新提示直接替换旧提示，与 1.x 行为一致。
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = message.text,
                        actionLabel = message.actionLabel,
                        duration = if (message.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
                }
            }
        }
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            val backStack = rememberNavBackStack(HomeRoute)
            Box(Modifier.fillMaxSize()) {
                // 首页常驻在导航层下方，导航层里的 HomeRoute 只是透明占位。
                // 从详情返回时首页无需重新组合整页卡片，切换首帧不再卡一下。
                HomeScreen(
                    viewModel = viewModel,
                    covered = backStack.size > 1,
                    onOpenItem = { item ->
                        backStack.add(
                            CompoundDetailRoute(
                                compoundId = item.compound.id,
                                highlightEntryId = (item as? ManualHomeItem.Inspection)?.entry?.id,
                            ),
                        )
                    },
                    onAddCompound = { backStack.add(CompoundEditorRoute()) },
                )
                ShoreANavDisplay(backStack, viewModel)
            }
            TransferHost(viewModel)
        }
    }
}

@Composable
private fun ShoreANavDisplay(backStack: NavBackStack<NavKey>, viewModel: ManualViewModel) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = Modifier.fillMaxSize(),
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        transitionSpec = { pushTransform() },
        popTransitionSpec = { popTransform() },
        predictivePopTransitionSpec = { _ -> predictivePopTransform() },
        entryProvider = entryProvider {
            // 透明占位，触摸会穿透到下方常驻的首页。
            entry<HomeRoute> {
                Spacer(Modifier.fillMaxSize())
            }
            entry<CompoundDetailRoute> { key ->
                CompoundDetailScreen(
                    viewModel = viewModel,
                    compoundId = key.compoundId,
                    initialHighlightEntryId = key.highlightEntryId,
                    onBack = { backStack.remove(key) },
                    onEditCompound = { backStack.add(CompoundEditorRoute(key.compoundId)) },
                    onAddInspection = { backStack.add(InspectionEditorRoute(key.compoundId)) },
                    onEditInspection = { entry -> backStack.add(InspectionEditorRoute(key.compoundId, entry.id)) },
                )
            }
            entry<CompoundEditorRoute> { key ->
                CompoundEditorScreen(
                    viewModel = viewModel,
                    compoundId = key.compoundId,
                    onDismiss = { backStack.remove(key) },
                    onSaved = { savedId ->
                        val index = backStack.indexOf(key)
                        when {
                            index < 0 -> Unit
                            // 新建胶料后直接进入它的详情页，便于继续添加检测标准。
                            key.compoundId == null -> backStack[index] = CompoundDetailRoute(savedId)
                            else -> backStack.removeAt(index)
                        }
                    },
                )
            }
            entry<InspectionEditorRoute> { key ->
                InspectionEditorScreen(
                    viewModel = viewModel,
                    compoundId = key.compoundId,
                    entryId = key.entryId,
                    onDismiss = { backStack.remove(key) },
                    onSaved = { backStack.remove(key) },
                )
            }
        },
    )
}

private val SlideSpec = spring<IntOffset>(stiffness = Spring.StiffnessMediumLow)

/** 上面这条弹簧把 20% 屏宽的位移收敛到 1px 内约需 410ms，下层页面原样保留到此之后。 */
private const val UnderlayHoldMillis = 450

/*
 * 页面过渡：只有叠在上面的页面滑动并淡入/淡出，下面的页面保持不动、不透明。
 *
 * 首页常驻在导航层下方，如果下层页面也跟着平移或变透明，首页会从空出来的边缘透出来。
 * 前进时新页面在上，返回时离开的页面在上（NavDisplay 按此安排 z 轴顺序）。
 */
private fun pushTransform(): ContentTransform =
    (slideInHorizontally(SlideSpec) { it / 5 } + fadeIn(tween(200)))
        .togetherWith(fadeOut(snap(delayMillis = UnderlayHoldMillis)))

private fun popTransform(): ContentTransform =
    EnterTransition.None togetherWith (slideOutHorizontally(SlideSpec) { it / 5 } + fadeOut(tween(150)))

/** 预测性返回：离开的页面随手势缩小，下方页面保持不动。 */
private fun predictivePopTransform(): ContentTransform =
    EnterTransition.None togetherWith scaleOut(targetScale = 0.7f)
