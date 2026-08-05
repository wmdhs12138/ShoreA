package com.wmdhs.shorea

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal data class CompoundFormData(
    val compoundCode: String,
    val testPieceCureTemperatureC: String,
    val testPieceCureTimeMinutes: String,
    val customBlockCureTimeMinutes: String,
    val notes: String,
)

internal data class InspectionFormData(
    val standardNumber: String,
    val partNumbers: List<String>,
    val hardness: HardnessSet,
    val productCategory: String,
    val color: String,
    val tensileStrength: String,
    val elongation: String,
    val notes: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualTopBar(
    hasLoaded: Boolean,
    compoundCount: Int,
    entryCount: Int,
    actionsEnabled: Boolean,
    searchActive: Boolean,
    query: String,
    sortOrder: ManualSortOrder,
    viewMode: ManualViewMode,
    onSearchActiveChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSortOrderChange: (ManualSortOrder) -> Unit,
    onViewModeChange: (ManualViewMode) -> Unit,
    onImportExport: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(searchActive) {
        if (searchActive && actionsEnabled) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "硬度块手册",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            "$compoundCount 个胶料 · $entryCount 个检测标准 · 本地保存",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = actionsEnabled,
                        modifier = Modifier.semantics { contentDescription = if (searchActive) "关闭搜索" else "搜索" },
                        onClick = {
                            if (searchActive) keyboard?.hide()
                            onSearchActiveChange(!searchActive)
                        },
                    ) { ManualTopBarIcon(if (searchActive) ManualTopBarIconType.CLOSE else ManualTopBarIconType.SEARCH) }
                    Box {
                        IconButton(
                            enabled = actionsEnabled,
                            modifier = Modifier.semantics { contentDescription = "排序，当前${sortOrder.label}" },
                            onClick = { sortMenuExpanded = true },
                        ) { ManualTopBarIcon(ManualTopBarIconType.SORT) }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            ManualSortOrder.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    modifier = Modifier.semantics { selected = option == sortOrder },
                                    onClick = { onSortOrderChange(option); sortMenuExpanded = false },
                                )
                            }
                        }
                    }
                    IconButton(
                        enabled = hasLoaded,
                        modifier = Modifier.semantics { contentDescription = "导入与导出" },
                        onClick = onImportExport,
                    ) { ManualTopBarIcon(ManualTopBarIconType.MORE) }
                },
            )
            if (searchActive && actionsEnabled) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("搜索胶料号、标准号、部品号或硬度") },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(
                            onClick = onClearQuery,
                            modifier = Modifier.semantics { contentDescription = "清除搜索内容" },
                        ) { ManualTopBarIcon(ManualTopBarIconType.CLOSE) }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide() }),
                    singleLine = true,
                )
            }
            if (actionsEnabled) {
                ExpressiveChoiceGroup(
                    options = ManualViewMode.entries,
                    selected = viewMode,
                    label = { it.label },
                    onSelected = onViewModeChange,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}

private enum class ManualTopBarIconType {
    SEARCH,
    SORT,
    VIEW,
    CLOSE,
    MORE,
}

@Composable
private fun ManualTopBarIcon(
    icon: ManualTopBarIconType,
    modifier: Modifier = Modifier,
) {
    val iconColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = 2.dp.toPx()
        when (icon) {
            ManualTopBarIconType.SEARCH -> {
                drawCircle(
                    color = iconColor,
                    radius = size.minDimension * 0.28f,
                    center = Offset(size.width * 0.41f, size.height * 0.41f),
                    style = Stroke(width = strokeWidth),
                )
                drawLine(
                    color = iconColor,
                    start = Offset(size.width * 0.61f, size.height * 0.61f),
                    end = Offset(size.width * 0.84f, size.height * 0.84f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            ManualTopBarIconType.SORT -> {
                listOf(0.28f, 0.5f, 0.72f).forEachIndexed { index, y ->
                    drawLine(
                        color = iconColor,
                        start = Offset(size.width * (0.2f + index * 0.08f), size.height * y),
                        end = Offset(size.width * (0.8f - index * 0.08f), size.height * y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
            ManualTopBarIconType.VIEW -> {
                val gap = size.width * 0.08f
                val cellSize = (size.width - gap * 3) / 2f
                for (row in 0..1) {
                    for (column in 0..1) {
                        drawRoundRect(
                            color = iconColor,
                            topLeft = Offset(
                                gap + column * (cellSize + gap),
                                gap + row * (cellSize + gap),
                            ),
                            size = androidx.compose.ui.geometry.Size(cellSize, cellSize),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(gap, gap),
                        )
                    }
                }
            }
            ManualTopBarIconType.MORE -> {
                listOf(.25f, .5f, .75f).forEach { x ->
                    drawCircle(color = iconColor, radius = 2.dp.toPx(), center = Offset(size.width * x, size.height / 2f))
                }
            }
            ManualTopBarIconType.CLOSE -> {
                drawLine(
                    color = iconColor,
                    start = Offset(size.width * 0.25f, size.height * 0.25f),
                    end = Offset(size.width * 0.75f, size.height * 0.75f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = iconColor,
                    start = Offset(size.width * 0.75f, size.height * 0.25f),
                    end = Offset(size.width * 0.25f, size.height * 0.75f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
internal fun ManualLoadingState(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.semantics { contentDescription = "正在读取本地手册" },
        ) {
            ExpressiveLoadingGlyph(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primary)
            Text("正在读取本地手册", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun ManualDataErrorState(
    innerPadding: PaddingValues,
    detail: String,
    onRestoreBackup: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "本地资料无法读取",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "为避免覆盖原数据，当前已停止编辑。可以从之前导出的备份恢复。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRestoreBackup) { Text("从备份恢复") }
        }
    }
}

@Composable
internal fun EmptyManualState(
    innerPadding: PaddingValues,
    onAddCompound: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "还没有胶料资料",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "建立第一种胶料，开始整理检测标准与部品硬度。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAddCompound) { Text("添加第一种胶料") }
        }
    }
}

@Composable
internal fun EmptyManualSearchState(
    innerPadding: PaddingValues,
    query: String,
    onClearSearch: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "没有匹配的检测标准",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "未找到与“$query”相关的胶料号、标准号、部品号或硬度",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onClearSearch) { Text("清除搜索") }
        }
    }
}

@Composable
internal fun SwipeManualHomeItem(
    item: ManualHomeItem,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    revealed: Boolean = false,
    searchQuery: String,
    viewMode: ManualViewMode,
    onOpen: () -> Unit,
    onReveal: () -> Unit,
    onInteractionOutsideDelete: () -> Unit,
    onDeleteInspection: (InspectionEntry) -> Unit,
    onDeleteEmptyCompound: (RubberCompound) -> Unit,
) {
    key(item.stableKey) {
        val coroutineScope = rememberCoroutineScope()
        val actionWidthPx = with(LocalDensity.current) { 88.dp.toPx() }
        var offsetPx by remember { mutableFloatStateOf(0f) }
        var deleteRequested by remember { mutableStateOf(false) }
        val revealFraction = (-offsetPx / actionWidthPx).coerceIn(0f, 1f)
        val cardCornerRadius = if (viewMode == ManualViewMode.COMPACT) 20.dp else 24.dp
        val itemShape = RoundedCornerShape(cardCornerRadius)
        val cardShape = RoundedCornerShape(
            topStart = cardCornerRadius,
            topEnd = cardCornerRadius * (1f - revealFraction),
            bottomEnd = cardCornerRadius * (1f - revealFraction),
            bottomStart = cardCornerRadius,
        )
        val highlightAlpha by animateFloatAsState(
            targetValue = if (highlighted) 1f else 0f,
            animationSpec = androidx.compose.animation.core.spring(),
            label = "restored-item-highlight",
        )
        val draggableState = rememberDraggableState { delta ->
            offsetPx = (offsetPx + delta).coerceIn(-actionWidthPx, 0f)
        }

        suspend fun animateOffsetTo(target: Float) {
            animate(
                initialValue = offsetPx,
                targetValue = target,
                animationSpec = androidx.compose.animation.core.spring(),
            ) { value, _ ->
                offsetPx = value
            }
        }

        LaunchedEffect(revealed) {
            val target = if (revealed) -actionWidthPx else 0f
            if (offsetPx != target) animateOffsetTo(target)
        }

        Box(
            modifier = modifier.fillMaxWidth()
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = highlightAlpha,
                    ),
                    shape = itemShape,
                )
                .clip(itemShape),
        ) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Surface(
                    modifier = Modifier.fillMaxHeight()
                        .width(88.dp)
                        .semantics {
                            if (!revealed) invisibleToUser()
                            role = Role.Button
                            contentDescription = when (item) {
                                is ManualHomeItem.Inspection -> "删除检测标准 ${item.entry.standardNumber}，危险操作"
                                is ManualHomeItem.EmptyCompound -> "删除胶料 ${item.compound.compoundCode}，危险操作"
                            }
                        }
                        .clickable(enabled = revealed && !deleteRequested) {
                            deleteRequested = true
                            coroutineScope.launch {
                                // 删除前先收起操作区。撤回时条目会以初始位置重新进入，
                                // 不会继承“已打开”状态。
                                animateOffsetTo(0f)
                                when (item) {
                                    is ManualHomeItem.Inspection -> {
                                        onDeleteInspection(item.entry)
                                    }
                                    is ManualHomeItem.EmptyCompound -> {
                                        onDeleteEmptyCompound(item.compound)
                                    }
                                }
                                deleteRequested = false
                            }
                        },
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "删除",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.semantics {
                    val label = when (item) {
                        is ManualHomeItem.Inspection -> "删除此检测标准"
                        is ManualHomeItem.EmptyCompound -> "删除此胶料"
                    }
                    customActions = listOf(CustomAccessibilityAction(label) {
                        when (item) {
                            is ManualHomeItem.Inspection -> onDeleteInspection(item.entry)
                            is ManualHomeItem.EmptyCompound -> onDeleteEmptyCompound(item.compound)
                        }
                        true
                    })
                }.offset {
                    IntOffset(offsetPx.roundToInt(), 0)
                }.draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { onInteractionOutsideDelete() },
                    onDragStopped = { velocity ->
                        val shouldReveal = velocity < -700f ||
                            (velocity <= 700f && offsetPx <= -actionWidthPx / 2f)
                        animateOffsetTo(if (shouldReveal) -actionWidthPx else 0f)
                        if (shouldReveal) onReveal() else onInteractionOutsideDelete()
                    },
                ),
            ) {
                ManualHomeCard(
                    item = item,
                    searchQuery = searchQuery,
                    viewMode = viewMode,
                    shape = cardShape,
                    onOpen = {
                        if (offsetPx < -1f) {
                            onInteractionOutsideDelete()
                        } else {
                            onInteractionOutsideDelete()
                            onOpen()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ManualHomeCard(
    item: ManualHomeItem,
    searchQuery: String,
    viewMode: ManualViewMode,
    shape: androidx.compose.ui.graphics.Shape,
    onOpen: () -> Unit,
) {
    when (item) {
        is ManualHomeItem.Inspection -> InspectionHomeCard(
            compound = item.compound,
            entry = item.entry,
            searchQuery = searchQuery,
            viewMode = viewMode,
            shape = shape,
            onOpen = onOpen,
        )
        is ManualHomeItem.EmptyCompound -> EmptyCompoundHomeCard(
            compound = item.compound,
            viewMode = viewMode,
            shape = shape,
            onOpen = onOpen,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectionHomeCard(
    compound: RubberCompound,
    entry: InspectionEntry,
    searchQuery: String,
    viewMode: ManualViewMode,
    shape: androidx.compose.ui.graphics.Shape,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        when (viewMode) {
            ManualViewMode.LIST -> InspectionListCardContent(
                compound = compound,
                entry = entry,
                searchQuery = searchQuery,
            )
            ManualViewMode.COMPACT -> InspectionCompactCardContent(
                compound = compound,
                entry = entry,
            )
            ManualViewMode.DETAILED -> InspectionDetailedCardContent(
                compound = compound,
                entry = entry,
                searchQuery = searchQuery,
            )
        }
    }
}

@Composable
private fun InspectionListCardContent(
    compound: RubberCompound,
    entry: InspectionEntry,
    searchQuery: String,
) {
    val fontScale = LocalDensity.current.fontScale
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints {
            val stacked = maxWidth < 400.dp || fontScale >= 1.3f
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ListCardMetadata(compound, entry, compact = false)
                    PrimaryHardnessDisplay(entry.effectiveHardness, compact = true)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) { ListCardMetadata(compound, entry, compact = false) }
                    PrimaryHardnessDisplay(entry.effectiveHardness, compact = true)
                }
            }
        }
        SecondaryHardnessRow(entry.hardness, entry.effectiveHardness?.source, compact = true)
        SearchEntryHint(entry, searchQuery)
    }
}

@Composable
private fun InspectionCompactCardContent(compound: RubberCompound, entry: InspectionEntry) {
    val fontScale = LocalDensity.current.fontScale
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        BoxWithConstraints {
            val stacked = maxWidth < 400.dp || fontScale >= 1.3f
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ListCardMetadata(compound, entry, compact = true)
                    PrimaryHardnessDisplay(entry.effectiveHardness, compact = true)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) { ListCardMetadata(compound, entry, compact = true) }
                    PrimaryHardnessDisplay(entry.effectiveHardness, compact = true)
                }
            }
        }
        SecondaryHardnessRow(entry.hardness, entry.effectiveHardness?.source, compact = true)
    }
}

@Composable
private fun ListCardMetadata(compound: RubberCompound, entry: InspectionEntry, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)) {
        PartNumberList(entry.partNumbers, compact = compact)
        if (compact) Text("${compound.compoundCode} · 标准号 ${entry.standardNumber.ifBlank { "未填写" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            HomeMetaLine("胶料号", compound.compoundCode)
            HomeMetaLine("标准号", entry.standardNumber.ifBlank { "未填写" })
        }
    }
}

@Composable
private fun InspectionDetailedCardContent(
    compound: RubberCompound,
    entry: InspectionEntry,
    searchQuery: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        PartNumberList(entry.partNumbers)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HomeMetaLine("胶料号", compound.compoundCode)
            HomeMetaLine("标准号", entry.standardNumber.ifBlank { "未填写" })
            PrimaryHardnessDisplay(entry.effectiveHardness, compact = true)
        }
        HorizontalDivider()
        AllHardnessDetails(entry.hardness, primarySource = entry.effectiveHardness?.source)
        if (
            compound.testPieceCureTemperatureC.isNotBlank() ||
            compound.testPieceCureTimeMinutes.isNotBlank()
        ) {
            Text(
                text = "试片硫化：${cureConditionText(compound.testPieceCureTemperatureC, compound.testPieceCureTimeMinutes)}；硬度块：${compound.blockCureTimeMinutes.ifBlank { "时间未填" }} min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OptionalDetailRow("产品类别", entry.productCategory)
        OptionalDetailRow("颜色", entry.color)
        OptionalDetailRow("拉伸强度", entry.tensileStrength)
        OptionalDetailRow("伸长率", entry.elongation)
        OptionalDetailRow("检测标准备注", entry.notes)
        OptionalDetailRow("胶料备注", compound.notes)
        SearchEntryHint(entry = entry, query = searchQuery)
    }
}

@Composable
private fun EmptyCompoundHomeCard(
    compound: RubberCompound,
    viewMode: ManualViewMode,
    shape: androidx.compose.ui.graphics.Shape,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = compound.compoundCode,
                style = if (viewMode == ManualViewMode.COMPACT) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.titleLarge
                },
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "尚未添加检测标准",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (
                compound.testPieceCureTemperatureC.isNotBlank() ||
                compound.testPieceCureTimeMinutes.isNotBlank()
            ) {
                Text(
                    text = "试片硫化：${cureConditionText(compound.testPieceCureTemperatureC, compound.testPieceCureTimeMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (compound.notes.isNotBlank()) {
                Text(
                    text = compound.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartNumberList(
    partNumbers: List<String>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val normalized = normalizePartNumbers(partNumbers)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        normalized.forEach { partNumber ->
            Surface(
                shape = RoundedCornerShape(if (compact) 7.dp else 9.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = partNumber,
                    modifier = Modifier.padding(
                        horizontal = if (compact) 7.dp else 9.dp,
                        vertical = if (compact) 3.dp else 5.dp,
                    ),
                    style = if (compact) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HomeMetaLine(label: String, value: String) {
    Text(
        text = "$label · $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PrimaryHardnessDisplay(
    hardness: EffectiveHardness?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (hardness == null) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (hardness == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
        if (hardness == null) {
            Text(
                text = "未设硬度",
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            HardnessValueDisplay(
                value = hardness.rawValue,
                compact = compact,
                accent = true,
            )
            Text(
                text = hardness.source.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SecondaryHardnessRow(
    hardness: HardnessSet,
    primarySource: HardnessSource?,
    compact: Boolean,
) {
    val values = hardness.allValues().filter { it.first != primarySource }
    if (values.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        values.forEach { (source, value) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = source.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HardnessValueDisplay(value = value, compact = compact)
            }
        }
    }
}

@Composable
private fun AllHardnessDetails(
    hardness: HardnessSet,
    primarySource: HardnessSource?,
) {
    val values = hardness.allValues().filter { it.first != primarySource }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        values.forEach { (source, value) ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = source.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (source == primarySource) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (source == primarySource) FontWeight.SemiBold else null,
                )
                HardnessValueDisplay(value = value, compact = true)
            }
        }
        if (hardness.allValues().isEmpty()) {
            Text(
                text = "未设硬度",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchEntryHint(entry: InspectionEntry, query: String) {
    if (query.isBlank()) return
    val matches = buildList {
        if (entry.productCategory.contains(query, ignoreCase = true)) add("产品类别")
        if (entry.color.contains(query, ignoreCase = true)) add("颜色")
        if (entry.tensileStrength.contains(query, ignoreCase = true)) add("拉伸强度")
        if (entry.elongation.contains(query, ignoreCase = true)) add("伸长率")
        if (entry.notes.contains(query, ignoreCase = true)) add("备注")
    }
    if (matches.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                text = "搜索命中：${matches.joinToString("、")}",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompoundDetailSheet(
    compound: RubberCompound,
    entries: List<InspectionEntry>,
    highlightedEntryId: Long?,
    onEditCompound: () -> Unit,
    onAddInspection: () -> Unit,
    onEditInspection: (InspectionEntry) -> Unit,
    onDeleteInspection: (InspectionEntry) -> Unit,
    onDeleteCompound: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompoundGlyph(Modifier.size(29.dp))
                    }
                    Column {
                        Text(
                            text = compound.compoundCode,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = "${entries.size} 个检测标准 · ${entries.sumOf { it.partNumbers.size }} 个部品关联",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onEditCompound) { Text("编辑胶料") }
                }
            }
            item { CureConditionCard(compound) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "检测标准",
                        modifier = Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onAddInspection) { Text("＋ 添加标准") }
                }
            }
            if (entries.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("还没有检测标准", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "添加标准号、部品号与三类硬度资料",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(entries, key = InspectionEntry::id) { entry ->
                    InspectionEntryDetailCard(
                        entry = entry,
                        highlighted = entry.id == highlightedEntryId,
                        onEdit = { onEditInspection(entry) },
                        onDelete = { onDeleteInspection(entry) },
                    )
                }
            }
            if (compound.notes.isNotBlank()) {
                item { DetailValueCard("胶料备注", compound.notes) }
            }
            item {
                TextButton(onClick = onDeleteCompound, modifier = Modifier.fillMaxWidth()) {
                    Text("删除胶料", color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun InspectionEntryDetailCard(
    entry: InspectionEntry,
    highlighted: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PartNumberList(entry.partNumbers)
            HomeMetaLine("检测标准号", entry.standardNumber.ifBlank { "未填写" })
            PrimaryHardnessBlock(entry)
            AllHardnessDetails(entry.hardness, entry.effectiveHardness?.source)
            OptionalDetailRow("产品类别", entry.productCategory)
            OptionalDetailRow("颜色", entry.color)
            OptionalDetailRow("拉伸强度", entry.tensileStrength)
            OptionalDetailRow("伸长率", entry.elongation)
            OptionalDetailRow("备注", entry.notes)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CureConditionCard(compound: RubberCompound) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("制作条件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (
                compound.testPieceCureTemperatureC.isBlank() &&
                compound.testPieceCureTimeMinutes.isBlank()
            ) {
                Text("尚未填写试片硫化条件", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ConditionRow(
                    label = "试片",
                    value = cureConditionText(
                        compound.testPieceCureTemperatureC,
                        compound.testPieceCureTimeMinutes,
                    ),
                )
                ConditionRow(
                    label = "硬度块",
                    value = cureConditionText(
                        compound.testPieceCureTemperatureC,
                        compound.blockCureTimeMinutes,
                    ),
                    supporting = if (compound.usesCustomBlockCureTime) {
                        "使用自定义时间"
                    } else {
                        "按试片时间自动 ×2"
                    },
                )
            }
        }
    }
}

@Composable
private fun ConditionRow(label: String, value: String, supporting: String = "") {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontWeight = FontWeight.SemiBold)
            if (supporting.isNotBlank()) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun cureConditionText(temperature: String, time: String): String {
    val temperatureText = temperature.takeIf(String::isNotBlank)?.let { "$it℃" } ?: "温度未填"
    val timeText = time.takeIf(String::isNotBlank)?.let { "$it min" } ?: "时间未填"
    return "$temperatureText × $timeText"
}

@Composable
private fun PrimaryHardnessBlock(entry: InspectionEntry) {
    val primary = entry.effectiveHardness
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = when (primary?.source) {
            HardnessSource.BLOCK_STANDARD -> MaterialTheme.colorScheme.primaryContainer
            HardnessSource.PRODUCT_STANDARD -> MaterialTheme.colorScheme.tertiaryContainer
            HardnessSource.TEST_PIECE -> MaterialTheme.colorScheme.secondaryContainer
            null -> MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("主要有效硬度", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryHardnessDisplay(primary, compact = false)
        }
    }
}

@Composable
private fun HardnessValueDisplay(
    value: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showCalculatedRange: Boolean = false,
    accent: Boolean = false,
) {
    val parsed = remember(value) { parseHardness(value) }
    val mainStyle = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium
    val valueColor = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val spokenValue = when (parsed) {
        is ParsedHardness.Tolerance -> "硬度 ${parsed.nominal} A，允许范围 ${parsed.lowerLimit} 到 ${parsed.upperLimit} A"
        is ParsedHardness.Range -> "硬度范围 ${parsed.lower} 到 ${parsed.upper} A"
        is ParsedHardness.Minimum -> "硬度最低 ${parsed.value} A"
        is ParsedHardness.Maximum -> "硬度最高 ${parsed.value} A"
        is ParsedHardness.Exact -> "硬度 ${parsed.value} A"
        is ParsedHardness.Raw -> "硬度 ${parsed.raw.ifBlank { "未填写" }}"
    }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spokenValue },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        when (parsed) {
            is ParsedHardness.Tolerance -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(parsed.nominal, style = mainStyle, color = valueColor, fontWeight = FontWeight.Bold)
                    Column(modifier = Modifier.padding(start = 3.dp)) {
                        Text(
                            "+${parsed.upperDeviation}",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 13.sp),
                            color = valueColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "−${parsed.lowerDeviation}",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 13.sp),
                            color = valueColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(" A", style = MaterialTheme.typography.labelLarge, color = valueColor, fontWeight = FontWeight.SemiBold)
                }
                if (showCalculatedRange) {
                    Text("允许范围 ${parsed.lowerLimit} — ${parsed.upperLimit} A", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is ParsedHardness.Range -> {
                Text("${parsed.lower} — ${parsed.upper} A", style = mainStyle, color = valueColor, fontWeight = FontWeight.Bold)
                if (showCalculatedRange) Text("下限 ${parsed.lower} · 上限 ${parsed.upper}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is ParsedHardness.Minimum -> {
                Text("${if (parsed.inclusive) "≥" else ">"} ${parsed.value} A", style = mainStyle, color = valueColor, fontWeight = FontWeight.Bold)
                if (showCalculatedRange) Text("最低要求", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is ParsedHardness.Maximum -> {
                Text("${if (parsed.inclusive) "≤" else "<"} ${parsed.value} A", style = mainStyle, color = valueColor, fontWeight = FontWeight.Bold)
                if (showCalculatedRange) Text("最高允许", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is ParsedHardness.Exact -> Text("${parsed.value} A", style = mainStyle, color = valueColor, fontWeight = FontWeight.Bold)
            is ParsedHardness.Raw -> Text(
                parsed.raw.ifBlank { "—" },
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
                color = valueColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun OptionalDetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value)
    }
}

@Composable
private fun DetailValueCard(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(value)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompoundEditorDialog(
    initial: RubberCompound?,
    compounds: List<RubberCompound>,
    onDismiss: () -> Unit,
    onSave: (CompoundFormData) -> Unit,
) {
    var compoundCode by rememberSaveable(initial?.id) { mutableStateOf(initial?.compoundCode.orEmpty()) }
    var temperature by rememberSaveable(initial?.id) { mutableStateOf(initial?.testPieceCureTemperatureC.orEmpty()) }
    var testPieceTime by rememberSaveable(initial?.id) { mutableStateOf(initial?.testPieceCureTimeMinutes.orEmpty()) }
    var customBlockTime by rememberSaveable(initial?.id) { mutableStateOf(initial?.customBlockCureTimeMinutes.orEmpty()) }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }

    val normalizedCode = compoundCode.trim().uppercase(Locale.ROOT)
    val duplicateCode = hasDuplicateCompoundCode(compounds, normalizedCode, initial?.id)
    val canSave = normalizedCode.isNotEmpty() && !duplicateCode
    val hasUnsavedChanges = compoundCode != initial?.compoundCode.orEmpty() ||
        temperature != initial?.testPieceCureTemperatureC.orEmpty() ||
        testPieceTime != initial?.testPieceCureTimeMinutes.orEmpty() ||
        customBlockTime != initial?.customBlockCureTimeMinutes.orEmpty() ||
        notes != initial?.notes.orEmpty()

    FullScreenEditorDialog(
        title = if (initial == null) "添加胶料" else "编辑胶料",
        canSave = canSave,
        hasUnsavedChanges = hasUnsavedChanges,
        onDismiss = onDismiss,
        onSave = {
            onSave(
                CompoundFormData(
                    compoundCode = normalizedCode,
                    testPieceCureTemperatureC = temperature.trim(),
                    testPieceCureTimeMinutes = testPieceTime.trim(),
                    customBlockCureTimeMinutes = customBlockTime.trim(),
                    notes = notes.trim(),
                ),
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { EditorSectionTitle("基本信息", "胶料号是手册的第一层级") }
            item {
                OutlinedTextField(
                    value = compoundCode,
                    onValueChange = { compoundCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("胶料号 *") },
                    placeholder = { Text("例如：P01331201") },
                    supportingText = { if (duplicateCode) Text("该胶料号已存在，请编辑原记录") },
                    isError = duplicateCode,
                    singleLine = true,
                )
            }
            item { EditorSectionTitle("试片硫化条件", "硬度块时间默认按试片时间的双倍计算") }
            item {
                OutlinedTextField(
                    value = temperature,
                    onValueChange = { temperature = sanitizeDecimalInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("硫化温度（℃）") },
                    placeholder = { Text("例如：180") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = testPieceTime,
                    onValueChange = { testPieceTime = sanitizeDecimalInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("试片硫化时间（min）") },
                    placeholder = { Text("例如：9") },
                    supportingText = {
                        val calculated = testPieceTime.toDoubleOrNull()?.times(2)?.let(::compactEditorNumber)
                        if (calculated != null && customBlockTime.isBlank()) Text("硬度块时间将自动计算为 $calculated min")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = customBlockTime,
                    onValueChange = { customBlockTime = sanitizeDecimalInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("硬度块自定义时间（可选）") },
                    placeholder = { Text("留空则自动使用试片时间 ×2") },
                    supportingText = { Text("仅在个别胶料不按双倍时间时填写") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
            item { EditorSectionTitle("备注", "用于记录胶料层面的补充信息") }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("胶料备注") },
                    minLines = 3,
                    maxLines = 6,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InspectionEditorDialog(
    compound: RubberCompound,
    entries: List<InspectionEntry>,
    initial: InspectionEntry?,
    onDismiss: () -> Unit,
    onSave: (InspectionFormData) -> Unit,
) {
    var standardNumber by rememberSaveable(initial?.id) { mutableStateOf(initial?.standardNumber.orEmpty()) }
    var partNumbersText by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.partNumbers?.joinToString("\n").orEmpty())
    }
    var testPieceHardness by rememberSaveable(initial?.id) { mutableStateOf(initial?.hardness?.testPieceHardness.orEmpty()) }
    var blockHardness by rememberSaveable(initial?.id) { mutableStateOf(initial?.hardness?.blockHardness.orEmpty()) }
    var productHardness by rememberSaveable(initial?.id) { mutableStateOf(initial?.hardness?.productHardness.orEmpty()) }
    var productCategory by rememberSaveable(initial?.id) { mutableStateOf(initial?.productCategory.orEmpty()) }
    var color by rememberSaveable(initial?.id) { mutableStateOf(initial?.color.orEmpty()) }
    var tensileStrength by rememberSaveable(initial?.id) { mutableStateOf(initial?.tensileStrength.orEmpty()) }
    var elongation by rememberSaveable(initial?.id) { mutableStateOf(initial?.elongation.orEmpty()) }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }

    val normalizedStandardNumber = standardNumber.trim()
    val parsedPartNumbers = parsePartNumbers(partNumbersText)
    val duplicateStandard = hasDuplicateStandardNumber(
        entries = entries,
        compoundId = compound.id,
        standardNumber = normalizedStandardNumber,
        excludingEntryId = initial?.id,
    )
    val partNumberConflicts = findPartNumberConflicts(
        entries = entries,
        compoundId = compound.id,
        partNumbers = parsedPartNumbers,
        excludingEntryId = initial?.id,
    )
    val canSave = parsedPartNumbers.isNotEmpty() &&
        !duplicateStandard &&
        partNumberConflicts.isEmpty()
    val hasUnsavedChanges = standardNumber != initial?.standardNumber.orEmpty() ||
        partNumbersText != initial?.partNumbers?.joinToString("\n").orEmpty() ||
        testPieceHardness != initial?.hardness?.testPieceHardness.orEmpty() ||
        blockHardness != initial?.hardness?.blockHardness.orEmpty() ||
        productHardness != initial?.hardness?.productHardness.orEmpty() ||
        productCategory != initial?.productCategory.orEmpty() || color != initial?.color.orEmpty() ||
        tensileStrength != initial?.tensileStrength.orEmpty() || elongation != initial?.elongation.orEmpty() ||
        notes != initial?.notes.orEmpty()

    fun submitForm() {
        onSave(
            InspectionFormData(
                standardNumber = normalizedStandardNumber,
                partNumbers = parsedPartNumbers,
                hardness = HardnessSet(
                    testPieceHardness = testPieceHardness.trim(),
                    blockHardness = blockHardness.trim(),
                    productHardness = productHardness.trim(),
                ),
                productCategory = productCategory.trim(),
                color = color.trim(),
                tensileStrength = tensileStrength.trim(),
                elongation = elongation.trim(),
                notes = notes.trim(),
            ),
        )
    }

    FullScreenEditorDialog(
        title = if (initial == null) "添加检测标准" else "编辑检测标准",
        subtitle = compound.compoundCode,
        canSave = canSave,
        hasUnsavedChanges = hasUnsavedChanges,
        onDismiss = onDismiss,
        onSave = { submitForm() },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { EditorSectionTitle("检测标准", "部品号面向客户展示，标准号用于区分检测要求（可选）") }
            item {
                OutlinedTextField(
                    value = standardNumber,
                    onValueChange = { standardNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("检测标准号") },
                    placeholder = { Text("例如：B2-12-1078/A") },
                    supportingText = {
                        Text(if (duplicateStandard) "该检测标准号已存在，请编辑原记录" else "标准号为空时仍可保存，部品号必须填写")
                    },
                    isError = duplicateStandard,
                    singleLine = true,
                )
            }
            item { EditorSectionTitle("适用部品", "执行同一检测标准的多个部品号可放在同一条记录") }
            item {
                OutlinedTextField(
                    value = partNumbersText,
                    onValueChange = { partNumbersText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("部品号 *") },
                    placeholder = { Text("每行一个完整部品号\n例如：\n3DE000023A\n3DE000023C") },
                    supportingText = {
                        Text(
                            text = if (partNumberConflicts.isEmpty()) {
                                "也支持使用逗号、分号或空格分隔；保存时会去重并排序"
                            } else {
                                "${partNumberConflicts.size} 个部品号已用于同一胶料的其他标准，请修改后保存"
                            },
                            color = if (partNumberConflicts.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    },
                    minLines = 4,
                    maxLines = 7,
                )
            }
            item { EditorSectionTitle("硬度标准", "主要有效硬度优先使用硬度块，其次产品，最后试片") }
            item {
                OutlinedTextField(
                    value = testPieceHardness,
                    onValueChange = { testPieceHardness = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("试片硬度") },
                    placeholder = { Text("例如：33 +2/-2 A") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = blockHardness,
                    onValueChange = { blockHardness = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("硬度块硬度") },
                    placeholder = { Text("例如：34 +3/-3 A") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = productHardness,
                    onValueChange = { productHardness = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("产品硬度") },
                    placeholder = { Text("例如：35 +3/-3 A") },
                    singleLine = true,
                )
            }
            item { EditorSectionTitle("低频资料", "这些信息在首页详细视图中显示") }
            item {
                OutlinedTextField(
                    value = productCategory,
                    onValueChange = { productCategory = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("产品类别") },
                    placeholder = { Text("例如：橡胶避振脚") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("颜色") },
                    placeholder = { Text("例如：黑色") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = tensileStrength,
                    onValueChange = { tensileStrength = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("拉伸强度") },
                    placeholder = { Text("例如：标准 ≥11.76 MPa；快检 ≥9 MPa") },
                    minLines = 2,
                    maxLines = 4,
                )
            }
            item {
                OutlinedTextField(
                    value = elongation,
                    onValueChange = { elongation = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("伸长率") },
                    placeholder = { Text("例如：≥400%") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("检测标准备注") },
                    minLines = 3,
                    maxLines = 6,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenEditorDialog(
    title: String,
    subtitle: String = "",
    canSave: Boolean,
    hasUnsavedChanges: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    var showDiscardConfirmation by rememberSaveable { mutableStateOf(false) }
    fun requestDismiss() {
        if (hasUnsavedChanges) showDiscardConfirmation = true else onDismiss()
    }
    BackHandler { requestDismiss() }

    Dialog(
        onDismissRequest = ::requestDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(title, fontWeight = FontWeight.SemiBold)
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        navigationIcon = { TextButton(onClick = ::requestDismiss) { Text("取消") } },
                        actions = {
                            TextButton(onClick = onSave, enabled = canSave) { Text("保存") }
                        },
                    )
                },
            ) { innerPadding -> content(innerPadding) }
        }
    }
    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("放弃未保存更改？") },
            text = { Text("当前表单中的修改尚未保存，放弃后无法恢复。") },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("放弃更改", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) { Text("继续编辑") }
            },
        )
    }
}

@Composable
private fun EditorSectionTitle(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DeleteInspectionDialog(
    entry: InspectionEntry,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val title = entry.standardNumber.ifBlank { entry.partNumbers.joinToString("、") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除检测标准？") },
        text = { Text("将删除标准“$title”及其 ${entry.partNumbers.size} 个部品关联。此操作会立即保存。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun DeleteCompoundDialog(
    compound: RubberCompound,
    entryCount: Int,
    partCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除胶料？") },
        text = {
            Text(
                "删除胶料“${compound.compoundCode}”将同时删除 $entryCount 个检测标准和 $partCount 个部品关联。此操作会立即保存。",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CompoundGlyph(modifier: Modifier = Modifier) {
    val iconColor = MaterialTheme.colorScheme.onPrimaryContainer
    Canvas(modifier = modifier) {
        val lineStartX = 4.dp.toPx()
        val lineEndX = size.width - 4.dp.toPx()
        val strokeWidth = 2.2.dp.toPx()
        val rowYs = floatArrayOf(5.dp.toPx(), size.height / 2f, size.height - 5.dp.toPx())
        rowYs.forEachIndexed { index, y ->
            drawLine(
                color = iconColor,
                start = Offset(lineStartX + (index * 2).dp.toPx(), y),
                end = Offset(lineEndX - (index * 2).dp.toPx(), y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun parsePartNumbers(rawValue: String): List<String> = normalizePartNumbers(
    rawValue.split(Regex("[\\s,，;；]+")),
)

private fun sanitizeDecimalInput(rawValue: String): String {
    val normalized = rawValue.replace(',', '.')
    val builder = StringBuilder()
    var hasDecimalPoint = false
    normalized.forEach { character ->
        when {
            character.isDigit() -> builder.append(character)
            character == '.' && !hasDecimalPoint -> {
                hasDecimalPoint = true
                builder.append(character)
            }
        }
    }
    return builder.toString()
}

private fun compactEditorNumber(value: Double): String {
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
}
