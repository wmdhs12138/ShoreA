package com.wmdhs.shorea.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wmdhs.shorea.R
import com.wmdhs.shorea.data.model.InspectionEntry
import com.wmdhs.shorea.data.model.ManualViewMode
import com.wmdhs.shorea.data.model.RubberCompound
import com.wmdhs.shorea.data.model.UNNAMED_STANDARD
import com.wmdhs.shorea.data.model.cureConditionText
import com.wmdhs.shorea.ui.components.HardnessAnswerPill
import com.wmdhs.shorea.ui.components.SecondaryHardnessRow

@Composable
internal fun ManualHomeCard(
    item: ManualHomeItem,
    viewMode: ManualViewMode,
    searchQuery: String,
    stackedHeader: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        when (item) {
            is ManualHomeItem.Inspection -> when (viewMode) {
                ManualViewMode.LIST ->
                    InspectionListContent(item.compound, item.entry, searchQuery, stackedHeader)
                ManualViewMode.COMPACT ->
                    InspectionCompactContent(item.compound, item.entry, searchQuery, stackedHeader)
                ManualViewMode.DETAILED ->
                    InspectionDetailedContent(item.compound, item.entry, searchQuery, stackedHeader)
            }

            is ManualHomeItem.EmptyCompound -> EmptyCompoundContent(item.compound)
        }
    }
}

@Composable
private fun InspectionListContent(
    compound: RubberCompound,
    entry: InspectionEntry,
    searchQuery: String,
    stacked: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InspectionAnswerHeader(compound, entry, stacked)
        SearchMatchHint(entry, searchQuery, includeHiddenHardness = true)
    }
}

@Composable
private fun InspectionAnswerHeader(
    compound: RubberCompound,
    entry: InspectionEntry,
    stacked: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PartNumberHeadline(entry.partNumbers)
                HardnessAnswerPill(entry.effectiveHardness, Modifier.align(Alignment.End))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                PartNumberHeadline(entry.partNumbers, Modifier.weight(1f))
                HardnessAnswerPill(entry.effectiveHardness)
            }
        }
        CompoundIdentity(compound)
        StandardLine(entry.standardNumber, maxLines = 2)
    }
}

