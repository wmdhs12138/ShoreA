package com.wmdhs.shorea

import java.math.BigDecimal
import java.util.Locale

internal enum class ManualSortOrder(
    val label: String,
) {
    COMPOUND_CODE("胶料号"),
    PART_NUMBER("部品号"),
    HARDNESS("硬度"),
}

internal enum class ManualViewMode(
    val label: String,
) {
    LIST("列表"),
    COMPACT("紧凑"),
    DETAILED("详细"),
}

internal data class HardnessManual(
    val compounds: List<RubberCompound> = emptyList(),
    val inspectionEntries: List<InspectionEntry> = emptyList(),
) {
    fun findCompound(compoundId: Long): RubberCompound? =
        compounds.firstOrNull { it.id == compoundId }

    fun findEntry(entryId: Long): InspectionEntry? =
        inspectionEntries.firstOrNull { it.id == entryId }

    fun entriesForCompound(compoundId: Long): List<InspectionEntry> =
        inspectionEntries.filter { it.compoundId == compoundId }

    fun deleteEntry(entryId: Long): HardnessManual = copy(
        inspectionEntries = inspectionEntries.filterNot { it.id == entryId },
    )

    fun deleteCompound(compoundId: Long): HardnessManual = copy(
        compounds = compounds.filterNot { it.id == compoundId },
        inspectionEntries = inspectionEntries.filterNot {
            it.compoundId == compoundId
        },
    )
}

internal data class RubberCompound(
    val id: Long,
    val compoundCode: String,
    val testPieceCureTemperatureC: String = "",
    val testPieceCureTimeMinutes: String = "",
    val customBlockCureTimeMinutes: String = "",
    val notes: String = "",
) {
    val blockCureTimeMinutes: String
        get() {
            if (customBlockCureTimeMinutes.isNotBlank()) {
                return customBlockCureTimeMinutes
            }

            val testPieceMinutes = testPieceCureTimeMinutes.toDoubleOrNull()
                ?: return ""

            return compactNumber(testPieceMinutes * 2.0)
        }

    val usesCustomBlockCureTime: Boolean
        get() = customBlockCureTimeMinutes.isNotBlank()
}

internal data class InspectionEntry(
    val id: Long,
    val compoundId: Long,
    val standardNumber: String = "",
    val partNumbers: List<String>,
    val hardness: HardnessSet = HardnessSet(),
    val productCategory: String = "",
    val color: String = "",
    val tensileStrength: String = "",
    val elongation: String = "",
    val notes: String = "",
) {
    val partNumberSortKey: String
        get() = normalizePartNumbers(partNumbers)
            .firstOrNull()
            ?.uppercase(Locale.ROOT)
            .orEmpty()

    val effectiveHardness: EffectiveHardness?
        get() = hardness.effectiveHardness

    fun matches(query: String): Boolean {
        if (query.isBlank()) {
            return true
        }

        return standardNumber.contains(query, ignoreCase = true) ||
            partNumbers.any { it.contains(query, ignoreCase = true) } ||
            hardness.testPieceHardness.contains(query, ignoreCase = true) ||
            hardness.blockHardness.contains(query, ignoreCase = true) ||
            hardness.productHardness.contains(query, ignoreCase = true) ||
            productCategory.contains(query, ignoreCase = true) ||
            color.contains(query, ignoreCase = true) ||
            tensileStrength.contains(query, ignoreCase = true) ||
            elongation.contains(query, ignoreCase = true) ||
            notes.contains(query, ignoreCase = true)
    }
}

internal data class HardnessSet(
    val testPieceHardness: String = "",
    val blockHardness: String = "",
    val productHardness: String = "",
) {
    val effectiveHardness: EffectiveHardness?
        get() {
            val candidate = when {
                blockHardness.isNotBlank() -> {
                    blockHardness to HardnessSource.BLOCK_STANDARD
                }

                productHardness.isNotBlank() -> {
                    productHardness to HardnessSource.PRODUCT_STANDARD
                }

                testPieceHardness.isNotBlank() -> {
                    testPieceHardness to HardnessSource.TEST_PIECE
                }

                else -> return null
            }
            val rawValue = candidate.first.trim()
            return EffectiveHardness(
                rawValue = rawValue,
                numericValue = extractHardnessSortValue(rawValue),
                source = candidate.second,
            )
        }

    fun normalized(): HardnessSet = copy(
        testPieceHardness = testPieceHardness.trim(),
        blockHardness = blockHardness.trim(),
        productHardness = productHardness.trim(),
    )

    fun allValues(): List<Pair<HardnessSource, String>> = buildList {
        testPieceHardness.trim().takeIf(String::isNotEmpty)?.let {
            add(HardnessSource.TEST_PIECE to it)
        }
        blockHardness.trim().takeIf(String::isNotEmpty)?.let {
            add(HardnessSource.BLOCK_STANDARD to it)
        }
        productHardness.trim().takeIf(String::isNotEmpty)?.let {
            add(HardnessSource.PRODUCT_STANDARD to it)
        }
    }
}

