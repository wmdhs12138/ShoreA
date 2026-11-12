package com.wmdhs.shorea.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmdhs.shorea.R
import com.wmdhs.shorea.data.model.ManualSortOrder
import com.wmdhs.shorea.data.model.ManualViewMode
import com.wmdhs.shorea.ui.LocalSnackbarHostState
import com.wmdhs.shorea.ui.ManualUiState
import com.wmdhs.shorea.ui.ManualViewModel
import com.wmdhs.shorea.ui.components.DeleteCompoundDialog
import com.wmdhs.shorea.ui.components.DeleteInspectionDialog
import com.wmdhs.shorea.ui.components.LoadingState
import com.wmdhs.shorea.ui.components.MessageState
import com.wmdhs.shorea.ui.components.SwipeToRevealDelete
import com.wmdhs.shorea.ui.components.plus
import com.wmdhs.shorea.ui.theme.supportsDynamicColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

@Composable
internal fun HomeScreen(
    viewModel: ManualViewModel,
    covered: Boolean,
    onOpenItem: (ManualHomeItem) -> Unit,
    onAddCompound: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items by viewModel.homeItems.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val focusKey by viewModel.homeFocusKey.collectAsStateWithLifecycle()

    var searchActive by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    var toolbarExpanded by rememberSaveable { mutableStateOf(true) }
    var pendingDeleteEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingDeleteCompoundId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun closeSearch() {
        searchActive = false
        viewModel.setSearchQuery("")
    }
    BackHandler(enabled = searchActive && !covered) { closeSearch() }

    // 首页常驻在其他页面下方：被盖住时收起键盘，并对无障碍服务隐藏。
    val focusManager = LocalFocusManager.current
    LaunchedEffect(covered) {
        if (covered) focusManager.clearFocus(force = true)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val manual = state.manual
    val showToolbar = state.canEdit && manual.compounds.isNotEmpty()

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .then(if (covered) Modifier.clearAndSetSemantics {} else Modifier),
        topBar = {
            AnimatedContent(
                targetState = searchActive && state.canEdit,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "home-top-bar",
            ) { searching ->
                if (searching) {
                    SearchTopBar(
                        query = query,
                        onQueryChange = viewModel::setSearchQuery,
                        onClose = ::closeSearch,
                    )
                } else {
                    HomeTopBar(
                        state = state,
                        scrollBehavior = scrollBehavior,
                        onSearch = { searchActive = true },
                        onOpenTransfer = viewModel::showTransferSheet,
                        onDynamicColorChange = viewModel::setDynamicColor,
                    )
                }
            }
        },
        snackbarHost = {
            // 被盖住时由上层页面显示提示，避免同一条 Snackbar 叠两份。
            if (!covered) SnackbarHost(LocalSnackbarHostState.current)
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (showToolbar) {
                HomeToolbar(
                    expanded = toolbarExpanded,
                    viewMode = state.preferences.viewMode,
                    sortOrder = state.preferences.sortOrder,
                    onViewModeChange = viewModel::setViewMode,
                    onSortOrderChange = viewModel::setSortOrder,
                    onAddCompound = onAddCompound,
                )
            }
        },
    ) { innerPadding ->
        val visibleItems = items
        when {
            state.isLoading || visibleItems == null -> LoadingState("正在读取本地手册", innerPadding)

            state.loadError != null -> MessageState(
                icon = R.drawable.ic_error,
                title = "本地资料无法读取",
                body = "为避免覆盖原数据，当前已停止编辑。可以从之前导出的备份恢复。",
                detail = state.loadError,
                contentPadding = innerPadding,
                polygon = MaterialShapes.SoftBurst,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                action = {
                    Button(onClick = viewModel::showTransferSheet, shapes = ButtonDefaults.shapes()) {
                        Text("从备份恢复")
                    }
                },
            )

            manual.compounds.isEmpty() -> MessageState(
                icon = R.drawable.ic_library_add,
                title = "还没有胶料资料",
                body = "建立第一种胶料，开始整理检测标准与部品硬度。也可以从 Excel 或备份导入。",
                contentPadding = innerPadding,
                polygon = MaterialShapes.Cookie9Sided,
                action = {
                    Button(onClick = onAddCompound, shapes = ButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                        Text("添加第一种胶料", modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                },
            )

            visibleItems.isEmpty() -> MessageState(
                icon = R.drawable.ic_search_off,
                title = "没有匹配的检测标准",
                body = "未找到与“${query.trim()}”相关的基胶、胶料号、标准号、部品号或硬度",
                contentPadding = innerPadding,
                polygon = MaterialShapes.Pentagon,
                action = {
                    Button(
                        onClick = { viewModel.setSearchQuery("") },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text("清除搜索") }
                },
            )

            else -> HomeGrid(
                items = visibleItems,
                viewMode = state.preferences.viewMode,
                query = query.trim(),
                focusKey = focusKey.takeUnless { covered },
                innerPadding = innerPadding,
                toolbarExpanded = toolbarExpanded,
                onToolbarExpandedChange = { toolbarExpanded = it },
                onFocusConsumed = viewModel::consumeHomeFocus,
                onOpenItem = onOpenItem,
                onDeleteItem = { item ->
                    when (item) {
                        is ManualHomeItem.Inspection -> pendingDeleteEntryId = item.entry.id
                        is ManualHomeItem.EmptyCompound -> pendingDeleteCompoundId = item.compound.id
                    }
                },
            )
        }
    }

    pendingDeleteEntryId?.let { entryId ->
        val entry = manual.findEntry(entryId)
        if (entry == null || !state.canEdit) {
            pendingDeleteEntryId = null
        } else {
            DeleteInspectionDialog(
                entry = entry,
                onConfirm = {
                    pendingDeleteEntryId = null
                    viewModel.deleteInspection(entry.id)
                },
                onDismiss = { pendingDeleteEntryId = null },
            )
        }
    }
    pendingDeleteCompoundId?.let { compoundId ->
        val compound = manual.findCompound(compoundId)
        if (compound == null || !state.canEdit) {
            pendingDeleteCompoundId = null
        } else {
            DeleteCompoundDialog(
                compound = compound,
                entryCount = manual.entryCountForCompound(compound.id),
                partCount = manual.partCountForCompound(compound.id),
                onConfirm = {
                    pendingDeleteCompoundId = null
                    viewModel.deleteCompound(compound.id)
                },
                onDismiss = { pendingDeleteCompoundId = null },
            )
        }
    }
}

@Composable
private fun HomeTopBar(
    state: ManualUiState,
    scrollBehavior: TopAppBarScrollBehavior,
    onSearch: () -> Unit,
    onOpenTransfer: () -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val manual = state.manual
    LargeFlexibleTopAppBar(
        title = { Text("硬度块手册", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = {
            Text(
                text = if (state.isLoading) {
                    "正在读取…"
                } else {
                    "${manual.compounds.size} 种胶料 · ${manual.inspectionEntries.size} 项标准 · " +
                        "按${state.preferences.sortOrder.label}排序"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            IconButton(
                onClick = onSearch,
                enabled = state.canEdit,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = "搜索")
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = !state.isLoading,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "更多选项")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = MaterialTheme.shapes.large,
                ) {
                    DropdownMenuItem(
                        text = { Text("导入与导出") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_file_open), contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onOpenTransfer()
                        },
                    )
                    if (supportsDynamicColor) {
                        val dynamicColor = state.preferences.dynamicColor
                        DropdownMenuItem(
                            text = { Text("壁纸取色") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_palette), contentDescription = null) },
                            trailingIcon = {
                                Switch(
                                    checked = dynamicColor,
                                    onCheckedChange = null,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            },
                            onClick = { onDynamicColorChange(!dynamicColor) },
                        )
                    }
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "关闭搜索")
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text("搜索胶料号、部品号、标准号、硬度…", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = "清除搜索内容")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )
        },
        expandedHeight = 72.dp,
    )
}

/** 底部浮动工具栏：视图切换 + 排序，右侧为添加胶料 FAB；向下滚动时收起为仅 FAB。 */
@Composable
private fun HomeToolbar(
    expanded: Boolean,
    viewMode: ManualViewMode,
    sortOrder: ManualSortOrder,
    onViewModeChange: (ManualViewMode) -> Unit,
    onSortOrderChange: (ManualSortOrder) -> Unit,
    onAddCompound: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    HorizontalFloatingToolbar(
        expanded = expanded,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onAddCompound) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "添加胶料")
            }
        },
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        ManualViewMode.entries.forEach { mode ->
            IconToggleButton(
                checked = mode == viewMode,
                onCheckedChange = { onViewModeChange(mode) },
                shapes = IconButtonDefaults.toggleableShapes(),
                colors = IconButtonDefaults.iconToggleButtonVibrantColors(
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.semantics { contentDescription = "${mode.label}视图" },
            ) {
                Icon(painterResource(mode.icon), contentDescription = null)
            }
        }
        Box {
            IconButton(
                onClick = { sortMenuExpanded = true },
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonVibrantColors(),
            ) {
                Icon(painterResource(R.drawable.ic_sort), contentDescription = "排序，当前按${sortOrder.label}")
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
                shape = MaterialTheme.shapes.large,
            ) {
                ManualSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text("按${order.label}") },
                        trailingIcon = if (order == sortOrder) {
                            { Icon(painterResource(R.drawable.ic_check), contentDescription = "当前") }
                        } else {
                            null
                        },
                        onClick = {
                            sortMenuExpanded = false
                            onSortOrderChange(order)
                        },
                    )
                }
            }
        }
    }
}

