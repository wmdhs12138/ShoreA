package com.wmdhs.shorea.data.model

import androidx.compose.runtime.Immutable
import java.math.BigDecimal
import java.util.Locale

/** 整本硬度块手册：胶料 + 隶属于胶料的检测标准。 */
@Immutable
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

    fun upsertCompound(compound: RubberCompound): HardnessManual =
        if (findCompound(compound.id) == null) {
            copy(compounds = compounds + compound)
        } else {
            copy(compounds = compounds.map { if (it.id == compound.id) compound else it })
        }

    fun upsertEntry(entry: InspectionEntry): HardnessManual =
        if (findEntry(entry.id) == null) {
            copy(inspectionEntries = inspectionEntries + entry)
        } else {
            copy(inspectionEntries = inspectionEntries.map { if (it.id == entry.id) entry else it })
        }

    fun deleteEntry(entryId: Long): HardnessManual = copy(
        inspectionEntries = inspectionEntries.filterNot { it.id == entryId },
    )

    fun deleteCompound(compoundId: Long): HardnessManual = copy(
        compounds = compounds.filterNot { it.id == compoundId },
        inspectionEntries = inspectionEntries.filterNot { it.compoundId == compoundId },
    )

    fun partCountForCompound(compoundId: Long): Int =
        entriesForCompound(compoundId).sumOf { it.partNumbers.size }

    fun entryCountForCompound(compoundId: Long): Int =
        inspectionEntries.count { it.compoundId == compoundId }

    val totalPartAssociationCount: Int
        get() = inspectionEntries.sumOf { it.partNumbers.size }
}

@Immutable
internal data class RubberCompound(
    val id: Long,
    val compoundCode: String,
    val baseRubber: String = "",
    val testPieceCureTemperatureC: String = "",
    val testPieceCureTimeMinutes: String = "",
    val customBlockCureTimeMinutes: String = "",
    val notes: String = "",
) {
    /** 硬度块硫化时间：未自定义时按试片时间 ×2。 */
    val blockCureTimeMinutes: String = if (customBlockCureTimeMinutes.isNotBlank()) {
        customBlockCureTimeMinutes
    } else {
        testPieceCureTimeMinutes.toDoubleOrNull()
            ?.let { compactNumber(it * 2.0) }
            .orEmpty()
    }

    val usesCustomBlockCureTime: Boolean = customBlockCureTimeMinutes.isNotBlank()

    val hasCureCondition: Boolean
        get() = testPieceCureTemperatureC.isNotBlank() ||
            testPieceCureTimeMinutes.isNotBlank() ||
            blockCureTimeMinutes.isNotBlank()

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        return compoundCode.contains(query, ignoreCase = true) ||
            baseRubber.contains(query, ignoreCase = true) ||
            testPieceCureTemperatureC.contains(query, ignoreCase = true) ||
            testPieceCureTimeMinutes.contains(query, ignoreCase = true) ||
            blockCureTimeMinutes.contains(query, ignoreCase = true) ||
            notes.contains(query, ignoreCase = true)
    }
}

@Immutable
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
    val partNumberSortKey: String = partNumbers
        .firstOrNull()
        ?.uppercase(Locale.ROOT)
        .orEmpty()

    val effectiveHardness: EffectiveHardness? = hardness.effectiveHardness

    /** 用于提示、对话框的简短名称。 */
    val displayTitle: String
        get() = standardNumber.ifBlank { partNumbers.joinToString("、") }

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
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

@Immutable
internal data class HardnessSet(
    val testPieceHardness: String = "",
    val blockHardness: String = "",
    val productHardness: String = "",
) {
    /** 主要有效硬度：硬度块 > 产品 > 试片。 */
    val effectiveHardness: EffectiveHardness? = run {
        val candidate = when {
            blockHardness.isNotBlank() -> blockHardness to HardnessSource.BLOCK_STANDARD
            productHardness.isNotBlank() -> productHardness to HardnessSource.PRODUCT_STANDARD
            testPieceHardness.isNotBlank() -> testPieceHardness to HardnessSource.TEST_PIECE
            else -> null
        }
        candidate?.let { (value, source) ->
            val rawValue = value.trim()
            EffectiveHardness(
                rawValue = rawValue,
                numericValue = extractHardnessSortValue(rawValue),
                source = source,
            )
        }
    }

    private val values: List<Pair<HardnessSource, String>> = buildList {
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

    fun normalized(): HardnessSet = copy(
        testPieceHardness = testPieceHardness.trim(),
        blockHardness = blockHardness.trim(),
        productHardness = productHardness.trim(),
    )

    fun allValues(): List<Pair<HardnessSource, String>> = values

    /** 除主要有效硬度以外的其他硬度。 */
    fun secondaryValues(): List<Pair<HardnessSource, String>> {
        val primary = effectiveHardness?.source
        return values.filter { it.first != primary }
    }
}

internal enum class HardnessSource(val label: String) {
    TEST_PIECE("试片"),
    BLOCK_STANDARD("硬度块"),
    PRODUCT_STANDARD("产品"),
}

@Immutable
internal data class EffectiveHardness(
    val rawValue: String,
    val numericValue: Double?,
    val source: HardnessSource,
)

/** 去空白、按大小写不敏感去重并排序。 */
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

/** 编辑器中多行/逗号/分号/空格分隔的部品号文本。 */
internal fun parsePartNumbers(rawValue: String): List<String> = normalizePartNumbers(
    rawValue.split(Regex("[\\s,，;；]+")),
)

internal fun compactNumber(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

internal fun cureConditionText(temperature: String, time: String): String {
    val temperatureText = temperature.takeIf(String::isNotBlank)?.let { "$it℃" } ?: "温度未填"
    val timeText = time.takeIf(String::isNotBlank)?.let { "$it min" } ?: "时间未填"
    return "$temperatureText × $timeText"
}