internal enum class HardnessSource {
    TEST_PIECE,
    BLOCK_STANDARD,
    PRODUCT_STANDARD,
}

internal val HardnessSource.label: String
    get() = when (this) {
        HardnessSource.TEST_PIECE -> "试片"
        HardnessSource.BLOCK_STANDARD -> "硬度块"
        HardnessSource.PRODUCT_STANDARD -> "产品"
    }

internal data class EffectiveHardness(
    val rawValue: String,
    val numericValue: Double?,
    val source: HardnessSource,
)

internal data class DisplayHardness(
    val value: String,
    val source: HardnessSource,
    val isPrimary: Boolean,
)

internal fun HardnessSet.displayHardnesses(): List<DisplayHardness> {
    val primarySource = effectiveHardness?.source
    return allValues().map { (source, value) ->
        DisplayHardness(
            value = value,
            source = source,
            isPrimary = source == primarySource,
        )
    }
}

internal fun normalizePartNumbers(values: Iterable<String>): List<String> {
    val seen = linkedSetOf<String>()
    return values
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter { value -> seen.add(value.uppercase(Locale.ROOT)) }
        .sortedWith(
            compareBy<String> { it.uppercase(Locale.ROOT) }
                .thenBy { it },
        )
}

internal sealed interface ManualHomeItem {
    val stableKey: String

    data class Inspection(
        val compound: RubberCompound,
        val entry: InspectionEntry,
    ) : ManualHomeItem {
        override val stableKey: String
            get() = "inspection:${entry.id}"
    }

    data class EmptyCompound(
        val compound: RubberCompound,
    ) : ManualHomeItem {
        override val stableKey: String
            get() = "empty-compound:${compound.id}"
    }
}

internal fun HardnessManual.toHomeItems(): List<ManualHomeItem> {
    val compoundsById = compounds.associateBy(RubberCompound::id)
    val compoundsWithEntries = mutableSetOf<Long>()
    val inspectionItems = inspectionEntries.mapNotNull { entry ->
        val compound = compoundsById[entry.compoundId]
            ?: return@mapNotNull null
        compoundsWithEntries += compound.id
        ManualHomeItem.Inspection(compound = compound, entry = entry)
    }
    val emptyItems = compounds
        .asSequence()
        .filter { it.id !in compoundsWithEntries }
        .map(::asEmptyHomeItem)
        .toList()
    return inspectionItems + emptyItems
}

private fun asEmptyHomeItem(compound: RubberCompound): ManualHomeItem =
    ManualHomeItem.EmptyCompound(compound)

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
    compoundForSort().compoundCode.trim().uppercase(Locale.ROOT)

private fun ManualHomeItem.typeSortRank(): Int = when (this) {
    is ManualHomeItem.Inspection -> 0
    is ManualHomeItem.EmptyCompound -> 1
}

private fun ManualHomeItem.standardSortKey(): String = when (this) {
    is ManualHomeItem.Inspection -> standardNumber.trim().uppercase(Locale.ROOT)
    is ManualHomeItem.EmptyCompound -> ""
}

private fun ManualHomeItem.partSortKey(): String = when (this) {
    is ManualHomeItem.Inspection -> partNumberSortKey
    is ManualHomeItem.EmptyCompound -> ""
}

private fun ManualHomeItem.partSortRank(): Int = when (this) {
    is ManualHomeItem.Inspection -> if (partSortKey().isBlank()) 1 else 0
    is ManualHomeItem.EmptyCompound -> 2
}

private fun ManualHomeItem.hardnessSortRank(): Int = when (this) {
    is ManualHomeItem.Inspection -> when {
        effectiveHardness?.numericValue != null -> 0
        effectiveHardness != null -> 1
        else -> 2
    }

    is ManualHomeItem.EmptyCompound -> 3
}

private fun ManualHomeItem.hardnessSortValue(): Double = when (this) {
    is ManualHomeItem.Inspection -> effectiveHardness?.numericValue ?: 0.0
    is ManualHomeItem.EmptyCompound -> 0.0
}

private fun ManualHomeItem.entitySortId(): Long = when (this) {
    is ManualHomeItem.Inspection -> entry.id
    is ManualHomeItem.EmptyCompound -> compound.id
}

private fun ManualHomeItem.compoundForSort(): RubberCompound = when (this) {
    is ManualHomeItem.Inspection -> compound
    is ManualHomeItem.EmptyCompound -> compound
}

private val ManualHomeItem.Inspection.compoundCode: String
    get() = compound.compoundCode

private val ManualHomeItem.Inspection.standardNumber: String
    get() = entry.standardNumber

