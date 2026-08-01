package com.wmdhs.shorea

import java.math.BigDecimal

internal sealed interface ParsedHardness {
    val raw: String

    data class Tolerance(
        override val raw: String,
        val nominal: String,
        val upperDeviation: String,
        val lowerDeviation: String,
        val lowerLimit: String,
        val upperLimit: String,
    ) : ParsedHardness

    data class Range(
        override val raw: String,
        val lower: String,
        val upper: String,
    ) : ParsedHardness

    data class Minimum(
        override val raw: String,
        val value: String,
        val inclusive: Boolean,
    ) : ParsedHardness

    data class Maximum(
        override val raw: String,
        val value: String,
        val inclusive: Boolean,
    ) : ParsedHardness

    data class Exact(
        override val raw: String,
        val value: String,
    ) : ParsedHardness

    data class Raw(override val raw: String) : ParsedHardness
}

internal fun parseHardness(rawValue: String): ParsedHardness {
    val raw = rawValue.trim()
    if (raw.isBlank()) return ParsedHardness.Raw(raw)

    tolerancePattern.matchEntire(raw)?.let { match ->
        val nominal = match.groupValues[1].toDecimalOrNull()
        val upper = match.groupValues[2].toDecimalOrNull()
        val lower = match.groupValues[3].toDecimalOrNull()
        if (nominal != null && upper != null && lower != null) {
            return ParsedHardness.Tolerance(
                raw = raw,
                nominal = nominal.compact(),
                upperDeviation = upper.compact(),
                lowerDeviation = lower.compact(),
                lowerLimit = nominal.subtract(lower).compact(),
                upperLimit = nominal.add(upper).compact(),
            )
        }
    }

    rangePattern.matchEntire(raw)?.let { match ->
        val lower = match.groupValues[1].toDecimalOrNull()
        val upper = match.groupValues[2].toDecimalOrNull()
        if (lower != null && upper != null) {
            return ParsedHardness.Range(
                raw = raw,
                lower = lower.min(upper).compact(),
                upper = lower.max(upper).compact(),
            )
        }
    }

    minimumPattern.matchEntire(raw)?.let { match ->
        return ParsedHardness.Minimum(
            raw = raw,
            value = match.groupValues[2].toDecimalOrNull()?.compact()
                ?: match.groupValues[2],
            inclusive = match.groupValues[1] in setOf("≥", ">="),
        )
    }

    maximumPattern.matchEntire(raw)?.let { match ->
        return ParsedHardness.Maximum(
            raw = raw,
            value = match.groupValues[2].toDecimalOrNull()?.compact()
                ?: match.groupValues[2],
            inclusive = match.groupValues[1] in setOf("≤", "<="),
        )
    }

    exactPattern.matchEntire(raw)?.let { match ->
        return ParsedHardness.Exact(
            raw = raw,
            value = match.groupValues[1].toDecimalOrNull()?.compact()
                ?: match.groupValues[1],
        )
    }

    return ParsedHardness.Raw(raw)
}

private fun String.toDecimalOrNull(): BigDecimal? =
    runCatching { BigDecimal(this) }.getOrNull()

private fun BigDecimal.compact(): String = stripTrailingZeros().toPlainString()

private val number = "(\\d+(?:\\.\\d+)?)"
private val tolerancePattern = Regex("^$number\\s*\\+\\s*$number\\s*[-−]\\s*$number$")
private val rangePattern = Regex("^$number\\s*[-−–—~～至]\\s*$number$")
private val minimumPattern = Regex("^(≥|>=|＞|>)\\s*$number$")
private val maximumPattern = Regex("^(≤|<=|＜|<)\\s*$number$")
private val exactPattern = Regex("^$number$")
