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

internal fun sortCompounds(
    compounds: List<RubberCompound>,
    order: ManualSortOrder,
): List<RubberCompound> = when (order) {
    ManualSortOrder.COMPOUND_CODE -> compounds.sortedWith(
        compareBy<RubberCompound> {
            it.compoundCode.uppercase(Locale.ROOT)
        }.thenBy(RubberCompound::id),
    )

    ManualSortOrder.PART_NUMBER -> compounds.sortedWith(
        compareBy<RubberCompound> {
            it.partNumberSortKey
        }.thenBy {
            it.compoundCode.uppercase(Locale.ROOT)
        }.thenBy(RubberCompound::id),
    )

    ManualSortOrder.HARDNESS -> compounds.sortedWith(
        compareBy<RubberCompound> { it.hardnessSortValue }
            .thenBy { it.compoundCode.uppercase(Locale.ROOT) }
            .thenBy(RubberCompound::id),
    )
}

private val hardnessNumberPattern = Regex("""\d+(?:\.\d+)?""")

internal data class RubberCompound(
    val id: Long,
    val compoundCode: String,
    val testPieceCureTemperatureC: String = "",
    val testPieceCureTimeMinutes: String = "",
    val customBlockCureTimeMinutes: String = "",
    val groups: List<PartSpecificationGroup> = emptyList(),
    val notes: String = "",
) {
    val totalPartCount: Int
        get() = groups.sumOf { it.partNumbers.size }

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

    val partNumberSortKey: String
        get() = groups
            .asSequence()
            .flatMap { it.partNumbers.asSequence() }
            .map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .minOrNull()
            ?: "\uFFFF"

    val hardnessSortValue: Double
        get() = groups
            .asSequence()
            .mapNotNull {
                it.recommendation?.value
                    ?: it.hardness.testPieceHardness
            }
            .mapNotNull { value ->
                hardnessNumberPattern
                    .find(value)
                    ?.value
                    ?.toDoubleOrNull()
            }
            .minOrNull()
            ?: Double.POSITIVE_INFINITY

    fun matches(query: String): Boolean {
        if (query.isBlank()) {
            return true
        }

        return compoundCode.contains(query, ignoreCase = true) ||
            testPieceCureTemperatureC.contains(query, ignoreCase = true) ||
            testPieceCureTimeMinutes.contains(query, ignoreCase = true) ||
            blockCureTimeMinutes.contains(query, ignoreCase = true) ||
            notes.contains(query, ignoreCase = true) ||
            groups.any { it.matches(query) }
    }
}

internal data class PartSpecificationGroup(
    val id: Long,
    val standardNumber: String = "",
    val partNumbers: List<String>,
    val hardness: HardnessSet = HardnessSet(),
    val productCategory: String = "",
    val color: String = "",
    val tensileStrength: String = "",
    val elongation: String = "",
    val notes: String = "",
) {
    val recommendation: HardnessRecommendation?
        get() = when {
            hardness.blockHardness.isNotBlank() -> HardnessRecommendation(
                value = hardness.blockHardness,
                source = HardnessSource.BLOCK_STANDARD,
            )

            hardness.productHardness.isNotBlank() -> HardnessRecommendation(
                value = hardness.productHardness,
                source = HardnessSource.PRODUCT_STANDARD,
            )

            else -> null
        }

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
)

internal data class HardnessRecommendation(
    val value: String,
    val source: HardnessSource,
)

internal enum class HardnessSource {
    BLOCK_STANDARD,
    PRODUCT_STANDARD,
}

private fun compactNumber(value: Double): String =
    BigDecimal.valueOf(value)
        .stripTrailingZeros()
        .toPlainString()