private val ManualHomeItem.Inspection.partNumberSortKey: String
    get() = entry.partNumberSortKey

private val ManualHomeItem.Inspection.effectiveHardness: EffectiveHardness?
    get() = entry.effectiveHardness

internal fun ManualHomeItem.matches(query: String): Boolean = when (this) {
    is ManualHomeItem.Inspection -> {
        compound.matchesCompoundFields(query) || entry.matches(query)
    }

    is ManualHomeItem.EmptyCompound -> compound.matchesCompoundFields(query)
}

internal fun RubberCompound.matchesCompoundFields(query: String): Boolean {
    if (query.isBlank()) {
        return true
    }
    return compoundCode.contains(query, ignoreCase = true) ||
        testPieceCureTemperatureC.contains(query, ignoreCase = true) ||
        testPieceCureTimeMinutes.contains(query, ignoreCase = true) ||
        blockCureTimeMinutes.contains(query, ignoreCase = true) ||
        notes.contains(query, ignoreCase = true)
}

internal fun HardnessManual.partCountForCompound(compoundId: Long): Int =
    entriesForCompound(compoundId).sumOf { it.partNumbers.size }

internal fun HardnessManual.entryCountForCompound(compoundId: Long): Int =
    entriesForCompound(compoundId).size

internal val HardnessManual.totalPartAssociationCount: Int
    get() = inspectionEntries.sumOf { it.partNumbers.size }

internal fun validateManual(manual: HardnessManual): Result<HardnessManual> =
    runCatching {
        val duplicateCompoundId = manual.compounds
            .groupBy(RubberCompound::id)
            .entries
            .firstOrNull { it.value.size > 1 }
        require(duplicateCompoundId == null) {
            "手册中存在重复的胶料 ID：${duplicateCompoundId?.key}"
        }

        val duplicateCompoundCode = manual.compounds
            .groupBy { it.compoundCode.trim().uppercase(Locale.ROOT) }
            .entries
            .firstOrNull { it.key.isNotEmpty() && it.value.size > 1 }
        require(duplicateCompoundCode == null) {
            "手册中存在重复的胶料号：${duplicateCompoundCode?.value?.first()?.compoundCode}"
        }

        manual.compounds.forEach { compound ->
            require(compound.id >= 0L) {
                "胶料 ${compound.compoundCode} 的 ID 非法"
            }
            require(compound.compoundCode.trim().isNotEmpty()) {
                "存在空胶料号的胶料资料"
            }
        }

        val compoundsById = manual.compounds.associateBy(RubberCompound::id)
        val duplicateEntryId = manual.inspectionEntries
            .groupBy(InspectionEntry::id)
            .entries
            .firstOrNull { it.value.size > 1 }
        require(duplicateEntryId == null) {
            "手册中存在重复的检测标准 ID：${duplicateEntryId?.key}"
        }

        val usedStandards = mutableSetOf<Pair<Long, String>>()
        val usedParts = mutableMapOf<Pair<Long, String>, InspectionEntry>()
        manual.inspectionEntries.forEach { entry ->
            require(entry.id >= 0L) {
                "检测标准 ${entry.standardNumber.ifBlank { "未填写标准号" }} 的 ID 非法"
            }
            val compound = compoundsById[entry.compoundId]
            require(compound != null) {
                "检测标准 ${entry.standardNumber.ifBlank { "未填写标准号" }} 关联了不存在的胶料 ID ${entry.compoundId}"
            }
            val normalizedParts = normalizePartNumbers(entry.partNumbers)
            require(normalizedParts.isNotEmpty()) {
                "胶料 ${compound.compoundCode} 的检测标准 ${entry.standardNumber.ifBlank { "未填写标准号" }} 至少需要一个部品号"
            }
            require(entry.partNumbers == normalizedParts) {
                "胶料 ${compound.compoundCode} 的检测标准 ${entry.standardNumber.ifBlank { "未填写标准号" }} 的部品号未按统一规则规范化"
            }

            val normalizedStandard = entry.standardNumber
                .trim()
                .uppercase(Locale.ROOT)
            if (normalizedStandard.isNotEmpty()) {
                require(usedStandards.add(entry.compoundId to normalizedStandard)) {
                    "胶料 ${compound.compoundCode} 中的标准号 ${entry.standardNumber} 重复"
                }
            }

            normalizedParts.forEach { partNumber ->
                val key = entry.compoundId to partNumber.uppercase(Locale.ROOT)
                val previous = usedParts.putIfAbsent(key, entry)
                require(previous == null) {
                    val previousStandard = previous?.standardNumber
                        ?.ifBlank { "未填写标准号" }
                    "胶料 ${compound.compoundCode} 中的部品号 $partNumber 同时属于检测标准 ${previousStandard} 和 ${entry.standardNumber.ifBlank { "未填写标准号" }}"
                }
            }
        }

        manual
    }

private fun compactNumber(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
