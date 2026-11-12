package com.wmdhs.shorea.ui.home

import androidx.compose.runtime.Immutable
import com.wmdhs.shorea.data.model.HardnessManual
import com.wmdhs.shorea.data.model.InspectionEntry
import com.wmdhs.shorea.data.model.ManualSortOrder
import com.wmdhs.shorea.data.model.RubberCompound
import java.util.Locale

/** 首页的一张卡片：一条检测标准，或一个尚未添加标准的胶料。 */
@Immutable
internal sealed interface ManualHomeItem {
    val stableKey: String
    val compound: RubberCompound

    data class Inspection(
        override val compound: RubberCompound,
        val entry: InspectionEntry,
    ) : ManualHomeItem {
        override val stableKey: String = inspectionKey(entry.id)
    }

    data class EmptyCompound(
        override val compound: RubberCompound,
    ) : ManualHomeItem {
        override val stableKey: String = emptyCompoundKey(compound.id)
    }
}

internal fun inspectionKey(entryId: Long): String = "inspection:$entryId"

internal fun emptyCompoundKey(compoundId: Long): String = "empty-compound:$compoundId"

/** 按搜索词过滤并排序；数据量大时在后台线程调用。 */
internal fun HardnessManual.visibleHomeItems(
    query: String,
    order: ManualSortOrder,
): List<ManualHomeItem> {
    val normalizedQuery = query.trim()
    val items = toHomeItems().let { all ->
        if (normalizedQuery.isEmpty()) all else all.filter { it.matches(normalizedQuery) }
    }
    return sortHomeItems(items, order)
}

internal fun HardnessManual.toHomeItems(): List<ManualHomeItem> {
    val compoundsById = compounds.associateBy(RubberCompound::id)
    val compoundsWithEntries = mutableSetOf<Long>()
    val inspectionItems = inspectionEntries.mapNotNull { entry ->
        val compound = compoundsById[entry.compoundId] ?: return@mapNotNull null
        compoundsWithEntries += compound.id
        ManualHomeItem.Inspection(compound = compound, entry = entry)
    }
    val emptyItems = compounds
        .filter { it.id !in compoundsWithEntries }
        .map { ManualHomeItem.EmptyCompound(it) }
    return inspectionItems + emptyItems
}

internal fun ManualHomeItem.matches(query: String): Boolean = when (this) {
    is ManualHomeItem.Inspection -> compound.matches(query) || entry.matches(query)
    is ManualHomeItem.EmptyCompound -> compound.matches(query)
}

internal fun sortHomeItems(
    items: List<ManualHomeItem>,
    order: ManualSortOrder,
): List<ManualHomeItem> = when (order) {
    ManualSortOrder.COMPOUND_CODE -> items.sortedWith(
        compareBy<ManualHomeItem> { it.compoundCodeSortKey() }
            .thenBy { it.typeSortRank() }
            .thenBy { it.standardSortKey() }
            .thenBy { it.partSortKey() }
            .thenBy { it.entitySortId() },
    )

    ManualSortOrder.PART_NUMBER -> items.sortedWith(
        compareBy<ManualHomeItem> { it.partSortRank() }
            .thenBy { it.partSortKey() }
            .thenBy { it.compoundCodeSortKey() }
            .thenBy { it.standardSortKey() }
            .thenBy { it.entitySortId() },
    )

    ManualSortOrder.HARDNESS -> items.sortedWith(
        compareBy<ManualHomeItem> { it.hardnessSortRank() }
            .thenBy { it.hardnessSortValue() }
            .thenBy { it.compoundCodeSortKey() }
            .thenBy { it.partSortKey() }
            .thenBy { it.entitySortId() },
    )
}

private fun ManualHomeItem.compoundCodeSortKey(): String =
    compound.compoundCode.trim().uppercase(Locale.ROOT)

private fun ManualHomeItem.typeSortRank(): Int = when (this) {
    is ManualHomeItem.Inspection -> 0
    is ManualHomeItem.EmptyCompound -> 1
}

private fun ManualHomeItem.standardSortKey(): String = when (this) {
    is ManualHomeItem.Inspection -> entry.standardNumber.trim().uppercase(Locale.ROOT)
    is ManualHomeItem.EmptyCompound -> ""
}

private fun ManualHomeItem.partSortKey(): String = when (this) {
    is ManualHomeItem.Inspection -> entry.partNumberSortKey
    is ManualHomeItem.EmptyCompound -> ""
}

private fun ManualHomeItem.partSortRank(): Int = when (this) {
    is ManualHomeItem.Inspection -> if (partSortKey().isBlank()) 1 else 0
    is ManualHomeItem.EmptyCompound -> 2
}

private fun ManualHomeItem.hardnessSortRank(): Int = when (this) {
    is ManualHomeItem.Inspection -> when {
        entry.effectiveHardness?.numericValue != null -> 0
        entry.effectiveHardness != null -> 1
        else -> 2
    }

    is ManualHomeItem.EmptyCompound -> 3
}

private fun ManualHomeItem.hardnessSortValue(): Double = when (this) {
    is ManualHomeItem.Inspection -> entry.effectiveHardness?.numericValue ?: 0.0
    is ManualHomeItem.EmptyCompound -> 0.0
}

private fun ManualHomeItem.entitySortId(): Long = when (this) {
    is ManualHomeItem.Inspection -> entry.id
    is ManualHomeItem.EmptyCompound -> compound.id
}
