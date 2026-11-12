package com.wmdhs.shorea.ui.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmdhs.shorea.R
import com.wmdhs.shorea.data.model.HardnessSource
import com.wmdhs.shorea.data.model.InspectionEntry
import com.wmdhs.shorea.data.model.RubberCompound
import com.wmdhs.shorea.data.model.UNNAMED_STANDARD
import com.wmdhs.shorea.data.model.cureConditionText
import com.wmdhs.shorea.ui.LocalSnackbarHostState
import com.wmdhs.shorea.ui.ManualViewModel
import com.wmdhs.shorea.ui.components.DeleteCompoundDialog
import com.wmdhs.shorea.ui.components.DeleteInspectionDialog
import com.wmdhs.shorea.ui.components.HardnessTextSize
import com.wmdhs.shorea.ui.components.HardnessValueText
import com.wmdhs.shorea.ui.components.SecondaryHardnessRow
import com.wmdhs.shorea.ui.components.ShapeIcon
import com.wmdhs.shorea.ui.components.plus
import com.wmdhs.shorea.ui.home.FactGrid
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
internal fun CompoundDetailScreen(
    viewModel: ManualViewModel,
    compoundId: Long,
    initialHighlightEntryId: Long?,
    onBack: () -> Unit,
    onEditCompound: () -> Unit,
    onAddInspection: () -> Unit,
    onEditInspection: (InspectionEntry) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusEntryId by viewModel.detailFocusEntryId.collectAsStateWithLifecycle()
    val manual = state.manual
    val compound = manual.findCompound(compoundId)

    // 胶料已被删除（本页删除或撤回其他操作导致）时返回上一页。
    LaunchedEffect(compound == null, state.isLoading) {
        if (compound == null && !state.isLoading) onBack()
    }
    if (compound == null) return

    val entries = remember(manual, compoundId) { manual.entriesForCompound(compoundId) }
    var highlightedEntryId by rememberSaveable { mutableStateOf(initialHighlightEntryId) }
    var pendingDeleteEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeleteCompound by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // 刚保存的检测标准：滚动到它并高亮。
    LaunchedEffect(focusEntryId) {
        val entryId = focusEntryId ?: return@LaunchedEffect
        if (entries.any { it.id == entryId }) highlightedEntryId = entryId
        viewModel.consumeDetailFocus(entryId)
    }
    LaunchedEffect(highlightedEntryId, entries.size) {
        val entryId = highlightedEntryId ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it.id == entryId }
        if (index >= 0) {
            val itemIndex = HEADER_ITEM_COUNT + index
            // 等首次布局完成，否则可见项为空会误判为"不在屏幕内"。
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
            // 卡片顶部至少露出 200dp（部品号、标准号与硬度开头）就不滚动，保留顶部概要。
            val mustShowPx = with(density) { 200.dp.roundToPx() }
            val topOnScreen = visible != null &&
                visible.offset >= layoutInfo.viewportStartOffset &&
                visible.offset + minOf(visible.size, mustShowPx) <=
                layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
            if (!topOnScreen) listState.animateScrollToItem(itemIndex)
            delay(2_000L)
            if (highlightedEntryId == entryId) highlightedEntryId = null
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(compound.compoundCode, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = {
                    Text(
                        text = compound.baseRubber.ifBlank { "未填写基胶" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onEditCompound,
                        enabled = state.canEdit,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = "编辑胶料")
                    }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            enabled = state.canEdit,
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
                                text = { Text("删除胶料", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_delete),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    confirmDeleteCompound = true
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        floatingActionButton = {
            if (state.canEdit) {
                ExtendedFloatingActionButton(
                    onClick = onAddInspection,
                    expanded = fabExpanded,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text("添加标准") },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding.plus(horizontal = 16.dp, top = 4.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") { CompoundSummary(compound, entries) }
            item(key = "cure") { CureConditionSection(compound) }
            item(key = "standards-title") { SectionTitle("检测标准") }
            inspectionEntries(
                entries = entries,
                highlightedEntryId = highlightedEntryId,
                canEdit = state.canEdit,
                onEdit = onEditInspection,
                onDelete = { pendingDeleteEntryId = it.id },
            )
            if (compound.notes.isNotBlank()) {
                item(key = "notes") { NotesCard("胶料备注", compound.notes) }
            }
        }
    }

    pendingDeleteEntryId?.let { entryId ->
        val entry = manual.findEntry(entryId)
        if (entry == null) {
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
    if (confirmDeleteCompound) {
        DeleteCompoundDialog(
            compound = compound,
            entryCount = entries.size,
            partCount = entries.sumOf { it.partNumbers.size },
            onConfirm = {
                confirmDeleteCompound = false
                viewModel.deleteCompound(compound.id)
            },
            onDismiss = { confirmDeleteCompound = false },
        )
    }
}

/** summary / cure / standards-title 三个固定条目，位于检测标准列表之前。 */
private const val HEADER_ITEM_COUNT = 3

@Composable
private fun CompoundSummary(compound: RubberCompound, entries: List<InspectionEntry>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapeIcon(
            icon = R.drawable.ic_science,
            polygon = MaterialShapes.Cookie9Sided,
            size = 64.dp,
            iconSize = 32.dp,
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatChip(value = entries.size.toString(), label = "项检测标准")
            StatChip(value = entries.sumOf { it.partNumbers.size }.toString(), label = "个部品关联")
        }
    }
}

@Composable
private fun StatChip(value: String, label: String) {
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(value, style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        trailing?.invoke()
    }
}

/** 硫化条件：M3E 分段列表（首尾大圆角、中间小圆角、条目间留缝）。 */
@Composable
private fun CureConditionSection(compound: RubberCompound) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SectionTitle("制作条件")
        val hasTestPiece = compound.testPieceCureTemperatureC.isNotBlank() ||
            compound.testPieceCureTimeMinutes.isNotBlank()
        val rows = buildList {
            if (hasTestPiece) {
                add(
                    Triple(
                        "试片",
                        cureConditionText(compound.testPieceCureTemperatureC, compound.testPieceCureTimeMinutes),
                        null,
                    ),
                )
            }
            if (hasTestPiece || compound.blockCureTimeMinutes.isNotBlank()) {
                add(
                    Triple(
                        "硬度块",
                        cureConditionText(compound.testPieceCureTemperatureC, compound.blockCureTimeMinutes),
                        if (compound.usesCustomBlockCureTime) "使用自定义时间" else "按试片时间自动 ×2",
                    ),
                )
            }
        }
        val segmentedColors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
        if (rows.isEmpty()) {
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                colors = segmentedColors,
                leadingContent = { Icon(painterResource(R.drawable.ic_thermostat), contentDescription = null) },
                supportingContent = { Text("编辑胶料即可补充试片温度与时间") },
            ) { Text("尚未填写硫化条件") }
        } else {
            rows.forEachIndexed { index, (label, value, supporting) ->
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = rows.size),
                    colors = segmentedColors,
                    leadingContent = {
                        Icon(
                            painterResource(if (index == 0 && hasTestPiece) R.drawable.ic_thermostat else R.drawable.ic_timer),
                            contentDescription = null,
                        )
                    },
                    overlineContent = { Text(label) },
                    supportingContent = supporting?.let { { Text(it) } },
                ) {
                    Text(value, style = MaterialTheme.typography.bodyLargeEmphasized)
                }
            }
        }
    }
}

private fun LazyListScope.inspectionEntries(
    entries: List<InspectionEntry>,
    highlightedEntryId: Long?,
    canEdit: Boolean,
    onEdit: (InspectionEntry) -> Unit,
    onDelete: (InspectionEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        item(key = "empty-standards") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShapeIcon(
                        icon = R.drawable.ic_description,
                        polygon = MaterialShapes.Clover4Leaf,
                        size = 56.dp,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text("还没有检测标准", style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        text = "添加标准号、部品号与三类硬度资料",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }
    itemsIndexed(entries, key = { _, entry -> entry.id }) { _, entry ->
        InspectionEntryCard(
            entry = entry,
            highlighted = entry.id == highlightedEntryId,
            canEdit = canEdit,
            onEdit = { onEdit(entry) },
            onDelete = { onDelete(entry) },
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun InspectionEntryCard(
    entry: InspectionEntry,
    highlighted: Boolean,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "entry-highlight",
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PartNumberChips(entry.partNumbers)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_description),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = entry.standardNumber.ifBlank { UNNAMED_STANDARD },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PrimaryHardnessPanel(entry)
            SecondaryHardnessRow(entry.hardness.secondaryValues())

            val facts = buildList {
                entry.productCategory.takeIf(String::isNotBlank)?.let { add("产品类别" to it) }
                entry.color.takeIf(String::isNotBlank)?.let { add("颜色" to it) }
                entry.tensileStrength.takeIf(String::isNotBlank)?.let { add("拉伸强度" to it) }
                entry.elongation.takeIf(String::isNotBlank)?.let { add("伸长率" to it) }
                entry.notes.takeIf(String::isNotBlank)?.let { add("备注" to it) }
            }
            if (facts.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FactGrid(facts)
            }

            if (canEdit) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text("删除", modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                    FilledTonalButton(onClick = onEdit, shapes = ButtonDefaults.shapes()) {
                        Icon(
                            painterResource(R.drawable.ic_edit),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text("编辑", modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                }
            }
        }
    }
}

@Composable
private fun PartNumberChips(partNumbers: List<String>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "部品号 ${partNumbers.joinToString("、")}" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        partNumbers.forEach { partNumber ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = partNumber,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PrimaryHardnessPanel(entry: InspectionEntry) {
    val primary = entry.effectiveHardness
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (primary?.source) {
        HardnessSource.BLOCK_STANDARD -> colors.primaryContainer to colors.onPrimaryContainer
        HardnessSource.PRODUCT_STANDARD -> colors.tertiaryContainer to colors.onTertiaryContainer
        HardnessSource.TEST_PIECE -> colors.secondaryContainer to colors.onSecondaryContainer
        null -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_speed), contentDescription = null, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = primary?.let { "主要有效硬度 · ${it.source.label}" } ?: "主要有效硬度",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (primary == null) {
                    Text("未设硬度", style = MaterialTheme.typography.titleMediumEmphasized)
                } else {
                    HardnessValueText(
                        value = primary.rawValue,
                        size = HardnessTextSize.Large,
                        color = content,
                        showCalculatedRange = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesCard(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(painterResource(R.drawable.ic_notes), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmallEmphasized)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