private val ManualViewMode.icon: Int
    get() = when (this) {
        ManualViewMode.LIST -> R.drawable.ic_view_agenda
        ManualViewMode.COMPACT -> R.drawable.ic_grid_view
        ManualViewMode.DETAILED -> R.drawable.ic_article
    }

private data class HomeGridLayout(
    val columns: Int,
    val horizontalPadding: Dp,
    val spacing: Dp,
    val cornerRadius: Dp,
    /** 卡片头部宽度不足时，部品号与硬度上下排列。 */
    val stackedHeader: Boolean,
)

private fun homeGridLayout(windowWidth: Dp, viewMode: ManualViewMode, fontScale: Float): HomeGridLayout {
    val largeFont = fontScale >= 1.3f
    val compact = viewMode == ManualViewMode.COMPACT
    val horizontalPadding = if (compact) 12.dp else 16.dp
    val spacing = if (compact) 10.dp else 12.dp
    val minColumnWidth = if (compact) 168.dp else 400.dp
    val available = windowWidth - horizontalPadding * 2
    val columns = when {
        compact && largeFont -> 1
        compact -> ((available + spacing) / (minColumnWidth + spacing)).toInt().coerceAtLeast(2)
        else -> ((available + spacing) / (minColumnWidth + spacing)).toInt().coerceAtLeast(1)
    }
    val cardWidth = (available - spacing * (columns - 1)) / columns
    val cardPadding = if (compact) 28.dp else 40.dp
    return HomeGridLayout(
        columns = columns,
        horizontalPadding = horizontalPadding,
        spacing = spacing,
        cornerRadius = if (compact) 20.dp else 28.dp,
        stackedHeader = largeFont || cardWidth - cardPadding < 300.dp,
    )
}

