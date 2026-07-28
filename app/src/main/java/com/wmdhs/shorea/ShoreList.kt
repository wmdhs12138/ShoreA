package com.wmdhs.shorea

import java.math.BigDecimal

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