@Composable
private fun PartNumberHeadline(
    partNumbers: List<String>,
    modifier: Modifier = Modifier,
) {
    if (partNumbers.isEmpty()) {
        Text(
            text = "未关联部品号",
            modifier = modifier,
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "部品号 ${partNumbers.joinToString("、")}"
        },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        partNumbers.forEach { partNumber ->
            Text(
                text = partNumber,
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompoundIdentity(compound: RubberCompound) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append("胶料号 ${compound.compoundCode}")
                    if (compound.baseRubber.isNotBlank()) append("；基胶 ${compound.baseRubber}")
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_science),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = compound.compoundCode,
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
        if (compound.baseRubber.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = compound.baseRubber,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun StandardLine(standardNumber: String, maxLines: Int) {
    Text(
        text = "依据 · ${standardNumber.ifBlank { UNNAMED_STANDARD }}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun InspectionCompactContent(
    compound: RubberCompound,
    entry: InspectionEntry,
    searchQuery: String,
    stacked: Boolean,
) {
    val partNumbers = prioritizePartNumbers(entry.partNumbers, searchQuery)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (stacked) {
            CompactPartNumberHeadline(partNumbers)
            HardnessAnswerPill(entry.effectiveHardness, Modifier.align(Alignment.End))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactPartNumberHeadline(partNumbers, Modifier.weight(1f))
                HardnessAnswerPill(entry.effectiveHardness)
            }
        }
        CompoundIdentity(compound)
        StandardLine(entry.standardNumber, maxLines = 1)
        SearchMatchHint(entry, searchQuery, includeHiddenHardness = true)
    }
}

@Composable
private fun CompactPartNumberHeadline(
    partNumbers: List<String>,
    modifier: Modifier = Modifier,
) {
    val primary = partNumbers.firstOrNull()
    val titleStyle = MaterialTheme.typography.titleLargeEmphasized
    FlowRow(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = if (partNumbers.isEmpty()) {
                "未关联部品号"
            } else {
                "部品号 ${partNumbers.joinToString("、")}"
            }
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = primary ?: "未关联部品号",
            style = titleStyle,
            color = if (primary == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            autoSize = if (primary != null && primary.length > 12) {
                TextAutoSize.StepBased(
                    minFontSize = 14.sp,
                    maxFontSize = titleStyle.fontSize,
                    stepSize = 1.sp,
                )
            } else {
                null
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (partNumbers.size > 1) {
            Text(
                text = "+${partNumbers.size - 1}",
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 搜索命中某个部品号时，把它排到最前，紧凑视图只显示第一个。 */
private fun prioritizePartNumbers(partNumbers: List<String>, searchQuery: String): List<String> {
    val query = searchQuery.trim()
    if (query.isEmpty()) return partNumbers
    val matching = partNumbers.firstOrNull { it.contains(query, ignoreCase = true) }
        ?: return partNumbers
    return listOf(matching) + partNumbers.filterNot { it == matching }
}

@Composable
private fun InspectionDetailedContent(
    compound: RubberCompound,
    entry: InspectionEntry,
    searchQuery: String,
    stacked: Boolean,
) {
    val secondaryHardness = entry.hardness.secondaryValues()
    val facts = buildList {
        entry.productCategory.takeIf(String::isNotBlank)?.let { add("产品类别" to it) }
        entry.color.takeIf(String::isNotBlank)?.let { add("颜色" to it) }
        entry.tensileStrength.takeIf(String::isNotBlank)?.let { add("拉伸强度" to it) }
        entry.elongation.takeIf(String::isNotBlank)?.let { add("伸长率" to it) }
    }
    val hasNotes = entry.notes.isNotBlank() || compound.notes.isNotBlank()
    val hasDetails = secondaryHardness.isNotEmpty() || compound.hasCureCondition ||
        facts.isNotEmpty() || hasNotes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        InspectionAnswerHeader(compound, entry, stacked)
        if (hasDetails) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (secondaryHardness.isNotEmpty()) {
            DetailSection("其他硬度") { SecondaryHardnessRow(secondaryHardness) }
        }
        if (compound.hasCureCondition) {
            DetailSection("硫化条件") {
                Text(
                    text = "试片 ${cureConditionText(compound.testPieceCureTemperatureC, compound.testPieceCureTimeMinutes)}" +
                        " · 硬度块 ${compound.blockCureTimeMinutes.ifBlank { "时间未填" }} min",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (facts.isNotEmpty()) {
            DetailSection("检测信息") { FactGrid(facts) }
        }
        if (hasNotes) {
            DetailSection("备注") {
                if (entry.notes.isNotBlank()) Fact("检测标准", entry.notes)
                if (compound.notes.isNotBlank()) Fact("胶料", compound.notes)
            }
        }
        SearchMatchHint(entry, searchQuery)
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
internal fun FactGrid(facts: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        facts.chunked(2).forEach { rowFacts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                rowFacts.forEach { (label, value) -> Fact(label, value, Modifier.weight(1f)) }
                if (rowFacts.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMediumEmphasized)
    }
}

@Composable
private fun EmptyCompoundContent(compound: RubberCompound) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(compound.compoundCode, style = MaterialTheme.typography.titleLargeEmphasized)
        if (compound.baseRubber.isNotBlank()) {
            Text(
                text = "基胶 · ${compound.baseRubber}",
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "尚未添加检测标准",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (compound.testPieceCureTemperatureC.isNotBlank() || compound.testPieceCureTimeMinutes.isNotBlank()) {
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

/** 搜索词命中卡片上未显示的字段时，提示命中位置。 */
@Composable
private fun SearchMatchHint(
    entry: InspectionEntry,
    query: String,
    includeHiddenHardness: Boolean = false,
) {
    if (query.isBlank()) return
    val matches = buildList {
        if (includeHiddenHardness) {
            entry.hardness.secondaryValues().forEach { (source, value) ->
                if (value.contains(query, ignoreCase = true)) add("${source.label}硬度")
            }
        }
        if (entry.productCategory.contains(query, ignoreCase = true)) add("产品类别")
        if (entry.color.contains(query, ignoreCase = true)) add("颜色")
        if (entry.tensileStrength.contains(query, ignoreCase = true)) add("拉伸强度")
        if (entry.elongation.contains(query, ignoreCase = true)) add("伸长率")
        if (entry.notes.contains(query, ignoreCase = true)) add("备注")
    }
    if (matches.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "命中：${matches.joinToString("、")}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