@Composable
private fun HomeGrid(
    items: List<ManualHomeItem>,
    viewMode: ManualViewMode,
    query: String,
    focusKey: String?,
    innerPadding: PaddingValues,
    toolbarExpanded: Boolean,
    onToolbarExpandedChange: (Boolean) -> Unit,
    onFocusConsumed: (String) -> Unit,
    onOpenItem: (ManualHomeItem) -> Unit,
    onDeleteItem: (ManualHomeItem) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val layout = remember(windowWidth, viewMode, density.fontScale) {
        homeGridLayout(windowWidth, viewMode, density.fontScale)
    }
    var revealedKey by remember { mutableStateOf<String?>(null) }
    var highlightedKey by remember { mutableStateOf<String?>(null) }

    // 搜索词变化后回到顶部；从详情页返回时（词未变）保留原滚动位置。
    var lastQuery by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query != lastQuery) {
            lastQuery = query
            gridState.scrollToItem(0)
        }
    }

    // 撤回删除后滚动到恢复的条目并短暂高亮。
    LaunchedEffect(focusKey, items) {
        val key = focusKey ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.stableKey == key }
        if (index < 0) return@LaunchedEffect
        highlightedKey = key
        gridState.animateScrollToItem(index)
        onFocusConsumed(key)
    }
    // 单独计时清除高亮：消费焦点会重启上面的 effect，不能把延时放在那里。
    LaunchedEffect(highlightedKey) {
        if (highlightedKey == null) return@LaunchedEffect
        delay(1_500L)
        highlightedKey = null
    }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .filter { it }
            .collect { revealedKey = null }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(layout.columns),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .floatingToolbarVerticalNestedScroll(
                expanded = toolbarExpanded,
                onExpand = { onToolbarExpandedChange(true) },
                onCollapse = { onToolbarExpandedChange(false) },
            )
            .pointerInput(revealedKey) {
                // 删除按钮露出时，点击列表任意位置都先收起。
                if (revealedKey != null) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                        } while (event.changes.any { it.pressed })
                        revealedKey = null
                    }
                }
            },
        contentPadding = innerPadding.plus(
            horizontal = layout.horizontalPadding,
            top = 8.dp,
            bottom = 112.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(layout.spacing),
        verticalArrangement = Arrangement.spacedBy(layout.spacing),
    ) {
        items(items = items, key = { it.stableKey }, contentType = { it::class }) { item ->
            SwipeToRevealDelete(
                revealed = revealedKey == item.stableKey,
                deleteDescription = when (item) {
                    is ManualHomeItem.Inspection -> "删除检测标准 ${item.entry.displayTitle}"
                    is ManualHomeItem.EmptyCompound -> "删除胶料 ${item.compound.compoundCode}"
                },
                onReveal = { revealedKey = item.stableKey },
                onConceal = { revealedKey = null },
                onDelete = { onDeleteItem(item) },
                cornerRadius = layout.cornerRadius,
                highlighted = highlightedKey == item.stableKey,
                modifier = Modifier.animateItem(
                    fadeInSpec = spring(),
                    placementSpec = spring(),
                    fadeOutSpec = spring(),
                ),
            ) { shape, guardClick ->
                ManualHomeCard(
                    item = item,
                    viewMode = viewMode,
                    searchQuery = query,
                    stackedHeader = layout.stackedHeader,
                    shape = shape,
                    onClick = { guardClick { onOpenItem(item) } },
                )
            }
        }
    }
}
